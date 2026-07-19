# fwd-vs-datalink

Demo comparing two ways to query data across two PostgreSQL databases: **postgres_fdw** and **dblink**.

## Architecture

- `postgres-main` (port 5432, schema `postgres_main`): table `users` — 4 records.
- `postgres-remote` (port 5433, schema `postgres_remote`): tables `account_details` — 10 records — and `trades` — 2000 seeded random trades (maker/taker, trading pair, matched quantities, price, matched time) over the last 30 days.
- Tables are linked by the shared `external_id` column (`trades.maker_id`/`taker_id` reference `account_details.external_id`).

On startup, the Spring Boot app runs 2 runners that provision postgres-main from `application.yaml` config (change the remote host/account, restart the app, and it's applied):

- `DblinkSetupRunner`: creates the `remote_dblink` server + user mapping for dblink.
- `PostgresFdwSetupRunner`: creates the `remote_fdw` server and imports foreign tables (listed in `remote-db.fdw.tables`, or all tables when empty) into the `postgres_main` schema.

## API

Filter users (joins `users` with `account_details` on `external_id`), JSON body with optional filters `username`, `email`, `fullName`, `phone`:

- `POST /api/users/filter/fdw` — uses foreign tables; the planner pushes conditions down to the remote automatically.
- `POST /api/users/filter/dblink` — dblink has no pushdown, so remote-column filters (`fullName`, `phone`) are embedded into the SQL sent to the remote; local-column filters are applied after the join.

Aggregate trades per user / day / trading pair (joins `users` with remote `trades` on `external_id`; a user's maker and taker sides both count), JSON body with optional filters `username`, `tradingPair`, `fromDate`, `toDate` (UTC, inclusive):

- `POST /api/trades/aggregate/fdw` — the trades scan (pair/time filters) is pushed down to the remote; the join with local `users` and the aggregation run locally.
- `POST /api/trades/aggregate/dblink` — the whole aggregation is baked into the SQL sent to the remote, so only the small per-user/day/pair buckets travel back and are joined with local `users`.

Day buckets are pinned to UTC on both paths — a bare `matched_time::date` would use the session time zone (JVM zone via JDBC, remote server default via dblink) and the two endpoints would disagree around midnight.

Swagger UI: http://localhost:8080/swagger-ui.html

## Trading web UI

http://localhost:8080/trades.html — static page (`src/main/resources/static/trades.html`, no build step) with filters for username, trading pair and UTC date range; calls `POST /api/trades/aggregate/fdw` and renders the buckets as a table.

## Run

```shell
docker compose down -v

docker compose up -d

./mvnw spring-boot:run
```

The `initdb/` scripts (including `trades.sql` + `trades_seed.sql`) only run on a fresh volume. To add the trades table to an already-initialized `postgres-remote` without wiping it:

```shell
docker exec -i postgres-remote psql -U username -d postgres_remote < initdb/remote/trades.sql
docker exec -i postgres-remote psql -U username -d postgres_remote < initdb/remote/trades_seed.sql
```
