import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth, defaultRouteFor } from './context/AuthContext';
import { AppLayout } from './components/AppLayout';
import { RequireRole } from './components/RequireRole';
import { LoginPage } from './pages/LoginPage';
import { RequestDetailPage } from './pages/RequestDetailPage';
import { MakerDashboard } from './pages/maker/MakerDashboard';
import { ApproverDashboard } from './pages/approver/ApproverDashboard';
import { AccountsPayableDashboard } from './pages/ap/AccountsPayableDashboard';
import { AdministratorDashboard } from './pages/admin/AdministratorDashboard';

function HomeRedirect() {
  const { user, loading } = useAuth();
  if (loading) return <p className="p-6 text-sm text-slate-500">Loading…</p>;
  return <Navigate to={defaultRouteFor(user)} replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route element={<RequireRole roles={['MAKER', 'APPROVER',
                                               'ACCOUNTS_PAYABLE', 'ADMINISTRATOR']} />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<HomeRedirect />} />
              <Route path="/requests/:requestId" element={<RequestDetailPage />} />

              <Route element={<RequireRole roles={['MAKER']} />}>
                <Route path="/maker" element={<MakerDashboard />} />
              </Route>

              <Route element={<RequireRole roles={['APPROVER']} />}>
                <Route path="/approver" element={<ApproverDashboard />} />
              </Route>

              <Route element={<RequireRole roles={['ACCOUNTS_PAYABLE']} />}>
                <Route path="/accounts-payable" element={<AccountsPayableDashboard />} />
              </Route>

              <Route element={<RequireRole roles={['ADMINISTRATOR']} />}>
                <Route path="/administrator" element={<AdministratorDashboard />} />
              </Route>
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
