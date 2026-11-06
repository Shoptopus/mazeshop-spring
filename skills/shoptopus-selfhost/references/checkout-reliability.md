# Checkout reliability — 6 November 2026

This revision supersedes baseline checkout/auth observations in commerce-and-access.md and findings F1–F5, the error-message portion of F6, the payment-label portion of F7, and query/production-exception portions of F10. Other findings still require assessment.

## Current contract

- React source is the sibling `shoptopus-spring-storefront`, origin `rolandtreiber/shoptopus-spring-storefront`. Rebuild using `scripts/build-storefront-static.sh`; preserve studio and brand assets. `static/brand/storefront-build.json` records the packaged frontend revision.
- Registration returns a signed-in, unverified account. A hashed, random, 24-hour email token is queued in `system_mail_outbox`; verification is idempotent and does not log the customer in. Verification does not block purchasing. Resend has a per-account cooldown and generic responses. JWT validation consults persisted sessions, and logout revokes them. Password recovery queues a real 60-minute single-use reset link, serializes token consumption, and revokes customer sessions on success.
- Guest contact is not an account identity. Existing-account email input must never grant addresses/account access. Registered carts require their owner. Guest cart merge cancels unpaid reservations first.
- Stripe is the only implemented storefront payment completion path. Browser-supplied confirmation cannot mark an order paid. Public payment config reports the actual key environment; never use live cards/funds for QA.
- Prepare locks/reserves stock and creates an order, items, payment, invoice and durable checkout attempt BEFORE returning a Stripe client secret. The attempt ID is the Stripe idempotency key. Unchanged requests reuse the pending attempt.
- Submit and signed webhook reconcile provider status, amount, currency and metadata against the recorded attempt. Duplicate and concurrent completion are serialized. A scheduled recovery task also settles successful payments after browser loss. Cancellation/expiry checks Stripe before releasing stock, including paid/cancel races.
- Paid completion queues receipts/admin notices in a retryable outbox after purchase state is durable. Mail provider failure must not undo an order. SMTP delivery cannot promise exactly once across process failure after send.
- Guest confirmation requires a scoped order access token (30 days); registered owner access also works. Do not log capability URLs. Invoice tokens remain separate and reusable.
- Card decline stays on payment for retry. Uncertain completion leads to the existing order status page, never an invitation to pay again. Google address lookup is optional; ordinary address fields must always be available. Sign-in/registration preserve a validated local return URL and guest basket.

## Deployment prerequisites

Configure at least one enabled delivery type with a matching enabled country rule. Enable Stripe with matching secret/publishable key environments. The webhook endpoint is `/api/storefront/checkout/stripe/webhook`; only this exact endpoint should bypass the private preview gate. Signature verification remains mandatory. Set provider `webhook_secret` for the selected test/live slot, or `APP_PAYMENT_STRIPE_WEBHOOK_SECRET`. Never store secrets in this document, source, command arguments or logs.

## Regression commands and evidence

Java 21 with Docker: `./mvnw -B -ntp clean verify` passed 354 tests with no failures/errors/skips. Reliability tests are `StorefrontAuthReliabilityApiTest`, `StorefrontStripeReliabilityApiTest`, `StripeWebhookSignatureTest`, and the updated completion/auth tests. These use mocked Stripe plus actual signature verification; they do not establish live Stripe/Gmail behavior.

Frontend: `node --test src/lib/checkout-reliability.test.ts` passed 5 tests; `npm run build` passed. The remaining lint findings predate this checkout work. Use real browser test-mode purchases and email receipt checks in addition to these regressions. Live QA results belong in `ops/checkout-qa-20261106.md`.
