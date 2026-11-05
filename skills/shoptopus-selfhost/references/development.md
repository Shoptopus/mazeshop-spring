# Development, packaging and validation

## Local prerequisites

The project targets Java 20. At the analysis baseline, the host's default JDK was 27; validation explicitly selected installed JDK 21, which successfully compiled release 20. Prefer the project's expected toolchain or a verified compatible JDK rather than diagnosing Lombok/compiler failures on an arbitrary default JDK. The included Maven wrapper specifies its distribution in `.mvn/wrapper/maven-wrapper.properties`.

PostgreSQL 16 and MailHog are defined in `docker-compose.yml`. `docker/postgres-init/01-create-databases.sql` creates both `shoptopus` and `shoptopus_logs`; the image's default database alone is insufficient. Docker init scripts run on a fresh volume, so an existing volume may need the missing databases created deliberately. Do not delete a volume as a routine connection fix.

V1 also installs PostgreSQL `pg_trgm`, used by native catalog search, so the database user must be able to install/use that extension. Video uploads invoke the `ffmpeg` executable synchronously in `AdminUiMediaService`; Java and writable storage alone are insufficient for those routes. Images use Java image processing. Snapshot creation/restoration additionally require `pg_dump` / `psql`.

```bash
java -version
./mvnw -version
docker info
docker compose up -d
./mvnw spring-boot:run
```

Defaults are backend/storefront `http://localhost:8080`, admin `/studio`, PostgreSQL `localhost:5432`, SMTP `localhost:1025`, MailHog UI `http://localhost:8025`. The compose stack provides supporting services, not an application container. The YAML CORS defaults permit localhost/127.0.0.1 ports 8081 and 8082. Check companion npm scripts before assuming a frontend dev-server command or proxy.

OpenAPI JSON is configured at `/api/docs/openapi` and Swagger UI at `/api/docs`. These internal documentation paths require authentication in the security configuration; their existence does not make every public API require a bearer token.

No application startup against a live database was performed in this analysis. Startup runs migrations, reconciles the root username and schedules maintenance; use isolated development infrastructure when validating it.

## Runtime configuration

