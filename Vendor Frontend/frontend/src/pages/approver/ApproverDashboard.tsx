import { useState } from 'react';
import { Link } from 'react-router-dom';
import { approvalApi, bankChangeApi } from '../../api/endpoints';
import { ApiError } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { StatusMessage } from '../../components/ui/StatusMessage';
import type { ApprovalTask, BankChangeRequest } from '../../types';
import { formatDateTime, humanise } from '../../utils/format';

export function ApproverDashboard() {
  const tasks = useAsync(() => approvalApi.myTasks(0, 25), []);
  const history = useAsync(() => approvalApi.myHistory(0, 25), []);
  const [reviewing, setReviewing] = useState<ApprovalTask | null>(null);
  const [banner, setBanner] = useState<string | null>(null);

  const onDecided = (message: string) => {
    setBanner(message);
    setReviewing(null);
    tasks.reload();
    history.reload();
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-slate-900">Approvals</h1>
        <p className="mt-1 text-sm text-slate-600">
          Tasks assigned to you. Each stage is decided independently; you will never be
          assigned both stages of the same request.
        </p>
      </div>

      {banner && <StatusMessage kind="success">{banner}</StatusMessage>}

      <Card title="My assigned tasks" description="Awaiting your decision">
        <DataTable<ApprovalTask>
          rows={tasks.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={tasks.loading}
          error={tasks.error}
          emptyMessage="You have no approval tasks waiting."
          columns={[
            {
              header: 'Request',
              cell: (row) => (
                <Link to={`/requests/${row.requestId}`}
                      className="font-medium text-brand-700 hover:underline">
                  {row.requestReference}
                </Link>
              ),
            },
            { header: 'Stage', cell: (row) => humanise(row.stage) },
            { header: 'Assigned', cell: (row) => formatDateTime(row.assignedAt) },
            {
              header: '',
              align: 'right',
              cell: (row) => (
                <Button variant="secondary" onClick={() => setReviewing(row)}>Review</Button>
              ),
            },
          ]}
        />
      </Card>

      <Card title="My decision history" description="Completed decisions are never editable">
        <DataTable<ApprovalTask>
          rows={history.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={history.loading}
          error={history.error}
          emptyMessage="You have not decided any tasks yet."
          columns={[
            { header: 'Request', cell: (row) => row.requestReference },
            { header: 'Stage', cell: (row) => humanise(row.stage) },
            {
              header: 'Decision',
              cell: (row) => (
                <Badge tone={row.status === 'APPROVED' ? 'green'
                  : row.status === 'REJECTED' ? 'rose' : 'slate'}>
                  {humanise(row.status)}
                </Badge>
              ),
            },
            { header: 'Decided', cell: (row) => formatDateTime(row.decidedAt) },
            { header: 'Comment', cell: (row) => row.decisionComment ?? '—' },
          ]}
        />
      </Card>

      <ReviewDialog task={reviewing} onClose={() => setReviewing(null)} onDecided={onDecided} />
    </div>
  );
}

function ReviewDialog({ task, onClose, onDecided }: {
  task: ApprovalTask | null;
  onClose: () => void;
  onDecided: (message: string) => void;
}) {
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'approve' | 'reject' | null>(null);

  const open = task !== null;

  // Load the full request so the approver reviews the detail, not just
  // a row in a list.
  const detail = useAsync<BankChangeRequest | null>(
    () => (task ? bankChangeApi.get(task.requestId) : Promise.resolve(null)),
    [task?.requestId],
  );
  const request = detail.data;

  const decide = async (approved: boolean) => {
    if (!task) return;
    setError(null);
    if (!approved && comment.trim().length < 10) {
      setError('A rejection needs a meaningful comment of at least 10 characters.');
      return;
    }
    setBusy(approved ? 'approve' : 'reject');
    try {
      await approvalApi.decide(task.id, approved, comment.trim());
      setComment('');
      onDecided(approved
        ? `Approval recorded for ${task.requestReference}.`
        : `Request ${task.requestReference} rejected.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The decision could not be recorded.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <Dialog open={open} title={task ? `Review ${task.requestReference}` : ''}
            description="Approve or reject this stage" onClose={onClose}>
      {error && <div className="mb-3"><StatusMessage kind="error">{error}</StatusMessage></div>}

      {request && (
        <dl className="mb-4 grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          <Row label="Vendor" value={`${request.vendorName} (${request.vendorCode})`} />
          <Row label="Raised by" value={request.makerName} />
          <Row label="Current reference"
               value={request.currentMaskedAccountReference ?? 'No banking record'} />
          <Row label="Proposed reference" value={request.proposedMaskedAccountReference} />
          <Row label="Proposed bank" value={request.proposedBankName} />
          <Row label="Evidence" value={request.evidenceReference} />
          <div className="sm:col-span-2">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Reason</dt>
            <dd className="mt-0.5 text-slate-800">{request.reason}</dd>
          </div>
        </dl>
      )}

      <label className="field-label" htmlFor="decision-comment">
        Comment <span className="font-normal text-slate-500">(required to reject)</span>
      </label>
      <textarea id="decision-comment" rows={3} className="field-input" value={comment}
                onChange={(event) => setComment(event.target.value)} />

      <div className="mt-4 flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant="danger" loading={busy === 'reject'} onClick={() => decide(false)}>
          Reject
        </Button>
        <Button loading={busy === 'approve'} onClick={() => decide(true)}>Approve</Button>
      </div>
    </Dialog>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-slate-800">{value}</dd>
    </div>
  );
}
