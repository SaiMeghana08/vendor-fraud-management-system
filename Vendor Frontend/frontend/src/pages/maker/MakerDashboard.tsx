import { Link } from 'react-router-dom';
import { bankChangeApi } from '../../api/endpoints';
import { useAsync } from '../../hooks/useAsync';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable } from '../../components/ui/DataTable';
import { CountdownBadge } from '../../components/ui/CountdownBadge';
import { CreateBankChangeForm } from './CreateBankChangeForm';
import type { BankChangeRequest } from '../../types';
import { bankChangeStatusTone, formatDateTime, humanise } from '../../utils/format';

export function MakerDashboard() {
  const requests = useAsync(() => bankChangeApi.mine(0, 25), []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-slate-900">Maker</h1>
        <p className="mt-1 text-sm text-slate-600">
          Raise bank-change requests and track them through dual approval and cooling-off.
        </p>
      </div>

      <CreateBankChangeForm onCreated={requests.reload} />

      <Card title="My requests" description="Approval stage and cooling-off status for each">
        <DataTable<BankChangeRequest>
          rows={requests.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={requests.loading}
          error={requests.error}
          emptyMessage="You have not raised any bank-change requests yet."
          columns={[
            {
              header: 'Reference',
              cell: (row) => (
                <Link to={`/requests/${row.id}`}
                      className="font-medium text-brand-700 hover:underline">
                  {row.requestReference}
                </Link>
              ),
            },
            { header: 'Vendor', cell: (row) => row.vendorName },
            {
              header: 'Status',
              cell: (row) => (
                <Badge tone={bankChangeStatusTone[row.status]}>{humanise(row.status)}</Badge>
              ),
            },
            {
              header: 'Approvals',
              cell: (row) => {
                const approved = row.approvalTasks.filter((t) => t.status === 'APPROVED').length;
                return <span className="text-slate-600">{approved} of 2</span>;
              },
            },
            { header: 'Cooling-off', cell: (row) => <CountdownBadge coolingOff={row.coolingOff} /> },
            { header: 'Raised', cell: (row) => formatDateTime(row.createdAt) },
          ]}
        />
      </Card>
    </div>
  );
}
