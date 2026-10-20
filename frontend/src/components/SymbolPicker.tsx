import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { cn } from '@/lib/cn';
import type { Stock } from '@/types';

const MAX_RESULTS = 50;

/**
 * Searchable symbol field. Replaces a plain <datalist>, which is unusable once
 * the universe contains several hundred NEPSE securities.
 */
export default function SymbolPicker({
  id,
  stocks,
  value,
  onChange,
  placeholder = 'Search symbol or company',
  invalid = false,
}: {
  id?: string;
  stocks: Stock[];
  value: string;
  onChange: (symbol: string) => void;
  placeholder?: string;
  invalid?: boolean;
}) {
  const [text, setText] = useState(value);
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const wrapper = useRef<HTMLDivElement>(null);
  const list = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setText(value);
  }, [value]);

  useEffect(() => {
    const onDocumentDown = (event: MouseEvent) => {
      if (wrapper.current && !wrapper.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocumentDown);
    return () => document.removeEventListener('mousedown', onDocumentDown);
  }, []);

  const results = useMemo(() => {
    const query = text.trim().toLowerCase();
    const matched = query.length === 0
      ? stocks
      : stocks.filter((stock) =>
        stock.symbol.toLowerCase().includes(query)
        || stock.companyName.toLowerCase().includes(query));
    return matched.slice(0, MAX_RESULTS);
  }, [stocks, text]);

  useEffect(() => {
    setHighlight(0);
  }, [text]);

  useEffect(() => {
    if (!open) return;
    const node = list.current?.children[highlight] as HTMLElement | undefined;
    node?.scrollIntoView({ block: 'nearest' });
  }, [highlight, open]);

  const choose = (stock: Stock) => {
    setText(stock.symbol);
    onChange(stock.symbol);
    setOpen(false);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!open) {
        setOpen(true);
        return;
      }
      setHighlight((current) => Math.min(current + 1, results.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlight((current) => Math.max(current - 1, 0));
    } else if (event.key === 'Enter') {
      if (open && results[highlight]) {
        event.preventDefault();
        choose(results[highlight]);
      }
    } else if (event.key === 'Escape') {
      if (open) {
        // Close the list without also closing the surrounding modal.
        event.stopPropagation();
        setOpen(false);
      }
    }
  };

  return (
    <div ref={wrapper} className="relative">
      <input
        id={id}
        type="text"
        value={text}
        placeholder={placeholder}
        autoComplete="off"
        spellCheck={false}
        role="combobox"
        aria-expanded={open}
        aria-controls={id ? `${id}-list` : undefined}
        aria-autocomplete="list"
        className={cn('input uppercase', invalid && 'border-rose-500 focus:border-rose-500')}
        onChange={(event) => {
          const next = event.target.value.toUpperCase();
          setText(next);
          onChange(next);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
      />

      {open && (
        <div
          id={id ? `${id}-list` : undefined}
          ref={list}
          role="listbox"
          className="absolute z-50 mt-1 max-h-64 w-full overflow-auto rounded-xl border border-slate-200 bg-white py-1 shadow-2xl dark:border-white/10 dark:bg-ink-850"
        >
          {results.length === 0 ? (
            <p className="px-3 py-2 text-xs text-slate-400">No matching security</p>
          ) : results.map((stock, index) => (
            <button
              type="button"
              key={stock.id}
              role="option"
              aria-selected={index === highlight}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(stock)}
              onMouseEnter={() => setHighlight(index)}
              className={cn(
                'flex w-full items-center gap-2 px-3 py-2 text-left text-xs transition',
                index === highlight ? 'bg-brand-500/10' : 'hover:bg-slate-50 dark:hover:bg-white/5',
              )}
            >
              <span className="w-20 shrink-0 font-bold text-slate-800 dark:text-slate-100">
                {stock.symbol}
              </span>
              <span className="min-w-0 flex-1 truncate text-slate-500 dark:text-slate-400">
                {stock.companyName}
              </span>
              {stock.instrumentType && stock.instrumentType !== 'Equity' && (
                <span className="shrink-0 rounded-md bg-slate-500/10 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-300">
                  {stock.instrumentType}
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
