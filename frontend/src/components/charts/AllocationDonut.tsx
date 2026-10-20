import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { formatNpr } from '@/lib/format';
import type { AllocationSlice } from '@/types';

const COLORS = ['#10b981', '#38bdf8', '#a78bfa', '#f59e0b', '#fb7185', '#2dd4bf', '#facc15', '#818cf8'];

export default function AllocationDonut({
  data,
  total,
  height = 230,
}: {
  data: AllocationSlice[];
  total: number;
  height?: number;
}) {
  const slices = data.slice(0, 8);

  return (
    <div>
      <div className="relative" style={{ width: '100%', height }}>
        <ResponsiveContainer>
          <PieChart>
            <Pie
              data={slices}
              dataKey="value"
              nameKey="label"
              cx="50%"
              cy="50%"
              innerRadius="63%"
              outerRadius="88%"
              paddingAngle={2}
              stroke="none"
            >
              {slices.map((entry, index) => (
                <Cell key={entry.label} fill={COLORS[index % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip
              formatter={(value: number) => formatNpr(value)}
              contentStyle={{
                borderRadius: 12,
                border: '1px solid rgba(148,163,184,0.25)',
                background: 'rgba(15,23,42,0.95)',
                color: '#e2e8f0',
                fontSize: 12,
              }}
            />
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">Portfolio</span>
          <span className="text-lg font-bold text-slate-900 dark:text-white">{formatNpr(total)}</span>
        </div>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2">
        {slices.map((slice, index) => (
          <div key={slice.label} className="flex items-center justify-between gap-2 text-xs">
            <span className="flex min-w-0 items-center gap-1.5">
              <span
                className="h-2 w-2 shrink-0 rounded-full"
                style={{ background: COLORS[index % COLORS.length] }}
              />
              <span className="truncate font-medium text-slate-600 dark:text-slate-300">{slice.label}</span>
            </span>
            <span className="font-semibold text-slate-500 dark:text-slate-400">{slice.percent.toFixed(1)}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}
