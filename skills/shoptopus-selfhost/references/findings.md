# Analysis findings and validation limits

The 6 November authentication/checkout implementation and provider QA supersede the relevant baseline observations below. Read [checkout-reliability.md](checkout-reliability.md) before acting on purchase, guest access, tokens, frontend source or error handling findings. Other observations still require source verification.

## How to use this reference

These findings describe static source/bundle inspection at commit `3f1e01322e0ff36c59473926f7d3b9523dd46602` on 2026-11-05. They are not results of a penetration test or a full integration run. Source paths are relative to the project root. Reconfirm a finding before changing code; use the requested task to decide whether remediation belongs in scope.

The architecture supports a broad e-commerce feature set, but the existence of a route, setting or happy-path test is not evidence of a complete authorization, payment or inventory lifecycle. The most consequential gaps are at the boundary between public storefront access and mutations of purchase state.

## F1 — Payment confirmation lacks a trusted verification boundary

**Priority: high.** `config/SecurityConfig.java` permits `/api/storefront/**`. `storefront/StorefrontCheckoutController.confirm` takes only a request body; `StorefrontCheckoutService.confirm` loads a supplied order, takes a supplied payment ID or the first order payment, then marks payment paid/order processing/invoice valid and sends a receipt.

There is no customer ownership check, provider signature or provider-result verification in this method. With an explicit payment ID, it also does not verify that the payment belongs to the supplied order. This is materially different from the Stripe verification in submit.

For a requested fix, define the trusted confirmation source, bind payment to order/store, preserve legitimate guest access through a suitable token or provider flow, and test denied cross-order confirmation plus replay. Do not simply require login and thereby assume guest checkout is handled. No signed webhook handler was found in the inspected source.

## F2 — Public order confirmation returns sensitive order details

**Priority: high.** `StorefrontOrderConfirmationController` accepts an order UUID without an authenticated principal or guest token. `StorefrontOrderConfirmationService.confirmation` returns the invoice address, items, totals and payment references for that ID.

Guest confirmation is a real UI requirement, but UUID possession is the current boundary. Evaluate owner access and a scoped expiring guest token when asked to secure it. Add tests for another customer's order and an unauthenticated request with only an order ID. The existing completion test deliberately exercises the public success path; it does not validate those restrictions.

## F3 — Ownership enforcement differs between guest/cart/checkout/upload paths

**Priority: high.** In `StorefrontCartService.validateCartAccess`, a null user returns without checking whether a cart belongs to a registered user. Anonymous `resolve` also directly returns a supplied cart. Authenticated cart access is stricter, but this does not protect anonymous callers.

`StorefrontCheckoutService.checkoutContext`, used by payment-intent and submit, loads cart/items directly without calling the ownership-checked `cart` projection. Guest contact resolution reuses an existing live user by submitted email without proving control of that email. These are observations of missing checks, not an assertion that every downstream account action can be taken.

Account media upload methods in `StorefrontAccountController` call `authEmail(authentication)` but discard its result; that helper returns null instead of rejecting anonymous access. Their upload services do not receive a customer identity. Because the route family is publicly permitted, this call is not an authentication guard.

For affected work, define guest capability handling explicitly, bind existing-account data to verified identity, enforce upload authentication/limits where required, and exercise anonymous, owner and different-user paths. Do not assume the account URL prefix enforces access.

## F4 — Checkout lacks complete payment replay and inventory protection

**Priority: high.** Stripe submit verifies retrieved intent status and amount. It does not compare intent currency, bind returned metadata to the cart/store/customer, or reserve a unique consumed intent reference. `StripePaymentIntentSnapshot` does not even include metadata, and no idempotency mechanism or unique payment-reference constraint was found in the checkout path/schema.

Cart add/update/merge check stock. Submit does not perform a matching reservation/decrement with a concurrency boundary, and product/variant entities do not provide an optimistic version field. A prior cart quantity check therefore cannot establish inventory correctness at purchase time. Retrying submit can also repeat order and notification effects.

For requested purchase-lifecycle work, use transactionally enforced identity/consumption and stock rules, and consider remote effects after commit or an outbox as appropriate. Verify same-intent reuse, repeated submit, changed price/currency, simultaneous last-item purchases and partial remote failures. These are targeted future tests; they were not executed during this analysis.

Non-Stripe submit currently treats an arbitrary nonblank provider plus nonempty client payment map as a pending payment. PayPal/Amazon settings and the legacy `PaymentService.execute` accepted response should not be presented as provider-confirmed payment completion.

## F5 — Logout does not revoke an otherwise valid JWT

**Priority: high.** `auth/AuthService.logout` deletes the token's database row. `auth/security/JwtAuthenticationFilter` validates signature/expiry and finds an undeleted user by email, without checking that row or a revocation version. The removed JWT can therefore still satisfy the filter until expiry while the email matches.

Root username reconciliation changes the email used for JWT lookup, which also affects existing sessions. Authentication work needs a deliberate session invalidation policy covering logout, reset/password changes and administrative identity changes; add a request using the same token after logout rather than testing only the logout response.

## F6 — Frontend error and multi-filter contracts need reconciliation

**Priority: medium.** The shipped storefront fetch helper prefers `error.message`; `GlobalExceptionHandler` emits `user_message` / `developer_message`. The admin bundle already handles `user_message`. Validation failures commonly lose their useful user-facing message in the storefront.

