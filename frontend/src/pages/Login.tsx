import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowRight, Eye, EyeOff, ShieldCheck, Sparkles, TrendingUp } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { errorMessage } from '@/lib/errors';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

export default function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  const onSubmit = async (values: FormValues) => {
    try {
      await login(values.email, values.password);
      toast.success('Welcome back');
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/dashboard', { replace: true });
    } catch (error) {
      toast.error(errorMessage(error, 'Could not sign in'));
    }
  };

  const fillDemo = () => {
    setValue('email', 'demo@nepse.local');
    setValue('password', 'demo1234');
  };

  return (
    <div className="relative min-h-screen overflow-hidden bg-slate-950 text-slate-100">
      <div className="pointer-events-none absolute inset-0 bg-grid opacity-20" />
      <div className="pointer-events-none absolute -left-40 top-1/4 h-96 w-96 rounded-full bg-brand-500/20 blur-3xl" />
      <div className="pointer-events-none absolute -right-32 bottom-0 h-96 w-96 rounded-full bg-cyan-500/10 blur-3xl" />

      <div className="relative mx-auto grid min-h-screen max-w-6xl items-center gap-12 px-6 py-12 lg:grid-cols-2">
        <div className="hidden lg:block">
          <div className="mb-8 flex items-center gap-3">
            <span className="rounded-2xl bg-brand-500 p-3 text-ink-950 shadow-glow">
              <TrendingUp className="h-6 w-6" strokeWidth={2.5} />
            </span>
            <div>
              <p className="text-lg font-extrabold tracking-tight">NEPSE Tracker</p>
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-brand-400">Portfolio intelligence</p>
            </div>
          </div>

          <h1 className="text-balance text-4xl font-extrabold leading-tight tracking-tight">
            Know exactly where your <span className="text-brand-400">NEPSE money</span> stands.
          </h1>
          <p className="mt-4 max-w-md text-sm leading-relaxed text-slate-400">
            FIFO profit and loss, live last-traded prices, averaging-down rules, price alerts and
            explainable buy/hold/sell recommendations — in one animated dashboard.
          </p>

          <div className="mt-8 space-y-4">
            {[
              { icon: ShieldCheck, title: 'FIFO cost basis', text: 'Realized and unrealized P&L that actually reconciles with your trades.' },
              { icon: Sparkles, title: 'Averaging engine', text: 'See the shares and capital needed to reach a target average.' },
              { icon: TrendingUp, title: 'Market context', text: 'SMA, RSI, momentum and trend scoring on every holding.' },
            ].map(({ icon: Icon, title, text }) => (
              <div key={title} className="flex gap-3">
                <span className="mt-0.5 rounded-xl bg-white/5 p-2 text-brand-300 ring-1 ring-white/10">
                  <Icon className="h-4 w-4" />
                </span>
                <div>
                  <p className="text-sm font-semibold">{title}</p>
                  <p className="text-xs text-slate-400">{text}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="mx-auto w-full max-w-md">
          <div className="rounded-3xl border border-white/10 bg-white/[0.04] p-7 shadow-2xl backdrop-blur-xl sm:p-9">
            <div className="mb-7 lg:hidden">
              <div className="mb-4 flex items-center gap-2.5">
                <span className="rounded-xl bg-brand-500 p-2 text-ink-950">
                  <TrendingUp className="h-5 w-5" />
                </span>
                <span className="font-extrabold tracking-tight">NEPSE Tracker</span>
              </div>
            </div>
            <h2 className="text-2xl font-bold tracking-tight">Sign in</h2>
            <p className="mt-1 text-sm text-slate-400">Pick up where your portfolio left off.</p>

            <form onSubmit={handleSubmit(onSubmit)} className="mt-7 space-y-5">
              <div>
                <label className="label text-slate-400" htmlFor="email">Email</label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  className="input border-white/10 bg-white/5 text-slate-100 placeholder:text-slate-500 focus:border-brand-400"
                  {...register('email')}
                />
                {errors.email && <p className="mt-1.5 text-xs text-rose-400">{errors.email.message}</p>}
              </div>

              <div>
                <label className="label text-slate-400" htmlFor="password">Password</label>
                <div className="relative">
                  <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    placeholder="••••••••"
                    className="input border-white/10 bg-white/5 pr-11 text-slate-100 placeholder:text-slate-500 focus:border-brand-400"
                    {...register('password')}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {errors.password && <p className="mt-1.5 text-xs text-rose-400">{errors.password.message}</p>}
              </div>

              <button type="submit" disabled={isSubmitting} className="btn-primary w-full py-3">
                {isSubmitting ? 'Signing in…' : 'Sign in'}
                {!isSubmitting && <ArrowRight className="h-4 w-4" />}
              </button>
            </form>

            <button
              onClick={fillDemo}
              className="mt-4 w-full rounded-xl border border-dashed border-brand-500/30 bg-brand-500/5 px-4 py-3 text-left transition hover:bg-brand-500/10"
            >
              <span className="block text-xs font-bold text-brand-300">Demo account</span>
              <span className="block text-[11px] text-slate-400">demo@nepse.local · demo1234 — click to autofill</span>
            </button>

            <p className="mt-6 text-center text-sm text-slate-400">
              New here?{' '}
              <Link to="/register" className="font-semibold text-brand-400 hover:text-brand-300">
                Create an account
              </Link>
            </p>
          </div>
          <p className="mt-5 text-center text-[11px] leading-relaxed text-slate-500">
            Educational tool only. Market data can be delayed or simulated. Not financial advice.
          </p>
        </div>
      </div>
    </div>
  );
}
