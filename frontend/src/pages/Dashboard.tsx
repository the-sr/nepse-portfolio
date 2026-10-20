import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  ArrowRight,
  ArrowUpRight,
  BriefcaseBusiness,
  CircleDollarSign,
  PieChart,
  TrendingDown,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { api } from '@/lib/api';
import {
  formatNpr,
  formatNprCompact,
  formatPercent,
  formatSignedNpr,
  pnlClass,
} from '@/lib/format';
import { cn } from '@/lib/cn';
import Card from '@/components/ui/Card';
import StatCard from '@/components/ui/StatCard';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';
import EmptyState from '@/components/ui/EmptyState';
import { CardsSkeleton, TableSkeleton } from '@/components/ui/Skeletons';
import LiveTicker from '@/components/layout/LiveTicker';
import PortfolioChart from '@/components/charts/PortfolioChart';
import AllocationDonut from '@/components/charts/AllocationDonut';
import RecommendationCard from '@/components/RecommendationCard';
import type {
  HistoricalValue,
  Movers,
  Portfolio,
  Recommendation,
  SectorPerformance,
  Stock,
} from '@/types';

function greeting() {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

function MoverList({ title, stocks, tone }: { title: string; stocks: Stock[]; tone: 'gain' | 'loss' }) {
  return (
    <Card className="p-4">
      <SectionTitle title={title} />
      {stocks.length === 0 ? (
        <p className="py-6 text-center text-xs text-slate-400">No movement yet.</p>
      ) : (
        <div className="space-y-1">
          {stocks.map((stock) => (
            <Link
              key={stock.id}
              to={`/stocks/${stock.symbol}`}
              className="flex items-center justify-between rounded-xl px-2 py-2 transition hover:bg-slate-50 dark:hover:bg-white/5"
            >
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-800 dark:text-slate-100">{stock.symbol}</p>
                <p className="max-w-[120px] truncate text-[10px] text-slate-400">{stock.companyName}</p>
              </div>
              <div className="text-right">
                <p className="text-xs font-semibold text-slate-700 dark:text-slate-200">{stock.ltp.toFixed(2)}</p>
                <p className={cn('text-[10px] font-bold', tone === 'gain' ? 'text-gain' : 'text-loss')}>
                  {formatPercent(stock.changePercent)}
                </p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </Card>
  );
}

export default function Dashboard() {
  const { user } = useAuth();

  const { data: portfolio, isLoading } = useQuery<Portfolio>({
    queryKey: ['portfolio'],
    queryFn: async () => (await api.get('/portfolio')).data,
    refetchInterval: 30_000,
  });

  const { data: history = [] } = useQuery<HistoricalValue[]>({
    queryKey: ['portfolio-history', 120],
    queryFn: async () => (await api.get('/portfolio/history', { params: { days: 120 } })).data,
  });

  const { data: movers } = useQuery<Movers>({
    queryKey: ['movers'],
    queryFn: async () => (await api.get('/market/movers')).data,
    refetchInterval: 60_000,
  });

  const { data: recommendations = [] } = useQuery<Recommendation[]>({
    queryKey: ['recommendations'],
    queryFn: async () => (await api.get('/analysis/recommendations')).data,
    refetchInterval: 120_000,
  });

  const { data: sectors = [] } = useQuery<SectorPerformance[]>({
    queryKey: ['sectors'],
    queryFn: async () => (await api.get('/market/sectors')).data,
  });

  const summary = portfolio?.summary;
  const holdings = portfolio?.holdings ?? [];
  const totalPl = summary?.totalProfitLoss ?? 0;

  return (
    <div>
      <PageHeader
        title={`${greeting()}, ${user?.name?.split(' ')[0] ?? 'investor'}`}
        subtitle="Your NEPSE portfolio, live prices and the decisions that matter today."
        actions={(
          <Link to="/portfolio" className="btn-primary">
            Add a trade <ArrowRight className="h-4 w-4" />
          </Link>
        )}
      />

      <LiveTicker initial={movers?.mostActive ?? []} />

      {isLoading ? (
        <CardsSkeleton />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="Portfolio value"
            value={formatNpr(summary?.currentValue ?? 0)}
            sub={<span className="muted">{summary?.holdingsCount ?? 0} holdings · {formatNprCompact(summary?.currentValue ?? 0)}</span>}
            icon={<Wallet className="h-5 w-5" />}
            accent="brand"
          />
          <StatCard
            label="Total profit / loss"
            value={<span className={pnlClass(totalPl)}>{formatSignedNpr(totalPl)}</span>}
            sub={<span className={pnlClass(totalPl)}>{formatPercent(summary?.totalProfitLossPercent)} overall</span>}
            icon={totalPl >= 0 ? <TrendingUp className="h-5 w-5" /> : <TrendingDown className="h-5 w-5" />}
            accent={totalPl >= 0 ? 'gain' : 'loss'}
            delay={0.05}
          />
          <StatCard
            label="Today"
            value={<span className={pnlClass(summary?.dayChange)}>{formatSignedNpr(summary?.dayChange ?? 0)}</span>}
            sub={<span className={pnlClass(summary?.dayChange)}>{formatPercent(summary?.dayChangePercent)} vs previous close</span>}
            icon={<CircleDollarSign className="h-5 w-5" />}
            accent="violet"
            delay={0.1}
          />
          <StatCard
            label="Invested"
            value={formatNpr(summary?.totalInvestment ?? 0)}
            sub={<span className="muted">Realized {formatSignedNpr(summary?.realizedProfitLoss ?? 0)}</span>}
            icon={<BriefcaseBusiness className="h-5 w-5" />}
            accent="amber"
            delay={0.15}
          />
        </div>
      )}

      <div className="mt-6 grid gap-4 xl:grid-cols-3">
        <Card className="p-5 xl:col-span-2">
          <SectionTitle
            title="Portfolio performance"
            subtitle="Last 120 days of value versus invested capital"
            action={(
              <div className="hidden items-center gap-3 text-[10px] font-semibold uppercase tracking-wider text-slate-400 sm:flex">
                <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-brand-500" /> Value</span>
                <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-slate-500" /> Invested</span>
              </div>
            )}
          />
          {history.length > 1 ? (
            <PortfolioChart data={history} />
          ) : (
            <EmptyState
              title="No performance history yet"
              description="Add your first transaction and the chart will start filling in."
              action={<Link to="/portfolio" className="btn-primary">Add a trade</Link>}
            />
          )}
        </Card>

        <Card className="p-5">
          <SectionTitle title="Allocation" subtitle="By symbol, at last traded price" />
          {holdings.length > 0 ? (
            <AllocationDonut data={portfolio?.allocation ?? []} total={summary?.currentValue ?? 0} />
          ) : (
            <EmptyState
              icon={<PieChart className="h-6 w-6" />}
              title="Nothing allocated yet"
              description="Your portfolio mix appears once you record a buy."
            />
          )}
        </Card>
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-3">
        <Card className="overflow-hidden xl:col-span-2">
          <div className="px-5 pt-5">
            <SectionTitle
              title="Holdings"
              subtitle="Ranked by current value"
              action={(
                <Link to="/portfolio" className="flex items-center gap-1 text-xs font-semibold text-brand-600 hover:text-brand-500 dark:text-brand-400">
                  View all <ArrowUpRight className="h-3.5 w-3.5" />
                </Link>
              )}
            />
          </div>
          {isLoading ? (
            <TableSkeleton />
          ) : holdings.length === 0 ? (
            <div className="px-5 pb-5">
              <EmptyState
                title="Your portfolio is empty"
                description="Record a buy to start tracking FIFO profit and loss against live prices."
                action={<Link to="/portfolio" className="btn-primary">Record a trade</Link>}
              />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-y border-slate-100 dark:border-white/5">
                    <th className="table-th">Symbol</th>
                    <th className="table-th text-right">Qty</th>
                    <th className="table-th text-right">Avg cost</th>
                    <th className="table-th text-right">LTP</th>
                    <th className="table-th text-right">P&L</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-white/5">
                  {holdings.slice(0, 6).map((holding) => (
                    <tr key={holding.stockId} className="transition hover:bg-slate-50/70 dark:hover:bg-white/[0.03]">
                      <td className="table-td">
                        <Link to={`/stocks/${holding.symbol}`} className="group flex items-center gap-2">
                          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-500/10 text-[10px] font-extrabold text-brand-700 dark:text-brand-300">
                            {holding.symbol.slice(0, 3)}
                          </span>
                          <span>
                            <span className="block font-bold text-slate-800 group-hover:text-brand-600 dark:text-white dark:group-hover:text-brand-300">
                              {holding.symbol}
                            </span>
                            <span className="block max-w-[140px] truncate text-[10px] text-slate-400">{holding.companyName}</span>
                          </span>
                        </Link>
                      </td>
                      <td className="table-td text-right font-medium">{holding.quantity}</td>
                      <td className="table-td text-right text-slate-500 dark:text-slate-400">{holding.averageCost.toFixed(2)}</td>
                      <td className="table-td text-right font-semibold">{holding.ltp.toFixed(2)}</td>
                      <td className={cn('table-td text-right font-semibold', pnlClass(holding.unrealizedProfitLoss))}>
                        <div>{formatSignedNpr(holding.unrealizedProfitLoss)}</div>
                        <div className="text-[10px] font-medium">{formatPercent(holding.unrealizedPercent)}</div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <div className="space-y-4">
          <MoverList title="Top gainers" stocks={movers?.gainers ?? []} tone="gain" />
          <MoverList title="Top losers" stocks={movers?.losers ?? []} tone="loss" />
        </div>
      </div>

      <div className="mt-6">
        <SectionTitle
          title="Recommendations"
          subtitle="Generated from trend, momentum and your cost basis"
          action={(
            <Link to="/analysis" className="flex items-center gap-1 text-xs font-semibold text-brand-600 hover:text-brand-500 dark:text-brand-400">
              Full analysis <ArrowUpRight className="h-3.5 w-3.5" />
            </Link>
          )}
        />
        {recommendations.length === 0 ? (
          <EmptyState
            title="No recommendations yet"
            description="Recommendations appear for the stocks you hold."
          />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {recommendations.slice(0, 3).map((recommendation, index) => (
              <motion.div
                key={recommendation.symbol}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
              >
                <RecommendationCard recommendation={recommendation} />
              </motion.div>
            ))}
          </div>
        )}
      </div>

      {sectors.length > 0 && (
        <Card className="mt-6 p-5">
          <SectionTitle title="Sector pulse" subtitle="Average day change across the NEPSE sectors we track" />
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {sectors.slice(0, 4).map((sector) => (
              <div key={sector.sector} className="rounded-xl border border-slate-100 p-3 dark:border-white/5">
                <p className="truncate text-xs font-semibold text-slate-600 dark:text-slate-300">{sector.sector}</p>
                <p className={cn('mt-1 text-lg font-bold', pnlClass(sector.avgChangePercent))}>
                  {formatPercent(sector.avgChangePercent)}
                </p>
                <p className="text-[10px] text-slate-400">{sector.stocks} symbols</p>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
