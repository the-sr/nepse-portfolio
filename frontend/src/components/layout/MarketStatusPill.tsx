import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { cn } from '@/lib/cn';

export interface MarketStatusInfo {
  marketStatus: {
    open: boolean;
    source: 'API' | 'CALENDAR';
    businessDate: string;
  };
  lastAttempt?: string;
  lastSuccess?: boolean;
  lastRows?: number;
  lastMessage?: string | null;
}

/**
 * Live market-open verdict straight from NEPSE (falling back to the calendar),
 * plus the market's own business date — the last real session. Hidden when the
 * endpoint has no data yet so the Topbar stays clean.
 */
export default function MarketStatusPill() {
  const { data } = useQuery<MarketStatusInfo>({
    queryKey: ['market-status'],
    queryFn: async () => (await api.get('/market/status')).data,
    refetchInterval: 60_000,
    staleTime: 30_000,
    retry: 1,
  });

  if (!data?.marketStatus) {
    return null;
  }

  const { open, source, businessDate } = data.marketStatus;
  const feedTime = data.lastAttempt
    ? new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Asia/Kathmandu',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(new Date(data.lastAttempt))
    : null;

  return (
    <div
      className="hidden items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold md:flex"
      title={data.lastMessage
        ? `${data.lastMessage}${businessDate ? ` · business date ${businessDate}` : ''}`
        : businessDate
          ? `Business date ${businessDate}`
          : undefined}
    >
      <span
        className={cn(
          'h-2 w-2 rounded-full',
          open ? 'bg-emerald-500 shadow-[0_0_6px] shadow-emerald-500/60' : 'bg-slate-400 dark:bg-slate-500',
        )}
      />
      <span className={open ? 'text-emerald-600 dark:text-emerald-300' : 'text-slate-500 dark:text-slate-400'}>
        {open ? 'Market Open' : 'Market Closed'}
      </span>
      {feedTime && data.lastRows != null && (
        <span className="text-[10px] uppercase tracking-wider text-slate-400">
          {data.lastRows} quotes · {feedTime} NPT
        </span>
      )}
      {businessDate && (
        <span className="text-[10px] uppercase tracking-wider text-slate-400">
          · {businessDate}
        </span>
      )}
      {source === 'CALENDAR' && (
        <span className="text-[10px] uppercase tracking-wider text-amber-500" title="NEPSE's own verdict is unreachable; using the calendar">
          (calendar)
        </span>
      )}
    </div>
  );
}
