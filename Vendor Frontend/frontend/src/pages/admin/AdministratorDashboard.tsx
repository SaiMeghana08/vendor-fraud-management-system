import { useState } from 'react';
import { Link } from 'react-router-dom';
import { approvalApi, auditApi, bankChangeApi, overrideApi } from '../../api/endpoints';
import { ApiError } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable } from '../../components/ui/DataTable';
import { Dialog } from '../../components/ui/Dialog';
import { StatusMessage } from '../../components/ui/StatusMessage';
import { ReassignDialog } from '../../components/ReassignDialog';
import type {
  ApprovalTask, AuditLog, BankChangeRequest, EmergencyOverride,
} from '../../types';
import { formatDateTime, humanise } from '../../utils/format';

export function AdministratorDashboard() {
  const pendingOverrides = useAsync(() => overrideApi.list('PENDING', 0, 25), []);
  const decidedOverrides = useAsync(() => overrideApi.list(undefined, 0, 25), []);
  const blocked = useAsync(() => bankChangeApi.blocked(), []);
  const pendingTasks = useAsync(() => approvalApi.allPending(0, 25), []);
  const [auditFilter, setAuditFilter] = useState('');
  const audit = useAsync(
    () => auditApi.list({ action: auditFilter || undefined, page: 0, size: 50 }),
    [auditFilter],
  );

  const [banner, setBanner] = useState<string | null>(null);
  const [deciding, setDeciding] = useState<EmergencyOverride | null>(null);
  const [reassigning, setReassigning] = useState<ApprovalTask | null>(null);

  const refresh = () => {
    pendingOverrides.reload();
    decidedOverrides.reload();
    blocked.reload();
    pendingTasks.reload();
    audit.reload();
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-slate-900">Administration</h1>
        <p className="mt-1 text-sm text-slate-600">
          Emergency override decisions, exception monitoring and audit oversight.
          User management is not performed here.
        </p>
      </div>

      {banner && <StatusMessage kind="success">{banner}</StatusMessage>}

      <Card title="Emergency overrides awaiting your decision"
            description="You cannot decide an override you requested yourself">
        <DataTable<EmergencyOverride>
          rows={pendingOverrides.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={pendingOverrides.loading}
          error={pendingOverrides.error}
          emptyMessage="No overrides are awaiting a decision."
          columns={[
            { header: 'Reference', cell: (row) => row.overrideReference },
            { header: 'Request', cell: (row) => row.bankChangeRequestReference ?? '—' },
            { header: 'Requested by', cell: (row) => row.requestedByName ?? '—' },
            { header: 'Justification', cell: (row) => row.justification },
            { header: '', align: 'right',
              cell: (row) => (
                <Button variant="secondary" onClick={() => setDeciding(row)}>Decide</Button>
              ) },
          ]}
        />
      </Card>

      <Card title="Exception monitoring"
            description="Requests whose post-cooling-off revalidation failed">
        <DataTable<BankChangeRequest>
          rows={blocked.data ?? []}
          keyOf={(row) => row.id}
          loading={blocked.loading}
          error={blocked.error}
          emptyMessage="No blocked activations."
          columns={[
            { header: 'Reference',
              cell: (row) => (
                <Link to={`/requests/${row.id}`}
                      className="font-medium text-brand-700 hover:underline">
                  {row.requestReference}
                </Link>
              ) },
            { header: 'Vendor', cell: (row) => row.vendorName },
            { header: 'Blocked because', cell: (row) => row.terminalReason ?? '—' },
            { header: 'Window ended', cell: (row) => formatDateTime(row.coolingOff?.endAt) },
          ]}
        />
      </Card>

      <Card title="Pending approval tasks" description="Reassign when an approver is unavailable">
        <DataTable<ApprovalTask>
          rows={pendingTasks.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={pendingTasks.loading}
          error={pendingTasks.error}
          emptyMessage="No pending approval tasks."
          columns={[
            { header: 'Request', cell: (row) => row.requestReference },
            { header: 'Stage', cell: (row) => humanise(row.stage) },
            { header: 'Assignee', cell: (row) => row.assigneeName },
            { header: '', align: 'right',
              cell: (row) => (
                <Button variant="secondary" onClick={() => setReassigning(row)}>Reassign</Button>
              ) },
          ]}
        />
      </Card>

      <Card title="Override decision history">
        <DataTable<EmergencyOverride>
          rows={(decidedOverrides.data?.content ?? []).filter((o) => o.status !== 'PENDING')}
          keyOf={(row) => row.id}
          loading={decidedOverrides.loading}
          error={decidedOverrides.error}
          emptyMessage="No decided overrides."
          columns={[
            { header: 'Reference', cell: (row) => row.overrideReference },
            { header: 'Status',
              cell: (row) => (
                <Badge tone={row.status === 'APPROVED' ? 'green' : 'rose'}>
                  {humanise(row.status)}
                </Badge>
              ) },
            { header: 'Decided by', cell: (row) => row.decidedByName ?? '—' },
            { header: 'Decided at', cell: (row) => formatDateTime(row.decidedAt) },
            { header: 'Comment', cell: (row) => row.decisionComment ?? '—' },
          ]}
        />
      </Card>

      <Card title="Audit log" description="Append-only; sensitive references are masked"
            actions={
              <input aria-label="Filter by action" placeholder="Filter by action…"
                     className="field-input w-56" value={auditFilter}
                     onChange={(event) => setAuditFilter(event.target.value.toUpperCase())} />
            }>
        <DataTable<AuditLog>
          rows={audit.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={audit.loading}
          error={audit.error}
          emptyMessage="No audit entries match this filter."
          columns={[
            { header: 'When', cell: (row) => formatDateTime(row.createdAt) },
            { header: 'Actor', cell: (row) => row.actorUsername },
            { header: 'Action', cell: (row) => humanise(row.action) },
            { header: 'Entity', cell: (row) => `${humanise(row.entityType)} #${row.entityId ?? '—'}` },
            { header: 'State', cell: (row) => (row.workflowState ? humanise(row.workflowState) : '—') },
            { header: 'Detail', cell: (row) => row.reason ?? '—' },
          ]}
        />
      </Card>

      <DecideOverrideDialog override={deciding} onClose={() => setDeciding(null)}
                            onDecided={(message) => {
                              setBanner(message);
                              setDeciding(null);
                              refresh();
                            }} />

      <ReassignDialog task={reassigning} onClose={() => setReassigning(null)}
                      onReassigned={(message) => {
                        setBanner(message);
                        setReassigning(null);
                        refresh();
                      }} />
    </div>
  );
}

function DecideOverrideDialog({ override, onClose, onDecided }: {
  override: EmergencyOverride | null;
  onClose: () => void;
  onDecided: (message: string) => void;
}) {
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'approve' | 'reject' | null>(null);

  const decide = async (approved: boolean) => {
    if (!override) return;
    setBusy(approved ? 'approve' : 'reject');
    setError(null);
    try {
      await overrideApi.decide(override.id, approved, comment.trim());
      setComment('');
      onDecided(`Override ${override.overrideReference} ${approved ? 'approved' : 'rejected'}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The decision could not be recorded.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <Dialog open={override !== null} title="Decide emergency override"
            description="Approval authorises a cooling-off exception and nothing else"
            onClose={onClose}>
      {error && <div className="mb-3"><StatusMessage kind="error">{error}</StatusMessage></div>}
      <StatusMessage kind="info">
        Approving brings the cooling-off deadline forward. Dual approval still stands,
        revalidation still runs, and payment holds are not released.
      </StatusMessage>
      {override && (
        <dl className="mt-3 space-y-2 text-sm">
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Justification</dt>
            <dd className="mt-0.5 text-slate-800">{override.justification}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Evidence</dt>
            <dd className="mt-0.5 text-slate-800">{override.evidenceReference}</dd>
          </div>
        </dl>
      )}
      <div className="mt-3">
        <label className="field-label" htmlFor="override-comment">Decision comment</label>
        <textarea id="override-comment" rows={3} className="field-input" value={comment}
                  onChange={(event) => setComment(event.target.value)} />
      </div>
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
