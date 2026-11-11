# Database

The authoritative schema lives in the Flyway migrations, under
`backend/src/main/resources/db/migration`, and runs automatically when the
backend starts:

| File | Contents |
|---|---|
| `V1__initial_schema.sql` | 14 tables, foreign keys, unique and check constraints, indexes |
| `V2__reference_data.sql` | The four roles and the three reference sequences |
| `V3__synthetic_seed_data.sql` | Synthetic vendors and their active banking records |

This folder holds only what sits *outside* the migrations:

- `schema/00_create_database.sql` — creates the database and the application
  user. Run once, as root, before the first start.

Users are never created by a migration. A password hash does not belong in
version control, so development accounts come from the controlled bootstrap
component described in `docs/DATABASE_SETUP.md`.

Every bank account reference in the seed data is fictional.
