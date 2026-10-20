import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Radio } from 'lucide-react';
import { cn } from '@/lib/cn';
import { formatPercent, pnlClass } from '@/lib/format';
import { usePriceStream } from '@/lib/streams';
import type { Stock } from '@/types';

export default function LiveTicker({ initial = [] }: { initial?: Stock[] }) {
  const [prices, setPrices] = useState<Stock[]>(initial);

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

  if (items.length === 0) return null;

  return (
    <div className="card mb-6 flex items-center gap-3 overflow-hidden px-4 py-2.5">
      <span className="flex shrink-0 items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-brand-600 dark:text-brand-400">
        <Radio className="h-3.5 w-3.5 animate-pulse" /> Live
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
        {prices.some((stock) => stock.source === 'SCRAPED') ? 'Scraped feed' : 'Seeded feed'}
      </span>
    </div>
  );
}
