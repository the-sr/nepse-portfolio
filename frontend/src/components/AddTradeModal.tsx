import { useEffect, useMemo, useRef, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Info } from 'lucide-react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import SymbolPicker from '@/components/SymbolPicker';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatNpr, formatPrice } from '@/lib/format';
import type { ChargesPreview, Holding, Stock, TradeType } from '@/types';

const TRADE_TYPES: { value: TradeType; label: string; hint: string }[] = [
  { value: 'BUY', label: 'Buy', hint: 'Secondary-market purchase — commission + SEBON fee apply.' },
  { value: 'SELL', label: 'Sell', hint: 'Commission + SEBON + DP Rs 25/scrip/day + CGT on the gain.' },
  { value: 'IPO', label: 'IPO', hint: 'Allotted at the offer price — no fees.' },
  { value: 'FPO', label: 'FPO', hint: 'Allotted at the offer price — no fees.' },
  { value: 'RIGHT', label: 'Right', hint: 'Right subscription at the offer price — no fees.' },
  { value: 'BONUS', label: 'Bonus', hint: 'Free shares at Rs 0 — dilutes your average cost.' },
  { value: 'AUCTION', label: 'Auction', hint: 'Priced like a buy; brokers may add their own charge.' },
];

const schema = z.object({
  symbol: z.string().min(1, 'Choose a symbol'),
  type: z.enum(['BUY', 'SELL', 'IPO', 'FPO', 'BONUS', 'RIGHT', 'AUCTION']),
  quantity: z.number().int().positive('Quantity must be at least 1'),
  price: z.number().min(0, 'Price cannot be negative'),
  brokerage: z.number().min(0).optional(),
  tax: z.number().min(0).optional(),
  tradeDate: z.string().min(1, 'Pick a date'),
  notes: z.string().max(500).optional(),
});

type FormValues = z.infer<typeof schema>;

function todayInput() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60_000).toISOString().slice(0, 10);
}

const typeHint = (type: TradeType | undefined) =>
  TRADE_TYPES.find((item) => item.value === type)?.hint ?? '';

