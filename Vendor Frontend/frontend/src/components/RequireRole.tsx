import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import type { RoleName } from '../types';

/**
 * Route guard. This is a usability measure, not a security boundary:
 * every rule it reflects is enforced again on the server, so hiding a
 * link never decides whether an operation is allowed.
 */
export function RequireRole({ roles }: { roles: RoleName[] }) {
  const { user, loading, hasAnyRole } = useAuth();

  if (loading) {
    return <p className="p-6 text-sm text-slate-500">Checking your session…</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (!hasAnyRole(...roles)) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h1 className="text-sm font-semibold text-slate-900">Not available to your role</h1>
        <p className="mt-1 text-sm text-slate-600">
          This area is restricted to: {roles.map((r) => r.replace('_', ' ')).join(', ')}.
        </p>
      </div>
    );
  }
  return <Outlet />;
}
