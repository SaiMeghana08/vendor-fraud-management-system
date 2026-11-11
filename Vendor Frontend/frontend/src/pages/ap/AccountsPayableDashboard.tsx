import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  approvalApi, bankChangeApi, holdApi, overrideApi, paymentApi, vendorApi,
} from '../../api/endpoints';
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
  ApprovalTask, BankChangeRequest, EmergencyOverride, Payment, PaymentHold,
} from '../../types';
import {
  formatDate, formatDateTime, formatMoney, humanise, paymentStatusTone,
} from '../../utils/format';

export function AccountsPayableDashboard() {
  const payments = useAsync(() => paymentApi.list(undefined, 0, 25), []);
  const holds = useAsync(() => holdApi.listActive(0, 25), []);
  const pendingTasks = useAsync(() => approvalApi.allPending(0, 25), []);
  const overrides = useAsync(() => overrideApi.list(undefined, 0, 25), []);
  const coolingOff = useAsync(() => bankChangeApi.list('COOLING_OFF', 0, 25), []);

  const [banner, setBanner] = useState<string | null>(null);
  const [releasing, setReleasing] = useState<PaymentHold | null>(null);
  const [overrideFor, setOverrideFor] = useState<BankChangeRequest | null>(null);
  const [reassigning, setReassigning] = useState<ApprovalTask | null>(null);

  const refreshAll = () => {
    payments.reload();
    holds.reload();
    overrides.reload();
    coolingOff.reload();
    pendingTasks.reload();
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-slate-900">Accounts Payable</h1>
        <p className="mt-1 text-sm text-slate-600">
          Payments, holds and eligibility. Releasing a hold is a separate, justified
          decision — nothing in the approval workflow releases one for you.
        </p>
      </div>

      {banner && <StatusMessage kind="success">{banner}</StatusMessage>}

      <CreatePaymentForm onCreated={(reference) => {
        setBanner(`Payment ${reference} created.`);
        refreshAll();
      }} />

      <Card title="Payments" description="Eligibility is recomputed live on every view">
        <DataTable<Payment>
          rows={payments.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={payments.loading}
          error={payments.error}
          emptyMessage="No payments yet."
          columns={[
            { header: 'Reference', cell: (row) => row.paymentReference },
            { header: 'Vendor', cell: (row) => row.vendorName },
            { header: 'Amount', align: 'right',
              cell: (row) => formatMoney(row.amount, row.currency) },
            { header: 'Due', cell: (row) => formatDate(row.dueDate) },
            { header: 'Status',
              cell: (row) => (
                <Badge tone={paymentStatusTone[row.status]}>{humanise(row.status)}</Badge>
              ) },
            { header: 'Eligibility',
              cell: (row) => (row.eligibleForProcessing
                ? <Badge tone="green">Eligible</Badge>
                : <span className="text-xs text-slate-600">{row.ineligibilityReason}</span>) },
            { header: '', align: 'right',
              cell: (row) => (
                <Button variant="secondary" disabled={!row.eligibleForProcessing}
                        onClick={async () => {
                          try {
                            await paymentApi.process(row.id);
                            setBanner(`Payment ${row.paymentReference} processed.`);
                            refreshAll();
                          } catch (err) {
                            setBanner(null);
                            alert(err instanceof ApiError
                              ? err.message : 'The payment could not be processed.');
                          }
                        }}>
                  Process
                </Button>
              ) },
          ]}
        />
      </Card>

      <Card title="Active payment holds"
            description="A release requires a justification and is audited">
        <DataTable<PaymentHold>
          rows={holds.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={holds.loading}
          error={holds.error}
          emptyMessage="No active holds."
          columns={[
            { header: 'Hold', cell: (row) => `#${row.id}` },
            { header: 'Payment', cell: (row) => `#${row.paymentId}` },
            { header: 'Reason', cell: (row) => row.reason },
            { header: 'Placed', cell: (row) => formatDateTime(row.createdAt) },
            { header: '', align: 'right',
              cell: (row) => (
                <Button variant="secondary" onClick={() => setReleasing(row)}>Release</Button>
              ) },
          ]}
        />
      </Card>

      <Card title="Requests in cooling-off"
            description="An emergency override can request a documented cooling-off exception only">
        <DataTable<BankChangeRequest>
          rows={coolingOff.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={coolingOff.loading}
          error={coolingOff.error}
          emptyMessage="No requests are in cooling-off."
          columns={[
            { header: 'Reference',
              cell: (row) => (
                <Link to={`/requests/${row.id}`}
                      className="font-medium text-brand-700 hover:underline">
                  {row.requestReference}
                </Link>
              ) },
            { header: 'Vendor', cell: (row) => row.vendorName },
            { header: 'Window ends',
              cell: (row) => formatDateTime(row.coolingOff?.endAt) },
            { header: '', align: 'right',
              cell: (row) => (
                <Button variant="secondary" onClick={() => setOverrideFor(row)}>
                  Request override
                </Button>
              ) },
          ]}
        />
      </Card>

      <Card title="Emergency override requests" description="Decided only by an Administrator">
        <DataTable<EmergencyOverride>
          rows={overrides.data?.content ?? []}
          keyOf={(row) => row.id}
          loading={overrides.loading}
          error={overrides.error}
          emptyMessage="No override requests."
          columns={[
            { header: 'Reference', cell: (row) => row.overrideReference },
            { header: 'Request', cell: (row) => row.bankChangeRequestReference ?? '—' },
            { header: 'Requested by', cell: (row) => row.requestedByName ?? '—' },
            { header: 'Status',
              cell: (row) => (
                <Badge tone={row.status === 'APPROVED' ? 'green'
                  : row.status === 'REJECTED' ? 'rose' : 'amber'}>
                  {humanise(row.status)}
                </Badge>
              ) },
            { header: 'Raised', cell: (row) => formatDateTime(row.createdAt) },
          ]}
        />
      </Card>

      <Card title="Pending approval tasks" description="Reassign a task when an approver is unavailable">
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
            { header: 'Assigned', cell: (row) => formatDateTime(row.assignedAt) },
            { header: '', align: 'right',
              cell: (row) => (
                <Button variant="secondary" onClick={() => setReassigning(row)}>Reassign</Button>
              ) },
          ]}
        />
      </Card>

      <ReleaseHoldDialog hold={releasing} onClose={() => setReleasing(null)}
                         onReleased={(message) => {
                           setBanner(message);
                           setReleasing(null);
                           refreshAll();
                         }} />

      <RequestOverrideDialog request={overrideFor} onClose={() => setOverrideFor(null)}
                             onRequested={(message) => {
                               setBanner(message);
                               setOverrideFor(null);
                               refreshAll();
                             }} />

      <ReassignDialog task={reassigning} onClose={() => setReassigning(null)}
                      onReassigned={(message) => {
                        setBanner(message);
                        setReassigning(null);
                        refreshAll();
                      }} />
    </div>
  );
}

// ------------------------------------------------------------------ //

const paymentSchema = z.object({
  vendorId: z.coerce.number().int().positive('Select a vendor'),
  amount: z.coerce.number().positive('Enter an amount greater than zero'),
  currency: z.string().regex(/^[A-Z]{3}$/, 'Three-letter ISO code, e.g. USD'),
  dueDate: z.string().min(1, 'Select a due date'),
  description: z.string().trim().min(3, 'Enter a business description').max(500),
});

type PaymentFormValues = z.infer<typeof paymentSchema>;

/**
 * Payment creation. Five business fields and nothing else: no payment
 * id, no reference input, no internal bank-account id, no manual
 * payment or hold status.
 */
function CreatePaymentForm({ onCreated }: { onCreated: (reference: string) => void }) {
  const vendors = useAsync(() => vendorApi.searchActive(), []);
  const [error, setError] = useState<string | null>(null);

  const { register, handleSubmit, reset, formState } = useForm<PaymentFormValues>({
    resolver: zodResolver(paymentSchema),
    defaultValues: { vendorId: 0, amount: 0, currency: 'USD', dueDate: '', description: '' },
  });

  const onSubmit = async (values: PaymentFormValues) => {
    setError(null);
    try {
      const created = await paymentApi.create(values);
      reset({ vendorId: 0, amount: 0, currency: 'USD', dueDate: '', description: '' });
      onCreated(created.paymentReference);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The payment could not be created.');
    }
  };

  return (
    <Card title="Create payment"
          description="The backend generates the payment reference and the initial status.">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {error && <StatusMessage kind="error">{error}</StatusMessage>}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <label className="field-label" htmlFor="payment-vendor">Vendor</label>
            <select id="payment-vendor" className="field-input" {...register('vendorId')}>
              <option value={0}>Select an active vendor…</option>
              {(vendors.data ?? []).map((vendor) => (
                <option key={vendor.id} value={vendor.id}>
                  {vendor.name} ({vendor.vendorCode})
                </option>
              ))}
            </select>
            {formState.errors.vendorId && (
              <p className="field-error">{formState.errors.vendorId.message}</p>
            )}
          </div>
          <div>
            <label className="field-label" htmlFor="payment-amount">Amount</label>
            <input id="payment-amount" type="number" step="0.01" className="field-input"
                   {...register('amount')} />
            {formState.errors.amount && (
              <p className="field-error">{formState.errors.amount.message}</p>
            )}
          </div>
          <div>
            <label className="field-label" htmlFor="payment-currency">Currency</label>
            <input id="payment-currency" className="field-input" {...register('currency')} />
            {formState.errors.currency && (
              <p className="field-error">{formState.errors.currency.message}</p>
            )}
          </div>
          <div>
            <label className="field-label" htmlFor="payment-due">Due date</label>
            <input id="payment-due" type="date" className="field-input" {...register('dueDate')} />
            {formState.errors.dueDate && (
              <p className="field-error">{formState.errors.dueDate.message}</p>
            )}
          </div>
          <div className="sm:col-span-2">
            <label className="field-label" htmlFor="payment-description">
              Business description
            </label>
            <input id="payment-description" className="field-input"
                   {...register('description')} />
            {formState.errors.description && (
              <p className="field-error">{formState.errors.description.message}</p>
            )}
          </div>
        </div>
        <div className="flex justify-end border-t border-slate-100 pt-4">
          <Button type="submit" loading={formState.isSubmitting}>Create payment</Button>
        </div>
      </form>
    </Card>
  );
}

function ReleaseHoldDialog({ hold, onClose, onReleased }: {
  hold: PaymentHold | null;
  onClose: () => void;
  onReleased: (message: string) => void;
}) {
  const [justification, setJustification] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!hold) return;
    if (justification.trim().length < 10) {
      setError('A justification of at least 10 characters is required.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await holdApi.release(hold.id, justification.trim());
      setJustification('');
      onReleased(`Hold #${hold.id} released.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The hold could not be released.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={hold !== null} title="Release payment hold"
            description="Conditions are rechecked at release time and the action is audited"
            onClose={onClose}>
      {error && <div className="mb-3"><StatusMessage kind="error">{error}</StatusMessage></div>}
      {hold && (
        <p className="mb-3 text-sm text-slate-600">
          Hold #{hold.id} on payment #{hold.paymentId}: {hold.reason}
        </p>
      )}
      <label className="field-label" htmlFor="release-justification">Justification</label>
      <textarea id="release-justification" rows={3} className="field-input"
                value={justification}
                onChange={(event) => setJustification(event.target.value)} />
      <div className="mt-4 flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={busy} onClick={submit}>Release hold</Button>
      </div>
    </Dialog>
  );
}

function RequestOverrideDialog({ request, onClose, onRequested }: {
  request: BankChangeRequest | null;
  onClose: () => void;
  onRequested: (message: string) => void;
}) {
  const [justification, setJustification] = useState('');
  const [evidence, setEvidence] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!request) return;
    if (justification.trim().length < 20) {
      setError('An emergency override needs a substantive justification '
        + '(at least 20 characters).');
      return;
    }
    if (!evidence.trim()) {
      setError('An evidence reference is required.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const created = await overrideApi.request(request.id, justification.trim(), evidence.trim());
      setJustification('');
      setEvidence('');
      onRequested(`Override ${created.overrideReference} submitted for an Administrator decision.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The override could not be requested.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={request !== null} title="Request emergency override"
            description="Requests a documented cooling-off exception only"
            onClose={onClose}>
      {error && <div className="mb-3"><StatusMessage kind="error">{error}</StatusMessage></div>}
      <StatusMessage kind="info">
        An approved override shortens the cooling-off window. It does not bypass dual
        approval, activate unapproved details, or release any payment hold.
      </StatusMessage>
      <div className="mt-3 space-y-3">
        <div>
          <label className="field-label" htmlFor="override-justification">Justification</label>
          <textarea id="override-justification" rows={3} className="field-input"
                    value={justification}
                    onChange={(event) => setJustification(event.target.value)} />
        </div>
        <div>
          <label className="field-label" htmlFor="override-evidence">Evidence reference</label>
          <input id="override-evidence" className="field-input" value={evidence}
                 onChange={(event) => setEvidence(event.target.value)} />
        </div>
      </div>
      <div className="mt-4 flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={busy} onClick={submit}>Submit request</Button>
      </div>
    </Dialog>
  );
}
