import { useEffect, useMemo, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Info } from 'lucide-react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatNpr } from '@/lib/format';
import type { Holding, Stock } from '@/types';

const schema = z.object({
  symbol: z.string().min(1, 'Choose a symbol'),
  type: z.enum(['BUY', 'SELL']),
  quantity: z.number().int().positive('Quantity must be at least 1'),
  price: z.number().positive('Price must be greater than zero'),
  brokerage: z.number().min(0),
  tax: z.number().min(0),
  tradeDate: z.string().min(1, 'Pick a date'),
  notes: z.string().max(500).optional(),
});

type FormValues = z.infer<typeof schema>;

function todayInput() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60_000).toISOString().slice(0, 10);
}

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
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: 'BUY',
      quantity: 1,
      brokerage: 0,
      tax: 0,
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
        brokerage: 0,
        tax: 0,
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
  const brokerage = watch('brokerage');
  const tax = watch('tax');

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
    if (selectedStock && lastPricedSymbol.current !== selectedStock.symbol) {
      lastPricedSymbol.current = selectedStock.symbol;
      reset((values) => ({ ...values, price: selectedStock.ltp }));
    }
  }, [open, selectedStock, reset]);

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const tradeDate = new Date(`${values.tradeDate}T12:00:00`).toISOString();
      return api.post('/portfolio/trades', {
        ...values,
        notes: values.notes || null,
        tradeDate,
      });
    },
    onSuccess: () => {
      toast.success('Trade recorded');
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      void queryClient.invalidateQueries({ queryKey: ['portfolio-history'] });
      void queryClient.invalidateQueries({ queryKey: ['trades'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      void queryClient.invalidateQueries({ queryKey: ['opportunities'] });
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not record the trade')),
  });

  const estimatedTotal = Number(quantity || 0) * Number(price || 0)
    + Number(brokerage || 0) + Number(tax || 0);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Record a trade"
      description="Buys and sells feed the FIFO cost-basis engine."
    >
      <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="trade-symbol">Symbol</label>
            <input
              id="trade-symbol"
              list="trade-symbols"
              placeholder="NICA"
              className="input uppercase"
              {...register('symbol')}
            />
            <datalist id="trade-symbols">
              {stocks.map((stock) => (
                <option key={stock.id} value={stock.symbol}>{stock.companyName}</option>
              ))}
            </datalist>
            {errors.symbol && <p className="mt-1 text-xs text-rose-500">{errors.symbol.message}</p>}
            {selectedStock && (
              <p className="mt-1 text-[11px] text-slate-400">
                LTP {formatNpr(selectedStock.ltp)} · {selectedStock.companyName}
              </p>
            )}
          </div>

          <div>
            <label className="label" htmlFor="trade-type">Type</label>
            <select id="trade-type" className="input" {...register('type')}>
              <option value="BUY">Buy</option>
              <option value="SELL">Sell</option>
            </select>
            {type === 'SELL' && (
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
            <label className="label" htmlFor="trade-price">Price per share</label>
            <input
              id="trade-price"
              type="number"
              step="0.01"
              min={0.01}
              className="input"
              {...register('price', { valueAsNumber: true })}
            />
            {errors.price && <p className="mt-1 text-xs text-rose-500">{errors.price.message}</p>}
          </div>

          <div>
            <label className="label" htmlFor="trade-brokerage">Brokerage</label>
            <input
              id="trade-brokerage"
              type="number"
              step="0.01"
              min={0}
              className="input"
              {...register('brokerage', { valueAsNumber: true })}
            />
          </div>

          <div>
            <label className="label" htmlFor="trade-tax">Tax / other fees</label>
            <input
              id="trade-tax"
              type="number"
              step="0.01"
              min={0}
              className="input"
              {...register('tax', { valueAsNumber: true })}
            />
          </div>

          <div>
            <label className="label" htmlFor="trade-date">Trade date</label>
            <input id="trade-date" type="date" className="input" {...register('tradeDate')} />
            {errors.tradeDate && <p className="mt-1 text-xs text-rose-500">{errors.tradeDate.message}</p>}
          </div>

          <div>
            <label className="label" htmlFor="trade-notes">Notes (optional)</label>
            <input id="trade-notes" className="input" placeholder="Reason, broker, etc." {...register('notes')} />
          </div>
        </div>

        <div className="flex items-start gap-2 rounded-xl bg-slate-50 p-3 text-xs text-slate-500 dark:bg-white/5 dark:text-slate-400">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand-500" />
          <div>
            <p>Estimated cash value: <span className="font-bold text-slate-700 dark:text-slate-200">{formatNpr(estimatedTotal)}</span></p>
            <p className="mt-0.5">Fees are included in the cost basis and realized P&L.</p>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-1">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={mutation.isPending}>Save trade</Button>
        </div>
      </form>
    </Modal>
  );
}
