import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { formatNprCompact, formatShortDate } from '@/lib/format';
import type { HistoryPoint } from '@/types';

export default function PriceChart({ data, height = 320 }: { data: HistoryPoint[]; height?: number }) {
  const rising = data.length > 1 && data[data.length - 1].close >= data[0].close;
  const color = rising ? '#10b981' : '#f43f5e';

  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer>
        <AreaChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity={0.32} />
              <stop offset="100%" stopColor={color} stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 6" stroke="rgba(148,163,184,0.16)" vertical={false} />
          <XAxis
            dataKey="date"
            tickFormatter={formatShortDate}
            tick={{ fontSize: 11, fill: '#94a3b8' }}
            axisLine={false}
            tickLine={false}
            minTickGap={30}
          />
          <YAxis
            domain={['auto', 'auto']}
            tickFormatter={(value: number) => value.toFixed(0)}
            tick={{ fontSize: 11, fill: '#94a3b8' }}
            axisLine={false}
            tickLine={false}
            width={56}
          />
          <Tooltip
            formatter={(value: number) => [formatNprCompact(value), 'Close']}
            labelFormatter={(label: string) => formatShortDate(label)}
            contentStyle={{
              borderRadius: 12,
              border: '1px solid rgba(148,163,184,0.25)',
              background: 'rgba(15,23,42,0.95)',
              color: '#e2e8f0',
              fontSize: 12,
            }}
          />
          <Area
            type="monotone"
            dataKey="close"
            stroke={color}
            strokeWidth={2.4}
            fill="url(#priceFill)"
            dot={false}
            activeDot={{ r: 4, fill: color }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
