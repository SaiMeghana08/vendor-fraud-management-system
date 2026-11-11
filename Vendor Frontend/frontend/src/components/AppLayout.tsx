import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LogOut, ShieldCheck } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { NotificationBell } from './NotificationBell';
import type { RoleName } from '../types';

interface NavItem {
  to: string;
  label: string;
  roles: RoleName[];
}

const NAV_ITEMS: NavItem[] = [
  { to: '/maker', label: 'Maker', roles: ['MAKER'] },
  { to: '/approver', label: 'Approvals', roles: ['APPROVER'] },
  { to: '/accounts-payable', label: 'Accounts Payable', roles: ['ACCOUNTS_PAYABLE'] },
  { to: '/administrator', label: 'Administration', roles: ['ADMINISTRATOR'] },
];

export function AppLayout() {
  const { user, logout, hasAnyRole } = useAuth();
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center gap-6 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-brand-600" aria-hidden />
            <span className="text-sm font-semibold text-slate-900">
              Vendor Bank-Change Controls
            </span>
          </div>

          <nav className="hidden flex-1 items-center gap-1 md:flex">
            {NAV_ITEMS.filter((item) => hasAnyRole(...item.roles)).map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `rounded-md px-3 py-1.5 text-sm font-medium ${
                    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
                  }`}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <NotificationBell />
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium text-slate-800">{user?.fullName}</p>
              <p className="text-xs text-slate-500">
                {user?.roles.map((role) => role.replace('_', ' ')).join(' · ')}
              </p>
            </div>
            <button type="button" onClick={onLogout}
                    className="rounded-md p-2 text-slate-500 hover:bg-slate-100"
                    aria-label="Sign out">
              <LogOut className="h-4 w-4" aria-hidden />
            </button>
          </div>
        </div>

        <nav className="flex gap-1 overflow-x-auto border-t border-slate-100 px-4 py-2 md:hidden">
          {NAV_ITEMS.filter((item) => hasAnyRole(...item.roles)).map((item) => (
            <NavLink key={item.to} to={item.to}
                     className={({ isActive }) =>
                       `whitespace-nowrap rounded-md px-3 py-1.5 text-sm ${
                         isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600'
                       }`}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
