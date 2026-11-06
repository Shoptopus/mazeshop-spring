# Raspberry Pi hosting

The shop runs as an unprivileged `shoptopus` systemd service. Apache terminates HTTPS and proxies to `127.0.0.1:18080`. PostgreSQL 16 hosts separate `shoptopus` and `shoptopus_logs` databases, listening only on loopback. Media is stored in `/var/lib/shoptopus/media`; releases and the dedicated Java 21 ARM64 runtime are in `/opt/shoptopus`.

The installed service/timer definitions are tracked in `systemd/`; environment files and web-server credentials are deliberately excluded. After reviewing any operational script or unit change, install it on the Pi explicitly and run `systemctl daemon-reload` for unit changes. The GitHub release workflow deploys the JAR only; it does not install root-owned infrastructure scripts.

## Deployment

`.github/workflows/pi-release.yml` tests and packages the backend with its **tracked, prebuilt React assets** on GitHub's Ubuntu runner. It does not rebuild the separate frontend repositories. It publishes a complete release named `pi-production-<commit>` with the JAR, SHA-256 checksum and signed provenance bundle. All actions are pinned to upstream commits.

`shoptopus-update.timer` checks public releases every five minutes. Before executing a JAR as the application user, the updater verifies its provenance against the exact repository, workflow, `main` branch and source commit, and rejects builds from self-hosted runners. Verification failure leaves the current app running. No inbound deployment port, GitHub token, SSH deployment key or self-hosted GitHub runner is required on the Pi.

The deployment source is the public repository `Shoptopus/mazeshop-spring`. Push/merge future application changes to its `main` branch to trigger the pipeline. The original `Shoptopus/shoptopus-selfhost` repository remains private and is not polled. A GitHub build must succeed and publish a signed release before the timer can install it; inspect the latest Actions run and updater journal to verify the full path. The initial installation was deployed locally after all 324 tests passed. The workflow has been validated with actionlint; the first real signed GitHub release is used for the final integration check.

Protect `main`, require review for changes to the workflow and restrict repository write access. An attacker who can change the trusted workflow on `main` can publish a trusted application build. Update the verifier deliberately if the repository, branch or workflow is renamed. The verification CLI and Java runtime are root-owned; update them using verified official releases.

During deployment, Apache serves maintenance responses, a complete backup is taken, and the JAR symlink is replaced atomically. Startup is checked using `/api/storefront/config`, which exercises the application's configuration and database. Failed startup restores the prior code and records `failed-release` to avoid retrying a broken release every five minutes. Database migrations are **not undone automatically**: incompatible migrations require restoring the matching backup. Test destructive migrations and plan that recovery before release.

## Secrets and preview access

`/etc/shoptopus` contains root-only runtime secrets, generated administrator credentials and backup configuration. Keep secrets out of Git, Actions logs and release assets. GitHub needs no production secrets for this deployment design. The Pi has a separate AWS identity scoped to the dedicated S3 backup repository; it does not have the administrator's AWS credentials.

The storefront and `/studio` are protected by a temporary Apache preview login. A Secure, HttpOnly cookie allows authenticated frontend requests to carry the app's Bearer token without conflicting with Basic authentication. Changing the preview password alone does not revoke existing preview cookies: rotate the random cookie secret in the root-only Apache gate configuration as well, then reload Apache.

As of 6 November 2026, Gmail is connected and Stripe is enabled with TEST keys; other payment providers remain disabled. The authentication/checkout reliability revision and actual provider QA are recorded in [the checkout report](../checkout-qa-20261106.md). Keep the preview gate until live keys, a matching live webhook/signing secret and commercial delivery settings are reviewed. The current webhook-secret environment override is for TEST events. Never expose the seeded `password123` administrator password; the initial installation replaces it before enabling the proxy.

## Public privacy notice

`/privacy-policy` is a standalone HTML page packaged from `src/main/resources/legal/privacy-policy.html` and served by `PrivacyPolicyController`. It loads no SPA assets or third-party scripts. The Pi Apache site grants anonymous access to the exact `/privacy-policy` route (optional trailing slash) and exact `/api/storefront/checkout/stripe/webhook` route. The webhook requires a valid Stripe signature; unsigned requests receive 400. The preview gate protects the rest of the application. Access logging omits request targets and referrers so capability query values are not recorded. Keep that exception specific when changing proxy configuration. Use `https://shop.mazeshop.co.uk/privacy-policy` in Google OAuth branding.

The notice identifies Fluentech LTD (14380150) using its Companies House registered office and `mazeshopsales@gmail.com` as the privacy contact. Review its data uses and retention commitments whenever providers, marketing, storage or operating practices change. It is a privacy notice, not a returns or warranty policy.

## Gmail mail delivery

Set `APP_API_PUBLIC_BASE_URL` to the public HTTPS shop origin before connecting Gmail. The OAuth callback is `/api/admin-ui/shop-settings/email/gmail/callback`; do not authorize a localhost callback for the Pi. Set the sender to the connected Gmail account and the display name to the shop name.

Use a dedicated Google OAuth web client, enable Gmail API, and authorize only Gmail send plus account identity. Store the client secret and refresh token through the protected runtime settings; keep credential downloads and token-bearing URLs out of Git. Mail settings and Gmail OAuth state values are redacted in entity audit snapshots. Runtime secrets remain in the database and encrypted backups.

