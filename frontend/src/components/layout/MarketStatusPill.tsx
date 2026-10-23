import { useQuery } from '@tanstack/react-query';
import NepaliDate from 'nepali-date-converter';
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

const englishDay = new Intl.DateTimeFormat('en-US', { weekday: 'long' });
const englishDate = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});

function parseIsoDate(iso: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim());
  if (!match) {
    return null;
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime()) ? null : date;
}

/** NEPSE's business date when it supplies one, else today in Nepal time. */
function todayNptIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kathmandu' }).format(new Date());
}

/** Bikram Sambat rendering for the tooltip: month day, year · weekday (English day).
 *  Note: the library has no `dddd` token — `ddd` is the full Nepali weekday.
 *  Empty when conversion fails. */
function nepaliDateLabel(date: Date): string {
  try {
    const d = new NepaliDate(date);
    return `${d.format('MMMM D, YYYY', 'np')} · ${d.format('ddd', 'np')} (${englishDay.format(date)})`;
  } catch {
    return '';
  }
}

/**
 * Market-open verdict from NEPSE (falling back to the calendar), coloured
 * red/green, dated with the business date when NEPSE supplies it and today
 * (Nepal time) otherwise. The tooltip carries the Bikram Sambat date.
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

  const { open, businessDate } = data.marketStatus;
  const date = parseIsoDate(businessDate ?? '') ?? parseIsoDate(todayNptIso());
  if (!date) {
    return null;
  }

  const label = `${open ? 'Market Open' : 'Market Closed'} - ${englishDate.format(date)} (${englishDay.format(date)})`;
  const bs = nepaliDateLabel(date);

  return (
    <div
      className="hidden items-center gap-1.5 rounded-full border border-slate-200 px-3 py-1.5 text-xs font-semibold dark:border-white/10 md:flex"
      title={bs || undefined}
    >
      <span
        className={cn(
          'h-2 w-2 rounded-full',
          open
            ? 'bg-emerald-500 shadow-[0_0_6px] shadow-emerald-500/60'
            : 'bg-rose-500 shadow-[0_0_6px] shadow-rose-500/60',
        )}
      />
      <span className={open ? 'text-emerald-600 dark:text-emerald-300' : 'text-rose-600 dark:text-rose-300'}>
        {label}
      </span>
    </div>
  );
}
