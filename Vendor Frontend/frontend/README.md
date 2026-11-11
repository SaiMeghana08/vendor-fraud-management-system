# Frontend — Fraudulent Vendor Bank-Change Detection Workflow

React 18 + TypeScript + Vite + Tailwind. Runs independently of the backend on
<http://localhost:5173>, proxying `/api` to <http://localhost:9090>.

## Requirements

Node.js 20+.

## Running

```bash
npm install
npm run dev
```

The backend must be running for anything beyond the login screen to load. No
screen contains mocked or hardcoded operational data.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Development server with the `/api` proxy |
| `npm run build` | `tsc -b && vite build` |
| `npm run preview` | Serve the production build |
| `npm run typecheck` | Types only |
| `npm run lint` | ESLint, zero warnings tolerated |
| `npm run test` | Vitest (15 tests) |

## Dashboards

| Route | Role | Contents |
|---|---|---|
| `/maker` | MAKER | Create request, my requests, status and cooling-off tracking |
| `/approver` | APPROVER | Assigned tasks, review and decide, decision history |
| `/accounts-payable` | ACCOUNTS_PAYABLE | Payments, creation, holds, eligibility, release, override requests, reassignment |
| `/administrator` | ADMINISTRATOR | Override decisions, exception monitoring, reassignment, audit log |
| `/requests/:id` | any | Full request detail and approval trail |

Route guards hide what a role cannot use. They are a usability measure only:
every rule they reflect is enforced again on the server.

## Layout

```
src/
├── api/         axios client + every endpoint the UI can call
├── components/  layout, notification bell, route guard, reassign dialog
│   └── ui/      Badge, Button, Card, DataTable, Dialog, StatusMessage, CountdownBadge
├── context/     AuthContext
├── hooks/       useAsync
├── pages/       login, request detail, and one folder per role
├── types/       mirrors the backend DTOs
└── utils/       formatting
```

## What the UI deliberately cannot do

- Choose either approver
- Supply a maker id, request reference, payment reference or status
- Shorten or skip cooling-off
- Release a payment hold from any approval or override screen
