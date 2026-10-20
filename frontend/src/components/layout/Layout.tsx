import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();

  return (
    <div className="min-h-screen bg-slate-100 text-slate-800 dark:bg-ink-950 dark:text-slate-100">
      <div className="pointer-events-none fixed inset-0 bg-grid opacity-40 dark:opacity-25" aria-hidden />
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="relative lg:pl-72">
        <Topbar onMenu={() => setSidebarOpen(true)} />
        <main className="mx-auto w-full max-w-[1500px] px-4 py-6 sm:px-6 lg:px-8">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.22 }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
          <footer className="mt-10 border-t border-slate-200 py-5 text-xs text-slate-400 dark:border-white/5">
            <p>
              NEPSE Tracker · Prices from Sharesansar/NEPSE public pages · Seeded history is labelled.
              Educational tool, not investment advice.
            </p>
          </footer>
        </main>
      </div>
    </div>
  );
}
