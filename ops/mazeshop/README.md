# Mazeshop content and catalogue

The source catalogue contains 103 distinct product listings grouped from the owner's `Data/Photos/Mazeshop/products` SMB share. `assets-manifest.json` maps each selected web image to its original relative filename, dimensions and SHA-256. The 391 prepared JPEG copies preserve the composition, use a maximum 1800-pixel edge and omit source EXIF/location metadata. Originals remain unchanged. Photographs are uploaded through the application's managed-media API and attached in order, with at most five images per product. Repeated lighting colours are examples of the same lamp rather than duplicate listings.

The brochure's charcoal/lime palette, fonts and native maze geometry come from the separate `laser-workshop` repository. Editable application brand styles live in `src/main/resources/static/brand/`, with font licences beside the font files. The storefront build preserves that directory and runs `scripts/apply-storefront-branding.mjs` after generating its HTML. Runtime theme settings, social links and CMS pages are applied through admin APIs. The older Next.js storefront checkout on the SMB share does not correspond to the deployed React bundle.

`home-top.html`, `home-bottom.html` and `about.html` are the initial CMS content sources. Photo placeholders resolve to managed-media URLs. Native product-collection blocks supply current product prices and links. The About narrative is based on MazeshopUK's own workshop story; public Facebook details supply the business phone, email, Instagram and Pinterest links. Do not introduce unsupported delivery promises, reviews, warranties or product specifications.

## Prices and availability

The owner requested all listings be enabled using best guesses on 5 November 2026. All 103 products now have a positive GBP price, made-to-order specifications and provisional capacity. Four matching historical MazeshopUK Etsy prices are retained: Pokémon-inspired binder £67.23, acrylic sphere lamp £22.00, Tree of Life bamboo plaque £26.95 and portrait string-art kit £44.90. Source URLs remain per product. The other 99 prices are explicitly marked `owner_authorized_estimate`; they are working estimates from the material, supplied quantity and design/production effort, not claimed Etsy observations. The Digimon binder is offered as a standard 9-pocket Card Guardian custom binder at £77.23.

`commercial_defaults` records the authority and shared assumptions. Each product's `specification` defines an indicative finished size, material, supplied quantity, included/excluded accessories, care and an estimated made-to-order dispatch window. The descriptions render the useful customer-facing details. Dimensions are design estimates, not measured finished inventory. Package weights are estimated in grams. Ordinary products have provisional capacity of five; the Pokémon binder has one slot per valid combination (19 total). These values represent assumed production capacity, not a count of finished stock. `catalogue-review.csv` records every price, quantity and assumption for review. Earlier questions are retained in `previous_review_notes` as history, not unresolved activation blockers.

The Pokémon binder has exactly 19 chart-backed combinations: eight colours for 4-pocket and eleven for 9-pocket. Its 4-pocket price is £67.23; the 9-pocket price is an owner-authorised estimate of £77.23. Manufacturer specifications establish [160-card 4-pocket](https://wearecardguardian.com/collections/frontpage/products/card-guardian-premium-4-pocket-card-binder-160-side-loading-pocket-with-zipper-black) and [360-card 9-pocket](https://wearecardguardian.com/products/premium-9-pocket-trading-card-binder-black) standard binders. The colour choices still follow the owner's charts. Do not confuse standard-card binders with toploader models or substitute Vault X specifications from photographed examples. Store the requested engraving/name in the product personalisation field.

Attribute option creation returns the parent attribute detail; resolve option IDs from its `options` array. Variant manifest signatures are saved separately: a changed manifest intentionally applies its stock/price defaults once, while identical reruns preserve later merchant stock/price edits. Mismatched attribute-option references can still be repaired independently.

## Apply and resume

Stage these files and the prepared `assets/` directory together on the Pi. Validate without changing anything:

```sh
python3 sync_catalogue.py --directory /path/to/staged-files
```

Apply using the Pi's root-only local application credentials:

```sh
sudo python3 sync_catalogue.py --directory /path/to/staged-files --catalogue-only --apply
```

Use `--catalogue-only` for product changes so existing shop settings and CMS pages remain untouched; omit it only when deliberately reapplying the full initial content setup. The home product collection uses the explicit four `featured_products` slugs, so enabling the complete catalogue does not expand that block. Take a backup first and avoid application deployment during import. The script makes authenticated calls only to loopback and never prints tokens or passwords. Its resumable state is `/etc/shoptopus/mazeshop-catalogue-state.json`, included in encrypted backups. It deduplicates uploads by content hash, with separate managed attachments when one photo is used by multiple products, and finds entities by slug and records each completed product's manifest signature. Identical reruns skip products; changes to a managed product's manifest deliberately reapply its catalogue fields. Do not rerun an old manifest to overwrite later merchant edits. Gmail, social-login configuration and payment-provider settings are preserved. Image binaries and runtime credentials do not belong in Git.
