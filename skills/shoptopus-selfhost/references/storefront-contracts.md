# React storefront contracts

## What was actually inspected

The selfhost checkout contains compiled React storefront and admin assets, not their editable React source trees, dependency lockfiles, or frontend tests. The scripts expect `../shoptopus-spring-storefront` and `../shoptopus-spring-admin`, with directory overrides described in development. Those checkouts were absent during this analysis, and the connected GitHub lookup did not expose the storefront repository.

Evidence comes from the backend controllers/services/tests and the assets referenced by `src/main/resources/static/index.html` and `static/studio/index.html`. At the baseline, the storefront entry was `assets/index-b55v2GZe.js`. Hashed filenames will change; always resolve through `index.html`.

The shipped storefront bundle identifies React/ReactDOM 19.2.5 and contains React Router, Zustand persistence, query caching, Zod validation, and Stripe Elements code. These observations do not establish the full source dependency versions or supported Node tooling. Node 20 and npm are the build scripts' expectations.

## Client transport and persistence

The storefront's shared fetch helper calls relative same-origin `/api/...` URLs, parses JSON, requires a top-level `data` key, and returns that value. It does not retain the outer `message` or pagination fields. JSON requests set content type; protected calls explicitly attach `Authorization: Bearer ...`. Uploads use `FormData` and the field `file`.

The shipped helper reads an error's `message`, whereas the backend's normal errors use `user_message` and `developer_message`. This causes generic error text for many validation failures; see findings before changing either side.

Zustand persists token/user under `shoptopus-storefront-auth` and cart ID under `shoptopus-storefront-cart` in local storage. Theme choice uses `shoptopus-storefront-mode`. Query keys include cart and cart-shell data, which are invalidated after cart mutations. Account logout and guest-cart resolution are coupled to these persisted states; changing the API alone is insufficient for a complete login/cart behavior change.

The admin bundle separately stores `shoptopus_admin_token` and `shoptopus_admin_active_store`, attaches `X-Admin-Store-Id`, and prefers backend `user_message` when handling errors. Do not share storage formats or fetch assumptions between clients without inspecting their source.

## Browser routes and store namespace

| UI | Shipped routes |
| --- | --- |
| Home, listing, product, page | `/`, `/shop`, `/product/:slug`, `/page/:slug` |
| Store versions | `/:storeSlug`, `/:storeSlug/shop`, `/:storeSlug/product/:slug`, `/:storeSlug/page/:slug` |
| Auth | `/login`, `/register`, `/forgot-password`, `/reset-password`, `/auth/social/callback` |
| Commerce | `/cart`, `/checkout`, `/:storeSlug/checkout`, `/order/:orderId/confirmation` |
| Account | `/account`, `/account/notifications`, `/account/messages`, `/account/messages/:threadId`, `/account/orders/:orderId` |
| Seller application | `/create-store` |

The client infers a store from the first path segment, excluding reserved segments such as `shop`, `product`, `page`, `auth`, `cart`, `checkout`, `account`, `order`, and `create-store`. Store onboarding separately validates reserved slugs. Keep these two lists compatible when adding top-level routes.

Catalog/config/page GETs use `?store=<slug>`. Checkout POSTs use `store_slug` in JSON; checkout summary uses `?store=<slug>`. Do not substitute the admin store UUID header for storefront routing.

## Response conventions

Most successful controllers wrap results in `ApiResponse`:

```json
{"message":"Storefront shop","data":{"items":[],"meta":{"page":1,"per_page":12,"total":0,"total_pages":1}}}
```

The generic wrapper can also contain outer `records`, `total_records`, and `next`. Null wrapper properties are omitted by `@JsonInclude`. Inner maps can retain nulls. `/shop` pagination is nested in `data.meta`; account lists/pages often use the outer paged wrapper. Preserve the particular endpoint's shape instead of normalizing them globally.

Typical backend errors have this shape, without a `data` wrapper:

```json
{"user_message":"Validation failed","developer_message":"Cart id is required."}
```

Field errors may add an `errors` map; unauthenticated responses use error code `1101`, and legacy admin authorization uses `1403`. `ApiException` and `GlobalExceptionHandler` are the central implementation. Unexpected exceptions currently expose detailed developer messages; see findings.

## API map

All routes below start with `/api/storefront` unless explicitly stated otherwise. Treat the access column as the intended use; filter-level `permitAll` means service/controller checks must still be inspected.

