import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Clock3,
  LogOut,
  Menu,
  Moon,
  Search,
  Settings as SettingsIcon,
  Sun,
  UserRound,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { api } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatPercent, pnlClass } from '@/lib/format';
import type { Stock } from '@/types';

function KathmanduClock() {
  const [time, setTime] = useState('');
  useEffect(() => {
    const format = () => new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Kathmandu',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(new Date());
    setTime(format());
    const timer = setInterval(() => setTime(format()), 30_000);
    return () => clearInterval(timer);
  }, []);
  return (
    <div className="hidden items-center gap-1.5 rounded-full border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-500 dark:border-white/10 dark:text-slate-400 md:flex">
      <Clock3 className="h-3.5 w-3.5" />
      {time} <span className="text-[10px] uppercase tracking-wider text-slate-400">NPT</span>
    </div>
  );
}

export default function Topbar({ onMenu }: { onMenu: () => void }) {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(query.trim()), 250);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const { data: results = [], isFetching } = useQuery<Stock[]>({
    queryKey: ['stock-search', debounced],
    queryFn: async () => (await api.get('/market/stocks', { params: { query: debounced } })).data,
    enabled: debounced.length > 0,
  });

  const goToStock = (symbol: string) => {
    setQuery('');
    setDebounced('');
    setSearchOpen(false);
    navigate(`/stocks/${symbol}`);
  };

  const initials = (user?.name ?? 'U')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-slate-100/80 backdrop-blur-xl dark:border-white/5 dark:bg-ink-950/75">
      <div className="flex h-16 items-center gap-3 px-4 sm:px-6">
        <button
          onClick={onMenu}
          className="rounded-xl border border-slate-200 p-2 text-slate-500 hover:bg-white dark:border-white/10 dark:text-slate-300 dark:hover:bg-white/5 lg:hidden"
          aria-label="Open navigation"
        >
          <Menu className="h-5 w-5" />
        </button>

        <div className="relative w-full max-w-md">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setSearchOpen(true);
              }}
              onFocus={() => setSearchOpen(true)}
              onBlur={() => setTimeout(() => setSearchOpen(false), 160)}
              placeholder="Search NEPSE symbols or companies…"
              className="input pl-10"
              aria-label="Search stocks"
            />
          </div>
          {searchOpen && debounced.length > 0 && (
            <div className="absolute mt-2 w-full overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl dark:border-white/10 dark:bg-ink-850">
              {isFetching && (
                <p className="px-4 py-3 text-sm text-slate-500 dark:text-slate-400">Searching…</p>
              )}
              {!isFetching && results.length === 0 && (
                <p className="px-4 py-3 text-sm text-slate-500 dark:text-slate-400">No symbols found.</p>
              )}
              {results.slice(0, 8).map((stock) => (
                <button
                  key={stock.id}
                  onMouseDown={() => goToStock(stock.symbol)}
                  className="flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left transition hover:bg-slate-50 dark:hover:bg-white/5"
                >
                  <span className="min-w-0">
                    <span className="block text-sm font-bold text-slate-900 dark:text-white">{stock.symbol}</span>
                    <span className="block truncate text-xs text-slate-500 dark:text-slate-400">{stock.companyName}</span>
                  </span>
                  <span className="text-right">
                    <span className="block text-sm font-semibold text-slate-800 dark:text-slate-200">{stock.ltp.toFixed(2)}</span>
                    <span className={cn('block text-xs font-semibold', pnlClass(stock.changePercent))}>
                      {formatPercent(stock.changePercent)}
                    </span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="ml-auto flex items-center gap-2">
          <KathmanduClock />
          <button
            onClick={toggleTheme}
            className="rounded-xl border border-slate-200 p-2 text-slate-500 transition hover:bg-white hover:text-slate-900 dark:border-white/10 dark:text-slate-300 dark:hover:bg-white/5 dark:hover:text-white"
            aria-label="Toggle colour theme"
          >
            {theme === 'dark' ? <Sun className="h-[18px] w-[18px]" /> : <Moon className="h-[18px] w-[18px]" />}
          </button>

          <div className="relative" ref={menuRef}>
            <button
              onClick={() => setMenuOpen((value) => !value)}
              className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white p-1.5 pr-3 transition hover:border-brand-500/40 dark:border-white/10 dark:bg-white/5"
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-500 text-xs font-extrabold text-ink-950">
                {initials}
              </span>
              <span className="hidden text-left sm:block">
                <span className="block max-w-[130px] truncate text-xs font-bold text-slate-800 dark:text-white">
                  {user?.name}
                </span>
                <span className="block text-[10px] uppercase tracking-wider text-slate-400">{user?.role}</span>
              </span>
            </button>
            {menuOpen && (
              <div className="absolute right-0 mt-2 w-56 overflow-hidden rounded-2xl border border-slate-200 bg-white py-1.5 shadow-xl dark:border-white/10 dark:bg-ink-850">
                <div className="border-b border-slate-100 px-4 py-2.5 dark:border-white/5">
                  <p className="text-sm font-semibold text-slate-800 dark:text-white">{user?.name}</p>
                  <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user?.email}</p>
                </div>
                <Link
                  to="/settings"
                  onClick={() => setMenuOpen(false)}
                  className="flex items-center gap-2.5 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-white/5"
                >
                  <SettingsIcon className="h-4 w-4" /> Settings
                </Link>
                <Link
                  to="/contact"
                  onClick={() => setMenuOpen(false)}
                  className="flex items-center gap-2.5 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-white/5"
                >
                  <UserRound className="h-4 w-4" /> Contact & support
                </Link>
                <button
                  onClick={() => {
                    setMenuOpen(false);
                    void logout();
                  }}
                  className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-500/10"
                >
                  <LogOut className="h-4 w-4" /> Sign out
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