| Concern | Important environment variables / source |
| --- | --- |
| Main DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Logs DB | `DB_LOGS_URL`, `DB_LOGS_USERNAME`, `DB_LOGS_PASSWORD` |
| JWT/root | `APP_JWT_SECRET`, `APP_JWT_EXPIRATION_SECONDS`, `APP_ROOT_USER_USERNAME` |
| Store behavior | `APP_MULTISTORE_ENABLED`, `APP_STOREFRONT_CURRENCY`, `APP_I18N_LANGUAGES` |
| Public links | `APP_STOREFRONT_PUBLIC_BASE_URL`, `APP_ADMIN_UI_PUBLIC_BASE_URL` |
| Browser access | `APP_CORS_ALLOWED_ORIGINS` |
| Media | `APP_MEDIA_STORAGE_PATH`, `APP_MEDIA_PUBLIC_BASE_URL`; defaults to `${user.dir}/uploads/admin-media` served at `/media` |
| SMTP/from | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS_ENABLE`, `MAIL_FROM_ADDRESS`, `MAIL_FROM_NAME` |
| Stripe | `STRIPE_SANDBOX_KEY`, `STRIPE_SANDBOX_SECRET_KEY`, with live-key fallback variables in YAML; DB provider configuration takes precedence when populated |
| Other integrations | PayPal/Amazon variables, `GOOGLE_MAPS_API_KEY`, `CHATGPT_API_KEY`; inspect configuration records and actual client implementation |
| Snapshots | `APP_SNAPSHOT_DIRECTORY`, `APP_SNAPSHOT_RETENTION`, `APP_SNAPSHOT_SCHEDULE_FIXED_DELAY`, `APP_SNAPSHOT_SCHEDULE_INITIAL_DELAY` |

Application settings also live in `system_settings` and store/provider records. For example, shop branding, social-provider configuration, SMTP/Gmail settings and Stripe test/live values are not controlled exclusively by environment variables. Trace precedence in the service relevant to the task. Do not copy existing provider secrets or sample key literals into documentation or test output.

The seeded root password is documented in the repository README and seed migrations; changing `APP_ROOT_USER_USERNAME` does not change that password. Root/bootstrap behavior is described in the commerce reference.

## Maven build modes

This distinction is specific to **selfhost**:

| Command | Behavior |
| --- | --- |
| `./mvnw -DskipTests compile` | Compile backend, copy existing resources; no Node invocation |
| `./mvnw package` | Backend build plus normal tests; no frontend compilation |
| `./mvnw -DskipTests package` | Skip test execution, compile tests, package existing resources |
| `./build:compile-storefront` | Wrapper for `./mvnw -Pcompile-storefront package` |
| `./prepare:release` | Wrapper for `./mvnw -Pprepare-release package` |

The `compile-storefront` profile runs only `scripts/build-storefront-static.sh` in `generate-resources`. The `prepare-release` profile runs storefront and admin scripts. Wrapper arguments are forwarded, so `./build:compile-storefront -DskipTests` is valid for an explicitly requested build without executing tests.

Do not carry over the older project's assumption that every Maven build executes frontend scripts. `-Dexec.skip=true` is useful only when intentionally suppressing profile-bound exec work; default selfhost builds do not need it.

“Does not build the admin” does **not** mean “does not package admin assets.” All 21 static files, including `static/studio`, are tracked and copied as ordinary resources at the baseline. A default jar can therefore contain the checked-in admin. A storefront-only rebuild preserves any existing `studio` directory. Verify jar contents when distribution requirements include excluding the admin.

The output artifact is `target/core-0.0.1-SNAPSHOT.jar`:

```bash
java -jar target/core-0.0.1-SNAPSHOT.jar
```

Use `jar tf` to confirm the intended `BOOT-INF/classes/static` contents after frontend or packaging changes. Do not assume a clean Maven target directory removes stale source-resource assets.

## Frontend source and generated assets

`scripts/build-storefront-static.sh` defaults to `../shoptopus-spring-storefront`; `scripts/build-admin-static.sh` defaults to `../shoptopus-spring-admin`. Override with `SHOPTOPUS_STOREFRONT_DIR` and `SHOPTOPUS_ADMIN_DIR` when the checkouts are elsewhere.

Both scripts use Node 20 through nvm if available, run `npm ci` only when `node_modules` is absent, then execute `npm run build`. They expect Vite-style `dist` output. Storefront uses `VITE_APP_BASE=/`; admin uses `VITE_APP_BASE=/studio/`.

The storefront script replaces root static children while retaining `studio`; the admin script replaces only `static/studio` children. These are generated-output operations. Check repository guidance, lockfiles and Git diff in the companion checkout before building. Existing `node_modules` is not evidence that a changed lockfile has been installed.

When fixing frontend behavior, locate the real API client, router, persistent state, component and query invalidation in the companion source; apply the change there, run its available checks, rebuild and inspect the resulting asset diff. The shipped bundle helps establish the current contract but is not a maintainable patch target. Do not invent a TypeScript directory structure or frontend test command when the source is unavailable.

## Database changes

Use the next unused version in `src/main/resources/db/migration/main` or `logs`, independently. At baseline the next versions were main V52 and logs V4; rediscover before writing. Avoid modifying applied migrations, including seed data migrations, because Flyway validates checksums.

Review schema migration, JPA mapping, explicit package registration, JDBC projections, seed/import data, API output and tests together. PostgreSQL JSONB, UUID casts, timestamp semantics, partial indexes and database-specific SQL make H2 an unsuitable substitute for integration coverage here.

For a new non-null column, plan how existing rows are populated before adding the constraint. The V40–V46 multistore migrations demonstrate backfills/defaults, but their fixed default store is not automatic authorization for new business data.

## Test strategy and source map

`IntegrationTestBase` starts a shared PostgreSQL 16 Testcontainer, creates the logs database, uses random HTTP ports and the `test` Spring profile, and isolates media in a UUID-named temporary directory. Most tests require Docker even if they live in `unit/`. Test names mentioning Laravel/Postman often exercise hard-coded compatibility calls; they do not generally require a live Laravel instance.

The two standalone classes are `MySqlDumpParserTest` and `EmailSettingsServiceTest` (the latter mocks repositories/provider HTTP). At baseline these contained four tests in total.

```bash
./mvnw -Dtest=MySqlDumpParserTest,EmailSettingsServiceTest test
./mvnw -Dtest=StorefrontCartCheckoutApiTest,StorefrontCheckoutCompletionApiTest test
./mvnw -Dtest=AdminPermissionAuthorizationTest test
./mvnw test
```

| Change | Start with these tests |
| --- | --- |
| Bootstrap/config/pages/catalog projections | `StorefrontBootstrapApiTest`, `StorefrontShopApiTest`, `PublicPageApiTest`, `CatalogApiTest` |
| Cart/pricing/checkout/receipts | `StorefrontCartCheckoutApiTest`, `StorefrontCheckoutCompletionApiTest`, `CartApiTest`, publicapi cart/checkout tests |
| Auth/account/root | `StorefrontAuthApiTest`, `StorefrontAccountApiTest`, `AuthApiTest`, publicapi auth tests, `RootUserBootstrapTest` |
| Roles/admin mutations | `AdminPermissionAuthorizationTest`, relevant `admin/basecrud/*ControllerTest` or `*BulkOperationsTest` |
| Media/menu/images | `ManagedFileProductFlowTest`, `AdminUiFileOptimizationTest`, `AdminUiPageMenuControllerTest`, storefront image-dimension assertions |
| Messaging | `StorefrontMessagingApiTest`, `OrderMessagingControllerTest` |
| Compatibility | `ApiContractTest`, `AdminRouteBehaviorTest`, `AdminLaravelRouteParityTest`, `PublicLaravelRouteParityTest`, Postman parity/payload tests |
| Persistence/audits | `DualDatabaseLoggingIntegrationTest` plus the affected API test |
| Mail settings/import | `EmailSettingsServiceTest`, `AdminEmailSettingsControllerTest`, `MySqlDumpParserTest`, `ImportExportApiTest` |

Surefire includes `*Test`, `*Tests` and `*IT`, fails when no tests are selected, writes reports to `.surefire-reports` rather than the usual target location, and supplies Docker API version 1.41. Quote wildcard selectors in shells. Existing route-parity checks sometimes assert only non-5xx; add a meaningful data/permission assertion for behavioral changes.

## Development seed data

The importer is registered only when `APP_DEV_TEST_DATA_ENABLED=true` and requires super-admin access. Routes are `GET /api/admin-ui/dev/test-data`, `POST /api/admin-ui/dev/test-data/seed`, and `DELETE` on the seed route. Seed cleanup mutates application data; enabling the flag is not a reason to run it during unrelated work.

Defaults for dump/images point into an older sibling Laravel checkout, absent from this selfhost checkout. Configure `APP_DEV_TEST_DATA_DUMP_PATH`, `APP_DEV_TEST_DATA_IMAGES_PATH`, and optionally `APP_DEV_TEST_DATA_SEED_KEY` explicitly for a requested import. Do not apply the MySQL dump directly to PostgreSQL; `MySqlDumpParser` and `DevTestDataService` handle the translation/tracking.

## Snapshots and maintenance

`SnapshotService` calls `pg_dump` for the **main** database and archives `database.sql`, metadata and the configured media directory under `uploads/`. It requires PostgreSQL client tooling and writable snapshot storage.

`SnapshotMaintenanceService` can be disabled with `APP_SNAPSHOT_MAINTENANCE_ENABLED=false` (default `true`). The Pi deployment disables it and uses external backups of both databases and media. When enabled, it runs after an initial delay of 15 minutes, then every 24 hours by default. After a successful snapshot it truncates both logs tables (`audits`, `event_logs`) and deletes snapshots older than the configured retention (default seven days). The snapshot does not contain the logs DB. Treat this as actual deletion, not log archiving; see F8.

Standalone `create-snapshot.sh` / `import-snapshot.sh` read an `application.yml` or `.yaml` alongside the scripts using a limited shell/YAML parser. Import drops and recreates the target main database's `public` schema. Inspect resolved configuration and use an appropriate restore environment for a requested restore; never use import as a generic development repair command.

## Validation recorded for this analysis

On 2026-11-05, with JDK 21 and Maven 3.10.0:

```bash
mvn -o -B -ntp -Dtest=MySqlDumpParserTest,EmailSettingsServiceTest test
```

Compiled all 275 main and 122 test Java files with release 20. Four selected tests passed, zero failures/errors/skips. Static resources were copied, including the checked-in frontend assets. This used installed Maven with cached dependencies, not a fresh wrapper download.

At the initial analysis Docker was not running. Subsequent Java 21 wrapper verification on 2026-11-05 passed all 324 tests after fixing shared test-fixture isolation, media dimension refresh, and adding the snapshot-maintenance configuration test. The full verification log is a local operational artifact, not a repository file. The Pi installation also exercised both migration sets on PostgreSQL 16 (main V51, logs V3), HTTPS, randomized admin login and an authenticated admin API session. Both offsite S3 database dumps were restored into disposable databases successfully. Frontend source rebuilds and real payment/mail provider flows remain unverified; the deployed site stays behind a preview access gate with payment providers disabled. See the hosting reference for deployment and backups.
