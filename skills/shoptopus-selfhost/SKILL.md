---
name: shoptopus-selfhost
description: Develop, debug, review, and test the Shoptopus self-hosted Spring Boot e-commerce backend and its React storefront integration. Use for work in shoptopus-selfhost, its API contracts, multistore permissions, checkout, content/media, and frontend packaging.
---

# Shoptopus Selfhost

Use this skill to make changes that fit the existing Shoptopus application and preserve its client contracts. It covers the self-hosted distribution, whose default Maven build differs from the older `shoptopus-spring` checkout.

## Establish the checkout

Find the project root using `pom.xml`, `scripts/build-storefront-static.sh`, and `src/main/java/com/shoptopus/core/CoreApplication.java`. Read applicable `AGENTS.md` and check Git status before editing. All source paths in the references are relative to that root; the skill may be installed elsewhere.

The analysis baseline is commit `3f1e01322e0ff36c59473926f7d3b9523dd46602`, inspected on 2026-11-05. Recheck source when the checkout differs. The companion React source repositories are separate; this checkout contains their compiled assets. Do not infer a source layout or npm scripts from those bundles.

## Read the reference for the task

| Work | Reference |
| --- | --- |
| Architecture, ownership, entities, database boundaries, integrations | [architecture.md](references/architecture.md) |
| React storefront behavior, browser routes, payloads, response shapes | [storefront-contracts.md](references/storefront-contracts.md) |
| Pricing, cart, checkout, invoices, authentication, multistore access | [commerce-and-access.md](references/commerce-and-access.md) |
| Local setup, Maven profiles, frontend builds, migrations, test selection, snapshots | [development.md](references/development.md) |
| Raspberry Pi hosting, verified GitHub releases, HTTPS, both-database backups | [hosting.md](references/hosting.md) |
| Existing defects, operational limitations, evidence and validation gaps | [findings.md](references/findings.md) |

Read only the references relevant to the requested change. Findings describe the inspected implementation; they do not authorize unrelated remediation or define behavior to preserve.

## Choose the correct API surface

- `/api/storefront/**`: React storefront facade; begin with `storefront/*Controller.java` and the corresponding service.
- `/api/admin-ui/**`: React admin facade; begin with `adminui/AdminUiController.java`. Permissions and session/navigation data come from `AdminUiSessionService` and `AdminRoleScopeService`.
- `/api/admin/**`: legacy-compatible admin routes; controllers in `admin/` use `AdminAccessService`. Several UI services delegate mutations to these domain services.
- `/api/public/**`, `/api/auth/**`, `/api/product*`, `/api/cart/**`, `/api/checkout/**`: older public contracts and aliases. Route/payload parity tests are intentional compatibility constraints.

Identify every consumer before changing shared code. A storefront change does not automatically belong in `CatalogService`, and the legacy `CheckoutService` is a different flow from `StorefrontCheckoutService`. Search callers rather than assuming these are interchangeable.

## Working rules that matter here

1. Trace controller → facade/domain service → repository or JDBC → migration → response mapper → behavioral test. Preserve the target surface's field names, nesting, status codes, and pagination.
2. Choose `mainTransactionManager` for application data and `logsTransactionManager` for logging data. A new persistence package must be added to the appropriate explicit entity and repository scans. Direct JDBC writes bypass JPA audit listeners and can leave managed entities stale.
3. Use a new migration in the correct database's migration directory. `spring.flyway.enabled=false` disables auto-configuration, while the custom `FlywayConfig` still migrates both databases. Hibernate validates schemas; it does not update them.
4. Check both ownership and store scope for reads, mutations, child records, and referenced IDs. Store scope is implemented in application code, not a universal tenant filter. Do not rely on a store header, UUID secrecy, JPA default-store assignment, or UI visibility as authorization.
5. Retain soft-delete filtering, translation maps, `BigDecimal` pricing, variant/customization identity, managed media ordering, and invoice snapshots when modifying their domains. See the commerce reference before changing money or checkout.
6. Treat the configured root username as mutable runtime configuration. `RootUserBootstrap` reconciles the fixed seeded user ID on startup; changing the email in the UI alone is not durable across restarts.
7. Edit React source in the companion checkout and rebuild through the relevant profile. `src/main/resources/static` contains tracked generated assets, including `/studio`; hand-editing minified JavaScript is not the source workflow.
8. Select tests by observable behavior and affected API families. Many tests under `unit/` still require PostgreSQL Testcontainers. Report what actually ran and distinguish compile success from application startup or integration success.

## Fast commands

Run from the project root with an appropriate JDK. The POM targets Java 20; JDK 21 was verified to compile it with `--release 20` at the baseline.

```bash
./mvnw -DskipTests compile
./mvnw -Dtest=MySqlDumpParserTest,EmailSettingsServiceTest test
./mvnw -Dtest=StorefrontShopApiTest test
./mvnw test
```

The last two commands require a running Docker daemon. Default Maven execution does not invoke Node. Frontend packaging uses `-Pcompile-storefront` or `-Pprepare-release`; details and the tracked-admin-assets caveat are in the development reference.

Useful source searches:

```bash
rg -n 'RequestMapping|GetMapping|PostMapping|PatchMapping' src/main/java/com/shoptopus/core/storefront
rg -n 'requirePermission|requireAnyPermission|scopedProduct|matchesActiveStore' src/main/java/com/shoptopus/core/adminui src/main/java/com/shoptopus/core/admin
rg -n 'store_id|user_store_roles' src/main/resources/db/migration
rg -n 'checkout|cart|status|total_amount' src/test/java/com/shoptopus/core/storefront
```

## Keep the skill accurate

When the architecture or workflows change, update the affected reference and its evidence baseline. Keep commands specific to the selfhost checkout. Preserve the distinction between observed implementation, a desired invariant, and a proposed improvement. The repository copy under `skills/shoptopus-selfhost` is the editable source; an installed personal copy may need refreshing after edits.
