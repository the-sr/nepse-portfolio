# Deploying to an Android phone (Termux) — the 24/7 zero-cost server

This guide turns an old Android phone into your always-on host. It replaces
Docker entirely: the pieces run natively in Termux.

**Why this is cheap:** a phone draws ~2–4 W plugged in 24/7 ≈ **2–3 kWh/month**
(a few rupees). A PC at ~100 W ≈ 70+ kWh/month. That's the whole trade.

The ML service is **not** installed on the phone (scikit-learn cannot be
pip-installed on Android's libc). The backend detects this and falls back to
its rule engine automatically — no data loss, just no ML blend layer.

> Recommendations in the app are educational, never financial advice.

---

## 0. What you need

| Thing | Where |
|---|---|
| Old Android phone (8+ GB RAM recommended), charger | — |
| **Termux** + **Termux:Boot** apps | **F-Droid only** (Play Store build is stale + incompatible signing) |
| Wi-Fi that stays on | router setting "keep Wi-Fi on during sleep" if available |

Install both apps, **open the Termux:Boot app once** (it must be launched
manually one time to register its boot permission), open Termux:

```bash
pkg update && pkg upgrade
termux-setup-storage    # accept the permission dialog
```

---

## 1. Run the setup script (does everything)

```bash
pkg install -y git
git clone https://github.com/the-sr/nepse-portfolio.git
cd nepse-portfolio
bash deploy/termux-setup.sh
```

The script is idempotent and installs:

- **PostgreSQL** (data dir `~/pgdata`, database `nepse_portfolio`)
- **openjdk-21** runtime
- A generated `~/nepse/.env` (JWT secret, DB URL, small heap `-Xmx640m`)
- **nginx** serving the frontend on port **3000** and proxying `/api` to the
  Spring Boot backend on 8080 (SSE-friendly: buffering off, long timeouts)
- **Termux:Boot autostart** script (`~/.termux/boot/01-nepse.sh`)
- **Hourly auto-update loop** that pulls new releases silently
- Wake lock (`termux-wake-lock`)

On first boot the backend starts importing the 569-security universe and
backfilling ~260 trading days of price history — the health endpoint may take
several minutes to report `UP`. That's normal.

What lands where:

```
~/nepse/
  nepse-portfolio.jar    # current backend (from GitHub Releases)
  nepse-portfolio.jar.prev   # previous version (rollback)
  web/                   # frontend build
  .env                   # secrets & settings (chmod 600)
  VERSION                # deployed release tag
  backend.log / deploy.log / nginx-error.log
~/pgdata/                # your database — never delete this
```

> Security: the DB runs with Termux-local trust auth and never leaves the
> phone; only nginx's port 3000 is exposed (over the tunnel). The `.env`
> holds the JWT secret — don't paste its contents anywhere.

---

## 2. Make Android leave the server alive (critical)

These three make the difference between "runs for months" and "died overnight":

1. **Disable battery optimization:** Android Settings → Apps → Termux →
   Battery → **Unrestricted** (wording varies by vendor; on some it's
   "Never optimizing / no restrictions"). Also do the same for **Termux:Boot**.
2. **Phantom process killer (Android 12+):** Android may silently cull
   background Java processes, which kills the backend within hours. Fix once
   from a PC (USB debugging enabled, `adb install`-style driver set up):
   ```bash
   adb shell device_config put activity_manager max_phantom_processes 2147483647
   ```
   Re-run this after major Android updates (it can reset).
3. **Battery honesty (24/7 charging):** constant charging can swell old
   batteries. Mitigate: case off, cool spot, out of sun, and if the phone
   offers a built-in charge limit (e.g. Samsung "Protect battery", 60–80%)
   enable it. Treat the battery as sacrificial — that's the point of using an
   old phone. If it heats or bulges, unplug and retire the device.

---

## 3. First release → the phone pulls it

The phone **never builds anything**; GitHub Actions does that for free:

1. On your PC: commit + tag the first release and push:
   ```bash
   git tag v0.1.0 && git push origin main --tags
   ```
2. CI runs backend tests + frontend build and publishes a GitHub Release with
   `nepse-portfolio.jar` + `frontend-dist.zip`.
3. On the phone (or automatically within the hour):
   ```bash
   bash ~/nepse/deploy.sh          # or watch it: bash ~/nepse/deploy.sh --check
   ```

**Releasing a new version later** is the same command with a new tag:
`git tag v0.2.0 && git push origin main --tags`. The phone updates itself.

**Rollback:**
```bash
mv ~/nepse/nepse-portfolio.jar.prev ~/nepse/nepse-portfolio.jar
bash ~/nepse/scripts/stop-backend.sh && bash ~/nepse/scripts/start-backend.sh
echo v0.1.0 > ~/nepse/VERSION
```

---

## 4. Public URL for friends (tunnel)

The phone is on your LAN — the internet can't see it until you tunnel.

**Easiest, instant, no signup (Cloudflare quick tunnel):**
```bash
pkg install cloudflared
cloudflared tunnel --url http://localhost:3000
```
Prints a random `https://…trycloudflare.com` URL. Caveat: the URL is
**randomized per run** — friends must re-fetch the address if the tunnel
restarts.

**Stable URL, free, email signup, no card (ngrok static domain):**
```bash
pkg install ngrok
# one-time: login at ngrok.com → copy your token, then claim a static domain:
ngrok config add-authtoken <your-token>
ngrok http --region=in --hostname=your-name.ngrok-free.app 3000
```

**Make tunnels auto-start after reboot** — add to `~/.termux/boot/02-tunnel.sh`:
```bash
#!/data/data/com.termux/files/usr/bin/sh
cloudflared tunnel --url http://localhost:3000
```
(one for ngrok instead, with your static hostname — the URL survives reboots)

> Note: tunnels carry public HTTPS to your own box. Keep the phone clean
> (no personal accounts on it), and the app's auth (JWT login) is your gate —
> keep strong passwords for admin accounts.

---

## 5. Verify everything

```bash
curl http://127.0.0.1:3000/actuator/health              # {"status":"UP"}
curl "http://127.0.0.1:3000/api/market/quotes?size=3"   # live NEPSE quotes
```

Then open `http://<phone-ip>:3000` in a LAN browser and log in (your existing
user or a fresh one — this is now your production database).

**Run either the phone OR the PC stack — not both.** Both hosts would scrape
NEPSE from the same public IP, doubling the risk of being rate-limited or
blocked. Pick one (the phone, for 24/7 uptime) and stop the other:
`docker compose stop backend` on the PC, or
`bash ~/nepse/scripts/stop-backend.sh` on the phone.

Migration extras for later, if you need them: `pg_dump` from the PC's
`postgres-data` volume to carry over your real portfolio history into the
phone's `~/pgdata` (next section), and the ML container stays PC-side only.

---

## 6. Troubleshooting

| Symptom | Diagnosis | Fix |
|---|---|---|
| `deploy.sh` says "no published release" | CI hasn't run / tag failed | check the Actions tab; re-push tag |
| Health never UP on first boot | Look at `~/nepse/backend.log` | usually universe import in progress; or DB issue → `psql -d nepse_portfolio -c '\dt'` |
| Died after phone reboot | Boot plugin didn't fire | open Termux:Boot app once; re-check `~/.termux/boot/01-nepse.sh` executable |
| Dies hours after boot | battery/phantom killer | Section 2 above |
| 502 from tunnel, backend fine | backend restarting during deploy | wait, retries resolve; or check `backend.log` |
| Data missing after reinstall | wiped `~/pgdata` | restore from pg_dump (PC docker volume `postgres-data` holds the master copy until you promote the phone to primary) |
| Disk fills with logs | `backend.log` grows | `tail -f` if debugging, else `truncate -s 0 ~/nepse/backend.log` periodically |

**Long session tip:** if you're SSH-less, run a controller terminal with
Termux and `tail -f ~/nepse/backend.log` while the setup runs.

---

## 7. Carrying the PC database over to the phone (optional, one shot)

Do this **before** the phone accumulates live data (easiest: right after
setup, before its own scraper/backfill writes anything), otherwise price
history rows will be duplicated:

```bash
# on the PC, dump the current volume's DB:
docker compose exec postgres pg_dump -U nepse nepse_portfolio > nepse_dump.sql

# copy nepse_dump.sql to the phone (adb push, USB file transfer, or any
# file-sync app), then inside Termux:
psql -d nepse_portfolio -f "$HOME/storage/downloads/nepse_dump.sql"
bash ~/nepse/scripts/stop-backend.sh && bash ~/nepse/scripts/start-backend.sh
```

It restores your users, trades and price history exactly as they were on the PC.

---

## 8. Cost summary

| Item | Cost |
|---|---|
| Termux, PostgreSQL, nginx, Java | रु 0 (free software) |
| GitHub Actions CI/CD (public repo) | रु 0 |
| Cloudflare quick tunnel / ngrok static | रु 0 |
| Electricity | ~रु 15–40/month |
| Bank card required | **none** |
