# Mazeshop content and catalogue

The source catalogue contains 103 distinct product listings grouped from the owner's `Data/Photos/Mazeshop/products` SMB share. `assets-manifest.json` maps each selected web image to its original relative filename, dimensions and SHA-256. The 391 prepared JPEG copies preserve the composition, use a maximum 1800-pixel edge and omit source EXIF/location metadata. Originals remain unchanged. Photographs are uploaded through the application's managed-media API and attached in order, with at most five images per product. Repeated lighting colours are examples of the same lamp rather than duplicate listings.

The brochure's charcoal/lime palette, fonts and native maze geometry come from the separate `laser-workshop` repository. Editable application brand styles live in `src/main/resources/static/brand/`, with font licences beside the font files. The storefront build preserves that directory and runs `scripts/apply-storefront-branding.mjs` after generating its HTML. Runtime theme settings, social links and CMS pages are applied through admin APIs. The older Next.js storefront checkout on the SMB share does not correspond to the deployed React bundle.

`home-top.html`, `home-bottom.html` and `about.html` are the initial CMS content sources. Photo placeholders resolve to managed-media URLs. Native product-collection blocks supply current product prices and links. The About narrative is based on MazeshopUK's own workshop story; public Facebook details supply the business phone, email, Instagram and Pinterest links. Do not introduce unsupported delivery promises, reviews, warranties or product specifications.

## Prices and availability

Only four matched historical MazeshopUK Etsy prices were available on 5 November 2026: Pokémon-inspired binder £67.23, acrylic sphere lamp £22.00, Tree of Life bamboo plaque £26.95 and portrait string-art kit £44.90. Source URLs are recorded per product. Those Etsy pages all say the items are unavailable, so their shop stock starts at zero. The remaining 99 products are unpriced drafts. No competing seller's price is used. Confirm availability, dimensions, inclusions and any option-specific prices in the admin before enabling orders.

The binder has exactly 19 chart-backed combinations: eight colours for the 4-pocket format and eleven for the 9-pocket format. The Card Guardian charts do not establish capacities or permit adopting Vault X claims from the inspiration listing. The attribute option creation API returns the parent attribute detail; resolve option IDs from its `options` array. The importer repairs only mismatched variant option references on reruns, preserving stock and price edits. Store the requested engraving/name in the product personalisation field. The separate Digimon examples are a draft custom-request listing; do not silently substitute their photographed Vault X branding into the Card Guardian options.

## Apply and resume

Stage these files and the prepared `assets/` directory together on the Pi. Validate without changing anything:

```sh
python3 sync_catalogue.py --directory /path/to/staged-files
```

Apply using the Pi's root-only local application credentials:

```sh
sudo python3 sync_catalogue.py --directory /path/to/staged-files --apply
```

Take a backup first and avoid application deployment during import. The script makes authenticated calls only to loopback and never prints tokens or passwords. Its resumable state is `/etc/shoptopus/mazeshop-catalogue-state.json`, included in encrypted backups. It deduplicates uploads by content hash, with separate managed attachments when one photo is used by multiple products, and finds entities by slug and records each completed product's manifest signature. Identical reruns skip products; changes to a managed product's manifest deliberately reapply its catalogue fields. Do not rerun an old manifest to overwrite later merchant edits. Gmail, social-login configuration and payment-provider settings are preserved. Image binaries and runtime credentials do not belong in Git.
