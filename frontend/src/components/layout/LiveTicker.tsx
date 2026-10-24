import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Radio } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatDateTime, formatPercent, pnlClass } from '@/lib/format';
import { usePriceStream } from '@/lib/streams';
import type { Stock } from '@/types';
import type { MarketStatusInfo } from './MarketStatusPill';

export default function LiveTicker({ initial = [] }: { initial?: Stock[] }) {
  const [prices, setPrices] = useState<Stock[]>(initial);

  const { data } = useQuery<MarketStatusInfo>({
    queryKey: ['market-status'],
    queryFn: async () => (await api.get('/market/status')).data,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });

  const isOpen = data?.marketStatus?.open ?? true;

  useEffect(() => {
    if (initial.length > 0) setPrices(initial);
  }, [initial]);

  usePriceStream((updates) => {
    setPrices((current) => {
      const map = new Map(current.map((stock) => [stock.symbol, stock]));
      for (const update of updates) map.set(update.symbol, update);
      return Array.from(map.values());
    });
  });

  const items = useMemo(() => {
    const list = prices.slice(0, 18);
    return list.length === 0 ? [] : [...list, ...list];
  }, [prices]);

  const feedText = useMemo(() => {
    const scraped = prices.filter((stock) => stock.source === 'SCRAPED');
    if (scraped.length === 0) return 'Seeded feed';
    const latest = scraped.reduce((a, b) => {
      const at = new Date(a.lastUpdated ?? 0).getTime();
      const bt = new Date(b.lastUpdated ?? 0).getTime();
      return at > bt ? a : b;
    });
    return latest.lastUpdated ? formatDateTime(latest.lastUpdated) : 'Scraped feed';
  }, [prices]);

  if (items.length === 0) return null;

  return (
    <div className="card mb-6 flex items-center gap-3 overflow-hidden px-4 py-2.5">
      <span
        className={cn(
          'flex shrink-0 items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.16em]',
          isOpen ? 'text-brand-600 dark:text-brand-400' : 'text-slate-400 dark:text-slate-500',
        )}>
        <Radio className="h-3.5 w-3.5 animate-pulse" /> {isOpen ? 'Live' : 'Closed'}
      </span>
      <div className="relative flex-1 overflow-hidden">
        <div className="flex w-max animate-marquee items-center gap-7 hover:[animation-play-state:paused]">
          {items.map((stock, index) => (
            <Link
              key={`${stock.symbol}-${index}`}
              to={`/stocks/${stock.symbol}`}
              className="flex shrink-0 items-center gap-2 text-xs"
            >
              <span className="font-bold text-slate-700 dark:text-slate-200">{stock.symbol}</span>
              <span className="font-mono text-slate-500 dark:text-slate-400">{stock.ltp.toFixed(2)}</span>
              <span className={cn('font-semibold', pnlClass(stock.changePercent))}>
                {formatPercent(stock.changePercent)}
              </span>
            </Link>
          ))}
        </div>
      </div>
      <span className="hidden shrink-0 text-[10px] text-slate-400 sm:block">
        {feedText}
      </span>
    </div>
  );
}
