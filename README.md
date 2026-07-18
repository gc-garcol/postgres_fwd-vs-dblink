# fwd-vs-datalink

Demo comparing two ways to query data across two PostgreSQL databases: **postgres_fdw** and **dblink**.

## Architecture

- `postgres-main` (port 5432, schema `postgres_main`): table `users` — 4 records.
- `postgres-remote` (port 5433, schema `postgres_remote`): table `account_details` — 10 records.
- The two tables are linked by the shared `external_id` column.

On startup, the Spring Boot app runs 2 runners that provision postgres-main from `application.yaml` config (change the remote host/account, restart the app, and it's applied):

- `DblinkSetupRunner`: creates the `remote_dblink` server + user mapping for dblink.
- `PostgresFdwSetupRunner`: creates the `remote_fdw` server and imports foreign tables (listed in `remote-db.fdw.tables`, or all tables when empty) into the `postgres_main` schema.

## API

Filter users (joins `users` with `account_details` on `external_id`), JSON body with optional filters `username`, `email`, `fullName`, `phone`:

- `POST /api/users/filter/fdw` — uses foreign tables; the planner pushes conditions down to the remote automatically.
- `POST /api/users/filter/dblink` — dblink has no pushdown, so remote-column filters (`fullName`, `phone`) are embedded into the SQL sent to the remote; local-column filters are applied after the join.

Swagger UI: http://localhost:8080/swagger-ui.html

## Run

```shell
docker compose down -v

docker compose up -d

./mvnw spring-boot:run
```
