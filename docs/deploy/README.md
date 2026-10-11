# Deploy templates (sanitized)

These templates contain no credentials. The isolated runtime at
`/home/eariver/wayfarer-dev/runtime` renders concrete configs out-of-band:

1. `mariadb-setup.sql` — shared databases/users (run as DBA once).
2. Runtime secrets are kept in `/home/eariver/wayfarer-dev/runtime/db-credentials.properties`
   (chmod 600, never committed) and are injected only into runtime plugin configs:
   - `LuckPerms/config.yml` on all five servers: `storage-method: mysql`,
     `messaging-service: sql`, address/database/username/password.
   - `mcMMO/config.yml` on Main/Frontier: `MySQL.Enabled: true`, `Name: wayfarer_mcmmo`,
     `TablePrefix: mcmmo_` (production prefix).
3. Redis (production parity): isolated instance on 127.0.0.1:6399 with cluster id `waymark`;
   `RedisEconomy/config.yml` per backend (`clientName: main|frontier`, port 6399).
4. ResourcePackManager: give each backend its own self-host port
   (`selfHostPort` explicit, e.g. main=25591) — the auto-derived `MC+1` port collides with the
   Frontier game port (25580).
