import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { bankChangeApi, vendorApi } from '../../api/endpoints';
import { ApiError } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { StatusMessage } from '../../components/ui/StatusMessage';
import type { VendorBankAccount } from '../../types';

/**
 * The Create Bank-Change Request form.
 *
 * <p>It shows exactly nine controls: the vendor, the masked read-only
 * current reference, the six proposed/justification fields, and Submit.
 * There is no approver field, no reference field and no status field -
 * not hidden, not disabled, not present. The backend generates those
 * and rejects them if they arrive.
 */
const schema = z.object({
  vendorId: z.coerce.number().int().positive('Select a vendor'),
  proposedAccountHolderName: z.string().trim().min(2, 'Enter the account holder name').max(160),
  proposedBankName: z.string().trim().min(2, 'Enter the bank name').max(160),
  proposedAccountReference: z.string().trim()
    .regex(/^[A-Za-z0-9-]{6,60}$/, 'Letters, digits and hyphens only (6-60 characters)'),
  proposedRoutingCode: z.string().trim()
    .regex(/^[A-Za-z0-9-]{4,40}$/, 'Letters, digits and hyphens only (4-40 characters)'),
  reason: z.string().trim().min(10, 'Give a meaningful reason (at least 10 characters)').max(1000),
  evidenceReference: z.string().trim().min(1, 'Enter the evidence reference').max(120),
});

type FormValues = z.infer<typeof schema>;

export function CreateBankChangeForm({ onCreated }: { onCreated: () => void }) {
  const [search, setSearch] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [createdReference, setCreatedReference] = useState<string | null>(null);
  const [currentAccount, setCurrentAccount] = useState<VendorBankAccount | null>(null);

  const vendors = useAsync(() => vendorApi.searchActive(search || undefined), [search]);

  const { register, handleSubmit, watch, reset, setError, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      vendorId: 0,
      proposedAccountHolderName: '',
      proposedBankName: '',
      proposedAccountReference: '',
      proposedRoutingCode: '',
      reason: '',
      evidenceReference: '',
    },
  });

  const selectedVendorId = watch('vendorId');

  // The current reference is fetched and shown read-only, so the Maker
  // can see what is being replaced without ever being able to edit it.
  useEffect(() => {
    if (!selectedVendorId) {
      setCurrentAccount(null);
      return;
    }
    let cancelled = false;
    vendorApi.currentBankAccount(Number(selectedVendorId))
      .then((account) => {
        if (!cancelled) setCurrentAccount(account ?? null);
      })
      .catch(() => {
        if (!cancelled) setCurrentAccount(null);
      });
    return () => { cancelled = true; };
  }, [selectedVendorId]);

  const onSubmit = async (values: FormValues) => {
    setSubmitError(null);
    setCreatedReference(null);
    try {
      const created = await bankChangeApi.create(values);
      setCreatedReference(created.requestReference);
      reset();
      setCurrentAccount(null);
      onCreated();
    } catch (err) {
      if (err instanceof ApiError) {
        // Field-level messages from Bean Validation land on the field.
        err.fieldErrors.forEach((fieldError) => {
          setError(fieldError.field as keyof FormValues, { message: fieldError.message });
        });
        setSubmitError(err.message);
      } else {
        setSubmitError('The request could not be submitted.');
      }
    }
  };

  return (
    <Card title="Create bank-change request"
          description="The backend assigns both approvers and generates the request reference.">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {createdReference && (
          <StatusMessage kind="success">
            Request {createdReference} submitted. An independent approver has been assigned
            automatically and applicable payment holds are in place.
          </StatusMessage>
        )}
        {submitError && <StatusMessage kind="error">{submitError}</StatusMessage>}

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <label className="field-label" htmlFor="vendor-search">Search vendors</label>
            <input id="vendor-search" className="field-input" placeholder="Name or vendor code"
                   value={search} onChange={(event) => setSearch(event.target.value)} />
          </div>

          <div>
            <label className="field-label" htmlFor="vendorId">Vendor</label>
            <select id="vendorId" className="field-input" {...register('vendorId')}>
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
            {vendors.error && <p className="field-error">{vendors.error}</p>}
          </div>

          <div>
            <label className="field-label" htmlFor="current-reference">
              Current bank reference
            </label>
            <input id="current-reference" className="field-input" readOnly disabled
                   value={currentAccount?.maskedAccountReference
                     ?? (selectedVendorId ? 'No banking record on file' : '')} />
            <p className="mt-1 text-xs text-slate-500">
              Masked and read-only. Submitting this request does not change it.
            </p>
          </div>

          <div>
            <label className="field-label" htmlFor="proposedAccountHolderName">
              Proposed account holder name
            </label>
            <input id="proposedAccountHolderName" className="field-input"
                   {...register('proposedAccountHolderName')} />
            {formState.errors.proposedAccountHolderName && (
              <p className="field-error">
                {formState.errors.proposedAccountHolderName.message}
              </p>
            )}
          </div>

          <div>
            <label className="field-label" htmlFor="proposedBankName">Proposed bank name</label>
            <input id="proposedBankName" className="field-input" {...register('proposedBankName')} />
            {formState.errors.proposedBankName && (
              <p className="field-error">{formState.errors.proposedBankName.message}</p>
            )}
          </div>

          <div>
            <label className="field-label" htmlFor="proposedAccountReference">
              Proposed account reference
            </label>
            <input id="proposedAccountReference" className="field-input"
                   placeholder="SYN-AC-200777" {...register('proposedAccountReference')} />
            {formState.errors.proposedAccountReference && (
              <p className="field-error">
                {formState.errors.proposedAccountReference.message}
              </p>
            )}
          </div>

          <div>
            <label className="field-label" htmlFor="proposedRoutingCode">Routing code</label>
            <input id="proposedRoutingCode" className="field-input" placeholder="SYNRT-0002"
                   {...register('proposedRoutingCode')} />
            {formState.errors.proposedRoutingCode && (
              <p className="field-error">{formState.errors.proposedRoutingCode.message}</p>
            )}
          </div>

          <div className="sm:col-span-2">
            <label className="field-label" htmlFor="reason">Reason for change</label>
            <textarea id="reason" rows={3} className="field-input" {...register('reason')} />
            {formState.errors.reason && (
              <p className="field-error">{formState.errors.reason.message}</p>
            )}
          </div>

          <div className="sm:col-span-2">
            <label className="field-label" htmlFor="evidenceReference">Evidence reference</label>
            <input id="evidenceReference" className="field-input" placeholder="EVD-2026-0001"
                   {...register('evidenceReference')} />
            {formState.errors.evidenceReference && (
              <p className="field-error">{formState.errors.evidenceReference.message}</p>
            )}
          </div>
        </div>

        <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
          <Button type="submit" loading={formState.isSubmitting}>Submit request</Button>
        </div>
      </form>
    </Card>
  );
}
