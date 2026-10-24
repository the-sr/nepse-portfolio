import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { RotateCcw } from 'lucide-react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import type { TradingRates as RatesType, CommissionTier } from '@/types';

const EMPTY_TIER: CommissionTier = { upTo: null, percent: null, flat: null };

export default function PortfolioSettingsModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [rates, setRates] = useState<RatesType | null>(null);

  const { data: effective } = useQuery<RatesType>({
    queryKey: ['trading-rates-effective'],
    queryFn: async () => (await api.get('/settings/trading-rates/effective')).data,
    enabled: open,
  });

  const { data: defaults } = useQuery<RatesType>({
    queryKey: ['trading-rates-defaults'],
    queryFn: async () => (await api.get('/settings/trading-rates/defaults')).data,
    enabled: open,
  });

  useEffect(() => {
    if (open && effective) {
      setRates(JSON.parse(JSON.stringify(effective)));
    }
  }, [open, effective]);

  const save = useMutation({
    mutationFn: async (payload: RatesType) => (await api.put('/settings/trading-rates', payload)).data,
    onSuccess: (saved: RatesType) => {
      toast.success('Charges & tax settings saved');
      setRates(saved);
      void queryClient.invalidateQueries({ queryKey: ['trading-rates-effective'] });
      void queryClient.invalidateQueries({ queryKey: ['trading-rates-defaults'] });
      void queryClient.invalidateQueries({ queryKey: ['trade-preview'] });
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      void queryClient.invalidateQueries({ queryKey: ['cgt'] });
      void queryClient.invalidateQueries({ queryKey: ['portfolio-history'] });
      void queryClient.invalidateQueries({ queryKey: ['realized-gains'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      void queryClient.invalidateQueries({ queryKey: ['opportunities'] });
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not save the rates')),
  });

  const resetToDefaults = () => {
    if (!defaults) return;
    if (window.confirm('Reset to verified default charges and tax rates?')) {
      setRates(JSON.parse(JSON.stringify(defaults)));
    }
  };

  if (!rates) {
    return (
      <Modal open={open} onClose={onClose} title="Charges & tax" description="Loading your cost schedule…">
        <div />
      </Modal>
    );
  }

  const setField = (field: keyof RatesType, value: string) => {
    const num = value === '' ? null : Number(value);
    setRates({ ...rates, [field]: num } as RatesType);
  };

  const tiers = rates.commissionTiers ?? [];

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Charges & tax"
      description="Commission tiers, SEBON fee, DP charge and CGT slabs — applied to all trades."
      size="lg"
    >
      <div className="max-h-[70vh] space-y-5 overflow-y-auto pr-1">
        <div>
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-white">Commission tiers</h3>
            <Button
              variant="ghost"
              type="button"
              className="h-8 px-2 text-xs"
              onClick={() => setRates({ ...rates, commissionTiers: [...tiers, EMPTY_TIER] })}
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
                {tiers.map((tier, index) => (
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
                          const next = [...tiers];
                          next[index] = { ...tier, upTo: value };
                          setRates({ ...rates, commissionTiers: next });
                        }}
                      />
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
                          const next = [...tiers];
                          next[index] = { ...tier, percent: value };
                          setRates({ ...rates, commissionTiers: next });
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
                          const next = [...tiers];
                          next[index] = { ...tier, flat: value };
                          setRates({ ...rates, commissionTiers: next });
                        }}
                      />
                    </td>
                    <td className="table-td text-right">
                      <Button
                        variant="ghost"
                        type="button"
                        onClick={() => setRates({ ...rates, commissionTiers: tiers.filter((_, i) => i !== index) })}
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
              <label className="label" htmlFor="sebon">SEBON fee (%)</label>
              <input
                id="sebon"
                type="number"
                step="0.001"
                className="input"
                value={rates.sebonFeePercent ?? ''}
                onChange={(e) => setField('sebonFeePercent', e.target.value)}
              />
              <p className="mt-1 text-[10px] text-slate-400">Charged on both buys and sells.</p>
            </div>
            <div>
              <label className="label" htmlFor="dp">DP charge (NPR/scrip/day)</label>
              <input
                id="dp"
                type="number"
                step="0.01"
                className="input"
                value={rates.dpChargePerScripPerDay ?? ''}
                onChange={(e) => setField('dpChargePerScripPerDay', e.target.value)}
              />
              <p className="mt-1 text-[10px] text-slate-400">Once per scrip per day on sells.</p>
            </div>
          </div>
        </div>

        <div>
          <h3 className="mb-2 text-sm font-semibold text-slate-900 dark:text-white">Capital gains tax</h3>
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <label className="label" htmlFor="short-rate">Short-term CGT (%)</label>
              <input
                id="short-rate"
                type="number"
                step="0.05"
                className="input"
                value={rates.cgtShortRate ?? ''}
                onChange={(e) => setField('cgtShortRate', e.target.value)}
              />
              <p className="mt-1 text-[10px] text-slate-400">Within the threshold.</p>
            </div>
            <div>
              <label className="label" htmlFor="long-rate">Long-term CGT (%)</label>
              <input
                id="long-rate"
                type="number"
                step="0.05"
                className="input"
                value={rates.cgtLongRate ?? ''}
                onChange={(e) => setField('cgtLongRate', e.target.value)}
              />
            </div>
            <div>
              <label className="label" htmlFor="threshold">Long-term threshold (days)</label>
              <input
                id="threshold"
                type="number"
                step="1"
                className="input"
                value={rates.cgtLongThresholdDays ?? ''}
                onChange={(e) => setField('cgtLongThresholdDays', e.target.value)}
              />
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between rounded-xl bg-slate-50 px-4 py-3 dark:bg-white/5">
          <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
            <input
              type="checkbox"
              checked={rates.buyFeesIncludedInBasis ?? true}
              onChange={(e) => setRates({ ...rates, buyFeesIncludedInBasis: e.target.checked })}
            />
            Include buy fees in cost basis
          </label>
          <Button variant="ghost" type="button" className="h-8" onClick={resetToDefaults} disabled={!defaults}>
            <RotateCcw className="h-4 w-4" /> Reset to defaults
          </Button>
        </div>

        <div className="flex justify-end gap-3 pt-1">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="button" loading={save.isPending} onClick={() => save.mutate(rates)}>
            Save
          </Button>
        </div>
      </div>
    </Modal>
  );
}
