import { useState } from 'react';
import { approvalApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { useAsync } from '../hooks/useAsync';
import { Button } from './ui/Button';
import { Dialog } from './ui/Dialog';
import { StatusMessage } from './ui/StatusMessage';
import type { ApprovalTask, ApproverOption } from '../types';

/**
 * Reassignment dialog, shared by the Accounts Payable and Administrator
 * dashboards.
 *
 * <p>The candidate list comes from the backend, which has already
 * excluded the Maker, the current assignee and anyone who has held a
 * stage of this request. The UI never computes eligibility itself.
 */
export function ReassignDialog({ task, onClose, onReassigned }: {
  task: ApprovalTask | null;
  onClose: () => void;
  onReassigned: (message: string) => void;
}) {
  const [replacementId, setReplacementId] = useState(0);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const candidates = useAsync<ApproverOption[]>(
    () => (task ? approvalApi.eligibleReplacements(task.id) : Promise.resolve([])),
    [task?.id],
  );

  const submit = async () => {
    if (!task) return;
    if (!replacementId) {
      setError('Select a replacement approver.');
      return;
    }
    if (reason.trim().length < 10) {
      setError('A reassignment reason of at least 10 characters is required.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await approvalApi.reassign(task.id, replacementId, reason.trim());
      setReplacementId(0);
      setReason('');
      onReassigned(`Task for ${task.requestReference} reassigned.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The task could not be reassigned.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={task !== null} title="Reassign approval task"
            description="Assignment history is preserved and the change is audited"
            onClose={onClose}>
      {error && <div className="mb-3"><StatusMessage kind="error">{error}</StatusMessage></div>}
      {task && (
        <p className="mb-3 text-sm text-slate-600">
          {task.requestReference} · {task.stage === 'FIRST' ? 'First' : 'Second'} stage,
          currently with {task.assigneeName}
        </p>
      )}

      <div className="space-y-3">
        <div>
          <label className="field-label" htmlFor="replacement">Replacement approver</label>
          <select id="replacement" className="field-input" value={replacementId}
                  onChange={(event) => setReplacementId(Number(event.target.value))}>
            <option value={0}>Select an eligible approver…</option>
            {(candidates.data ?? []).map((option) => (
              <option key={option.id} value={option.id}>{option.fullName}</option>
            ))}
          </select>
          {candidates.loading && (
            <p className="mt-1 text-xs text-slate-500">Loading eligible approvers…</p>
          )}
          {!candidates.loading && (candidates.data ?? []).length === 0 && (
            <p className="mt-1 text-xs text-amber-700">
              No independent approver is currently eligible for this request.
            </p>
          )}
        </div>
        <div>
          <label className="field-label" htmlFor="reassign-reason">Reason</label>
          <textarea id="reassign-reason" rows={3} className="field-input" value={reason}
                    onChange={(event) => setReason(event.target.value)} />
        </div>
      </div>

      <div className="mt-4 flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={busy} onClick={submit}>Reassign</Button>
      </div>
    </Dialog>
  );
}
