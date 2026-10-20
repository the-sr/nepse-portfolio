import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowUpRight, BrainCircuit, Gauge } from 'lucide-react';
import Badge from '@/components/ui/Badge';
import { actionClasses, actionLabel, formatPercent, formatSignedNpr, pnlClass, sourceClasses } from '@/lib/format';
import { cn } from '@/lib/cn';
import type { Recommendation } from '@/types';

export default function RecommendationCard({
  recommendation,
  compact = false,
}: {
  recommendation: Recommendation;
  compact?: boolean;
}) {
  const rec = recommendation;
  return (
    <motion.div
      whileHover={{ y: -3 }}
      transition={{ duration: 0.2 }}
      className="card card-hover flex flex-col p-4"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link to={`/stocks/${rec.symbol}`} className="group flex items-center gap-1.5">
            <span className="text-base font-extrabold tracking-tight text-slate-900 group-hover:text-brand-600 dark:text-white dark:group-hover:text-brand-300">
              {rec.symbol}
            </span>
            <ArrowUpRight className="h-3.5 w-3.5 text-slate-400" />
          </Link>
          <p className="truncate text-xs text-slate-500 dark:text-slate-400">{rec.companyName}</p>
        </div>
        <span className={cn('chip', actionClasses(rec.action))}>{actionLabel(rec.action)}</span>
      </div>

      <div className="mt-3 flex items-end justify-between gap-3">
        <div>
          <p className="text-lg font-bold text-slate-900 dark:text-white">रु {rec.ltp.toFixed(2)}</p>
          <p className={cn('text-xs font-semibold', pnlClass(rec.changePercent))}>
            {formatPercent(rec.changePercent)} today
          </p>
        </div>
        <span className={cn('chip', sourceClasses(rec.source))}>
          {rec.source === 'ML' ? <BrainCircuit className="h-3 w-3" /> : <Gauge className="h-3 w-3" />}
          {rec.source}
        </span>
      </div>

      <div className="mt-3">
        <div className="flex items-center justify-between text-[10px] font-semibold uppercase tracking-wider text-slate-400">
          <span>Confidence</span>
          <span>{rec.confidence.toFixed(0)}%</span>
        </div>
        <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-white/10">
          <motion.div
            className="h-full rounded-full bg-brand-500"
            initial={{ width: 0 }}
            animate={{ width: `${Math.min(100, rec.confidence)}%` }}
            transition={{ duration: 0.6, ease: 'easeOut' }}
          />
        </div>
      </div>

      {rec.holdingQuantity != null && (
        <div className="mt-3 grid grid-cols-2 gap-2 rounded-xl bg-slate-50 p-2.5 text-xs dark:bg-white/5">
          <div>
            <p className="text-[10px] uppercase tracking-wider text-slate-400">Holding</p>
            <p className="font-semibold text-slate-700 dark:text-slate-200">{rec.holdingQuantity} shares</p>
          </div>
          <div>
            <p className="text-[10px] uppercase tracking-wider text-slate-400">Unrealized</p>
            <p className={cn('font-semibold', pnlClass(rec.profitLoss))}>
              {formatSignedNpr(rec.profitLoss)} ({formatPercent(rec.profitLossPercent)})
            </p>
          </div>
        </div>
      )}

      {!compact && rec.reasons.length > 0 && (
        <ul className="mt-3 space-y-1.5 border-t border-slate-100 pt-3 text-xs text-slate-600 dark:border-white/5 dark:text-slate-400">
          {rec.reasons.slice(0, 3).map((reason) => (
            <li key={reason} className="flex gap-1.5">
              <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-brand-500" />
              <span>{reason}</span>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3 flex items-center justify-between text-[10px] text-slate-400">
        <Badge variant={rec.indicators.trend === 'BULLISH' ? 'gain' : rec.indicators.trend === 'BEARISH' ? 'loss' : 'neutral'}>
          {rec.indicators.trend}
        </Badge>
        <span>RSI {rec.indicators.rsi14?.toFixed(1) ?? '—'}</span>
      </div>
    </motion.div>
  );
}
