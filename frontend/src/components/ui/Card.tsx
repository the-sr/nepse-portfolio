import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

export default function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('card', className)}>{children}</div>;
}
