import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { formatNpr, formatNprCompact, formatShortDate } from '@/lib/format';
import type { HistoricalValue } from '@/types';

type TooltipPayload = {
  name?: string;
  value?: number | string;
  color?: string;
};

function ChartTooltip({ active, payload, label }: {
  active?: boolean;
  payload?: TooltipPayload[];
  label?: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-xl border border-slate-200 bg-white/95 p-3 text-xs shadow-xl backdrop-blur dark:border-white/10 dark:bg-ink-850/95">
      <p className="mb-1.5 font-semibold text-slate-500 dark:text-slate-400">{formatShortDate(label ?? '')}</p>
      {payload.map((entry) => (
        <p key={entry.name} className="flex items-center gap-2 font-medium text-slate-700 dark:text-slate-200">
          <span className="h-2 w-2 rounded-full" style={{ background: entry.color }} />
          {entry.name}: {formatNpr(Number(entry.value))}
        </p>
      ))}
    </div>
  );
}

export default function PortfolioChart({ data, height = 280 }: { data: HistoricalValue[]; height?: number }) {
  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer>
        <AreaChart data={data} margin={{ top: 10, right: 8, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="portfolioValue" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#10b981" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#10b981" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 6" stroke="rgba(148,163,184,0.16)" vertical={false} />
          <XAxis
            dataKey="date"
            tickFormatter={formatShortDate}
            tick={{ fontSize: 11, fill: '#94a3b8' }}
            axisLine={false}
            tickLine={false}
            minTickGap={28}
          />
          <YAxis
            tickFormatter={(value: number) => formatNprCompact(value)}
            tick={{ fontSize: 11, fill: '#94a3b8' }}
            axisLine={false}
            tickLine={false}
            width={78}
          />
          <Tooltip content={<ChartTooltip />} />
          <Area
            type="monotone"
            dataKey="value"
            name="Value"
            stroke="#10b981"
            strokeWidth={2.4}
            fill="url(#portfolioValue)"
            dot={false}
            activeDot={{ r: 4, fill: '#10b981' }}
          />
          <Area
            type="monotone"
            dataKey="investment"
            name="Invested"
            stroke="#64748b"
            strokeDasharray="5 5"
            strokeWidth={1.6}
            fill="transparent"
            dot={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