The storefront bundle sends multi-category/tag/attribute filters through repeated query parameters. `StorefrontController.shop` binds each to one `String`, and `StorefrontShopService.csvUuids` expects CSV. This is a concrete contract mismatch to verify with a multi-selection integration request; it was not demonstrated in a running server here.

Fix these in the companion source and add representative client/API tests. Preserve the distinction between a safe user message and detailed developer diagnostics.

## F7 — Currency, status and filter meanings are duplicated

**Priority: medium.** `StorefrontConfigService` reads configurable currency, while cart, shop, checkout and confirmation helpers use GBP labels/defaults; checkout context sets GBP. `StripePaymentGateway` assumes two-decimal minor units. Changing a config value alone does not change the full currency behavior.

`StorefrontOrderConfirmationService.paymentStatusLabel` labels payment value 2 refunded; `AdminUiOrderService.paymentStatusLabel` labels 2 failed and 3 refunded. Order revert uses 6 outside the common 0–3 UI labels. `AdminUiProductService`'s active filter uses `status != 2`, including drafts, whereas storefront visibility uses exactly 1.

Before changing status values or adding currency support, enumerate persisted values, all writers/projections and client assumptions. A shared domain representation may help, but migration and compatibility behavior need to be explicit.

## F8 — Scheduled maintenance deletes logs without including them in the snapshot

**Priority: high for retention-sensitive deployments.** `backup/SnapshotService` dumps only `mainDataSourceProperties` and media. After a successful snapshot, `SnapshotMaintenanceService` truncates the separate logs DB's `audits` and `event_logs` tables, then deletes expired snapshots.

The schedule defaults to an initial 15-minute delay and 24-hour fixed delay, with seven-day snapshot retention. The operation is not a backup of the logs it deletes. If logs must be retained, a requested operations change needs a separate archive/retention design and a restore check. Do not describe the existing snapshots as complete two-database backups.

`import-snapshot.sh` drops/recreates the target main `public` schema. This is an operational restore tool, not a routine migration repair.

## F9 — Default packaging still includes the tracked admin bundle

**Priority: medium; depends on distribution requirements.** Default Maven builds no longer invoke the frontend scripts, but `src/main/resources/static/studio` is tracked and included by ordinary resource copying. The storefront build script explicitly preserves `studio`. Thus a build that never invokes the admin compiler can still ship the admin UI.

The README says the default flow suits distributions where admin should stay out of the standard artifact path; that is not enforced by a resource exclusion in the POM. Verify artifact contents and the desired distribution policy before changing the profiles or deleting bundled files. Also verify that rebuilt assets correspond to the backend version; no companion source revision is recorded by these scripts.

## F10 — Logs and exception diagnostics can retain sensitive values

**Priority: medium to high depending on access.** `logging/AuditTrailCaptureService.snapshot` reflects scalar fields without a secret-field redaction list. User password hashes, access-token values/content and system-setting strings can therefore enter the audit DB. `ApiLoggingAspect` stores raw URI/query information, which can contain receipt or OAuth handoff tokens.

`GlobalExceptionHandler` exposes deepest internal exception details in `developer_message`. Audit/logging changes should consider useful diagnostics, redaction and who can retrieve those records together. Do not copy real token/credential examples into skill documents or issue reports.

Audit failures are swallowed, and direct JDBC writes bypass entity snapshots. This is also a completeness limitation for features that require reliable audit history.

## F11 — Catalog scalability is limited by in-memory filtering and repeated pricing queries

**Priority: medium as catalog size grows.** `StorefrontShopService.scopedProducts` loads all products and filters/sorts/paginates in Java. Category visibility makes further catalog passes. Price sort invokes pricing from comparators; `StorefrontPricingService.bestDiscountRule` retrieves and scans a store's rules for each call. Product-card projection batches some variant/media access, so avoid claiming every relationship has an N+1 issue.

For a requested performance task, measure query counts, result size and latency first, then preserve filtering/visibility/translation/discount semantics while moving suitable work to SQL or batching. No load test or production-size measurement was performed here.

## Coverage and maintenance gaps

- **Integration coverage was unavailable in this session:** Docker was stopped. All main/test sources compiled; only the two standalone classes (four tests) ran successfully. A green compile is not proof that migrations/bootstrap/controller contracts pass against PostgreSQL.
- **Multistore coverage is missing:** no test reference to `app.multistore.enabled`, `user_store_roles` or `X-Admin-Store-Id` was found at baseline. Add explicit second-store/role tests when changing scope-sensitive behavior.
- **Frontend source is absent:** built bundles permit contract inspection, but rebuilding, linting, source-level review and browser end-to-end checks need the companion checkout. Do not claim those checks passed.
- **Compatibility tests vary in strength:** some route parity tests only reject 5xx responses, so an existing test may tolerate missing data or a 4xx response. Prefer domain behavior/denied-access assertions when changing a route.
- **Manual email queue semantics are limited:** `OutgoingEmailQueueService.processQueued` processes queued rows, marks failures failed and has no internal scheduler/retry/claim mechanism. It should not be described as an exactly-once background mail delivery system.
- **CSV quoting is asymmetric:** `io/ImportExportService` exports quoted commas/quotes but imports by splitting on commas. Round-trip values containing delimiters need dedicated handling if import/export work is requested.
- **Documentation has stale links:** the README contains absolute paths from the author's previous checkout. Resolve source paths locally instead of following those paths as evidence.

The most useful next validation, when infrastructure and frontend source are available, is a focused purchase/auth/store-isolation suite plus a rebuilt-client checkout smoke test. Select the relevant parts for the task rather than treating this list as mandatory work for every small change.
