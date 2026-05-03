ALTER TABLE products
    ADD COLUMN IF NOT EXISTS favourites BIGINT NOT NULL DEFAULT 0;

UPDATE products p
SET favourites = counts.favourites
FROM (
    SELECT product_id, COUNT(*)::bigint AS favourites
    FROM favorited_products
    GROUP BY product_id
) counts
WHERE counts.product_id = p.id;

UPDATE products
SET favourites = 0
WHERE favourites IS NULL;
