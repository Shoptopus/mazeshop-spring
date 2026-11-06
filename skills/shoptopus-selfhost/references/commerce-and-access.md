# Commerce and access behavior

The 6 November authentication/checkout implementation and provider QA supersede the relevant baseline observations below. Read [checkout-reliability.md](checkout-reliability.md) before acting on purchase, guest access, tokens, frontend source or error handling findings. Other observations still require source verification.

## Cart identity and lifecycle

Sources: `storefront/StorefrontCartService.java`, `cart/Cart.java`, `cart/CartItem.java`, and `cart/repository/CartItemRepository.java`.

A cart can span stores; a line is identified by cart, product, optional variant and normalized customization. Two differently customized copies must remain separate. Add merges a matching line and validates aggregate quantity against variant stock when selected, otherwise product stock. Variants must be enabled, undeleted and belong to the product. Customization normalization preserves an optional value; the validation hook currently imposes no requirement.

Anonymous creation makes a fresh cart; authenticated creation reuses the latest user cart. `resolve` merges a supplied guest cart into the user's cart, preserves line identity, validates merged stock and clears source items. `AuthService.mergeLoginCart` also implements a login-cart path, so changes must cover both flows.

Cart responses contain sanitized `items`, per-store `groups`, and totals. Storefront checkout selects one group's product/store IDs and clears only that store's checked-out lines. Keep the other stores' lines and persisted cart ID usable.

Current ownership checks are weaker for anonymous requests and inconsistent across checkout paths. Do not describe this as a secure capability system; see findings F1–F3.

## Pricing and delivery

Sources: `StorefrontPricingService`, pricing helpers in `StorefrontCartService` / `StorefrontCheckoutService`, and `DeliveryType` / `DeliveryRule`.

Product discount selection considers enabled, undeleted, time-valid rules in the product's store that directly reference the product or one of its categories. It selects the rule with the greatest monetary reduction on the product base price, rather than stacking rules.

Discount type 1 is percentage; type 2 is a fixed amount. Percentage reductions use scale 2 and `HALF_UP`; final prices are clamped to zero. A variant's own price replaces the product base, with the selected product discount then applied to the variant price. Changing rule selection for variants is a behavior change, not a refactor.

Checkout computes:

```text
original subtotal = sum(original unit price × quantity)
items subtotal    = sum(discounted unit price × quantity)
item savings      = original subtotal − items subtotal
voucher savings   = valid percentage/fixed reduction, capped at items subtotal
total savings     = item savings + voucher savings
total             = max(0, items subtotal + delivery − voucher savings)
```

Voucher lookup is case-insensitive and store-scoped; enabled flag and valid-from/until are checked. This implementation does not establish a full redemption quota/reservation system.

Delivery requires an enabled type in the checkout store with an enabled, undeleted, same-store matching rule. Matching considers country, minimum/maximum cart price, weight and case-insensitive postcode prefix. The earliest matching rule is selected by creation time then ID. Confirm which subtotal/weight helpers are used when changing delivery thresholds; display totals are not interchangeable with matching inputs.

Currency configuration is only partially wired: config advertises `app.storefront.currency`, while cart/checkout projections and checkout context frequently hard-code GBP. The gateway assumes two-decimal minor units. See F7 before currency work.

## Purchase flow

Source: `StorefrontCheckoutService`, with `InvoiceSnapshotService`, `InvoiceAccessService`, `ShopMailService` and `EngagementService`.

1. Summary resolves cart/store, selected group, addresses and public payment settings.
2. Delivery/voucher calls recalculate from server data. Payment-intent creation also computes the total and sends metadata containing cart/store/delivery/contact references to Stripe.
3. Submit resolves the selected store's lines, a user or guest contact, an address, voucher and allowed delivery type, then recalculates prices.
4. Stripe submit retrieves the intent and requires `succeeded` and matching amount. Other providers currently accept client payment details and create a pending payment.
5. Submit creates an order (`status=1`), priced `OrderItem` snapshots, a payment, an invoice snapshot and a receipt access token; it notifies admins and clears the selected store's lines. Stripe submit also sends the customer receipt immediately.
6. The separate confirm method marks payment paid, order processing and invoice valid, and sends the receipt. Its current lack of ownership/provider verification is a defect, detailed in F1.

Submit returns `order_id`, `payment_id`, textual status and an API `confirmation_url`. The browser separately uses `/order/{orderId}/confirmation` as its UI route. Do not confuse the UI route, public API summary and receipt token URL.

The older `publicapi/CheckoutService` creates a much simpler pending order, rejects carts spanning multiple stores and does not implement the same item/invoice/Stripe pipeline. Its revert flow sets status 6. Preserve compatibility deliberately when extracting shared commerce logic.

