import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  ArrowUpRight,
  BrainCircuit,
  Layers,
  Lightbulb,
  ShieldAlert,
  Sparkles,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import { api } from '@/lib/api';
import {
  actionClasses,
  actionLabel,
  formatNpr,
  formatPercent,
  pnlClass,
} from '@/lib/format';
import { cn } from '@/lib/cn';
import Card from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import StatCard from '@/components/ui/StatCard';
import EmptyState from '@/components/ui/EmptyState';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';
import { CardsSkeleton } from '@/components/ui/Skeletons';
import RecommendationCard from '@/components/RecommendationCard';
import type { DcaOpportunity, Recommendation, Settings } from '@/types';

type Filter = 'ALL' | 'BUY' | 'HOLD' | 'SELL';

function levelVariant(level: string): 'warn' | 'loss' | 'violet' {
  if (level === 'AGGRESSIVE') return 'loss';
  if (level === 'MAJOR') return 'warn';
  return 'violet';
}

export default function Analysis() {
  const [filter, setFilter] = useState<Filter>('ALL');

  const { data: recommendations = [], isLoading } = useQuery<Recommendation[]>({
    queryKey: ['recommendations'],
    queryFn: async () => (await api.get('/analysis/recommendations')).data,
    refetchInterval: 120_000,
  });

  const { data: opportunities = [] } = useQuery<DcaOpportunity[]>({
    queryKey: ['opportunities'],
    queryFn: async () => (await api.get('/analysis/opportunities')).data,
  });

  const { data: settings } = useQuery<Settings>({
    queryKey: ['settings'],
    queryFn: async () => (await api.get('/settings')).data,
  });

  const counts = useMemo(() => ({
    buy: recommendations.filter((rec) => rec.action.includes('BUY')).length,
    hold: recommendations.filter((rec) => rec.action === 'HOLD').length,
    sell: recommendations.filter((rec) => rec.action.includes('SELL')).length,
    ml: recommendations.filter((rec) => rec.source === 'ML').length,
  }), [recommendations]);

  const filtered = useMemo(() => {
    const list = recommendations.filter((rec) => {
      if (filter === 'ALL') return true;
      if (filter === 'BUY') return rec.action.includes('BUY');
      if (filter === 'SELL') return rec.action.includes('SELL');
      return rec.action === filter;
    });
    return [...list].sort((a, b) => b.confidence - a.confidence);
  }, [recommendations, filter]);

  return (
    <div>
      <PageHeader
        title="Analysis"
        subtitle="Trend, momentum, volume and portfolio context scored for every holding."
        actions={(
          <div className="flex items-center gap-2 rounded-full border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-500 dark:border-white/10 dark:text-slate-400">
            <BrainCircuit className="h-3.5 w-3.5 text-violet-500" />
            {counts.ml > 0 ? `${counts.ml} ML signals` : 'Rule engine active'}
          </div>
        )}
      />

      {isLoading ? (
        <CardsSkeleton />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="Positions analysed"
            value={recommendations.length}
            sub={<span className="muted">One signal per open holding</span>}
            icon={<Layers className="h-5 w-5" />}
          />
          <StatCard
            label="Buy signals"
            value={counts.buy}
            sub={<span className="text-gain">Strong buy + buy</span>}
            icon={<TrendingUp className="h-5 w-5" />}
            accent="gain"
            delay={0.05}
          />
          <StatCard
            label="Hold"
            value={counts.hold}
            sub={<span className="muted">No clear edge</span>}
            icon={<Sparkles className="h-5 w-5" />}
            accent="violet"
            delay={0.1}
          />
          <StatCard
            label="Sell signals"
            value={counts.sell}
            sub={<span className="text-loss">Review risk and exits</span>}
            icon={<TrendingDown className="h-5 w-5" />}
            accent="loss"
            delay={0.15}
          />
        </div>
      )}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {(['ALL', 'BUY', 'HOLD', 'SELL'] as Filter[]).map((value) => (
            <button
              key={value}
              onClick={() => setFilter(value)}
              className={cn(
                'rounded-xl px-3.5 py-2 text-xs font-semibold transition',
                filter === value
                  ? 'bg-brand-500 text-ink-950'
                  : 'border border-slate-200 text-slate-600 hover:border-brand-500/40 dark:border-white/10 dark:text-slate-300',
              )}
            >
              {value === 'ALL' ? 'All signals' : value.charAt(0) + value.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
        <p className="text-[11px] text-slate-400">
          Scores combine trend 30 · RSI 20 · momentum 20 · position 15 · volume 10 · concentration 5
        </p>
      </div>

      {isLoading ? (
        <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={index} className="card p-4"><div className="skeleton h-48 w-full" /></div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="mt-4">
          <EmptyState
            icon={<TrendingUp className="h-6 w-6" />}
            title={recommendations.length === 0 ? 'No holdings to analyse' : 'No signals in this bucket'}
            description={recommendations.length === 0
              ? 'Add your NEPSE transactions and recommendations will appear here automatically.'
              : 'Try another filter to see the rest of your holdings.'}
            action={recommendations.length === 0
              ? <Link to="/portfolio" className="btn-primary">Add a trade</Link>
              : undefined}
          />
        </div>
      ) : (
        <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((recommendation, index) => (
            <motion.div
              key={recommendation.symbol}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: Math.min(index * 0.04, 0.3) }}
            >
              <RecommendationCard recommendation={recommendation} />
            </motion.div>
          ))}
        </div>
      )}

      <Card className="mt-6 p-5">
        <SectionTitle
          title="Averaging opportunities"
          subtitle={settings
            ? `Triggered at −${settings.lossThresholdMinor}%, −${settings.lossThresholdMajor}% and −${settings.lossThresholdAggressive}% with a ${formatNpr(settings.baseBuyAmount)} base tranche`
            : 'Positions below your configured loss thresholds'}
          action={<Lightbulb className="h-4 w-4 text-amber-500" />}
        />
        {opportunities.length === 0 ? (
          <EmptyState
            title="No averaging triggers right now"
            description="Nothing is far enough below cost to justify a tranche under your current rules."
          />
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {opportunities.map((opportunity) => (
              <div key={opportunity.symbol} className="rounded-xl border border-slate-100 p-4 dark:border-white/5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <Link to={`/stocks/${opportunity.symbol}`} className="group flex items-center gap-1.5">
                      <span className="text-sm font-extrabold text-slate-900 group-hover:text-brand-600 dark:text-white dark:group-hover:text-brand-300">
                        {opportunity.symbol}
                      </span>
                      <ArrowUpRight className="h-3 w-3 text-slate-400" />
                    </Link>
                    <p className="text-[11px] text-slate-400">{opportunity.companyName}</p>
                  </div>
                  <Badge variant={levelVariant(opportunity.level)}>{opportunity.level}</Badge>
                </div>

                <div className="mt-3 flex items-center justify-between rounded-lg bg-rose-500/5 px-3 py-2">
                  <span className="text-[11px] text-slate-500 dark:text-slate-400">Unrealized loss</span>
                  <span className={cn('text-sm font-bold', pnlClass(opportunity.lossPercent))}>
                    {formatPercent(opportunity.lossPercent)}
                  </span>
                </div>

                <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                  Buy <span className="font-bold text-slate-800 dark:text-slate-100">{opportunity.suggestedQuantity} shares</span>{' '}
                  for <span className="font-bold text-slate-800 dark:text-slate-100">{formatNpr(opportunity.suggestedBudget)}</span>{' '}
                  to lower the average to <span className="font-bold text-brand-600 dark:text-brand-300">रु {opportunity.newAverageCost.toFixed(2)}</span>{' '}
                  ({opportunity.averageImprovementPercent.toFixed(1)}% lower).
                </p>
                <div className="mt-3 flex items-center justify-between text-[10px] text-slate-400">
                  <span>Avg cost रु {opportunity.averageCost.toFixed(2)} · LTP रु {opportunity.ltp.toFixed(2)}</span>
                  <Link to={`/stocks/${opportunity.symbol}`} className="font-semibold text-brand-600 hover:text-brand-500 dark:text-brand-400">
                    Simulate
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <Card className="p-5">
          <SectionTitle title="How the score works" subtitle="Transparent weights, no black box" />
          <div className="space-y-2 text-xs">
            {[
              ['Trend structure', '30', 'Price versus SMA20 and SMA50'],
              ['RSI (14)', '20', 'Oversold adds, overbought subtracts'],
              ['Momentum', '20', '7-day and 30-day returns'],
              ['Position context', '15', 'Loss depth and profit protection'],
              ['Volume confirmation', '10', 'Volume versus its 20-day average'],
              ['Concentration', '5', 'Penalty above your position cap'],
            ].map(([name, weight, description]) => (
              <div key={name} className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
                <div>
                  <p className="font-semibold text-slate-700 dark:text-slate-200">{name}</p>
                  <p className="text-[10px] text-slate-400">{description}</p>
                </div>
                <span className="chip bg-brand-500/10 text-brand-700 dark:text-brand-300">{weight} pts</span>
              </div>
            ))}
          </div>
        </Card>

        <Card className="p-5">
          <SectionTitle title="Reading the signals" subtitle="A disciplined process beats a hot tip" />
          <div className="space-y-3 text-xs text-slate-600 dark:text-slate-400">
            <div className="rounded-xl border border-slate-100 p-3 dark:border-white/5">
              <p className={cn('chip mb-2', actionClasses('BUY'))}>{actionLabel('BUY')}</p>
              <p>Trend and momentum align. Check position size and stage entries instead of buying everything at once.</p>
            </div>
            <div className="rounded-xl border border-slate-100 p-3 dark:border-white/5">
              <p className={cn('chip mb-2', actionClasses('HOLD'))}>{actionLabel('HOLD')}</p>
              <p>Mixed evidence. Let the existing position run, but keep the averaging rules ready if the thesis weakens.</p>
            </div>
            <div className="rounded-xl border border-slate-100 p-3 dark:border-white/5">
              <p className={cn('chip mb-2', actionClasses('SELL'))}>{actionLabel('SELL')}</p>
              <p>Trend or overbought risk dominates. Consider trimming, protecting gains, or revisiting the original thesis.</p>
            </div>
          </div>
          <div className="mt-3 flex items-start gap-2 rounded-xl bg-amber-500/5 p-3 text-[11px] text-amber-700 dark:text-amber-300">
            <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            Signals are educational and based on historical prices. They are not personalised financial advice.
          </div>
        </Card>
      </div>
    </div>
  );
}
