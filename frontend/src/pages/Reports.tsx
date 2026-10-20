import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  CalendarRange,
  Download,
  FileSpreadsheet,
  Info,
  ReceiptText,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { toast } from 'sonner';
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
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import EmptyState from '@/components/ui/EmptyState';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';
import { TableSkeleton } from '@/components/ui/Skeletons';
import PortfolioChart from '@/components/charts/PortfolioChart';
import type { HistoricalValue, RealizedGainsReport } from '@/types';

function isoDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

export default function Reports() {
  const today = new Date();
  const oneYearAgo = new Date();
  oneYearAgo.setFullYear(today.getFullYear() - 1);

  const [from, setFrom] = useState(isoDate(oneYearAgo));
  const [to, setTo] = useState(isoDate(today));
  const [downloading, setDownloading] = useState(false);

  const { data: report, isLoading } = useQuery<RealizedGainsReport>({
    queryKey: ['realized-gains', from, to],
    queryFn: async () => (await api.get('/reports/realized-gains', { params: { from, to } })).data,
  });

  const { data: history = [] } = useQuery<HistoricalValue[]>({
    queryKey: ['portfolio-history', 365],
    queryFn: async () => (await api.get('/portfolio/history', { params: { days: 365 } })).data,
  });

  const downloadCsv = async () => {
    setDownloading(true);
    try {
      const response = await api.get('/reports/realized-gains', {
        params: { from, to, format: 'csv' },
        responseType: 'blob',
      });
      const url = URL.createObjectURL(response.data as Blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `realized-gains-${from}-to-${to}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      toast.success('CSV downloaded');
    } catch (error) {
      toast.error(errorMessage(error, 'Could not download the report'));
    } finally {
      setDownloading(false);
    }
  };

  const rows = report?.rows ?? [];
  const profitable = rows.filter((row) => row.profitLoss > 0).length;

  return (
    <div>
      <PageHeader
        title="Reports"
        subtitle="Realized gains matched by FIFO, with holding periods and a configurable CGT estimate."
        actions={(
          <Button variant="ghost" onClick={downloadCsv} loading={downloading}>
            <Download className="h-4 w-4" /> Export CSV
          </Button>
        )}
      />

      <Card className="mb-6 p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label className="label" htmlFor="report-from">From</label>
            <input
              id="report-from"
              type="date"
              className="input"
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </div>
          <div className="flex-1">
            <label className="label" htmlFor="report-to">To</label>
            <input
              id="report-to"
              type="date"
              className="input"
              value={to}
              min={from}
              onChange={(event) => setTo(event.target.value)}
            />
          </div>
          <div className="flex items-center gap-2 rounded-xl bg-slate-50 px-3 py-2.5 text-xs text-slate-500 dark:bg-white/5 dark:text-slate-400">
            <CalendarRange className="h-4 w-4 text-brand-500" />
            {formatDate(from)} → {formatDate(to)}
          </div>
        </div>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Realized proceeds"
          value={formatNpr(report?.totalProceeds ?? 0)}
          sub={<span className="muted">{rows.length} matched sale rows</span>}
          icon={<Wallet className="h-5 w-5" />}
        />
        <StatCard
          label="Realized P&L"
          value={<span className={pnlClass(report?.totalProfitLoss)}>{formatSignedNpr(report?.totalProfitLoss ?? 0)}</span>}
          sub={<span className={pnlClass(report?.totalProfitLoss)}>{profitable} profitable sales</span>}
          icon={<TrendingUp className="h-5 w-5" />}
          accent={(report?.totalProfitLoss ?? 0) >= 0 ? 'gain' : 'loss'}
          delay={0.05}
        />
        <StatCard
          label="Estimated CGT"
          value={formatNpr(report?.estimatedTax ?? 0)}
          sub={<span className="muted">Only profitable sales are taxed</span>}
          icon={<ReceiptText className="h-5 w-5" />}
          accent="amber"
          delay={0.1}
        />
        <StatCard
          label="Cost basis"
          value={formatNpr(report?.totalCostBasis ?? 0)}
          sub={<span className="muted">Fees allocated per share</span>}
          icon={<FileSpreadsheet className="h-5 w-5" />}
          accent="violet"
          delay={0.15}
        />
      </div>

      <Card className="mt-6 overflow-hidden">
        <div className="p-5">
          <SectionTitle
            title="Realized gains and losses"
            subtitle="One row per FIFO lot matched by a sell"
          />
        </div>
        {isLoading ? (
          <TableSkeleton rows={5} />
        ) : rows.length === 0 ? (
          <div className="px-5 pb-5">
            <EmptyState
              icon={<ReceiptText className="h-6 w-6" />}
              title="No realized sales in this period"
              description="Sell some shares to realize a gain or loss, then export the FIFO breakdown for your records."
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-y border-slate-100 bg-slate-50/60 dark:border-white/5 dark:bg-white/[0.02]">
                  <th className="table-th">Symbol</th>
                  <th className="table-th text-right">Qty</th>
                  <th className="table-th">Bought</th>
                  <th className="table-th">Sold</th>
                  <th className="table-th text-right">Held</th>
                  <th className="table-th text-right">Cost</th>
                  <th className="table-th text-right">Proceeds</th>
                  <th className="table-th text-right">P&L</th>
                  <th className="table-th text-right">Tax rate</th>
                  <th className="table-th text-right">Est. tax</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-white/5">
                {rows.map((row, index) => (
                  <tr key={`${row.symbol}-${row.sellDate}-${index}`} className="transition hover:bg-slate-50/70 dark:hover:bg-white/[0.03]">
                    <td className="table-td">
                      <span className="font-bold text-slate-800 dark:text-white">{row.symbol}</span>
                      <span className="ml-2 hidden text-[10px] text-slate-400 xl:inline">{row.companyName}</span>
                    </td>
                    <td className="table-td text-right">{row.quantity}</td>
                    <td className="table-td text-slate-500 dark:text-slate-400">{formatDate(row.buyDate)}</td>
                    <td className="table-td text-slate-500 dark:text-slate-400">{formatDate(row.sellDate)}</td>
                    <td className="table-td text-right text-slate-500 dark:text-slate-400">
                      {row.holdingDays}d {row.holdingDays > 365 ? <span className="chip ml-1 bg-emerald-500/10 text-emerald-600 dark:text-emerald-300">long</span> : null}
                    </td>
                    <td className="table-td text-right">{formatNpr(row.costBasis)}</td>
                    <td className="table-td text-right">{formatNpr(row.proceeds)}</td>
                    <td className={cn('table-td text-right font-semibold', pnlClass(row.profitLoss))}>
                      {formatSignedNpr(row.profitLoss)}
                    </td>
                    <td className="table-td text-right text-slate-500 dark:text-slate-400">{row.taxRatePercent}%</td>
                    <td className="table-td text-right text-slate-500 dark:text-slate-400">{formatNpr(row.estimatedTax)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-6 grid gap-4 xl:grid-cols-3">
        <Card className="p-5 xl:col-span-2">
          <SectionTitle
            title="Portfolio value, one year"
            subtitle="Current holdings valued against historical closes"
          />
          {history.length > 1 ? (
            <PortfolioChart data={history} height={260} />
          ) : (
            <EmptyState title="Not enough history" description="The chart fills in as daily prices accumulate." />
          )}
        </Card>
        <Card className="p-5">
          <SectionTitle title="Tax assumptions" subtitle="Nepal CGT defaults, editable in Settings" />
          <div className="space-y-3 text-xs text-slate-600 dark:text-slate-400">
            <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
              <span>Holding ≤ 365 days</span>
              <span className="font-bold text-slate-800 dark:text-slate-100">7.5%</span>
            </div>
            <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
              <span>Holding &gt; 365 days</span>
              <span className="font-bold text-slate-800 dark:text-slate-100">5.0%</span>
            </div>
            <div className="flex items-start gap-2 rounded-xl bg-amber-500/5 p-3 text-[11px] text-amber-700 dark:text-amber-300">
              <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              The estimate applies your settings to positive FIFO gains only. Brokerage, DP charges and
              actual tax filing rules may differ; confirm with a licensed professional.
            </div>
            <div className="rounded-xl border border-slate-100 p-3 dark:border-white/5">
              <p className="font-semibold text-slate-700 dark:text-slate-200">FIFO reminder</p>
              <p className="mt-1">
                Every sale consumes the oldest open lot first. Fees paid on both buy and sell are spread
                per share, so realized and unrealized numbers reconcile.
              </p>
            </div>
            <p className="text-[10px] text-slate-400">
              Total return on this report: {formatPercent(
                report && report.totalCostBasis > 0
                  ? (report.totalProfitLoss / report.totalCostBasis) * 100
                  : 0,
              )}
            </p>
          </div>
        </Card>
      </div>
    </div>
  );
}
