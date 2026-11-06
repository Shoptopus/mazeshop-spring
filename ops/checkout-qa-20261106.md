# Authentication and checkout reliability audit — 6 November 2026

Fresh end-to-end audit of Mazeshop on the Raspberry Pi, using Stripe **test keys and test cards only**. The reported historical instance no longer exists; this audit cannot establish its original root cause. All timestamps below are UTC.

## Deployed implementation

Backend/application: `c7b22a93594865d48313eff3d08d22230c5bb35a`, signed release `pi-production-c7b22a93594865d48313eff3d08d22230c5bb35a`. [GitHub Actions run 42200958599](https://github.com/Shoptopus/mazeshop-spring/actions/runs/42200958599) succeeded before deployment. The Pi verified provenance, took its predeployment backup, installed the release and passed startup health checks at 10:21:59.

React source: `rolandtreiber/shoptopus-spring-storefront`, revision `b95917b0301c21490e685082d9ff707e33c6e0da`. Tracked assets were rebuilt from that checkout; `/brand/storefront-build.json` records the revision and Node version. CI packages these assets; it does not clone or rebuild the private frontend repository.

## Defects fixed

- Manual delivery-address entry is always available; Google suggestions are optional. Newly saved addresses stay selected, and the address editor closes after saving.
- Sign-in preserves the basket and checkout destination. Guest delivery details survive refresh in per-cart session storage. Card submission stays disabled until the secure form is ready, with a reload option for loading failures.
- Registration creates a signed-in, unverified account and sends a real confirmation email. Random, hashed, expiring verification tokens replace the previous immediate-verification behavior.
- Forgot-password now sends a real, single-use, 60-minute reset link. Reset serializes token consumption and revokes existing customer sessions. Logout invalidates the persisted session; separate logins have distinct session identifiers.
- Checkout creates a durable pending order, payment, invoice and stock reservation **before** exposing the Stripe client secret. An idempotency key and persistent checkout attempt prevent duplicate order creation on retries.
- Browser completion, signed webhooks and scheduled recovery reconcile the same attempt against trusted Stripe status, amount, currency and metadata. Client-supplied confirmation cannot mark an order paid. Successful payment can complete after the browser disappears.
- Known card/authentication failures allow correction on the payment form. An uncertain provider result goes to the existing order's status page, without inviting another payment. Confirmation distinguishes paid, unpaid and pending outcomes.
- Guest email is contact information, never account authentication. Existing-account emails cannot grant saved addresses or order history. Registered baskets require ownership; anonymous confirmations require an expiring order-specific capability.
- Stock reservations are serialized; unpaid cancellation/expiry checks Stripe before releasing stock, once. Purchased quantities are cleared without discarding unrelated basket additions.
- Paid completion queues customer receipts and admin notices in a retryable outbox. Mail failures cannot roll back the purchase. Production errors omit internal diagnostics, and application/Apache logs exclude capability query strings.
- Customer payment labels show Stripe rather than opaque payment-method IDs. The seeded store display name now shows Mazeshop.

## Validation

`./mvnw -B -ntp clean verify` on Java 21/PostgreSQL 16 Testcontainers passed **354 tests, zero failures/errors/skips**. The suite also passed with alphabetical class ordering. It covers email verification, single-use password reset/session revocation, real Stripe SDK webhook HMAC checks, provider mismatch/replay, concurrent last-item reservation, cancellation, browser-loss recovery, guest isolation and mail retry.

Frontend: **7 native Node tests passed**; TypeScript/Vite build passed. Whole-project ESLint remains at **15 errors and 1 warning**, in existing application/Google Maps code (the pre-audit App.tsx alone had 18 errors and 1 warning); the checkout helper and its tests pass ESLint. Lint is not being represented as clean. Vite also reports the existing bundle-size warning.

CI exposed shared fixture interference in the mail-outbox retry test. The fixture now drains earlier queued admin notices before simulating an outage and asserts successful second-attempt delivery. Failed CI runs were not deployed.

### Actual browser, Stripe and Gmail checks

| Scenario | Observed result |
| --- | --- |
| Fresh registration and email confirmation | Actual Gmail confirmation arrived; its link rendered Email confirmed. Account changed from unverified to verified. |
| Password recovery | Actual reset email/link arrived and rendered the form. API reset succeeded once; replay and old password were rejected; new password worked; the earlier session returned 401. Native browser login rejected the old password and accepted the new existing QA credential. |
| Guest basket → login → checkout | Basket and return destination survived login. No account addresses appeared from earlier guest purchases. |
| Guest checkout using an existing customer's email | Completed anonymously; order user remained null and it did not appear in that customer's history. Customer access without its guest capability was denied. |
| Delivery refresh | Guest name/email/address/note/delivery selection survived refresh. Manual address entry worked without Google lookup. |
| Insufficient funds | Stripe's test decline displayed a useful message, retained the basket/form, and left order/payment unpaid. |
| Cancelled 3DS challenge → retry | Public-domain checkout retained the same order/PaymentIntent. Cancel displayed an authentication error; retry and completed authentication produced one paid order and cleared the basket. |
| Registered checkout | Public-domain browser purchase succeeded with the reset QA credential, saved address, customization and selected binder variant. Clean confirmation URL remained readable by the owner. |
| Browser-loss recovery | Prepared in the storefront, then confirmed through Stripe TEST API without a storefront submit. A signed webhook/recovery completed the already recorded order. Real webhook HTTP 200 was observed. |
| Receipts/invoice | All three customer receipts were sent. Gmail showed distinct guest and registered receipts. The recovery email's order link opened the paid confirmation; its reusable PDF download returned HTTP 200 and a valid PDF twice. |
| Order/session boundaries | Anonymous registered-order access and customer access to a same-email guest order returned 403. Owner access returned 200. Logout revoked its JWT. |
| Final address/voucher regression | On the final deployed build, saving a second address selected it and closed the editor. An invalid voucher displayed Voucher code is invalid and kept the GBP 81.18 total. The unpaid regression basket was then removed. |

The original frontend automation session interfered with Stripe iframe loading in Chrome. Disconnecting the browser debugger and using native Chrome controls resolved it. Actual public guest/registered card entries and 3DS actions above used native Chrome. A temporary loopback Vite/SSH diagnostic setup was removed; it introduced no production CORS exception.

### Test purchase reconciliation and cleanup

| Order ID | Purchase | Stripe TEST total | Final state |
| --- | --- | --- | --- |
| `0cbea3b8-1cfa-4402-826c-0cd9847b0103` | Guest browser-loss recovery; Teal 4-pocket | GBP 71.18 | Fully refunded at Stripe; order cancelled; consumed product/variant stock restored |
| `a75b8fcd-2af4-4c6a-ba98-a2f880e5ddee` | Public guest; cancelled/retried 3DS; Green 4-pocket | GBP 71.18 | Fully refunded at Stripe; order cancelled; consumed product/variant stock restored |
| `f5a01e82-04d9-4861-9403-43bf839554fe` | Registered browser checkout; Blue 9-pocket | GBP 81.18 | Fully refunded at Stripe; order cancelled; consumed product/variant stock restored |
| `ce3ccfa0-d0a3-4b65-860f-5535255928e3` | Decline/failed-authentication diagnostic; Black 9-pocket | No capture | Expired/cancelled; reservation released |

Records remain as QA / DO NOT FULFIL audit evidence. Refunds were verified against Stripe charge objects, not just application labels. QA-only restocking was atomic and guarded by a persistent per-payment marker; all recorded product and variant quantities match the pre-QA baseline. This cleanup is not a claim that ordinary admin cancellation automatically restocks paid orders. The customer browser session was signed out and its final unpaid basket emptied. Eight confirmation/reset/receipt/admin-notice outbox messages were sent, with none pending/failed at cleanup.

## Configuration and remaining launch decisions

- Stripe is enabled in **TEST mode**, with all four configured keys confirmed to be test keys. The test webhook signing secret is root-only. Other payment providers remain disabled. No live-money payment, wallet, alternative provider or zero-total order is claimed tested.
- With the owner's explicit approval for the dedicated account, Stripe public trading/support details and statement descriptors were changed to Mazeshop. A subsequent actual 3DS challenge displayed **MAZESHOP**. Legal entity remains Fluentech LTD.
- UK standard delivery is provisionally **GBP 3.95**, country GB. Review postage and the earlier owner-authorized catalogue estimates before advertising.
- The preview gate remains. Only the exact privacy page and Stripe webhook bypass it. Unsigned webhook requests receive 400; the remaining anonymous shop/API is protected. Do not require preview authentication at Stripe's signed webhook route.
- For real sales, install matching **live** publishable/secret keys and a **live** webhook/signing secret for this endpoint. The current environment override is a TEST signing secret and must be replaced for live events. Complete one controlled live payment/refund and review receipt/fulfilment before advertising.
- Three successful TEST flows do not establish every bank, browser, wallet or production failure mode. Automated failure/replay/concurrency coverage complements, rather than replaces, these provider checks. SMTP/Gmail cannot guarantee exactly-once mail delivery after a process failure between sending and recording success.

Secrets, passwords, client secrets, reset tokens and order capability URLs are excluded from this report.

## Final operations and recovery verification

The post-cleanup encrypted backup completed locally and in S3. Both offsite database dumps were downloaded and restored into disposable databases, verified at **main V52 / logs V3**, then dropped; the running databases were not replaced. The reusable restore verifier now reads the current migration baseline rather than assuming V51.

A separate S3 read-back verified all three cancelled/refunded order records and their idempotent QA stock-restoration markers, the full recorded product/variant stock baseline, eight sent outbox records, the exact current runtime environment and Apache recovery copy, and a byte-for-byte JPEG sample. Live media count remains 402. The active Apache configuration was copied into its root-only recovery file before the final backup.

Final trusted-HTTPS smoke checks passed: anonymous access rejected, preview cookie and Bearer token coexist, admin login works, default administrator password fails, management/documentation routes are blocked, unsigned Stripe webhook returns 400 and privacy page returns 200. App, update timer and backup timer are active. Temporary diagnostic servers/tunnel/configuration were removed; the final application release remains `c7b22a9`.

The installed personal skill was refreshed from the repository reference. Application changes and rebuilt assets are committed in their respective repositories; this report/runbook follow-up does not require another application restart.
