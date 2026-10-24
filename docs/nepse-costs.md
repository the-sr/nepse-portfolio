# NEPSE transaction costs & tax (research, verified 2026-10-23)

All figures below were verified against broker fee pages and Nepali tax-practice
sources in October 2026. **Rates change** — they live in admin/user config, not
in code. Sources: Crystal — [trading-commission-and-fees](https://crystalsecurities.com/trading-commission-and-fees/); Imperial/Linch (same tiers); [Hamroshare calculator](https://hamroshare.com.np/market-tools/calculator); [NepseUncle](https://nepseuncle.com/share-calculator); [Nexus Advisory tax calculator](https://nexusadvisory.com.np/tools/share-tax-calculator/);
[Stock Brokers Association cost-value notice](https://www.sharesansar.com/newsdetail/stock-brokers-association-urges-investors-to-submit-their-cost-values-with-t1-find-full-notice);
[NMB fees & CGT](https://nimbstock.com.np/knowledge/fees-commission-and-cgt/); Budget 2083/84 CGT coverage (Sharesansar/social reporting).

## 1. What a NEPSE trade actually costs (secondary market)

The broker's quoted "commission" bundles three components:

| Component | Share of quoted % |
|---|---|
| Broker | 79.4% |
| NEPSE exchange commission | 20.0% |
| SEBON regulatory fee | 0.6% |

So the **tiered total-commission %** already includes NEPSE + SEBON. Separate items:

| Item | Buy | Sell | Rate |
|---|---|---|---|
| Total commission (tiers) | ✓ | ✓ | Rs ≤2,500 → **Rs 10 flat**; 2,501–50,000 → **0.36%**; 50,001–500,000 → **0.33%**; 500,001–2,000,000 → **0.31%**; >2,000,000 → 0.30% |
| SEBON transaction fee | ✓ | ✓ | **0.015%** of transaction amount |
| DP (CDS) charge | ✓ only free transfers record | ✓/transfers | **Rs 25 per scrip per day** — once per day even when a scrip is sold several times that day |
| CGT (capital gains tax) | — | ✓ | withheld by broker on the net gain |

Note the DP rule carefully: the charge is once per (broker, scrip, calendar
day) — so the cost engine aggregates it that way, not per trade.

## 2. CGT — the law is weighted average cost (not FIFO)

Income Tax Act 2058 **§95क(2)**: the capital gain on shares is computed using
the **weighted average cost of acquisition**. Nepali tax calculators and
brokers confirm: *"Tax is worked out on your weighted average cost, not on the
price of the particular lot you sold."*

Consequences the app adopts:

- **Lot ledger (quantity)** = FIFO — which shares remain and how old they are
  (used for holding period and for quantity/sell feasibility).
- **Cost & tax (money)** = per-scrip weighted average; corporate actions update
  the average:
  - **BUY/AUCTION**: (price + buy-side fees) joins the WAC pool
  - **IPO/FPO/RIGHT**: subscription/offer price joins the pool, no fees
  - **BONUS**: free shares join at Rs 0 → **dilutes** the average (the bonus
    TDS the company already withheld at issue is inherited info, not cost)
  - **SELL**: removes qty at the current WAC; proceeds net of sell fees
  - same-day trades apply in chronological order (date, then trade id), so an
    intraday buy before a sell affects the same sell's WAC
- **Holding period** for the slab = age of the **earliest remaining lot** of
  that scrip (standard broker practice).
- **Slabs (2083/84 budget, active)**: ≤365 days → **5%**; >365 days → **3.75%**.
  Losses don't create tax (rate applied to positive gain only). Old default
  slabs (7.5%/5%) were the pre-2083/84 values — configuration now holds the
  current ones and remains editable per broker statement.

## 3. Transaction types and how each is booked

| Type | Cost basis joining the WAC pool | Fees charged at booking |
|---|---|---|
| `BUY` | price + (total commission + SEBON 0.015%) | computed from rate table |
| `AUCTION` | price + same fees as BUY (brokers may add their own policy; configurable) | computed like BUY |
| `IPO` / `FPO` | subscription/offer price | **none** (no broker, no SEBON, no DP on allotment) |
| `RIGHT` | offer price (usually face value) | **none** |
| `BONUS` | **Rs 0** (free shares dilute the average) | **none**; note the TDS the company already took |
| `SELL` | — | total commission + SEBON 0.015% + DP Rs 25/scrip/day + CGT on net gain |

## 4. How the app lets you control all of it

Three layers, most specific wins:

```
values typed on the trade itself  >  your portfolio settings  >  admin global rates  >  verified defaults (below)
```

Verified defaults (also seeded into the DB by migration V3):

```json
{
  "commissionTiers": [
    {"upTo": 2500,     "percent": null, "flat": 10},
    {"upTo": 50000,    "percent": 0.36, "flat": null},
    {"upTo": 500000,   "percent": 0.33, "flat": null},
    {"upTo": 2000000,  "percent": 0.31, "flat": null},
    {"upTo": null,     "percent": 0.30, "flat": null}
  ],
  "sebonFeePercent": 0.015,
  "dpChargePerScripPerDay": 25,
  "cgtShortRate": 5.0,
  "cgtLongRate": 3.75,
  "cgtLongThresholdDays": 365,
  "buyFeesIncludedInBasis": true
}
```

Users edit their own schedule from **Portfolio → Charges & tax** (the button
next to "Add trade"); the modal is pre-filled with the effective schedule and
has a "Reset to defaults" action. When SEBON/budget law changes again, admins
edit the global row and users can override their own — no code change needed,
and historic trades keep the amounts they were booked with (fees are
snapshotted on the trade row at creation).

> Rates in force at the time of a trade are the ones a broker deducts, so the
> engine resolves the config **at the trade date** only for new entries; it
> never re-prices history.

Disclaimer: this document and the app's tax math are educational aids, not
advice from a licensed tax practitioner. Confirm anything you file with your
broker/CDS statement or a CA.
