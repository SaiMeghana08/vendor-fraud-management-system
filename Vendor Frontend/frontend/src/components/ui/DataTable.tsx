import type { ReactNode } from 'react';
import { Loader2 } from 'lucide-react';

export interface Column<T> {
  header: string;
  cell: (row: T) => ReactNode;
  align?: 'left' | 'right';
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  keyOf: (row: T) => string | number;
  loading?: boolean;
  error?: string | null;
  emptyMessage?: string;
}

/**
 * One table for every list in the app, with the three states a real
 * screen needs: loading, an error the user can act on, and a
 * meaningful empty message.
 */
export function DataTable<T>({ columns, rows, keyOf, loading, error,
                               emptyMessage = 'Nothing to show yet' }: Props<T>) {
  if (loading) {
    return (
      <div className="flex items-center gap-2 py-8 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
        Loading…
      </div>
    );
  }
  if (error) {
    return (
      <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">
        {error}
      </p>
    );
  }
  if (rows.length === 0) {
    return <p className="py-8 text-sm text-slate-500">{emptyMessage}</p>;
  }
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.header}
                scope="col"
                className={`px-5 py-2 text-xs font-semibold uppercase tracking-wide
                            text-slate-500 ${column.align === 'right'
                              ? 'text-right' : 'text-left'}`}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row) => (
            <tr key={keyOf(row)} className="hover:bg-slate-50">
              {columns.map((column) => (
                <td
                  key={column.header}
                  className={`whitespace-nowrap px-5 py-3 text-slate-700
                              ${column.align === 'right' ? 'text-right' : ''}`}
                >
                  {column.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
