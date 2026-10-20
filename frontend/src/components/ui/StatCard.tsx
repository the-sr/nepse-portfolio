import type { ReactNode } from 'react';
import { motion } from 'framer-motion';
import { cn } from '@/lib/cn';

export default function StatCard({
  label,
  value,
  sub,
  icon,
  accent = 'brand',
  delay = 0,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  icon: ReactNode;
  accent?: 'brand' | 'gain' | 'loss' | 'violet' | 'amber';
  delay?: number;
}) {
  const accents: Record<string, string> = {
    brand: 'bg-brand-500/10 text-brand-600 dark:text-brand-300',
    gain: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
    loss: 'bg-rose-500/10 text-rose-600 dark:text-rose-300',
    violet: 'bg-violet-500/10 text-violet-600 dark:text-violet-300',
    amber: 'bg-amber-500/10 text-amber-600 dark:text-amber-300',
  };

  return (
    <motion.div
      className="card card-hover p-5"
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay }}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            {label}
          </p>
          <p className="mt-2 truncate text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            {value}
          </p>
          {sub && <div className="mt-1.5 text-xs font-medium">{sub}</div>}
        </div>
        <div className={cn('rounded-xl p-2.5', accents[accent])}>{icon}</div>
      </div>
    </motion.div>
  );
}
