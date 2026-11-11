import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RequireRole } from '../src/components/RequireRole';
import { AuthProvider, defaultRouteFor } from '../src/context/AuthContext';
import type { UserProfile } from '../src/types';

function profile(roles: UserProfile['roles']): UserProfile {
  return { id: 1, username: 'u', fullName: 'User', email: 'u@test', active: true, roles };
}

describe('role-based navigation', () => {
  it('sends each role to its own dashboard', () => {
    expect(defaultRouteFor(profile(['MAKER']))).toBe('/maker');
    expect(defaultRouteFor(profile(['APPROVER']))).toBe('/approver');
    expect(defaultRouteFor(profile(['ACCOUNTS_PAYABLE']))).toBe('/accounts-payable');
    expect(defaultRouteFor(profile(['ADMINISTRATOR']))).toBe('/administrator');
  });

  it('sends a signed-out visitor to the login page', () => {
    expect(defaultRouteFor(null)).toBe('/login');
  });

  it('redirects an unauthenticated visitor away from a guarded route', async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/maker']}>
          <Routes>
            <Route element={<RequireRole roles={['MAKER']} />}>
              <Route path="/maker" element={<p>Maker area</p>} />
            </Route>
            <Route path="/login" element={<p>Login page</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Maker area')).toBeNull();
  });
});
