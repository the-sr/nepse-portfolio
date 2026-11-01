import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import {
  BellRing,
  Database,
  LogOut,
  Moon,
  Palette,
  Percent,
  RotateCcw,
  Save,
  ShieldAlert,
  SlidersHorizontal,
  Sun,
  UserRound,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatNpr } from '@/lib/format';
import { cn } from '@/lib/cn';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeletons';
import type { Settings as SettingsType, TradingRates as RatesType, CommissionTier } from '@/types';

const schema = z.object({
  lossThresholdMinor: z.number().min(0.5).max(50),
  lossThresholdMajor: z.number().min(1).max(70),
  lossThresholdAggressive: z.number().min(1.5).max(90),
  baseBuyAmount: z.number().min(100),
  maxPositionPercent: z.number().min(1).max(100),
  maxCapitalPerStock: z.number().min(100),
}).superRefine((values, ctx) => {
  if (!(values.lossThresholdMinor < values.lossThresholdMajor
    && values.lossThresholdMajor < values.lossThresholdAggressive)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['lossThresholdMajor'],
      message: 'Use increasing thresholds: minor < major < aggressive',
    });
    }
});

type FormValues = z.infer<typeof schema>;

const EMPTY_TIER: CommissionTier = { upTo: null, percent: null, flat: null };

