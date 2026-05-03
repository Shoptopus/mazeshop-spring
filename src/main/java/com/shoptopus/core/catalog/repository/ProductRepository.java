package com.shoptopus.core.catalog.repository;

import com.shoptopus.core.catalog.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query(value = """
            select p.* from products p
            where p.deleted_at is null
            and (:categoryId is null or exists (
                select 1 from product_product_category ppc
                where ppc.product_id = p.id and ppc.product_category_id = cast(:categoryId as uuid)
            ))
            and (:minPrice is null or p.price >= :minPrice)
            and (:maxPrice is null or p.price <= :maxPrice)
            and (
                :search is null
                or similarity(coalesce(p.name->>:lang, p.name->>'en', ''), :search) > 0.2
                or coalesce(p.name->>:lang, p.name->>'en', '') ilike concat('%', :search, '%')
            )
            order by
                case when :search is not null then similarity(coalesce(p.name->>:lang, p.name->>'en', ''), :search) else 0 end desc,
                p.created_at desc
            """,
            countQuery = """
                    select count(*) from products p
                    where p.deleted_at is null
                    and (:categoryId is null or exists (
                        select 1 from product_product_category ppc
                        where ppc.product_id = p.id and ppc.product_category_id = cast(:categoryId as uuid)
                    ))
                    and (:minPrice is null or p.price >= :minPrice)
                    and (:maxPrice is null or p.price <= :maxPrice)
                    and (
                        :search is null
                        or similarity(coalesce(p.name->>:lang, p.name->>'en', ''), :search) > 0.2
                        or coalesce(p.name->>:lang, p.name->>'en', '') ilike concat('%', :search, '%')
                    )
                    """,
            nativeQuery = true)
    Page<Product> search(
            @Param("search") String search,
            @Param("categoryId") String categoryId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("lang") String lang,
            Pageable pageable
    );

    Optional<Product> findBySlugAndDeletedAtIsNull(String slug);
}
