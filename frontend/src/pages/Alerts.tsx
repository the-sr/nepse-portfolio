import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import {
  BellRing,
  BellOff,
  Clock3,
  Plus,
  Radio,
  Target,
  Trash2,
  Zap,
} from 'lucide-react';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatDateTime, formatNpr, formatPercent, pnlClass } from '@/lib/format';
import { cn } from '@/lib/cn';
import { useAlertStream } from '@/lib/streams';
import Card from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import EmptyState from '@/components/ui/EmptyState';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeletons';
import type { AlertEvent, AlertItem, AlertType, Stock } from '@/types';

const schema = z.object({
  symbol: z.string().min(1, 'Choose a symbol'),
  type: z.enum(['ABOVE', 'BELOW', 'PERCENT_CHANGE']),
  threshold: z.number().positive('Threshold must be greater than zero'),
});

type FormValues = z.infer<typeof schema>;

const TYPE_LABELS: Record<string, string> = {
  ABOVE: 'Rises above',
  BELOW: 'Falls below',
  PERCENT_CHANGE: 'Moves by',
};

function thresholdText(alert: AlertItem) {
  return alert.type === 'PERCENT_CHANGE'
    ? `±${alert.threshold}%`
    : formatNpr(alert.threshold);
}

export default function Alerts() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  const { data: alerts = [], isLoading } = useQuery<AlertItem[]>({
    queryKey: ['alerts'],
    queryFn: async () => (await api.get('/alerts')).data,
  });

  const { data: events = [] } = useQuery<AlertEvent[]>({
    queryKey: ['alert-events'],
    queryFn: async () => (await api.get('/alerts/events')).data,
  });

  const { data: stocks = [] } = useQuery<Stock[]>({
    queryKey: ['stocks', 'all'],
    queryFn: async () => (await api.get('/market/stocks')).data,
  });

  useAlertStream((event) => {
    toast.info(event.message);
    void queryClient.invalidateQueries({ queryKey: ['alerts'] });
    void queryClient.invalidateQueries({ queryKey: ['alert-events'] });
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { type: 'ABOVE', threshold: 0, symbol: '' },
  });

  const createMutation = useMutation({
    mutationFn: async (values: FormValues) => api.post('/alerts', values),
    onSuccess: () => {
      toast.success('Alert armed');
      void queryClient.invalidateQueries({ queryKey: ['alerts'] });
      reset({ type: 'ABOVE', threshold: 0, symbol: '' });
      setOpen(false);
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not create the alert')),
  });

  const toggleMutation = useMutation({
    mutationFn: async (alert: AlertItem) =>
      api.put(`/alerts/${alert.id}`, { active: !alert.active }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not update the alert')),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/alerts/${id}`),
    onSuccess: () => {
      toast.success('Alert removed');
      void queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not remove the alert')),
  });

  const activeCount = useMemo(() => alerts.filter((alert) => alert.active).length, [alerts]);

  return (
    <div>
      <PageHeader
        title="Price alerts"
        subtitle="Watch a level or a daily move. Alerts are evaluated after every scrape and streamed live."
        actions={(
          <Button onClick={() => setOpen(true)}>
            <Plus className="h-4 w-4" /> New alert
          </Button>
        )}
      />

      <div className="mb-4 flex flex-wrap gap-2 text-xs">
        <span className="chip bg-brand-500/10 text-brand-700 dark:text-brand-300">
          <Radio className="h-3 w-3" /> {activeCount} armed
        </span>
        <span className="chip bg-slate-500/10 text-slate-600 dark:text-slate-300">
          <Zap className="h-3 w-3" /> {events.length} recent triggers
        </span>
        <span className="chip bg-slate-500/10 text-slate-600 dark:text-slate-300">
          <Clock3 className="h-3 w-3" /> Checked every scrape cycle
        </span>
      </div>

      <div className="grid gap-4 xl:grid-cols-5">
        <div className="space-y-4 xl:col-span-3">
          {isLoading ? (
            <div className="card p-5"><Skeleton className="h-40 w-full" /></div>
          ) : alerts.length === 0 ? (
            <Card className="p-5">
              <EmptyState
                icon={<BellRing className="h-6 w-6" />}
                title="No alerts armed"
                description="Set a level above or below the market, or a daily percentage move you don't want to miss."
                action={<Button onClick={() => setOpen(true)}>Create your first alert</Button>}
              />
            </Card>
          ) : (
            alerts.map((alert) => (
              <Card key={alert.id} className={cn('p-4', alert.triggered && 'border-amber-500/30')}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-start gap-3">
                    <span className={cn(
                      'mt-0.5 rounded-xl p-2',
                      alert.active ? 'bg-brand-500/10 text-brand-600 dark:text-brand-300' : 'bg-slate-500/10 text-slate-400',
                    )}>
                      {alert.active ? <BellRing className="h-4 w-4" /> : <BellOff className="h-4 w-4" />}
                    </span>
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-extrabold text-slate-900 dark:text-white">{alert.symbol}</span>
                        <Badge variant={alert.triggered ? 'warn' : alert.active ? 'brand' : 'neutral'}>
                          {alert.triggered ? 'Triggered' : alert.active ? 'Armed' : 'Paused'}
                        </Badge>
                      </div>
                      <p className="text-[11px] text-slate-400">{alert.companyName}</p>
                      <p className="mt-1.5 text-sm text-slate-600 dark:text-slate-300">
                        {TYPE_LABELS[alert.type]}{' '}
                        <span className="font-bold text-slate-900 dark:text-white">{thresholdText(alert)}</span>
                      </p>
                      <p className="mt-0.5 text-[11px] text-slate-400">
                        LTP {alert.ltp != null ? formatNpr(alert.ltp) : '—'}
                        {alert.changePercent != null && (
                          <span className={cn('ml-2 font-semibold', pnlClass(alert.changePercent))}>
                            {formatPercent(alert.changePercent)}
                          </span>
                        )}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => toggleMutation.mutate(alert)}
                      className="rounded-lg border border-slate-200 px-3 py-1.5 text-[11px] font-semibold text-slate-600 transition hover:border-brand-500/40 hover:text-brand-600 dark:border-white/10 dark:text-slate-300"
                    >
                      {alert.active ? 'Pause' : 'Re-arm'}
                    </button>
                    <button
                      onClick={() => deleteMutation.mutate(alert.id)}
                      className="rounded-lg p-1.5 text-slate-400 transition hover:bg-rose-500/10 hover:text-rose-500"
                      aria-label={`Delete alert for ${alert.symbol}`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
                {alert.lastTriggeredAt && (
                  <p className="mt-3 border-t border-slate-100 pt-2 text-[10px] text-slate-400 dark:border-white/5">
                    Last triggered {formatDateTime(alert.lastTriggeredAt)} · edit or re-arm to trigger again
                  </p>
                )}
              </Card>
            ))
          )}
        </div>

        <Card className="p-5 xl:col-span-2">
          <SectionTitle
            title="Trigger feed"
            subtitle="Live events from your armed alerts"
            action={<Target className="h-4 w-4 text-brand-500" />}
          />
          {events.length === 0 ? (
            <EmptyState
              icon={<Zap className="h-6 w-6" />}
              title="Nothing triggered yet"
              description="When a level is crossed, the event appears here and streams to this browser."
            />
          ) : (
            <div className="relative space-y-4 before:absolute before:bottom-2 before:left-[5px] before:top-2 before:w-px before:bg-slate-200 dark:before:bg-white/10">
              {events.map((event) => (
                <div key={event.id} className="relative pl-5">
                  <span className="absolute left-0 top-1.5 h-2.5 w-2.5 rounded-full border-2 border-white bg-amber-500 dark:border-ink-850" />
                  <p className="text-xs font-semibold text-slate-800 dark:text-slate-100">
                    {event.symbol} · {formatNpr(event.price)}
                  </p>
                  <p className="mt-0.5 text-[11px] leading-relaxed text-slate-500 dark:text-slate-400">
                    {event.message}
                  </p>
                  <p className="mt-0.5 text-[10px] text-slate-400">{formatDateTime(event.createdAt)}</p>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <Card className="mt-6 p-5">
        <SectionTitle title="How alerts are evaluated" />
        <div className="grid gap-3 text-xs sm:grid-cols-3">
          <div className="rounded-xl bg-slate-50 p-3 dark:bg-white/5">
            <p className="font-semibold text-slate-700 dark:text-slate-200">Above / below</p>
            <p className="mt-1 text-slate-500 dark:text-slate-400">
              Compares the last traded price with your threshold on every successful scrape.
            </p>
          </div>
          <div className="rounded-xl bg-slate-50 p-3 dark:bg-white/5">
            <p className="font-semibold text-slate-700 dark:text-slate-200">Daily move</p>
            <p className="mt-1 text-slate-500 dark:text-slate-400">
              Fires when the absolute change from the previous close reaches your percentage.
            </p>
          </div>
          <div className="rounded-xl bg-slate-50 p-3 dark:bg-white/5">
            <p className="font-semibold text-slate-700 dark:text-slate-200">One shot</p>
            <p className="mt-1 text-slate-500 dark:text-slate-400">
              Each alert fires once. Pause and re-arm it when you want another notification.
            </p>
          </div>
        </div>
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Create a price alert"
        description="We check this against the next scraped NEPSE tick."
        size="sm"
      >
        <form onSubmit={handleSubmit((values) => createMutation.mutate(values))} className="space-y-4">
          <div>
            <label className="label" htmlFor="alert-symbol">Symbol</label>
            <input
              id="alert-symbol"
              list="alert-symbols"
              placeholder="NICA"
              className="input uppercase"
              {...register('symbol')}
            />
            <datalist id="alert-symbols">
              {stocks.map((stock) => (
                <option key={stock.id} value={stock.symbol}>{stock.companyName}</option>
              ))}
            </datalist>
            {errors.symbol && <p className="mt-1 text-xs text-rose-500">{errors.symbol.message}</p>}
          </div>

          <div>
            <label className="label" htmlFor="alert-type">Condition</label>
            <select id="alert-type" className="input" {...register('type')}>
              <option value="ABOVE">Price rises above</option>
              <option value="BELOW">Price falls below</option>
              <option value="PERCENT_CHANGE">Daily move exceeds (±%)</option>
            </select>
          </div>

          <div>
            <label className="label" htmlFor="alert-threshold">Threshold</label>
            <input
              id="alert-threshold"
              type="number"
              step="0.01"
              min={0.01}
              className="input"
              {...register('threshold', { valueAsNumber: true })}
            />
            {errors.threshold && <p className="mt-1 text-xs text-rose-500">{errors.threshold.message}</p>}
          </div>

          <div className="flex justify-end gap-3 pt-1">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" loading={createMutation.isPending}>Arm alert</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