Inventory is checked on cart mutation, but checkout lacks a complete reservation/decrement/idempotency boundary at baseline. A new checkout feature should assess F3/F4 rather than assume stock, payment or notification effects are exactly-once.

## Historical receipts and status values

`InvoiceSnapshotService` copies purchase details into JSONB snapshot fields. Render receipts from the recorded transaction rather than repricing against edited products, discounts, addresses or delivery settings. `InvoicePdfService` uses OpenPDF; it is distinct from browser print layout.

`InvoiceAccessService` issues a UUID token for 30 days, type 4 and accessable type `Invoice`. Validation checks type, accessable type, expiry and invoice existence; reading does not consume the token. The completion API test verifies reusable receipt downloads. OAuth state also uses numeric type 4 with a different discriminator: token type alone is insufficient when extending shared token handling.

| Status domain | Existing values |
| --- | --- |
| Product | 0 draft, 1 visible storefront product, 2 archived |
| Order in current UI projections | 0 pending, 1 processing, 2 completed, 3 cancelled; the legacy revert path also uses 6 |
| Payment | 0 pending, 1 paid/captured; values 2/3 are inconsistently labeled across storefront and admin |
| Store | Enum `PENDING`, `ACTIVE`, `DEACTIVATED`, `SUSPENDED` |

Inspect every reader/writer before changing integer statuses. An admin “active” filter currently includes drafts (`status != 2`), while storefront visibility requires exactly 1. Labels and filters are not universal domain enums.

## Authentication and authorization

`SecurityConfig` is stateless, disables CSRF, allows configured CORS origins and installs `JwtAuthenticationFilter`. It permits the whole `/api/storefront/**` family at filter level for mixed guest/customer use. Authentication and object authorization therefore have to be enforced by the relevant controller/service.

`JwtService` signs the subject/user ID and email claim with an HMAC key. The filter validates the token and looks up an undeleted user by email, then grants `ROLE_USER`. Admin capabilities are database-derived rather than encoded as all JWT authorities. Logout deletes the access-token row, but the filter does not consult that row; see F5.

Two admin authorization paths coexist:

- `AdminAccessService` checks global `model_has_roles` → `role_has_permissions`; global `super_admin` bypasses its permission check.
- `AdminUiSessionService` uses `AdminRoleScopeService` to union global roles/permissions with `user_store_roles` for the active store. Session returns user, roles, permissions, navigation, multistore state, manageable stores and active store.

A route protected by one helper is not equivalent to a route protected by the other. Navigation visibility is a projection, not enforcement. New permissions normally require seed/migration data, server enforcement, navigation/form changes if relevant, and a denied-access test.

Admin accounts are invite-only in the intended flow. Invite acceptance associates the assigned role/store; direct admin user creation is intentionally blocked. Customer registration is separate and currently marks email verified immediately. Inspect `AuthService` before changing registration, resets or token lifecycles.

`RootUserBootstrap` runs on startup for fixed user ID `11111111-1111-1111-1111-111111111111`. It normalizes the configured username, changes that row's email if needed, fails on conflict with another live account, and leaves its password unchanged. It does not create a missing root row. Use `rootUserUsername` in integration tests instead of assuming the default email.

## Multistore resolution and scoping

Multistore defaults off. `DefaultStoreResolver` prefers fixed ID `22222222-2222-2222-2222-222222222222`; the storefront resolver's default is the earliest undeleted store, so do not assume every resolver selects identically.

With multistore on, `StorefrontStoreResolver` resolves an explicit lowercased slug only to an active, undeleted store. With no slug, it chooses the default active store or first active store; `superstore` is true only when no active store can be selected. Despite historical names, omitting `store` is not an unconditional all-stores marketplace request.

`AdminStoreContext` considers `X-Admin-Store-Id` for users with global store-management access. Ordinary store admins resolve through assignments/user store/default; they do not gain switching rights just by sending a header. When multistore is off, `activeStoreOptional` returns empty and many UI services intentionally disable scope filtering.

Store scope is partial: products, attributes/options, delivery, discounts/vouchers, pages/menus, ratings/banners/notifications and orders/payments carry it; category/tag taxonomy and other records use different sharing/ownership rules. Variants inherit scope through products. Migrations V40–V46 and actual service queries are authoritative.

For a scoped feature, check lists, detail lookup, form options, parent/child attachment, update, delete, bulk operations, reporting and export. Referenced IDs must belong to the allowed store/owner as well as exist. Add a focused second-store and denied-role scenario where the behavior depends on scope; the baseline suite has no explicit multistore configuration coverage.
