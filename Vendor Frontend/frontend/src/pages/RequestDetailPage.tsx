import { useParams, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { bankChangeApi } from '../api/endpoints';
import { useAsync } from '../hooks/useAsync';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { DataTable } from '../components/ui/DataTable';
import { CountdownBadge } from '../components/ui/CountdownBadge';
import type { ApprovalTask } from '../types';
import { bankChangeStatusTone, formatDateTime, humanise } from '../utils/format';

/** Full detail of one request, including its approval trail. */
export function RequestDetailPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const id = Number(requestId);
  const { data, loading, error } = useAsync(() => bankChangeApi.get(id), [id]);

  if (loading) return <p className="text-sm text-slate-500">Loading request…</p>;
  if (error) {
    return (
      <div className="rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
        {error}
      </div>
    );
  }
  if (!data) return null;

  return (
    <div className="space-y-5">
      <Link to=".." relative="path"
            className="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden /> Back
      </Link>

      <Card title={data.requestReference}
            description={`${data.vendorName} · ${data.vendorCode}`}
            actions={<Badge tone={bankChangeStatusTone[data.status]}>
              {humanise(data.status)}
            </Badge>}>
        <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
          <Detail label="Raised by" value={data.makerName} />
          <Detail label="Raised at" value={formatDateTime(data.createdAt)} />
          <Detail label="Current bank reference"
                  value={data.currentMaskedAccountReference ?? 'No banking record on file'} />
          <Detail label="Proposed bank" value={data.proposedBankName} />
          <Detail label="Proposed account holder" value={data.proposedAccountHolderName} />
          <Detail label="Proposed reference" value={data.proposedMaskedAccountReference} />
          <Detail label="Routing code" value={data.proposedRoutingCode} />
          <Detail label="Evidence reference" value={data.evidenceReference} />
          <div className="sm:col-span-2">
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">
              Reason for change
            </dt>
            <dd className="mt-1 text-sm text-slate-800">{data.reason}</dd>
          </div>
          {data.terminalReason && (
            <div className="sm:col-span-2">
              <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">
                Outcome note
              </dt>
              <dd className="mt-1 text-sm text-slate-800">{data.terminalReason}</dd>
            </div>
          )}
          <div className="sm:col-span-2">
            <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">
              Cooling-off
            </dt>
            <dd className="mt-1"><CountdownBadge coolingOff={data.coolingOff} /></dd>
          </div>
        </dl>
      </Card>

      <Card title="Approval trail"
            description="Assignments and decisions, including any reassignment">
        <DataTable<ApprovalTask>
          rows={data.approvalTasks}
          keyOf={(task) => task.id}
          emptyMessage="No approval tasks yet"
          columns={[
            { header: 'Stage', cell: (t) => humanise(t.stage) },
            { header: 'Approver', cell: (t) => t.assigneeName },
            {
              header: 'Status',
              cell: (t) => (
                <Badge tone={t.status === 'APPROVED' ? 'green'
                  : t.status === 'REJECTED' ? 'rose'
                  : t.status === 'REASSIGNED' ? 'slate' : 'amber'}>
                  {humanise(t.status)}
                </Badge>
              ),
            },
            { header: 'Assigned', cell: (t) => formatDateTime(t.assignedAt) },
            { header: 'Decided', cell: (t) => formatDateTime(t.decidedAt) },
            { header: 'Comment', cell: (t) => t.decisionComment ?? '—' },
          ]}
        />
      </Card>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-1 text-sm text-slate-800">{value}</dd>
    </div>
  );
}
