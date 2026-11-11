import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useNavigate } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { useAuth, defaultRouteFor } from '../context/AuthContext';
import { Button } from '../components/ui/Button';
import { StatusMessage } from '../components/ui/StatusMessage';
import { ApiError } from '../api/client';

const schema = z.object({
  username: z.string().min(1, 'Enter your username'),
  password: z.string().min(1, 'Enter your password'),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { user, login, loading } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  const { register, handleSubmit, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', password: '' },
  });

  if (loading) {
    return <p className="p-6 text-sm text-slate-500">Checking your session…</p>;
  }
  if (user) {
    return <Navigate to={defaultRouteFor(user)} replace />;
  }

  const onSubmit = async (values: FormValues) => {
    setError(null);
    try {
      const profile = await login(values.username, values.password);
      navigate(defaultRouteFor(profile), { replace: true });
    } catch (err) {
      setError(err instanceof ApiError
        ? err.message
        : 'Sign-in failed. Please try again.');
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center gap-2">
          <ShieldCheck className="h-6 w-6 text-brand-600" aria-hidden />
          <h1 className="text-base font-semibold text-slate-900">
            Vendor Bank-Change Controls
          </h1>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} noValidate
              className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          {error && <StatusMessage kind="error">{error}</StatusMessage>}

          <div>
            <label className="field-label" htmlFor="username">Username</label>
            <input id="username" autoComplete="username" className="field-input"
                   {...register('username')} />
            {formState.errors.username && (
              <p className="field-error">{formState.errors.username.message}</p>
            )}
          </div>

          <div>
            <label className="field-label" htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="current-password"
                   className="field-input" {...register('password')} />
            {formState.errors.password && (
              <p className="field-error">{formState.errors.password.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" loading={formState.isSubmitting}>
            Sign in
          </Button>

          <p className="text-xs text-slate-500">
            Accounts are created by an administrator through the controlled bootstrap
            process. There is no self-service registration.
          </p>
        </form>
      </div>
    </div>
  );
}
