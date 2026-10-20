import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  ArrowLeft,
  BrainCircuit,
  Calculator,
  Gauge,
  Info,
  Plus,
  ShieldAlert,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import {
  actionClasses,
  actionLabel,
  formatDateTime,
  formatNpr,
  formatPercent,
  formatPrice,
  formatSignedNpr,
  pnlClass,
  sourceClasses,
  trendClasses,
} from '@/lib/format';
import { cn } from '@/lib/cn';
import Card from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeletons';
import EmptyState from '@/components/ui/EmptyState';
import PriceChart from '@/components/charts/PriceChart';
import AddTradeModal from '@/components/AddTradeModal';
import type {
  Portfolio,
  Settings,
  SimulationResponse,
  StockAnalysis,
  StockDetail,
} from '@/types';

const RANGES = ['1M', '3M', '6M', '1Y'] as const;
type Range = (typeof RANGES)[number];
type Mode = 'QTY' | 'BUDGET' | 'TARGET';

function Metric({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div className="rounded-xl border border-slate-100 px-3 py-2.5 dark:border-white/5">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">{label}</p>
      <p className={cn('mt-0.5 text-sm font-bold text-slate-800 dark:text-slate-100', tone)}>{value}</p>
    </div>
  );
}

export default function StockDetailPage() {
  const { symbol = '' } = useParams();
  const queryClient = useQueryClient();
  const [range, setRange] = useState<Range>('6M');
  const [tradeOpen, setTradeOpen] = useState(false);
  const [mode, setMode] = useState<Mode>('BUDGET');
  const [quantity, setQuantity] = useState('100');
  const [budget, setBudget] = useState('10000');
  const [target, setTarget] = useState('');
  const [price, setPrice] = useState('');
  const [result, setResult] = useState<SimulationResponse | null>(null);

  const { data: detail, isLoading } = useQuery<StockDetail>({
    queryKey: ['stock', symbol, range],
    queryFn: async () => (await api.get(`/market/stocks/${symbol}`, { params: { range } })).data,
    enabled: Boolean(symbol),
    refetchInterval: 60_000,
  });

  const { data: analysis } = useQuery<StockAnalysis>({
    queryKey: ['analysis', symbol],
    queryFn: async () => (await api.get(`/analysis/stocks/${symbol}`)).data,
    enabled: Boolean(symbol),
    refetchInterval: 120_000,
  });

  const { data: portfolio } = useQuery<Portfolio>({
    queryKey: ['portfolio'],
    queryFn: async () => (await api.get('/portfolio')).data,
  });

  const { data: settings } = useQuery<Settings>({
    queryKey: ['settings'],
    queryFn: async () => (await api.get('/settings')).data,
  });

  const simulation = useMutation({
    mutationFn: async (payload: Record<string, unknown>) =>
      (await api.post<SimulationResponse>('/analysis/average-simulation', payload)).data,
    onSuccess: (data) => setResult(data),
    onError: (error) => {
      setResult(null);
      toast.error(errorMessage(error, 'Could not run the simulation'));
    },
  });

  const stock = detail?.stock ?? analysis?.stock;
  const indicators = analysis?.indicators;
  const recommendation = analysis?.recommendation;
  const holding = portfolio?.holdings.find((item) => item.symbol === stock?.symbol);

  const runSimulation = () => {
    if (!stock) return;
    const payload: Record<string, unknown> = {
      symbol: stock.symbol,
      mode,
      price: price ? Number(price) : undefined,
    };
    if (mode === 'QTY') payload.quantity = Number(quantity);
    if (mode === 'BUDGET') payload.budget = Number(budget);
    if (mode === 'TARGET') payload.targetAverage = Number(target);
    simulation.mutate(payload);
  };

  if (isLoading && !stock) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-52" />
        <div className="card p-5">
          <Skeleton className="h-64 w-full" />
        </div>
      </div>
    );
  }

  if (!stock) {
    return (
      <EmptyState
        title="Stock not found"
        description="The symbol may not be in the tracked NEPSE universe."
        action={<Link to="/portfolio" className="btn-primary">Back to portfolio</Link>}
      />
    );
  }

  const rising = stock.changePercent >= 0;

  return (
    <div>
      <Link to="/portfolio" className="mb-4 inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-brand-600 dark:text-slate-400">
        <ArrowLeft className="h-3.5 w-3.5" /> Back to portfolio
      </Link>

      <PageHeader
        title={(
          <span className="flex flex-wrap items-center gap-3">
            {stock.symbol}
            <Badge variant={stock.source === 'SCRAPED' ? 'gain' : 'neutral'}>
              {stock.source === 'SCRAPED' ? 'Live scraped' : 'Seeded price'}
            </Badge>
          </span>
        )}
        subtitle={`${stock.companyName}${stock.sector ? ` · ${stock.sector}` : ''}`}
        actions={(
          <Button onClick={() => setTradeOpen(true)}>
            <Plus className="h-4 w-4" /> Record trade
          </Button>
        )}
      />

      <div className="grid items-start gap-4 xl:grid-cols-3">
        <Card className="p-5 xl:col-span-2">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white">
                {formatPrice(stock.ltp)}
              </p>
              <p className={cn('mt-1 flex items-center gap-1.5 text-sm font-semibold', pnlClass(stock.changePercent))}>
                {rising ? <TrendingUp className="h-4 w-4" /> : <TrendingDown className="h-4 w-4" />}
                {formatSignedNpr(stock.change)} ({formatPercent(stock.changePercent)}) today
              </p>
              <p className="mt-1 text-[11px] text-slate-400">
                Updated {formatDateTime(stock.lastUpdated)}
              </p>
            </div>
            <div className="flex rounded-xl border border-slate-200 p-1 dark:border-white/10">
              {RANGES.map((value) => (
                <button
                  key={value}
                  onClick={() => setRange(value)}
                  className={cn(
                    'rounded-lg px-3 py-1.5 text-xs font-semibold transition',
                    range === value
                      ? 'bg-brand-500 text-ink-950'
                      : 'text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-white',
                  )}
                >
                  {value}
                </button>
              ))}
            </div>
          </div>
          {detail && detail.history.length > 1 ? (
            <div className="mt-4">
              <PriceChart data={detail.history} />
            </div>
          ) : (
            <div className="mt-4">
              <EmptyState title="No price history" description="History builds up as the scraper records daily bars." />
            </div>
          )}
        </Card>

        <Card className="p-5">
          <SectionTitle
            title="Recommendation"
            subtitle="Explainable score, not financial advice"
            action={recommendation && (
              <span className={cn('chip', sourceClasses(recommendation.source))}>
                {recommendation.source === 'ML' ? <BrainCircuit className="h-3 w-3" /> : <Gauge className="h-3 w-3" />}
                {recommendation.source}
              </span>
            )}
          />
          {!recommendation ? (
            <Skeleton className="h-40 w-full" />
          ) : (
            <>
              <div className="flex items-center justify-between gap-3">
                <span className={cn('chip px-3 py-1.5 text-sm', actionClasses(recommendation.action))}>
                  {actionLabel(recommendation.action)}
                </span>
                <span className="text-sm font-bold text-slate-700 dark:text-slate-200">
                  {recommendation.confidence.toFixed(0)}% confidence
                </span>
              </div>
              <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-white/10">
                <motion.div
                  className={cn('h-full rounded-full', recommendation.action.includes('BUY') ? 'bg-emerald-500' : recommendation.action.includes('SELL') ? 'bg-rose-500' : 'bg-slate-400')}
                  initial={{ width: 0 }}
                  animate={{ width: `${Math.min(100, recommendation.confidence)}%` }}
                  transition={{ duration: 0.7 }}
                />
              </div>
              <p className="mt-2 text-[11px] text-slate-400">Technical score: {recommendation.score} / 100</p>

              {holding && (
                <div className="mt-3 grid grid-cols-2 gap-2 rounded-xl bg-slate-50 p-3 text-xs dark:bg-white/5">
                  <div>
                    <p className="text-[10px] uppercase tracking-wider text-slate-400">Your position</p>
                    <p className="font-semibold text-slate-700 dark:text-slate-200">{holding.quantity} shares</p>
                  </div>
                  <div>
                    <p className="text-[10px] uppercase tracking-wider text-slate-400">Unrealized</p>
                    <p className={cn('font-semibold', pnlClass(holding.unrealizedProfitLoss))}>
                      {formatPercent(holding.unrealizedPercent)}
                    </p>
                  </div>
                </div>
              )}

              <ul className="mt-3 space-y-1.5 text-xs text-slate-600 dark:text-slate-400">
                {recommendation.reasons.slice(0, 5).map((reason) => (
                  <li key={reason} className="flex gap-2">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-brand-500" />
                    <span>{reason}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
          <div className="mt-4 flex items-start gap-2 rounded-xl bg-amber-500/5 p-3 text-[11px] text-amber-700 dark:text-amber-300">
            <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            Educational signal based on price history. Verify independently before trading.
          </div>
        </Card>
      </div>

      {indicators && (
        <Card className="mt-6 p-5">
          <SectionTitle title="Technical snapshot" subtitle={`${indicators.dataPoints} daily bars used`} />
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-6">
            <Metric label="Trend" value={indicators.trend} tone={trendClasses(indicators.trend)} />
            <Metric label="RSI (14)" value={indicators.rsi14?.toFixed(2) ?? '—'} />
            <Metric label="SMA 20" value={indicators.sma20?.toFixed(2) ?? '—'} />
            <Metric label="SMA 50" value={indicators.sma50?.toFixed(2) ?? '—'} />
            <Metric label="7d return" value={formatPercent(indicators.return7d)} tone={pnlClass(indicators.return7d)} />
            <Metric label="30d return" value={formatPercent(indicators.return30d)} tone={pnlClass(indicators.return30d)} />
            <Metric label="Support (20d)" value={indicators.support?.toFixed(2) ?? '—'} />
            <Metric label="Resistance (20d)" value={indicators.resistance?.toFixed(2) ?? '—'} />
            <Metric label="52w high" value={indicators.high52Week?.toFixed(2) ?? '—'} />
            <Metric label="52w low" value={indicators.low52Week?.toFixed(2) ?? '—'} />
            <Metric label="Volatility (ann.)" value={indicators.volatility ? `${indicators.volatility.toFixed(1)}%` : '—'} />
            <Metric label="Volume ratio" value={indicators.volumeRatio ? `${indicators.volumeRatio.toFixed(2)}x` : '—'} />
          </div>
        </Card>
      )}

      <div className="mt-6 grid items-start gap-4 xl:grid-cols-3">
        <Card className="p-5 xl:col-span-2">
          <SectionTitle
            title="Averaging simulator"
            subtitle="Work out the shares and capital needed to move your average cost"
            action={<Calculator className="h-4 w-4 text-brand-500" />}
          />

          {!holding ? (
            <EmptyState
              title="You don't hold this stock"
              description="Record a buy first, then the simulator can project your new average cost."
              action={<Button onClick={() => setTradeOpen(true)}>Record a buy</Button>}
            />
          ) : (
            <>
              <div className="grid grid-cols-3 gap-2 rounded-xl bg-slate-50 p-3 text-center dark:bg-white/5">
                <div>
                  <p className="text-[10px] uppercase tracking-wider text-slate-400">Quantity</p>
                  <p className="text-sm font-bold text-slate-800 dark:text-white">{holding.quantity}</p>
                </div>
                <div>
                  <p className="text-[10px] uppercase tracking-wider text-slate-400">Average cost</p>
                  <p className="text-sm font-bold text-slate-800 dark:text-white">रु {holding.averageCost.toFixed(2)}</p>
                </div>
                <div>
                  <p className="text-[10px] uppercase tracking-wider text-slate-400">Current loss</p>
                  <p className={cn('text-sm font-bold', pnlClass(holding.unrealizedPercent))}>
                    {formatPercent(holding.unrealizedPercent)}
                  </p>
                </div>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {([
                  ['QTY', 'By quantity'],
                  ['BUDGET', 'By budget'],
                  ['TARGET', 'Target average'],
                ] as [Mode, string][]).map(([value, label]) => (
                  <button
                    key={value}
                    onClick={() => { setMode(value); setResult(null); }}
                    className={cn(
                      'rounded-xl px-3 py-2 text-xs font-semibold transition',
                      mode === value
                        ? 'bg-brand-500 text-ink-950'
                        : 'border border-slate-200 text-slate-600 hover:border-brand-500/40 dark:border-white/10 dark:text-slate-300',
                    )}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {mode === 'QTY' && (
                  <div>
                    <label className="label" htmlFor="sim-quantity">Extra shares</label>
                    <input id="sim-quantity" type="number" min={1} className="input" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
                  </div>
                )}
                {mode === 'BUDGET' && (
                  <div>
                    <label className="label" htmlFor="sim-budget">Budget (NPR)</label>
                    <input id="sim-budget" type="number" min={1} className="input" value={budget} onChange={(event) => setBudget(event.target.value)} />
                  </div>
                )}
                {mode === 'TARGET' && (
                  <div>
                    <label className="label" htmlFor="sim-target">Target average</label>
                    <input id="sim-target" type="number" step="0.01" className="input" placeholder={holding.averageCost.toFixed(2)} value={target} onChange={(event) => setTarget(event.target.value)} />
                  </div>
                )}
                <div>
                  <label className="label" htmlFor="sim-price">Buy price (optional)</label>
                  <input id="sim-price" type="number" step="0.01" className="input" placeholder={`LTP ${stock.ltp.toFixed(2)}`} value={price} onChange={(event) => setPrice(event.target.value)} />
                </div>
              </div>

              <Button className="mt-4" onClick={runSimulation} loading={simulation.isPending}>
                Simulate
              </Button>

              {result && (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={cn(
                    'mt-4 rounded-xl border p-4 text-sm',
                    result.feasible
                      ? 'border-brand-500/30 bg-brand-500/5'
                      : 'border-rose-500/30 bg-rose-500/5',
                  )}
                >
                  <p className={cn('font-semibold', result.feasible ? 'text-brand-700 dark:text-brand-300' : 'text-rose-600 dark:text-rose-300')}>
                    {result.message}
                  </p>
                  {result.feasible && (
                    <div className="mt-3 grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
                      <div>
                        <p className="text-slate-400">Add shares</p>
                        <p className="font-bold text-slate-800 dark:text-white">{result.addedQuantity}</p>
                      </div>
                      <div>
                        <p className="text-slate-400">Capital needed</p>
                        <p className="font-bold text-slate-800 dark:text-white">{formatNpr(result.addedCapital)}</p>
                      </div>
                      <div>
                        <p className="text-slate-400">New average</p>
                        <p className="font-bold text-brand-600 dark:text-brand-300">रु {result.newAverageCost.toFixed(2)}</p>
                      </div>
                      <div>
                        <p className="text-slate-400">Average reduction</p>
                        <p className="font-bold text-brand-600 dark:text-brand-300">{result.averageReductionPercent.toFixed(2)}%</p>
                      </div>
                    </div>
                  )}
                </motion.div>
              )}
            </>
          )}
        </Card>

        <Card className="p-5">
          <SectionTitle title="Market details" subtitle={`Source: ${stock.source.toLowerCase()}`} />
          <div className="grid grid-cols-2 gap-2">
            <Metric label="Open" value={stock.openPrice.toFixed(2)} />
            <Metric label="Previous close" value={stock.prevClose.toFixed(2)} />
            <Metric label="High" value={stock.highPrice.toFixed(2)} />
            <Metric label="Low" value={stock.lowPrice.toFixed(2)} />
            <Metric label="Volume" value={stock.volume.toLocaleString('en-IN')} />
            <Metric label="Turnover" value={formatNpr(stock.turnover)} />
            <Metric label="Day range" value={`${stock.lowPrice.toFixed(0)} – ${stock.highPrice.toFixed(0)}`} />
            <Metric label="Sector" value={stock.sector ?? '—'} />
          </div>
          {settings && (
            <div className="mt-5">
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                Your averaging rules
              </p>
              <div className="space-y-2 text-xs">
                <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
                  <span className="text-slate-500 dark:text-slate-400">Minor tranche</span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">
                    −{settings.lossThresholdMinor}% · {formatNpr(settings.baseBuyAmount * 1.5)}
                  </span>
                </div>
                <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
                  <span className="text-slate-500 dark:text-slate-400">Major tranche</span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">
                    −{settings.lossThresholdMajor}% · {formatNpr(settings.baseBuyAmount * 2)}
                  </span>
                </div>
                <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
                  <span className="text-slate-500 dark:text-slate-400">Aggressive tranche</span>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">
                    −{settings.lossThresholdAggressive}% · {formatNpr(settings.baseBuyAmount * 2.5)}
                  </span>
                </div>
              </div>
              <p className="mt-2 flex items-start gap-1.5 text-[10px] text-slate-400">
                <Info className="mt-0.5 h-3 w-3 shrink-0" />
                Position cap {settings.maxPositionPercent}% · per-stock cap {formatNpr(settings.maxCapitalPerStock)}
              </p>
            </div>
          )}
        </Card>
      </div>

      <AddTradeModal
        open={tradeOpen}
        onClose={() => {
          setTradeOpen(false);
          void queryClient.invalidateQueries({ queryKey: ['analysis', symbol] });
        }}
        holdings={portfolio?.holdings ?? []}
        defaultSymbol={stock.symbol}
      />
    </div>
  );
}