export default function AddTradeModal({
  open,
  onClose,
  holdings = [],
  defaultSymbol,
}: {
  open: boolean;
  onClose: () => void;
  holdings?: Holding[];
  defaultSymbol?: string;
}) {
  const queryClient = useQueryClient();
  const { data: stocks = [] } = useQuery<Stock[]>({
    queryKey: ['stocks', 'all'],
    queryFn: async () => (await api.get('/market/stocks')).data,
    enabled: open,
  });

  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: 'BUY',
      quantity: 1,
      brokerage: undefined,
      tax: undefined,
      tradeDate: todayInput(),
      notes: '',
      symbol: defaultSymbol ?? '',
      price: 0,
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        type: 'BUY',
        quantity: 1,
        brokerage: undefined,
        tax: undefined,
        tradeDate: todayInput(),
        notes: '',
        symbol: defaultSymbol ?? '',
        price: 0,
      });
    }
  }, [open, defaultSymbol, reset]);

  const symbol = watch('symbol');
  const type = watch('type');
  const quantity = watch('quantity');
  const price = watch('price');

  const selectedStock = useMemo(
    () => stocks.find((stock) => stock.symbol.toLowerCase() === (symbol ?? '').toLowerCase()),
    [stocks, symbol],
  );

  const holding = useMemo(
    () => holdings.find((item) => item.symbol.toLowerCase() === (symbol ?? '').toLowerCase()),
    [holdings, symbol],
  );

  const lastPricedSymbol = useRef<string | null>(null);

  useEffect(() => {
    if (!open) {
      lastPricedSymbol.current = null;
      return;
    }
    if (selectedStock && lastPricedSymbol.current !== selectedStock.symbol && type !== 'BONUS') {
      lastPricedSymbol.current = selectedStock.symbol;
      reset((values) => ({ ...values, price: selectedStock.ltp }));
    }
  }, [open, selectedStock, reset, type]);

  // Bonus shares enter free: no offer price is asked for.
  useEffect(() => {
    if (type === 'BONUS') {
      setValue('price', 0);
    }
  }, [type, setValue]);

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const tradeDate = new Date(`${values.tradeDate}T12:00:00`).toISOString();
      return api.post('/portfolio/trades', {
        symbol: values.symbol,
        type: values.type,
        quantity: values.quantity,
        price: values.price,
        // Omitted fee fields let the backend compute from the rate schedule.
        brokerage: values.brokerage == null ? null : values.brokerage,
        sebonFee: null,
        dpCharge: null,
        tax: values.tax == null ? null : values.tax,
        tradeDate,
        notes: values.notes || null,
      });
    },
    onSuccess: () => {
      toast.success('Trade recorded');
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      void queryClient.invalidateQueries({ queryKey: ['portfolio-history'] });
      void queryClient.invalidateQueries({ queryKey: ['trades'] });
      void queryClient.invalidateQueries({ queryKey: ['cgt'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      void queryClient.invalidateQueries({ queryKey: ['opportunities'] });
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not record the trade')),
  });

  // Live charges preview, debounced so typing doesn't spam the API.
  const [previewInput, setPreviewInput] = useState<string>('');
  useEffect(() => {
    const timer = setTimeout(() => {
      setPreviewInput(JSON.stringify({
        symbol,
        type,
        quantity,
        price,
        brokerage: watch('brokerage') ?? null,
        tax: watch('tax') ?? null,
      }));
    }, 400);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, type, quantity, price, watch('brokerage'), watch('tax')]);

  // react-query requires a strictly boolean `enabled`; a falsy-but-not-boolean
  // value (like the empty symbol string '') throws on render.
  const previewEnabled = useMemo(() => {
    if (!open || !previewInput) {
      return false;
    }
    try {
      const parsed = JSON.parse(previewInput) as {
        symbol?: string; type?: string; quantity?: number; price?: number;
      };
      return Boolean(
        typeof parsed.symbol === 'string' && parsed.symbol.length > 0
        && typeof parsed.quantity === 'number' && parsed.quantity >= 1
        && ((typeof parsed.price === 'number' && parsed.price > 0) || parsed.type === 'BONUS'),
      );
    } catch {
      return false;
    }
  }, [open, previewInput]);

  const preview = useQuery<ChargesPreview>({
    queryKey: ['trade-preview', previewInput],
    queryFn: async () => (await api.post('/portfolio/trades/preview', JSON.parse(previewInput))).data,
    enabled: previewEnabled,
    staleTime: 15_000,
  });

  const isSell = type === 'SELL';
  const isBonus = type === 'BONUS';
  const isOffer = type === 'IPO' || type === 'FPO' || type === 'RIGHT';

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Record a transaction"
      description="Allotments, bonus shares and market trades feed the same cost-basis engine."
      size="lg"
    >
      <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="max-h-[70vh] space-y-4 overflow-y-auto overflow-x-hidden pr-1">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="trade-symbol">Symbol</label>
            <Controller
              control={control}
              name="symbol"
              render={({ field }) => (
                <SymbolPicker
                  id="trade-symbol"
                  stocks={stocks}
                  value={field.value}
                  onChange={field.onChange}
                  invalid={Boolean(errors.symbol)}
                />
              )}
            />
            {errors.symbol && <p className="mt-1 text-xs text-rose-500">{errors.symbol.message}</p>}
            {selectedStock && !isBonus && (
              <p className="mt-1 text-[11px] text-slate-400">
                LTP {formatPrice(selectedStock.ltp)} · {selectedStock.companyName}
                {selectedStock.instrumentType && selectedStock.instrumentType !== 'Equity'
                  && ` · ${selectedStock.instrumentType}`}
              </p>
            )}
          </div>

          <div>
            <label className="label" htmlFor="trade-type">Type</label>
            <select id="trade-type" className="input" {...register('type')}>
              {TRADE_TYPES.map((item) => (
                <option key={item.value} value={item.value}>{item.label}</option>
              ))}
            </select>
            {isSell && (
              <p className="mt-1 text-[11px] text-slate-400">
                Available: {holding?.quantity ?? 0} shares
              </p>
            )}
          </div>

          <div>
            <label className="label" htmlFor="trade-quantity">Quantity</label>
            <input
              id="trade-quantity"
              type="number"
              min={1}
              className="input"
              {...register('quantity', { valueAsNumber: true })}
            />
            {errors.quantity && <p className="mt-1 text-xs text-rose-500">{errors.quantity.message}</p>}
          </div>

          <div>
            <label className="label" htmlFor="trade-price">
              {isBonus ? 'Price (bonus = Rs 0)' : isOffer ? 'Offer / subscription price' : 'Price per share'}
            </label>
            <input
              id="trade-price"
              type="number"
              step="0.01"
              min={0}
              disabled={isBonus}
              className="input disabled:cursor-not-allowed disabled:opacity-60"
              {...register('price', { valueAsNumber: true })}
            />
            {errors.price && <p className="mt-1 text-xs text-rose-500">{errors.price.message}</p>}
          </div>

          <div>
            <label className="label" htmlFor="trade-brokerage">
              Commission override (Rs)
            </label>
            <input
              id="trade-brokerage"
              type="number"
              step="0.01"
              min={0}
              placeholder={isBonus || isOffer ? '0' : 'auto'}
              className="input"
              {...register('brokerage', { valueAsNumber: true })}
            />
            <p className="mt-1 text-[10px] text-slate-400">
              Leave blank to use your rate schedule.
            </p>
          </div>

          <div>
            <label className="label" htmlFor="trade-tax">Tax / TDS override (Rs)</label>
            <input
              id="trade-tax"
              type="number"
              step="0.01"
              min={0}
              placeholder="auto"
              className="input"
              {...register('tax', { valueAsNumber: true })}
            />
            <p className="mt-1 text-[10px] text-slate-400">
              Sells: CGT on the gain. Bonus: TDS the company already deducted.
            </p>
          </div>

          <div>
            <label className="label" htmlFor="trade-date">Trade date</label>
            <input id="trade-date" type="date" className="input" {...register('tradeDate')} />
            {errors.tradeDate && <p className="mt-1 text-xs text-rose-500">{errors.tradeDate.message}</p>}
          </div>

          <div>
            <label className="label" htmlFor="trade-notes">Notes (optional)</label>
            <input id="trade-notes" className="input" placeholder="e.g. bonus TDS ~5%, broker name" {...register('notes')} />
          </div>
        </div>

        <div className="rounded-xl bg-slate-50 p-3 text-xs text-slate-500 dark:bg-white/5 dark:text-slate-400">
          <div className="flex items-start gap-2">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand-500" />
            <p>{typeHint(type)}</p>
          </div>
          {preview.data && (
            <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 border-t border-slate-200 pt-2 dark:border-white/10 sm:grid-cols-3">
              <div>
                <dt className="text-[10px] uppercase tracking-wide">Amount</dt>
                <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.amount)}</dd>
              </div>
              <div>
                <dt className="text-[10px] uppercase tracking-wide">Commission</dt>
                <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.brokerage)}</dd>
              </div>
              <div>
                <dt className="text-[10px] uppercase tracking-wide">SEBON fee</dt>
                <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.sebonFee)}</dd>
              </div>
              {isSell && (
                <>
                  <div>
                    <dt className="text-[10px] uppercase tracking-wide">DP charge</dt>
                    <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.dpCharge)}</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] uppercase tracking-wide">WAC / share</dt>
                    <dd className="font-semibold text-slate-700 dark:text-slate-200">
                      {formatPrice(preview.data.wacPerShare)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[10px] uppercase tracking-wide">
                      CGT ({preview.data.cgtRate}% · {preview.data.holdingDays}d)
                    </dt>
                    <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.cgt)}</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] uppercase tracking-wide">Net proceeds</dt>
                    <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.netProceeds)}</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] uppercase tracking-wide">Net profit after tax</dt>
                    <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.netProfit)}</dd>
                  </div>
                </>
              )}
              {!isSell && (
                <div>
                  <dt className="text-[10px] uppercase tracking-wide">Total cash out</dt>
                  <dd className="font-semibold text-slate-700 dark:text-slate-200">{formatNpr(preview.data.totalCost)}</dd>
                </div>
              )}
            </dl>
          )}
          {preview.isFetching && <p className="mt-1 text-[10px]">Calculating charges…</p>}
        </div>

        <div className="flex justify-end gap-3 pt-1">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={mutation.isPending}>Save transaction</Button>
        </div>
      </form>
    </Modal>
  );
}