| Method and suffix | Contract / source |
| --- | --- |
| `GET /config`, `GET /home` | Bootstrap, fetched concurrently by the client. Config has `auth`, `i18n`, `media`, `integrations`, `commerce`, `shop`; home has banners, featured products, page/menu/footer data and home page |
| `GET /pages`, `/pages/home`, `/pages/{slug}` | Localized content; optional `store`, list accepts `menu_only` |
| `GET /shop/filters`, `GET /shop` | Storefront shop facade; listing includes `items`, `meta`, `filters`, `active_filters`, selected category/parent |
| `GET /products/{slug}`, `/products/{slug}/related` | Detailed product/variant/media projection; related accepts `limit` |
| `GET /ratings`, `POST /banners/{bannerId}/click` | Public rating feed and click recording |
| `POST /auth/register`, `/auth/login` | `data.auth` has authenticated flag, token, token_type and user; DTO aliases define accepted names |
| `GET /auth/me`, `POST /auth/logout` | Me requires a resolved authenticated user; logout removes the database token but JWT revocation has a known gap |
| `POST /auth/forgot-password`, `/auth/reset-password` | Reset flow; see `AuthService` and dedicated auth/reset tests |
| `GET /auth/social-providers`, `/auth/social/{provider}` | Public provider descriptors and authorization link |
| `GET /auth/social/{provider}/authorize`, `/callback`, `/auth/social/complete?token=...` | OAuth redirect/state/handoff sequence; inspect `SocialLoginService` |
| `GET /cart/{cartId}`, `POST /cart/resolve` | Persisted cart lookup or guest-to-user merge; ownership caveats are in findings |
| `POST /cart/items`, `PATCH /cart/items/{lineId}`, `DELETE /cart/items/{lineId}` | Mutations return the full recalculated cart |
| `GET /checkout/{cartId}` | `cart`, `cart_group`, `selected_store`, owned addresses, enabled payment providers |
| `POST /checkout/delivery-options`, `/voucher`, `/payment-intent` | Recalculate valid options, voucher and Stripe intent from server-side data |
| `POST /checkout/submit`, `/confirm` | Create order/payment/invoice; provider confirmation has material access/verification limitations |
| `GET /orders/{orderId}/confirmation` | Public order summary with address/payment data at baseline; see findings |
| `GET /invoice/{token}` | PDF response, not a JSON wrapper; token is reusable until expiry |
| `/account/profile`, `/account/addresses`, `/account/orders` | Profile, owned addresses/orders and detail/mutation routes; see `StorefrontAccountController` |
| `/account/orders/{orderId}/message-thread`, `/account/messages/**` | Order conversations and message image upload |
| `/account/orders/{orderId}/ratings/order`, `/ratings/products/{productId}` | Order-linked customer ratings; media at `/account/ratings/media/images` and `/videos` |
| `/account/notifications`, `/account/notifications/clear` | Account notification listing/clearing |
| `POST /stores`, `/stores/logo` | Multistore seller onboarding and logo upload |
| `POST /api/product/{id}/favorite` | The React client still calls this older public endpoint for favorites |

For exact verbs and child paths not expanded above, inspect the controller before implementing a client. This is a navigation map, not a generated exhaustive API specification.

## Catalog details that affect UI work

`StorefrontShopService` exposes products only when `status == 1` and not deleted. Categories must be enabled, not deleted, and have a visible assigned product; parent visibility and descendant expansion matter. Tags come from visible products. Attributes must be enabled and `show_in_filters=true` for the filter UI.

Listing parameters are `search`, `category_ids`, `tag_ids`, `attribute_option_ids`, `sort`, `page`, `per_page`, `store`. The backend CSV-parses ID strings. The shipped bundle appends repeated parameters for multi-selection; verify actual multi-select requests rather than assuming all values survive scalar binding.

Page defaults to 1; page size defaults to 12 and caps at 48. Sorts are `newest_added`, `price_low_to_high`, `price_high_to_low`, `top_rated`, `most_popular`, `name_alphabetical`. Unknown sort falls back to newest.

Product detail includes variant combinations, option availability, pricing, related products, content and managed media. Variant `attribute_options` can be a map or array and may contain UUID/slug/value references; several projections normalize it. Changing this representation requires checking shop, cart and order-confirmation mappings together.

Media payloads include paths/URLs, type, ordering and, where present, width/height. Preserve dimensions introduced in V51 so the UI can reserve image space. Fallback legacy cover-photo JSON still exists alongside managed attachments.

## Cart and checkout payload examples

Cart addition uses a product UUID, not its browser slug. `cart_id`, variant and customization are optional when applicable:

```json
{"product_id":"<uuid>","quantity":1,"cart_id":"<uuid>","product_variant_id":"<uuid>","customization_value":"Initials"}
```

An authenticated checkout normally uses an owned `address_id`; a guest supplies `contact` and `address`. Required address fields are `address_line_1`, `town`, and `post_code`; country defaults to `UK`. A Stripe submit carries a successful intent ID:

```json
{"cart_id":"<uuid>","store_slug":"example-shop","delivery_type_id":"<uuid>","payment_provider":"stripe","payment_intent_id":"pi_example","contact":{"email":"buyer@example.com","first_name":"Buyer","last_name":"Example"},"address":{"address_line_1":"1 Example Road","town":"London","post_code":"E1 1AA","country":"UK"}}
```

The browser should use server-provided totals and allowed delivery choices. Do not use formatted `GBP ...` strings for arithmetic. Commerce state, safety gaps and the meaning of confirmation are explained in the commerce and findings references.

## Mazeshop content layer (November 2026)

The Mazeshop deployment keeps editable styles, fonts and native SVG branding under `src/main/resources/static/brand/`; `scripts/apply-storefront-branding.mjs` adds the stylesheet and icons after a storefront rebuild. Do not patch minified React bundles. The separate `laser-workshop` source establishes its visual identity. CMS templates, sourced product definitions and the resumable loopback admin importer live under `ops/mazeshop/`. Runtime merchant edits remain in PostgreSQL. Refer to that directory's README before reapplying a manifest.

Managed media has one attachment owner per file: reusing one uploaded file ID in two products transfers it to the last product. Upload a separate attachment for each owner even when the bytes match. Attribute option creation under `/api/admin-ui/attributes/{id}/options` returns the parent attribute detail, not the newly created option. Resolve the option by slug from `options` before mapping variant attribute IDs to option IDs. Verify `variant_attributes` and `variant_combinations` from the public product endpoint, rather than relying on variant counts alone.
