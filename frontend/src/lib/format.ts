const nprFormatter = new Intl.NumberFormat('en-IN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const numberFormatter = new Intl.NumberFormat('en-IN', {
  maximumFractionDigits: 2,
});

export function formatNpr(value: number | null | undefined): string {
  return `रु ${nprFormatter.format(Number(value ?? 0))}`;
}

export function formatSignedNpr(value: number | null | undefined): string {
  const amount = Number(value ?? 0);
  const sign = amount > 0 ? '+' : amount < 0 ? '-' : '';
  return `${sign}रु ${nprFormatter.format(Math.abs(amount))}`;
}

export function formatNprCompact(value: number | null | undefined): string {
  const amount = Number(value ?? 0);
  const abs = Math.abs(amount);
  if (abs >= 1_00_00_000) {
    return `रु ${(amount / 1_00_00_000).toFixed(2)} Cr`;
  }
  if (abs >= 1_00_000) {
    return `रु ${(amount / 1_00_000).toFixed(2)} L`;
  }
  if (abs >= 1_000) {
    return `रु ${(amount / 1_000).toFixed(1)} K`;
  }
  return `रु ${numberFormatter.format(amount)}`;
}

export function formatNumber(value: number | null | undefined): string {
  return numberFormatter.format(Number(value ?? 0));
}

export function formatPercent(value: number | null | undefined, digits = 2): string {
  const amount = Number(value ?? 0);
  const sign = amount > 0 ? '+' : '';
  return `${sign}${amount.toFixed(digits)}%`;
}

export function formatDate(value: string | Date | null | undefined): string {
  if (!value) return '—';
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) return '—';
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatShortDate(value: string | Date): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
}

export function pnlClass(value: number | null | undefined): string {
  const amount = Number(value ?? 0);
  if (amount > 0) return 'text-gain';
  if (amount < 0) return 'text-loss';
  return 'text-slate-500 dark:text-slate-400';
}

export function actionLabel(action: string): string {
  const labels: Record<string, string> = {
    STRONG_BUY: 'Strong buy',
    BUY: 'Buy',
    HOLD: 'Hold',
    SELL: 'Sell',
    STRONG_SELL: 'Strong sell',
  };
  return labels[action] ?? action;
}

export function actionClasses(action: string): string {
  const classes: Record<string, string> = {
    STRONG_BUY: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 ring-1 ring-emerald-500/30',
    BUY: 'bg-brand-500/15 text-brand-700 dark:text-brand-300 ring-1 ring-brand-500/30',
    HOLD: 'bg-slate-500/10 text-slate-600 dark:text-slate-300 ring-1 ring-slate-500/20',
    SELL: 'bg-amber-500/15 text-amber-700 dark:text-amber-300 ring-1 ring-amber-500/30',
    STRONG_SELL: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 ring-1 ring-rose-500/30',
  };
  return classes[action] ?? classes.HOLD;
}

export function sourceClasses(source: string): string {
  return source === 'ML'
    ? 'bg-violet-500/15 text-violet-600 dark:text-violet-300 ring-1 ring-violet-500/30'
    : 'bg-sky-500/15 text-sky-600 dark:text-sky-300 ring-1 ring-sky-500/30';
}

export function trendClasses(trend: string): string {
  if (trend === 'BULLISH') return 'text-gain';
  if (trend === 'BEARISH') return 'text-loss';
  return 'text-slate-500 dark:text-slate-400';
}
