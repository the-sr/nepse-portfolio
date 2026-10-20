import { NavLink } from 'react-router-dom';
import {
  BarChart3,
  BellRing,
  BriefcaseBusiness,
  FileText,
  LayoutDashboard,
  Mail,
  Settings as SettingsIcon,
  Sparkles,
  TrendingUp,
  X,
} from 'lucide-react';
import { cn } from '@/lib/cn';

const items = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/portfolio', label: 'Portfolio', icon: BriefcaseBusiness },
  { to: '/analysis', label: 'Analysis', icon: Sparkles },
  { to: '/alerts', label: 'Alerts', icon: BellRing },
  { to: '/reports', label: 'Reports', icon: FileText },
  { to: '/contact', label: 'Contact', icon: Mail },
  { to: '/settings', label: 'Settings', icon: SettingsIcon },
];

export default function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <>
      {open && (
        <div
          className="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm lg:hidden"
          onClick={onClose}
          aria-hidden
        />
      )}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-72 flex-col border-r border-slate-200 bg-white transition-transform duration-300 dark:border-white/5 dark:bg-ink-900 lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex items-center justify-between px-6 py-5">
          <NavLink to="/dashboard" className="flex items-center gap-3" onClick={onClose}>
            <span className="rounded-xl bg-brand-500 p-2 text-ink-950 shadow-glow">
              <TrendingUp className="h-5 w-5" strokeWidth={2.5} />
            </span>
            <span>
              <span className="block text-base font-extrabold tracking-tight text-slate-900 dark:text-white">
                NEPSE Tracker
              </span>
              <span className="block text-[11px] font-medium uppercase tracking-[0.18em] text-brand-600 dark:text-brand-400">
                Portfolio intelligence
              </span>
            </span>
          </NavLink>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-white/10 lg:hidden"
            aria-label="Close navigation"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-3">
          {items.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={onClose}
              className={({ isActive }) => cn(
                'group flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-semibold transition',
                isActive
                  ? 'bg-brand-500/12 text-brand-700 ring-1 ring-brand-500/20 dark:text-brand-300'
                  : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-white/5 dark:hover:text-white',
              )}
            >
              <Icon className="h-[18px] w-[18px]" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="px-4 pb-5">
          <div className="rounded-2xl border border-brand-500/20 bg-gradient-to-br from-brand-500/10 to-transparent p-4">
            <div className="flex items-center gap-2 text-xs font-bold text-brand-700 dark:text-brand-300">
              <BarChart3 className="h-4 w-4" />
              NEPSE market feed
            </div>
            <p className="mt-1.5 text-[11px] leading-relaxed text-slate-500 dark:text-slate-400">
              Live prices scraped from Sharesansar. Seeded history is labelled until real data builds up.
            </p>
            <div className="mt-3 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />
              Educational use only
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}
