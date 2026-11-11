import type { ReactNode } from 'react';

type Tone = 'slate' | 'green' | 'amber' | 'rose' | 'blue';

const TONES: Record<Tone, string> = {
  slate: 'bg-slate-100 text-slate-700 ring-slate-200',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  rose: 'bg-rose-50 text-rose-700 ring-rose-200',
  blue: 'bg-brand-50 text-brand-700 ring-brand-100',
};

export function Badge({ tone = 'slate', children }: { tone?: string; children: ReactNode }) {
  const className = TONES[(tone as Tone) in TONES ? (tone as Tone) : 'slate'];
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs
                      font-medium ring-1 ring-inset ${className}`}>
      {children}
    </span>
  );
}
