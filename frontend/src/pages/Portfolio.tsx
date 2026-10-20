import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  ArrowDownUp,
  ArrowUpRight,
  BriefcaseBusiness,
  CircleDollarSign,
  Plus,
  Search,
  Trash2,
  TrendingDown,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import {
  formatDate,
  formatNpr,
  formatPercent,
  formatSignedNpr,
  pnlClass,
} from '@/lib/format';
import { cn } from '@/lib/cn';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';
import Card from '@/components/ui/Card';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeletons';
import AddTradeModal from '@/components/AddTradeModal';
import type { Holding, Portfolio, Trade } from '@/types';

type SortKey = 'symbol' | 'quantity' | 'currentValue' | 'unrealizedProfitLoss' | 'unrealizedPercent';
type Filter = 'all' | 'gain' | 'loss';

export default function PortfolioPage() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [presetSymbol, setPresetSymbol] = useState<string | undefined>(undefined);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [sortKey, setSortKey] = useState<SortKey>('currentValue');
  const [sortDirection, setSortDirection] = useState<1 | -1>(-1);

  const { data: portfolio, isLoading } = useQuery<Portfolio>({
    queryKey: ['portfolio'],
    queryFn: async () => (await api.get('/portfolio')).data,
    refetchInterval: 30_000,
  });

  const { data: trades = [], isLoading: tradesLoading } = useQuery<Trade[]>({
    queryKey: ['trades'],
    queryFn: async () => (await api.get('/portfolio/trades')).data,
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/portfolio/trades/${id}`),
    onSuccess: () => {
      toast.success('Trade deleted');
      void queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      void queryClient.invalidateQueries({ queryKey: ['portfolio-history'] });
      void queryClient.invalidateQueries({ queryKey: ['trades'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      void queryClient.invalidateQueries({ queryKey: ['opportunities'] });
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not delete the trade')),
  });

  const holdings = portfolio?.holdings ?? [];
  const summary = portfolio?.summary;

  const filtered = useMemo(() => {
    const search = query.trim().toLowerCase();
    const list = holdings.filter((holding) => {
      const matchesSearch = !search
        || holding.symbol.toLowerCase().includes(search)
        || holding.companyName.toLowerCase().includes(search);
      const matchesFilter = filter === 'all'
        || (filter === 'gain' && holding.unrealizedProfitLoss > 0)
        || (filter === 'loss' && holding.unrealizedProfitLoss < 0);
      return matchesSearch && matchesFilter;
    });

    return [...list].sort((a, b) => {
      if (sortKey === 'symbol') {
        return a.symbol.localeCompare(b.symbol) * sortDirection;
      }
      return ((a[sortKey] as number) - (b[sortKey] as number)) * sortDirection;
    });
  }, [holdings, query, filter, sortKey, sortDirection]);

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDirection((direction) => (direction === 1 ? -1 : 1));
    } else {
      setSortKey(key);
      setSortDirection(key === 'symbol' ? 1 : -1);
    }
  };

  const confirmDelete = (trade: Trade) => {
    if (window.confirm(`Delete the ${trade.type} of ${trade.quantity} ${trade.symbol} shares?`)) {
      deleteMutation.mutate(trade.id);
    }
  };

  return (
    <div>
      <PageHeader
        title="Portfolio"
        subtitle="Every holding marked to the latest NEPSE price, with FIFO cost basis."
        actions={(
          <Button onClick={() => { setPresetSymbol(undefined); setModalOpen(true); }}>
            <Plus className="h-4 w-4" /> Add trade
          </Button>
        )}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Current value"
          value={formatNpr(summary?.currentValue ?? 0)}
          sub={<span className="muted">{holdings.length} open positions</span>}
          icon={<Wallet className="h-5 w-5" />}
        />
        <StatCard
          label="Unrealized P&L"
          value={<span className={pnlClass(summary?.unrealizedProfitLoss)}>{formatSignedNpr(summary?.unrealizedProfitLoss ?? 0)}</span>}
          sub={<span className={pnlClass(summary?.unrealizedProfitLoss)}>{formatPercent(summary?.totalProfitLossPercent)} on invested</span>}
          icon={(summary?.unrealizedProfitLoss ?? 0) >= 0 ? <TrendingUp className="h-5 w-5" /> : <TrendingDown className="h-5 w-5" />}
          accent={(summary?.unrealizedProfitLoss ?? 0) >= 0 ? 'gain' : 'loss'}
          delay={0.05}
        />
        <StatCard
          label="Realized P&L"
          value={<span className={pnlClass(summary?.realizedProfitLoss)}>{formatSignedNpr(summary?.realizedProfitLoss ?? 0)}</span>}
          sub={<span className="muted">FIFO matched sells</span>}
          icon={<CircleDollarSign className="h-5 w-5" />}
          accent="violet"
          delay={0.1}
        />
        <StatCard
          label="Invested"
          value={formatNpr(summary?.totalInvestment ?? 0)}
          sub={<span className="muted">Including allocated fees</span>}
          icon={<BriefcaseBusiness className="h-5 w-5" />}
          accent="amber"
          delay={0.15}
        />
      </div>

      <Card className="mt-6 overflow-hidden">
        <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
          <SectionTitle title="Holdings" subtitle="Click a column to sort" />
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Filter holdings"
                className="input h-10 w-48 pl-9"
                aria-label="Filter holdings"
              />
            </div>
            <div className="flex rounded-xl border border-slate-200 p-1 dark:border-white/10">
              {(['all', 'gain', 'loss'] as Filter[]).map((value) => (
                <button
                  key={value}
                  onClick={() => setFilter(value)}
                  className={cn(
                    'rounded-lg px-3 py-1.5 text-xs font-semibold capitalize transition',
                    filter === value
                      ? 'bg-brand-500 text-ink-950'
                      : 'text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-white',
                  )}
                >
                  {value}
                </button>
              ))}
            </div>
          </div>
        </div>

        {isLoading ? (
          <TableSkeleton rows={6} />
        ) : holdings.length === 0 ? (
          <div className="px-5 pb-5">
            <EmptyState
              title="No holdings yet"
              description="Record your first buy to see live P&L, allocation and averaging suggestions."
              action={<Button onClick={() => setModalOpen(true)}>Record a trade</Button>}
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-y border-slate-100 bg-slate-50/60 dark:border-white/5 dark:bg-white/[0.02]">
                  {([
                    ['symbol', 'Symbol'],
                    ['quantity', 'Qty'],
                    ['currentValue', 'Value'],
                    ['unrealizedProfitLoss', 'P&L'],
                    ['unrealizedPercent', 'Return'],
                  ] as [SortKey, string][]).map(([key, label]) => (
                    <th
                      key={key}
                      className={cn('table-th cursor-pointer select-none', key !== 'symbol' && 'text-right')}
                      onClick={() => toggleSort(key)}
                    >
                      <span className={cn('inline-flex items-center gap-1', key !== 'symbol' && 'justify-end')}>
                        {label}
                        <ArrowDownUp className={cn('h-3 w-3', sortKey === key ? 'text-brand-500' : 'text-slate-300 dark:text-slate-600')} />
                      </span>
                    </th>
                  ))}
                  <th className="table-th text-right">Avg cost</th>
                  <th className="table-th text-right">LTP</th>
                  <th className="table-th text-right">Day</th>
                  <th className="table-th text-right">Weight</th>
                  <th className="table-th text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-white/5">
                {filtered.map((holding) => (
                  <tr key={holding.stockId} className="transition hover:bg-slate-50/70 dark:hover:bg-white/[0.03]">
                    <td className="table-td">
                      <Link to={`/stocks/${holding.symbol}`} className="group flex items-center gap-2.5">
                        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-500/10 text-[10px] font-extrabold text-brand-700 dark:text-brand-300">
                          {holding.symbol.slice(0, 3)}
                        </span>
                        <span>
                          <span className="block font-bold text-slate-800 group-hover:text-brand-600 dark:text-white dark:group-hover:text-brand-300">
                            {holding.symbol}
                          </span>
                          <span className="block max-w-[160px] truncate text-[10px] text-slate-400">
                            {holding.companyName}
                          </span>
                        </span>
                      </Link>
                    </td>
                    <td className="table-td text-right font-medium">{holding.quantity}</td>
                    <td className="table-td text-right font-semibold">{formatNpr(holding.currentValue)}</td>
                    <td className={cn('table-td text-right font-semibold', pnlClass(holding.unrealizedProfitLoss))}>
                      {formatSignedNpr(holding.unrealizedProfitLoss)}
                    </td>
                    <td className={cn('table-td text-right font-semibold', pnlClass(holding.unrealizedPercent))}>
                      {formatPercent(holding.unrealizedPercent)}
                    </td>
                    <td className="table-td text-right text-slate-500 dark:text-slate-400">{holding.averageCost.toFixed(2)}</td>
                    <td className="table-td text-right font-semibold">{holding.ltp.toFixed(2)}</td>
                    <td className={cn('table-td text-right', pnlClass(holding.dayChange))}>
                      <div className="font-medium">{formatSignedNpr(holding.dayChange)}</div>
                      <div className="text-[10px]">{formatPercent(holding.dayChangePercent)}</div>
                    </td>
                    <td className="table-td text-right text-slate-500 dark:text-slate-400">
                      {holding.weightPercent.toFixed(1)}%
                    </td>
                    <td className="table-td text-right">
                      <button
                        onClick={() => {
                          setPresetSymbol(holding.symbol);
                          setModalOpen(true);
                        }}
                        className="rounded-lg p-1.5 text-slate-400 transition hover:bg-brand-500/10 hover:text-brand-600"
                        aria-label={`Trade ${holding.symbol}`}
                      >
                        <Plus className="h-4 w-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {filtered.length === 0 && (
              <p className="px-5 py-8 text-center text-sm text-slate-400">No holdings match this filter.</p>
            )}
          </div>
        )}
      </Card>

      <Card className="mt-6 overflow-hidden">
        <div className="p-5">
          <SectionTitle
            title="Transaction history"
            subtitle="Newest trades are listed first. Deleting a buy that breaks a later sell is rejected."
            action={<Badge variant="neutral">{trades.length} trades</Badge>}
          />
        </div>
        {tradesLoading ? (
          <TableSkeleton rows={4} />
        ) : trades.length === 0 ? (
          <div className="px-5 pb-5">
            <EmptyState
              title="No transactions recorded"
              description="Add a buy or sell and it will appear here with fees and total cash value."
              action={<Button onClick={() => setModalOpen(true)}>Add trade</Button>}
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-y border-slate-100 bg-slate-50/60 dark:border-white/5 dark:bg-white/[0.02]">
                  <th className="table-th">Date</th>
                  <th className="table-th">Type</th>
                  <th className="table-th">Symbol</th>
                  <th className="table-th text-right">Qty</th>
                  <th className="table-th text-right">Price</th>
                  <th className="table-th text-right">Fees</th>
                  <th className="table-th text-right">Total</th>
                  <th className="table-th">Notes</th>
                  <th className="table-th text-right">Delete</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-white/5">
                {[...trades].reverse().map((trade) => (
                  <tr key={trade.id} className="transition hover:bg-slate-50/70 dark:hover:bg-white/[0.03]">
                    <td className="table-td text-slate-500 dark:text-slate-400">{formatDate(trade.tradeDate)}</td>
                    <td className="table-td">
                      <Badge variant={trade.type === 'BUY' ? 'brand' : 'warn'}>{trade.type}</Badge>
                    </td>
                    <td className="table-td">
                      <Link to={`/stocks/${trade.symbol}`} className="font-bold text-slate-800 hover:text-brand-600 dark:text-white dark:hover:text-brand-300">
                        {trade.symbol}
                      </Link>
                      <span className="ml-2 hidden text-[10px] text-slate-400 md:inline">{trade.companyName}</span>
                    </td>
                    <td className="table-td text-right">{trade.quantity}</td>
                    <td className="table-td text-right">{trade.price.toFixed(2)}</td>
                    <td className="table-td text-right text-slate-500 dark:text-slate-400">{formatNpr(trade.fees)}</td>
                    <td className="table-td text-right font-semibold">{formatNpr(trade.total)}</td>
                    <td className="table-td max-w-[180px] truncate text-slate-500 dark:text-slate-400">{trade.notes ?? '—'}</td>
                    <td className="table-td text-right">
                      <button
                        onClick={() => confirmDelete(trade)}
                        disabled={deleteMutation.isPending}
                        className="rounded-lg p-1.5 text-slate-400 transition hover:bg-rose-500/10 hover:text-rose-500 disabled:opacity-40"
                        aria-label={`Delete trade ${trade.id}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-brand-500/20 bg-brand-500/5 px-5 py-4 text-sm">
        <p className="text-slate-600 dark:text-slate-300">
          Want to see what buying more would do to your average cost?
        </p>
        <Link to="/analysis" className="btn-ghost h-9 py-1.5 text-xs">
          Open averaging analysis <ArrowUpRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      <AddTradeModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        holdings={holdings}
        defaultSymbol={presetSymbol}
      />
    </div>
  );
}
