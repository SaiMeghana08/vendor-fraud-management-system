import { useEffect, useState } from 'react';
import { Clock } from 'lucide-react';
import type { CoolingOffView } from '../../types';
import { formatCountdown, formatDateTime } from '../../utils/format';

/**
 * Read-only cooling-off display.
 *
 * <p>The countdown ticks locally for readability, but the deadline it
 * counts towards is the backend's. There is no control here - and none
 * anywhere in the UI - that can shorten or skip the window.
 */
export function CountdownBadge({ coolingOff }: { coolingOff?: CoolingOffView }) {
  const [remaining, setRemaining] = useState(coolingOff?.remainingSeconds ?? 0);

  useEffect(() => {
    setRemaining(coolingOff?.remainingSeconds ?? 0);
    if (!coolingOff || coolingOff.elapsed) return;
    const timer = setInterval(() => setRemaining((value) => Math.max(0, value - 1)), 1000);
    return () => clearInterval(timer);
  }, [coolingOff]);

  if (!coolingOff) {
    return <span className="text-xs text-slate-400">Not started</span>;
  }

  return (
    <span className="inline-flex flex-col gap-0.5 text-xs">
      <span className="inline-flex items-center gap-1 font-medium text-slate-700">
        <Clock className="h-3.5 w-3.5" aria-hidden />
        {remaining > 0 ? formatCountdown(remaining) : 'Window closed — awaiting revalidation'}
      </span>
      <span className="text-slate-500">Ends {formatDateTime(coolingOff.endAt)}</span>
      {coolingOff.exceptionApplied && (
        <span className="text-amber-700">Cooling-off exception applied</span>
      )}
    </span>
  );
}
