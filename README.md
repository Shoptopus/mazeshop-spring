# Shoptopus Self-Host

`shoptopus-spring-selfhost` is the open-source self-hosted distribution of Shoptopus. It includes the Spring Boot backend and the storefront integration path. The admin frontend is intentionally not part of the default open-source build.

## What is included

- Spring Boot backend API
- PostgreSQL migrations for main and logs databases
- optional packaged storefront assets
- optional packaged admin assets for internal/managed release builds only
- Docker Compose for local PostgreSQL and MailHog

## What is not included by default

- the admin frontend is not built by the standard Maven package flow
- open-source users can run the backend alone or package it together with the storefront
- if you also have a private checkout of `../shoptopus-spring-admin`, you can still produce the combined managed build locally

## Requirements

- Java 20
- Node.js 20 for storefront or admin asset builds
- npm
- Docker and Docker Compose
- PostgreSQL client tools if you want to use snapshot import/export scripts directly

## Configuration

Primary runtime configuration lives in [src/main/resources/application.yml](/Users/rolandtreiber/Projects/shoptopus-new/shoptopus-spring-selfhost/src/main/resources/application.yml:1).

Important settings:

```yaml
spring:
  datasource:
    main:
      url: jdbc:postgresql://localhost:5432/shoptopus
      username: postgres
      password: postgres
    logs:
      url: jdbc:postgresql://localhost:5432/shoptopus_logs
      username: postgres
      password: postgres

app:
  root-user:
    username: owner@example.com
  multistore:
    enabled: false
  jwt:
    secret: replace-with-strong-secret-32-chars-minimum
  storefront:
    public-base-url: http://localhost:8080
  admin-ui:
    public-base-url: http://localhost:8080/studio
  media:
    storage-path: ./uploads/admin-media
    public-base-url: /media
```

Environment variable overrides that matter most:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `DB_LOGS_URL`, `DB_LOGS_USERNAME`, `DB_LOGS_PASSWORD`
- `APP_ROOT_USER_USERNAME`
- `APP_MULTISTORE_ENABLED`
- `APP_JWT_SECRET`
- `APP_STOREFRONT_PUBLIC_BASE_URL`
- `APP_ADMIN_UI_PUBLIC_BASE_URL`
- `APP_MEDIA_STORAGE_PATH`
- `APP_MEDIA_PUBLIC_BASE_URL`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM_ADDRESS`, `MAIL_FROM_NAME`

### Root super-admin

On startup, the seeded root account is reconciled to `app.root-user.username`. The default value is `superadmin@m.com`, but you should change it before exposing the app publicly.

Default seeded password:

- username: value of `app.root-user.username`
- password: `password123`

Change that password immediately after first login.

### Global settings

Common global settings in `application.yml`:

- `app.root-user.username`: login identifier for the seeded root account
- `app.multistore.enabled`: enables store-scoped behavior and multi-store administration
- `app.storefront.public-base-url`: public storefront base URL
- `app.admin-ui.public-base-url`: public admin URL if you run a managed/private admin build
- `app.media.storage-path`: filesystem path for uploads
- `app.media.public-base-url`: public URL prefix for media
- `app.snapshot.directory`: snapshot output directory
- `app.snapshot.retention`: snapshot retention period

## Running with Docker

### Start supporting services

```bash
docker compose up -d
```

This starts:

- PostgreSQL on `localhost:5432`
- MailHog SMTP on `localhost:1025`
- MailHog UI on `http://localhost:8025`

### Run the backend from the CLI

```bash
./mvnw spring-boot:run
```

Default local endpoints:

- API and packaged storefront: `http://localhost:8080`
- packaged admin, if present: `http://localhost:8080/studio`

## Running from the CLI without Docker Compose

If you already have PostgreSQL and SMTP elsewhere, point the datasource and mail settings at those services and run:

```bash
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup for both databases.

## Tests

Run the full automated test suite:

```bash
./mvnw test
```

Run one test class:

```bash
./mvnw -Dtest=AdminRouteBehaviorTest test
```

The integration tests use Testcontainers PostgreSQL.

## Build and package flows

### Default package flow

The default Maven package flow does not build frontend applications. It only compiles and packages the backend:

```bash
./mvnw package
```

This is the right flow for:

- backend-only development
- CI builds that should not depend on adjacent frontend repositories
- open-source distributions where the admin should stay out of the standard artifact path

### Storefront-only package flow

`build:compile-storefront` packages the backend and also compiles the adjacent storefront checkout at `../shoptopus-spring-storefront`. It does not attempt to build the admin.

```bash
./build:compile-storefront
```

You can also call the Maven profile directly:

```bash
./mvnw -Pcompile-storefront package
```

### Release package flow

`prepare:release` reproduces the previous combined build behavior. It packages the backend and compiles both:

- `../shoptopus-spring-storefront`
- `../shoptopus-spring-admin`

```bash
./prepare:release
```

Equivalent Maven command:

```bash
./mvnw -Pprepare-release package
```

Use this only when both adjacent frontend repositories are available.

## Frontend build expectations

The helper build scripts live in [scripts](/Users/rolandtreiber/Projects/shoptopus-new/shoptopus-spring-selfhost/scripts:1):

- [build-storefront-static.sh](/Users/rolandtreiber/Projects/shoptopus-new/shoptopus-spring-selfhost/scripts/build-storefront-static.sh:1)
- [build-admin-static.sh](/Users/rolandtreiber/Projects/shoptopus-new/shoptopus-spring-selfhost/scripts/build-admin-static.sh:1)

By default they look for adjacent repositories:

- storefront: `../shoptopus-spring-storefront`
- admin: `../shoptopus-spring-admin`

Optional overrides:

- `SHOPTOPUS_STOREFRONT_DIR=/absolute/path/to/storefront`
- `SHOPTOPUS_ADMIN_DIR=/absolute/path/to/admin`

## Running the packaged jar

Build the artifact first:

```bash
./mvnw package
```

Then run the jar:

```bash
java -jar target/core-0.0.1-SNAPSHOT.jar
```

Or with explicit runtime overrides:

```bash
APP_ROOT_USER_USERNAME=owner@example.com \
APP_MULTISTORE_ENABLED=true \
APP_JWT_SECRET=replace-me-with-a-real-secret \
java -jar target/core-0.0.1-SNAPSHOT.jar
```

## First boot checklist

1. Configure both PostgreSQL databases.
2. Set `app.root-user.username` or `APP_ROOT_USER_USERNAME`.
3. Set a strong `app.jwt.secret`.
4. Start the application.
5. Sign in with the configured root username and the default password `password123`.
6. Change the password immediately.
7. Configure store, media, mail, and payment settings.
8. Enable `app.multistore.enabled` only if you need multiple stores.

## License

This repository is distributed under the GNU General Public License v3.0. See [LICENSE](/Users/rolandtreiber/Projects/shoptopus-new/shoptopus-spring-selfhost/LICENSE:1).
