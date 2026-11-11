import { AlertTriangle, CheckCircle2, Info } from 'lucide-react';

type Kind = 'success' | 'error' | 'info';

const STYLES: Record<Kind, { className: string; Icon: typeof Info }> = {
  success: { className: 'bg-emerald-50 text-emerald-800', Icon: CheckCircle2 },
  error: { className: 'bg-rose-50 text-rose-800', Icon: AlertTriangle },
  info: { className: 'bg-brand-50 text-brand-900', Icon: Info },
};

export function StatusMessage({ kind, children }: { kind: Kind; children: React.ReactNode }) {
  const { className, Icon } = STYLES[kind];
  return (
    <div role={kind === 'error' ? 'alert' : 'status'}
         className={`flex items-start gap-2 rounded-md px-3 py-2 text-sm ${className}`}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <span>{children}</span>
    </div>
  );
}
