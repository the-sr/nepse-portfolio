import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Compass, TrendingUp } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

export default function NotFound() {
  const { user } = useAuth();

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-100 px-6 dark:bg-ink-950">
      <div className="pointer-events-none absolute inset-0 bg-grid opacity-40 dark:opacity-20" />
      <div className="pointer-events-none absolute left-1/2 top-1/2 h-96 w-96 -translate-x-1/2 -translate-y-1/2 rounded-full bg-brand-500/10 blur-3xl" />

      <motion.div
        className="relative w-full max-w-lg text-center"
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className="mx-auto mb-6 flex h-16 w-16 animate-float items-center justify-center rounded-3xl bg-brand-500 text-ink-950 shadow-glow">
          <TrendingUp className="h-8 w-8" strokeWidth={2.5} />
        </div>
        <p className="text-7xl font-extrabold tracking-tighter text-slate-300 dark:text-white/10">404</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">This page is off the board</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          The symbol, report or route you followed does not exist. The market may have moved, but this
          link definitely did.
        </p>
        <div className="mt-7 flex flex-wrap justify-center gap-3">
          <Link to={user ? '/dashboard' : '/login'} className="btn-primary">
            <ArrowLeft className="h-4 w-4" /> {user ? 'Back to dashboard' : 'Sign in'}
          </Link>
          <Link to="/portfolio" className="btn-ghost">
            <Compass className="h-4 w-4" /> Go to portfolio
          </Link>
        </div>
      </motion.div>
    </div>
  );
}
