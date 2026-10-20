import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  Bug,
  CheckCircle2,
  Clock3,
  Mail,
  MessageSquare,
  Send,
  ShieldAlert,
  Sparkles,
} from 'lucide-react';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import { PageHeader, SectionTitle } from '@/components/ui/PageHeader';

const schema = z.object({
  name: z.string().min(2, 'Tell us your name'),
  email: z.string().email('Enter a valid email'),
  message: z.string().min(10, 'Please add at least 10 characters').max(4000),
});

type FormValues = z.infer<typeof schema>;

export default function Contact() {
  const [sent, setSent] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', email: '', message: '' },
  });

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => (await api.post('/contact', values)).data,
    onSuccess: () => {
      setSent(true);
      reset();
      toast.success('Message received');
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not send the message')),
  });

  return (
    <div>
      <PageHeader
        title="Contact & support"
        subtitle="Found a bad price, a parsing bug or have an idea for the averaging engine? Send a note."
      />

      <div className="grid items-start gap-4 xl:grid-cols-5">
        <Card className="p-5 xl:col-span-3">
          <SectionTitle
            title="Send a message"
            subtitle="Stored securely and emailed to the administrator when mail is configured."
            action={<MessageSquare className="h-4 w-4 text-brand-500" />}
          />

          {sent ? (
            <div className="flex flex-col items-center justify-center py-10 text-center">
              <span className="rounded-2xl bg-brand-500/10 p-4 text-brand-500">
                <CheckCircle2 className="h-8 w-8" />
              </span>
              <h3 className="mt-4 text-lg font-bold text-slate-900 dark:text-white">Message received</h3>
              <p className="mt-1 max-w-sm text-sm text-slate-500 dark:text-slate-400">
                Thanks for helping improve NEPSE Tracker. If you reported a data issue, include the
                timestamp and symbol so it can be traced in the scrape log.
              </p>
              <Button variant="ghost" className="mt-5" onClick={() => setSent(false)}>
                Send another message
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="label" htmlFor="contact-name">Name</label>
                  <input id="contact-name" className="input" placeholder="Your name" {...register('name')} />
                  {errors.name && <p className="mt-1 text-xs text-rose-500">{errors.name.message}</p>}
                </div>
                <div>
                  <label className="label" htmlFor="contact-email">Email</label>
                  <input id="contact-email" type="email" className="input" placeholder="you@example.com" {...register('email')} />
                  {errors.email && <p className="mt-1 text-xs text-rose-500">{errors.email.message}</p>}
                </div>
              </div>
              <div>
                <label className="label" htmlFor="contact-message">Message</label>
                <textarea
                  id="contact-message"
                  rows={6}
                  className="input resize-y"
                  placeholder="What happened? Include the symbol, page and approximate time if it is a data issue."
                  {...register('message')}
                />
                {errors.message && <p className="mt-1 text-xs text-rose-500">{errors.message.message}</p>}
              </div>
              <div className="flex items-center justify-between gap-3">
                <p className="text-[11px] text-slate-400">No spam. Messages are only used to improve the app.</p>
                <Button type="submit" loading={mutation.isPending}>
                  <Send className="h-4 w-4" /> Send message
                </Button>
              </div>
            </form>
          )}
        </Card>

        <div className="space-y-4 xl:col-span-2">
          <Card className="p-5">
            <SectionTitle title="What to include" action={<Bug className="h-4 w-4 text-amber-500" />} />
            <ul className="space-y-3 text-xs text-slate-600 dark:text-slate-400">
              <li className="flex gap-2">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-brand-500" />
                The symbol and the price you expected versus what the app shows.
              </li>
              <li className="flex gap-2">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-brand-500" />
                Approximate Nepal time, so the scrape run can be inspected.
              </li>
              <li className="flex gap-2">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-brand-500" />
                A screenshot if the table layout or a chart looks wrong.
              </li>
              <li className="flex gap-2">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-brand-500" />
                Feature ideas: new indicators, alert types or report formats.
              </li>
            </ul>
          </Card>

          <Card className="p-5">
            <SectionTitle title="Response times" action={<Clock3 className="h-4 w-4 text-violet-500" />} />
            <div className="space-y-2 text-xs">
              <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
                <span className="text-slate-500 dark:text-slate-400">Data corrections</span>
                <span className="font-semibold text-slate-800 dark:text-slate-100">1–2 market days</span>
              </div>
              <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
                <span className="text-slate-500 dark:text-slate-400">Feature requests</span>
                <span className="font-semibold text-slate-800 dark:text-slate-100">Best effort</span>
              </div>
              <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-white/5">
                <span className="text-slate-500 dark:text-slate-400">Security issues</span>
                <span className="font-semibold text-slate-800 dark:text-slate-100">Priority</span>
              </div>
            </div>
            <div className="mt-4 flex items-center gap-2 rounded-xl bg-brand-500/5 p-3 text-[11px] text-brand-700 dark:text-brand-300">
              <Mail className="h-3.5 w-3.5" />
              Email delivery is optional; messages are always stored in the database.
            </div>
          </Card>

          <Card className="p-5">
            <div className="flex items-start gap-3">
              <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-500" />
              <div className="text-[11px] leading-relaxed text-slate-500 dark:text-slate-400">
                <p className="font-semibold text-slate-700 dark:text-slate-200">Not a brokerage</p>
                <p className="mt-1">
                  We cannot place orders, recover broker credentials or give personalised investment
                  advice. For account issues, contact your broker or CDSC directly.
                </p>
              </div>
            </div>
          </Card>

          <div className="flex items-center gap-2 text-[11px] text-slate-400">
            <Sparkles className="h-3.5 w-3.5 text-brand-500" />
            Built with Spring Boot, React and a scikit-learn recommender.
          </div>
        </div>
      </div>
    </div>
  );
}
