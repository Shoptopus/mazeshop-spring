# Checkout reliability — 6 November 2026

This revision supersedes baseline checkout/auth observations in commerce-and-access.md and findings F1/F2/F4/F5, the cart/guest portions of F3, the error-message portion of F6, the payment-status portion of F7, the source-provenance portion of F9, and query/production-exception portions of F10. The F3 account-media upload observation is outside this checkout revision. Other findings still require assessment.

## Current contract

- React source is the sibling `shoptopus-spring-storefront`, origin `rolandtreiber/shoptopus-spring-storefront`. Rebuild using `scripts/build-storefront-static.sh`; preserve studio and brand assets. `static/brand/storefront-build.json` records the packaged frontend revision. The build script preserves this file rather than regenerating it: refresh its revision/Node fields from the source checkout and verify them before committing rebuilt assets. The 6 November deployed frontend is `b95917b0301c21490e685082d9ff707e33c6e0da`.
- Registration returns a signed-in, unverified account. A hashed, random, 24-hour email token is queued in `system_mail_outbox`; verification is idempotent and does not log the customer in. Verification does not block purchasing. Resend has a per-account cooldown and generic responses. JWT validation consults persisted sessions, and logout revokes them. Password recovery queues a real 60-minute single-use reset link, serializes token consumption, and revokes customer sessions on success.
- Guest contact is not an account identity. Existing-account email input must never grant addresses/account access. Registered carts require their owner. Guest cart merge cancels unpaid reservations first.
- Stripe is the only implemented storefront payment completion path. Browser-supplied confirmation cannot mark an order paid. Public payment config reports the actual key environment; never use live cards/funds for QA.
- Prepare locks/reserves stock and creates an order, items, payment, invoice and durable checkout attempt BEFORE returning a Stripe client secret. The attempt ID is the Stripe idempotency key. Unchanged requests reuse the pending attempt.
- Submit and signed webhook reconcile provider status, amount, currency and metadata against the recorded attempt. Duplicate and concurrent completion are serialized. A scheduled recovery task also settles successful payments after browser loss. Cancellation/expiry checks Stripe before releasing stock, including paid/cancel races.
- Paid completion queues receipts/admin notices in a retryable outbox after purchase state is durable. Mail provider failure must not undo an order. SMTP delivery cannot promise exactly once across process failure after send.
- Guest confirmation requires a scoped order access token (30 days); registered owner access also works. Do not log capability URLs. Invoice tokens remain separate and reusable.
- Card decline and known failed/cancelled bank authentication stay on payment for retry. Only trusted `requires_payment_method` / `requires_confirmation` outcomes permit a fresh payment attempt; processing/unknown outcomes must keep checking the existing order. Disable payment until Stripe onReady and provide a retry for load failure. Keep whitelisted guest delivery drafts per cart in sessionStorage; never store card fields or passwords there. Update the address query cache before selecting a newly saved address to avoid falling back to an older address.
- Google address lookup is optional; ordinary address fields must always be available. Sign-in/registration preserve a validated local return URL and guest basket.

## Deployment prerequisites

Configure at least one enabled delivery type with a matching enabled country rule. Enable Stripe with matching secret/publishable key environments. The webhook endpoint is `/api/storefront/checkout/stripe/webhook`; only this exact endpoint should bypass the private preview gate. Signature verification remains mandatory. Set provider `webhook_secret` for the selected test/live slot, or `APP_PAYMENT_STRIPE_WEBHOOK_SECRET`. Never store secrets in this document, source, command arguments or logs.

## Regression commands and evidence

Java 21 with Docker: `./mvnw -B -ntp clean verify` passed 354 tests with no failures/errors/skips. Reliability tests are `StorefrontAuthReliabilityApiTest`, `StorefrontStripeReliabilityApiTest`, `StripeWebhookSignatureTest`, and the updated completion/auth tests. These use mocked Stripe plus actual signature verification; they do not establish live Stripe/Gmail behavior.

Frontend: `node --test src/lib/checkout-reliability.test.ts` passed 7 tests; `npm run build` passed. The remaining lint findings predate this checkout work. Use real browser test-mode purchases and email receipt checks in addition to these regressions. Live QA results belong in `ops/checkout-qa-20261106.md`.

## Actual provider QA and operational boundary

On 6 November, signed application release `c7b22a93594865d48313eff3d08d22230c5bb35a` passed CI and was installed through the Pi verifier. Actual public-domain guest and registered purchases, 3DS cancellation/retry, a deliberate insufficient-funds decline, API-confirmed browser-loss recovery, real Gmail confirmation/reset/receipts and owner/guest isolation were exercised. All three captured test charges were refunded; fulfilment was cancelled and only the QA-consumed stock was restored. Detailed evidence and explicit limits are in `ops/checkout-qa-20261106.md`.

The Pi retains its preview gate, with only the exact privacy route and signed Stripe webhook open. Capability URLs must not enter Apache access/referrer logs. The Pi currently uses TEST keys and a TEST webhook secret override: live activation needs matching live keys AND a live endpoint/signing secret. UK GBP 3.95 delivery is a provisional business setting, not a generic application default. Consult current runtime settings before reusing these observations.

When a Chrome automation debugger prevents Stripe frames loading, detach it and use native Chrome controls for the real test-card challenge. Do not replace production PaymentElement or relax production CORS to work around that diagnostic limitation. Stop local diagnostic servers/tunnels afterwards. Before checkout deployment, allow in-progress payments to settle; check the exact commit's successful CI and signed release, then verify the installed marker and restore any temporarily stopped update timer.