Google OAuth projects in Testing issue Gmail refresh tokens that expire after seven days. Complete Google's required branding/privacy information and use the appropriate production status for a durable connection. The Gmail identity is the shop's sender account; customers do not need to grant Gmail permissions. Verify an actual test message and token refresh after setup. The test endpoint commits settings before delivery so Gmail refresh can persist a new token in its separate transaction; do not wrap that orchestration in a main-database transaction.

## Backups and recovery

`shoptopus-backup.timer` runs nightly at 02:45 with up to ten minutes of jitter. The job briefly stops only this app, dumps **both databases** with PostgreSQL 16 tools, and backs up the dumps, media and `/etc/shoptopus` into an encrypted local restic repository. It resumes the app before copying the consistent snapshot to S3. Expect a brief interruption while the app restarts.

The application's original scheduled snapshot/log-purge job is disabled with `APP_SNAPSHOT_MAINTENANCE_ENABLED=false`; its default behavior is unchanged for other installations. It must stay disabled here so the external backup retains audit logs.

Local and offsite retention keeps seven daily, four weekly and three monthly snapshots. Weekly integrity checks read a sample of repository data and prune unused data. S3 is private, encrypted, versioned and requires TLS. The backup identity cannot permanently delete object versions; noncurrent versions expire after 30 days to bound storage costs. Recovery of maliciously deleted versions requires the AWS owner identity. Versioning is a recovery window, not an immutable archive.

Store an offline copy of the restic recovery password separately from the Pi. A copy only on the Pi cannot recover a lost Pi. S3 and restic both require usable credentials at recovery time. Rotate the dedicated AWS access key periodically; no console login is needed for that identity.

Routine checks:

```sh
sudo systemctl status shoptopus shoptopus-firewall apache2
sudo systemctl list-timers 'shoptopus*'
sudo journalctl -u shoptopus -n 80 --no-pager
sudo journalctl -u shoptopus-backup -n 40 --no-pager
sudo journalctl -u shoptopus-update -n 40 --no-pager
sudo systemctl start shoptopus-backup.service
sudo systemctl start shoptopus-backup-check.service
```

For restic operations, use a root shell, export `/etc/shoptopus/restic.env`, and additionally `/etc/shoptopus/s3.env` for the offsite repository. Avoid printing those files. Restore to a separate directory, create disposable databases owned by the corresponding app roles, and feed each dump to `pg_restore --exit-on-error --no-owner --no-acl --role <owner>`. Verify both migration histories (`flyway_schema_history_main` and `flyway_schema_history_logs`) and application data before replacing a live database. `verify-restore.py` implements this safe offsite database check, comparing the restored migration versions with the running databases. Run it after a fresh backup while no deployment/migration is in progress.

## Host security and operations

SSH is key-only and LAN-only. Public ingress is HTTP/HTTPS; the independent nftables guard also filters Docker forwarding on IPv4 and IPv6 without editing Docker-managed rules. LAN services remain accessible. Inspect the firewall after changes to network interfaces or LAN prefixes. Use a DHCP reservation for the Pi's LAN address.

The app has a 768 MiB Java heap, 1.5 GiB systemd memory cap and two-core CPU quota. Memory cgroups must actually be enabled at boot; verify `MemoryCurrent` reports a number. The app has filesystem protections, no elevated capabilities and write access limited to its state directory. Keep native media tools (`ffmpeg`, PostgreSQL 16 client) installed.

Certbot renews the shop certificate through its HTTP webroot and validates/reloads Apache afterwards. Do not close public port 80 while using HTTP-01 validation. If the home router lacks NAT loopback, test HTTPS from the LAN with local DNS or `curl --resolve shop.mazeshop.co.uk:443:192.168.0.43 https://shop.mazeshop.co.uk/`; use mobile data to test the public URL.

Security upgrades are enabled without automatic reboot on this shared host. Schedule maintenance for OS updates and reboots, and check all existing sites afterwards. Existing encrypted TLS keys on unrelated sites need their own secure startup mechanism; do not remove encryption from those keys as part of routine shop deployment. The host-specific audit and recovery paths belong in private operational records, outside this public repository.

Use wired Ethernet and reliable power for a live shop. The current home-server design has no redundancy; outages of power, broadband or this Pi interrupt the shop. Monitor backup/service failures in the journal and configure an alert destination before relying on unattended operation.

## Mazeshop appearance and catalogue

The brochure reference is the separate `laser-workshop` repository. The shop uses its charcoal/lime palette, self-hosted DM Sans and Manrope fonts, and maze geometry. Editable shop styles and licensed fonts live in `src/main/resources/static/brand/`; `scripts/apply-storefront-branding.mjs` reapplies the stylesheet link after a React build. The build script preserves that directory. Configure colours, contact details, social links, pages and products through the admin APIs; preserve unrelated Gmail/auth/payment sections when updating shop settings.

Product photographs come from the owner's mounted SMB share, `Data/Photos/Mazeshop/products`. Keep source originals unchanged, remove location metadata from web copies and attach at most five varied images per product. Retain prices traced to matching MazeshopUK Etsy listings. On 5 November 2026 the owner explicitly requested best guesses and activation of all products: the current manifest therefore records 99 owner-authorised estimated prices, indicative specifications and provisional made-to-order capacity. Keep estimates distinguishable from external observations and physical inventory counts; consult ops/mazeshop/README.md before reapplying defaults. The unrelated Etsy example is a configuration reference and does not establish Mazeshop's brand, colours, capacities or pricing.
