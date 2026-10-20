import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

type Variant = 'gain' | 'loss' | 'neutral' | 'brand' | 'warn' | 'violet' | 'info';

const variants: Record<Variant, string> = {
  gain: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 ring-1 ring-emerald-500/30',
  loss: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 ring-1 ring-rose-500/30',
  neutral: 'bg-slate-500/10 text-slate-600 dark:text-slate-300 ring-1 ring-slate-500/20',
  brand: 'bg-brand-500/15 text-brand-700 dark:text-brand-300 ring-1 ring-brand-500/30',
  warn: 'bg-amber-500/15 text-amber-700 dark:text-amber-300 ring-1 ring-amber-500/30',
  violet: 'bg-violet-500/15 text-violet-600 dark:text-violet-300 ring-1 ring-violet-500/30',
  info: 'bg-sky-500/15 text-sky-600 dark:text-sky-300 ring-1 ring-sky-500/30',
};

export default function Badge({
  variant = 'neutral',
  className,
  children,
}: {
  variant?: Variant;
  className?: string;
  children: ReactNode;
}) {
  return <span className={cn('chip', variants[variant], className)}>{children}</span>;
}
