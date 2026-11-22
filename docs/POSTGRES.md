# PostgreSQL setup (production)

Postgres is **not** assumed running in the cloud workspace. Use this on your machine or VPS.

## Create database (Linux/macOS)

```bash
sudo -u postgres createuser -P maiclient    # choose a password
sudo -u postgres createdb -O maiclient maiclient
psql -U maiclient -d maiclient -f api/sql/schema.sql
```

## Windows (PostgreSQL installer)

1. Install PostgreSQL 16+ from https://www.postgresql.org/download/windows/
2. Open **SQL Shell (psql)** or pgAdmin.
3. Create role + database:

```sql
CREATE USER maiclient WITH PASSWORD 'choose_a_strong_password';
CREATE DATABASE maiclient OWNER maiclient;
```

4. Apply schema:

```bash
psql -U maiclient -d maiclient -h localhost -f api\sql\schema.sql
```

## Connection string

```
DATABASE_URL=postgres://maiclient:YOUR_PASSWORD@localhost:5432/maiclient
```

Put this in `api/config/.env` (never commit `.env`).

## Migration strategy

- v0: single `schema.sql` applied once.
- Later: numbered files `sql/001_....sql` applied in order; track in a `schema_migrations` table when needed.

## Health

- `GET /health` — process up
- After prod start: `GET /v1/auth/me` without token → 401 confirms auth stack loaded

