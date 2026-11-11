import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateBankChangeForm } from '../src/pages/maker/CreateBankChangeForm';
import { bankChangeApi, vendorApi } from '../src/api/endpoints';

vi.mock('../src/api/endpoints', () => ({
  vendorApi: {
    searchActive: vi.fn(),
    currentBankAccount: vi.fn(),
    bankAccountHistory: vi.fn(),
  },
  bankChangeApi: {
    create: vi.fn(),
  },
}));

const mockedVendorApi = vi.mocked(vendorApi);
const mockedBankChangeApi = vi.mocked(bankChangeApi);

describe('Create bank-change request form', () => {
  beforeEach(() => {
    mockedVendorApi.searchActive.mockResolvedValue([
      { id: 7, vendorCode: 'VND-1001', name: 'Northwind Industrial Supplies', active: true },
    ]);
    mockedVendorApi.currentBankAccount.mockResolvedValue({
      id: 1,
      accountHolderName: 'Northwind Industrial Supplies',
      bankName: 'Synthetic Bank of Avalon',
      maskedAccountReference: '•••••••••0001',
      routingCode: 'SYNRT-0001',
      status: 'ACTIVE',
    });
  });

  it('offers no approver, reference or status field anywhere', async () => {
    render(<CreateBankChangeForm onCreated={vi.fn()} />);
    await waitFor(() => expect(mockedVendorApi.searchActive).toHaveBeenCalled());

    // The nine controls the specification allows, and nothing else that
    // could steer the workflow.
    expect(screen.queryByLabelText(/approver/i)).toBeNull();
    expect(screen.queryByLabelText(/request reference/i)).toBeNull();
    expect(screen.queryByLabelText(/^status/i)).toBeNull();
    expect(screen.queryByLabelText(/approval stage/i)).toBeNull();
    expect(screen.queryByLabelText(/maker/i)).toBeNull();
  });

  it('shows the current bank reference masked and read-only', async () => {
    render(<CreateBankChangeForm onCreated={vi.fn()} />);
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByLabelText('Vendor')).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText('Vendor'), '7');

    const current = await screen.findByLabelText('Current bank reference');
    await waitFor(() => expect(current).toHaveValue('•••••••••0001'));
    expect(current).toBeDisabled();
  });

  it('refuses to submit without a reason and an evidence reference', async () => {
    render(<CreateBankChangeForm onCreated={vi.fn()} />);
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByLabelText('Vendor')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit request/i }));

    expect(await screen.findByText(/select a vendor/i)).toBeInTheDocument();
    expect(await screen.findByText('Give a meaningful reason (at least 10 characters)'))
      .toBeInTheDocument();
    expect(mockedBankChangeApi.create).not.toHaveBeenCalled();
  });

  it('rejects an account reference with disallowed characters', async () => {
    render(<CreateBankChangeForm onCreated={vi.fn()} />);
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByLabelText('Vendor')).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText('Vendor'), '7');
    await user.type(screen.getByLabelText('Proposed account reference'), 'bad ref!!');
    await user.click(screen.getByRole('button', { name: /submit request/i }));

    expect(await screen.findByText('Letters, digits and hyphens only (6-60 characters)'))
      .toBeInTheDocument();
    expect(mockedBankChangeApi.create).not.toHaveBeenCalled();
  });

  it('sends exactly the six business fields the backend accepts', async () => {
    mockedBankChangeApi.create.mockResolvedValue({
      id: 1,
      requestReference: 'BCR-2026-000001',
      vendorId: 7,
      vendorName: 'Northwind Industrial Supplies',
      vendorCode: 'VND-1001',
      makerId: 3,
      makerName: 'Maya Kerr',
      proposedAccountHolderName: 'Northwind Industrial Supplies',
      proposedBankName: 'Fictional Mutual Bank',
      proposedMaskedAccountReference: '•••••••••0777',
      proposedRoutingCode: 'SYNRT-0002',
      reason: 'Vendor consolidated its banking',
      evidenceReference: 'EVD-2026-0001',
      status: 'PENDING_FIRST_APPROVAL',
      createdAt: '2026-11-11T10:00:00Z',
      updatedAt: '2026-11-11T10:00:00Z',
      approvalTasks: [],
    });

    render(<CreateBankChangeForm onCreated={vi.fn()} />);
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByLabelText('Vendor')).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText('Vendor'), '7');
    await user.type(screen.getByLabelText('Proposed account holder name'), 'Northwind Industrial');
    await user.type(screen.getByLabelText('Proposed bank name'), 'Fictional Mutual Bank');
    await user.type(screen.getByLabelText('Proposed account reference'), 'SYN-AC-200777');
    await user.type(screen.getByLabelText('Routing code'), 'SYNRT-0002');
    await user.type(screen.getByLabelText('Reason for change'),
      'Vendor consolidated its banking to a single provider');
    await user.type(screen.getByLabelText('Evidence reference'), 'EVD-2026-0001');
    await user.click(screen.getByRole('button', { name: /submit request/i }));

    await waitFor(() => expect(mockedBankChangeApi.create).toHaveBeenCalledTimes(1));
    const payload = mockedBankChangeApi.create.mock.calls[0][0];
    expect(Object.keys(payload).sort()).toEqual([
      'evidenceReference',
      'proposedAccountHolderName',
      'proposedAccountReference',
      'proposedBankName',
      'proposedRoutingCode',
      'reason',
      'vendorId',
    ]);
    expect(await screen.findByText(/BCR-2026-000001 submitted/i)).toBeInTheDocument();
  });
});