export default function Settings() {
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();
  const queryClient = useQueryClient();

  const isAdmin = user?.role === 'ADMIN';

  const [adminRates, setAdminRates] = useState<RatesType | null>(null);

  const { data: settings, isLoading } = useQuery<SettingsType>({
    queryKey: ['settings'],
    queryFn: async () => (await api.get('/settings')).data,
  });

  const { data: adminGlobalRates } = useQuery<RatesType>({
    queryKey: ['admin-trading-rates'],
    queryFn: async () => (await api.get('/admin/trading-rates')).data,
    enabled: isAdmin,
  });

  const { data: defaults } = useQuery<RatesType>({
    queryKey: ['trading-rates-defaults'],
    queryFn: async () => (await api.get('/settings/trading-rates/defaults')).data,
    enabled: isAdmin,
  });

  useEffect(() => {
    if (isAdmin && adminGlobalRates) {
      setAdminRates(JSON.parse(JSON.stringify(adminGlobalRates)));
    }
  }, [isAdmin, adminGlobalRates]);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      lossThresholdMinor: 5,
      lossThresholdMajor: 10,
      lossThresholdAggressive: 15,
      baseBuyAmount: 10000,
      maxPositionPercent: 25,
      maxCapitalPerStock: 100000,
    },
  });

  useEffect(() => {
    if (settings) {
      reset({
        lossThresholdMinor: settings.lossThresholdMinor,
        lossThresholdMajor: settings.lossThresholdMajor,
        lossThresholdAggressive: settings.lossThresholdAggressive,
        baseBuyAmount: settings.baseBuyAmount,
        maxPositionPercent: settings.maxPositionPercent,
        maxCapitalPerStock: settings.maxCapitalPerStock,
      });
    }
  }, [settings, reset]);

  const mutation = useMutation({
    mutationFn: async (values: FormValues) =>
      api.put('/settings', { ...values, theme }),
    onSuccess: (_data, values) => {
      toast.success('Settings saved');
      reset(values);
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
      void queryClient.invalidateQueries({ queryKey: ['opportunities'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not save settings')),
  });

  const saveAdminRates = useMutation({
    mutationFn: async (payload: RatesType) => (await api.put('/admin/trading-rates', payload)).data,
    onSuccess: (saved: RatesType) => {
      toast.success('Global trading rates updated for all users');
      setAdminRates(saved);
      void queryClient.invalidateQueries({ queryKey: ['admin-trading-rates'] });
      void queryClient.invalidateQueries({ queryKey: ['trading-rates-effective'] });
      void queryClient.invalidateQueries({ queryKey: ['trade-preview'] });
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      void queryClient.invalidateQueries({ queryKey: ['cgt'] });
      void queryClient.invalidateQueries({ queryKey: ['portfolio-history'] });
      void queryClient.invalidateQueries({ queryKey: ['realized-gains'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      void queryClient.invalidateQueries({ queryKey: ['opportunities'] });
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not save global rates')),
  });

  const resetAdminRatesToDefaults = () => {
    if (!defaults) return;
    if (window.confirm('Reset global trading rates to verified defaults? This affects all users without personal overrides.')) {
      setAdminRates(JSON.parse(JSON.stringify(defaults)));
    }
  };

  const setAdminField = (field: keyof RatesType, value: string) => {
    if (!adminRates) return;
    const num = value === '' ? null : Number(value);
    setAdminRates({ ...adminRates, [field]: num } as RatesType);
  };

  const values = watch();

  return (
    <div>
      <PageHeader
        title="Settings"
        subtitle="Tune averaging rules and risk limits for your portfolio."
        actions={(
          <Button
            onClick={handleSubmit((data) => mutation.mutate(data))}
            loading={mutation.isPending}
            disabled={!isDirty}
          >
            <Save className="h-4 w-4" /> Save changes
          </Button>
        )}
      />

      <div className="grid gap-4 xl:grid-cols-3">
        <Card className="p-5">
          <SectionTitle title="Account" subtitle="Your sign-in identity" action={<UserRound className="h-4 w-4 text-brand-500" />} />
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-500 text-sm font-extrabold text-ink-950">
              {(user?.name ?? 'U').split(' ').map((part) => part[0]).slice(0, 2).join('').toUpperCase()}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-bold text-slate-900 dark:text-white">{user?.name}</p>
              <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user?.email}</p>
            </div>
          </div>
          <div className="mt-4 flex items-center justify-between rounded-xl bg-slate-50 px-3 py-2.5 text-xs dark:bg-white/5">
            <span className="text-slate-500 dark:text-slate-400">Access level</span>
            <Badge variant={user?.role === 'ADMIN' ? 'violet' : 'brand'}>{user?.role}</Badge>
          </div>
          <Button variant="ghost" className="mt-4 w-full" onClick={() => void logout()}>
            <LogOut className="h-4 w-4" /> Sign out
          </Button>
        </Card>

        <Card className="p-5">
          <SectionTitle title="Appearance" subtitle="Saved on this device" action={<Palette className="h-4 w-4 text-brand-500" />} />
          <div className="grid grid-cols-2 gap-3">
            <button
              onClick={() => setTheme('dark')}
              className={cn(
                'rounded-xl border p-4 text-left transition',
                theme === 'dark'
                  ? 'border-brand-500/60 bg-brand-500/5 ring-1 ring-brand-500/30'
                  : 'border-slate-200 hover:border-brand-500/30 dark:border-white/10',
              )}
            >
              <Moon className="h-5 w-5 text-brand-500" />
              <p className="mt-2 text-sm font-semibold text-slate-800 dark:text-white">Dark</p>
              <p className="text-[10px] text-slate-400">Low-light trading desk</p>
            </button>
            <button
              onClick={() => setTheme('light')}
              className={cn(
                'rounded-xl border p-4 text-left transition',
                theme === 'light'
                  ? 'border-brand-500/60 bg-brand-500/5 ring-1 ring-brand-500/30'
                  : 'border-slate-200 hover:border-brand-500/30 dark:border-white/10',
              )}
            >
              <Sun className="h-5 w-5 text-amber-500" />
              <p className="mt-2 text-sm font-semibold text-slate-800 dark:text-white">Light</p>
              <p className="text-[10px] text-slate-400">Daylight readability</p>
            </button>
          </div>
          <div className="mt-4 rounded-xl bg-slate-50 p-3 text-[11px] text-slate-500 dark:bg-white/5 dark:text-slate-400">
            The theme applies instantly and is included the next time you save settings.
          </div>
        </Card>

        <Card className="p-5">
          <SectionTitle title="Data & alerts" subtitle="How this app gets its numbers" action={<Database className="h-4 w-4 text-brand-500" />} />
          <div className="space-y-2 text-xs">
            <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
              <span className="text-slate-500 dark:text-slate-400">Primary feed</span>
              <span className="font-semibold text-slate-800 dark:text-slate-100">Sharesansar</span>
            </div>
            <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
              <span className="text-slate-500 dark:text-slate-400">Fallback</span>
              <span className="font-semibold text-slate-800 dark:text-slate-100">Cached prices</span>
            </div>
            <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
              <span className="text-slate-500 dark:text-slate-400">Alert checks</span>
              <span className="flex items-center gap-1 font-semibold text-slate-800 dark:text-slate-100">
                <BellRing className="h-3 w-3 text-brand-500" /> Every scrape
              </span>
            </div>
          </div>
          <p className="mt-3 text-[11px] leading-relaxed text-slate-400">
            NEPSE has no reliable public historical API. Seeded daily bars are clearly labelled until the
            scraper has collected enough real sessions.
          </p>
        </Card>
      </div>

      {isLoading ? (
        <div className="mt-6 card p-5"><Skeleton className="h-56 w-full" /></div>
      ) : (
        <form onSubmit={handleSubmit((data) => mutation.mutate(data))} className="mt-6 space-y-4">
          <Card className="p-5">
            <SectionTitle
              title="Averaging rules"
              subtitle="Loss depth that triggers a tranche, and the base amount to deploy"
              action={<SlidersHorizontal className="h-4 w-4 text-brand-500" />}
            />
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div>
                <label className="label" htmlFor="minor">Minor loss trigger (%)</label>
                <input id="minor" type="number" step="0.1" className="input" {...register('lossThresholdMinor', { valueAsNumber: true })} />
                <p className="mt-1 text-[10px] text-slate-400">Base {formatNpr(values.baseBuyAmount * 1.5)} tranche</p>
              </div>
              <div>
                <label className="label" htmlFor="major">Major loss trigger (%)</label>
                <input id="major" type="number" step="0.1" className="input" {...register('lossThresholdMajor', { valueAsNumber: true })} />
                <p className="mt-1 text-[10px] text-slate-400">Base {formatNpr(values.baseBuyAmount * 2)} tranche</p>
              </div>
              <div>
                <label className="label" htmlFor="aggressive">Aggressive trigger (%)</label>
                <input id="aggressive" type="number" step="0.1" className="input" {...register('lossThresholdAggressive', { valueAsNumber: true })} />
                <p className="mt-1 text-[10px] text-slate-400">Base {formatNpr(values.baseBuyAmount * 2.5)} tranche</p>
              </div>
              <div>
                <label className="label" htmlFor="base">Base buy amount (NPR)</label>
                <input id="base" type="number" step="100" className="input" {...register('baseBuyAmount', { valueAsNumber: true })} />
              </div>
            </div>
            {errors.lossThresholdMajor && (
              <p className="mt-2 text-xs text-rose-500">{errors.lossThresholdMajor.message}</p>
            )}
          </Card>

          <Card className="p-5">
            <SectionTitle
              title="Risk controls"
              subtitle="Hard caps applied to averaging suggestions"
              action={<ShieldAlert className="h-4 w-4 text-amber-500" />}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="position">Max position (%)</label>
                <input id="position" type="number" step="1" className="input" {...register('maxPositionPercent', { valueAsNumber: true })} />
                <p className="mt-1 text-[10px] text-slate-400">No single holding above this share of value.</p>
              </div>
              <div>
                <label className="label" htmlFor="capital">Max capital per stock</label>
                <input id="capital" type="number" step="1000" className="input" {...register('maxCapitalPerStock', { valueAsNumber: true })} />
                <p className="mt-1 text-[10px] text-slate-400">Ceiling for one averaging plan.</p>
              </div>
            </div>
          </Card>

          <div className="flex justify-end">
            <Button type="submit" loading={mutation.isPending} disabled={!isDirty}>
              <Save className="h-4 w-4" /> Save settings
            </Button>
          </div>
        </form>
      )}
  {isAdmin && (
    <Card className="mt-6 p-5">
      <SectionTitle
        title="Global trading charges & tax"
        subtitle="Admin defaults applied to all users who haven't set personal overrides"
        action={<Percent className="h-4 w-4 text-brand-500" />}
      />
      {!adminRates ? (
        <Skeleton className="h-64 w-full" />
      ) : (
        <div className="space-y-5">
          <div>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-900 dark:text-white">Commission tiers</h3>
              <Button
                variant="ghost"
                type="button"
                className="h-8 px-2 text-xs"
                onClick={() => setAdminRates({ ...adminRates, commissionTiers: [...(adminRates.commissionTiers ?? []), EMPTY_TIER] })}
              >
                + Add tier
              </Button>
            </div>
            <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-white/10">
              <table className="w-full min-w-[640px]">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 dark:border-white/5 dark:bg-white/[0.02]">
                    <th className="table-th">Amount up to (NPR)</th>
                    <th className="table-th text-right">Percent</th>
                    <th className="table-th text-right">Flat minimum (NPR)</th>
                    <th className="table-th" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-white/5">
                  {(adminRates.commissionTiers ?? []).map((tier, index) => (
                    <tr key={index}>
                      <td className="table-td">
                        <input
                          type="number"
                          step="0.01"
                          min={0}
                          className="input min-w-[7rem]"
                          aria-label="Tier upper bound"
                          value={tier.upTo ?? ''}
                          placeholder="unlimited"
                          onChange={(e) => {
                            const value = e.target.value === '' ? null : Number(e.target.value);
                            const next = [...(adminRates.commissionTiers ?? [])];
                            next[index] = { ...tier, upTo: value };
                            setAdminRates({ ...adminRates, commissionTiers: next });
                          }} />
                      </td>
                      <td className="table-td">
                        <input
                          type="number"
                          step="0.01"
                          min={0}
                          className="input min-w-[7rem]"
                          aria-label="Tier percent"
                          value={tier.percent ?? ''}
                          onChange={(e) => {
                            const value = e.target.value === '' ? null : Number(e.target.value);
                            const next = [...(adminRates.commissionTiers ?? [])];
                            next[index] = { ...tier, percent: value };
                            setAdminRates({ ...adminRates, commissionTiers: next });
                          }}
                        />
                      </td>
                      <td className="table-td">
                        <input
                          type="number"
                          step="0.01"
                          min={0}
                          className="input min-w-[7rem]"
                          aria-label="Tier flat minimum"
                          value={tier.flat ?? ''}
                          onChange={(e) => {
                            const value = e.target.value === '' ? null : Number(e.target.value);
                            const next = [...(adminRates.commissionTiers ?? [])];
                            next[index] = { ...tier, flat: value };
                            setAdminRates({ ...adminRates, commissionTiers: next });
                          }}
                        />
                      </td>
                      <td className="table-td text-right">
                        <Button
                          variant="ghost"
                          type="button"
                          onClick={() => setAdminRates({ ...adminRates, commissionTiers: (adminRates.commissionTiers ?? []).filter((_, i) => i !== index) })}
                        >
                          Remove
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-semibold text-slate-900 dark:text-white">Charges</h3>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="admin-sebon">SEBON fee (%)</label>
                <input
                  id="admin-sebon"
                  type="number"
                  step="0.001"
                  className="input"
                  value={adminRates.sebonFeePercent ?? ''}
                  onChange={(e) => setAdminField('sebonFeePercent', e.target.value)}
                />
                <p className="mt-1 text-[10px] text-slate-400">Charged on both buys and sells.</p>
              </div>
              <div>
                <label className="label" htmlFor="admin-dp">DP charge (NPR/scrip/day)</label>
                <input
                  id="admin-dp"
                  type="number"
                  step="0.01"
                  className="input"
                  value={adminRates.dpChargePerScripPerDay ?? ''}
                  onChange={(e) => setAdminField('dpChargePerScripPerDay', e.target.value)}
                />
                <p className="mt-1 text-[10px] text-slate-400">Once per scrip per day on sells.</p>
              </div>
            </div>
          </div>

          <div>
            <h3 className="mb-2 text-sm font-semibold text-slate-900 dark:text-white">Capital gains tax</h3>
            <div className="grid gap-4 sm:grid-cols-3">
              <div>
                <label className="label" htmlFor="admin-short-rate">Short-term CGT (%)</label>
                <input
                  id="admin-short-rate"
                  type="number"
                  step="0.05"
                  className="input"
                  value={adminRates.cgtShortRate ?? ''}
                  onChange={(e) => setAdminField('cgtShortRate', e.target.value)}
                />
                <p className="mt-1 text-[10px] text-slate-400">Within the threshold.</p>
              </div>
              <div>
                <label className="label" htmlFor="admin-long-rate">Long-term CGT (%)</label>
                <input
                  id="admin-long-rate"
                  type="number"
                  step="0.05"
                  className="input"
                  value={adminRates.cgtLongRate ?? ''}
                  onChange={(e) => setAdminField('cgtLongRate', e.target.value)}
                />
              </div>
              <div>
                <label className="label" htmlFor="admin-threshold">Long-term threshold (days)</label>
                <input
                  id="admin-threshold"
                  type="number"
                  step="1"
                  className="input"
                  value={adminRates.cgtLongThresholdDays ?? ''}
                  onChange={(e) => setAdminField('cgtLongThresholdDays', e.target.value)}
                />
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between rounded-xl bg-slate-50 px-4 py-3 dark:bg-white/5">
            <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
              <input
                type="checkbox"
                checked={adminRates.buyFeesIncludedInBasis ?? true}
                onChange={(e) => setAdminRates({ ...adminRates, buyFeesIncludedInBasis: e.target.checked })}
              />
              Include buy fees in cost basis
            </label>
            <Button variant="ghost" type="button" className="h-8" onClick={resetAdminRatesToDefaults} disabled={!defaults}>
              <RotateCcw className="h-4 w-4" /> Reset to defaults
            </Button>
          </div>

          <div className="flex justify-end gap-3 pt-1">
            <Button type="button" loading={saveAdminRates.isPending} onClick={() => saveAdminRates.mutate(adminRates)}>
              <Save className="h-4 w-4" /> Save global rates
            </Button>
          </div>
        </div>
      )}
    </Card>
  )}

  <Card className="mt-6 p-5">
    <div className="flex items-start gap-3">
      <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-500" />
      <div className="text-xs text-slate-600 dark:text-slate-400">
        <p className="font-semibold text-slate-800 dark:text-slate-200">Educational use only</p>
        <p className="mt-1">
          NEPSE Tracker calculates portfolio metrics and rule-based signals for learning. It does not
          know your tax situation, risk tolerance or investment objectives. Nothing here is financial
          advice. Market data may be delayed, incomplete or simulated.
        </p>
      </div>
    </div>
  </Card>
</div>
  );
}
