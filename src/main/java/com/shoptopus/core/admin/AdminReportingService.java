package com.shoptopus.core.admin;

import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminReportingService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbc;
    private final ProductCategoryRepository productCategoryRepository;

    public AdminReportingService(JdbcTemplate jdbc, ProductCategoryRepository productCategoryRepository) {
        this.jdbc = jdbc;
        this.productCategoryRepository = productCategoryRepository;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> overview = overviewReport(1, 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) ((Map<String, Object>) overview.get("data")).get("stats");
        Map<String, Object> dashboard = new LinkedHashMap<>(stats);
        dashboard.put("ratings", scalar("select count(*) from ratings where deleted_at is null"));
        return Map.of(
                "message", "dashboard",
                "data", dashboard
        );
    }

    public Map<String, Object> genericReport() {
        return Map.of(
                "message", "report",
                "data", Map.of(
                        "type", "generic",
                        "series", List.of(),
                        "generated_at", OffsetDateTime.now().toString()
                )
        );
    }

    public Map<String, Object> overviewReport() {
        return overviewReport(1, 1);
    }

    public Map<String, Object> overviewReport(Integer signupsRange, Integer ordersRange) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("orders", scalar("select count(*) from orders where deleted_at is null"));
        stats.put("products", scalar("select count(*) from products where deleted_at is null"));
        stats.put("payments", scalar("select count(*) from payments where deleted_at is null"));
        stats.put("customers", scalar("select count(*) from users where deleted_at is null"));

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("orders", stats.get("orders"));
        totals.put("products", stats.get("products"));
        totals.put("payments", stats.get("payments"));
        totals.put("customers", stats.get("customers"));

        return Map.of(
                "message", "report",
                "data", Map.of(
                        "type", "overview",
                        "stats", stats,
                        "totals", totals,
                        "user_signups_over_time", dailySeries(
                                """
                                select date_trunc('day', created_at)::date as day, count(*) as total
                                from users
                                where deleted_at is null
                                  and created_at >= ?
                                group by 1
                                order by 1
                                """,
                                rangeStart(signupsRange),
                                "Users"
                        ),
                        "orders_by_status_pie_chart_data", List.of(
                                pieSlice("Pending", orderStatusCount(0), "#f59e0b"),
                                pieSlice("Processing", orderStatusCount(1), "#3b82f6"),
                                pieSlice("Completed", orderStatusCount(2), "#10b981"),
                                pieSlice("Cancelled", orderStatusCount(3), "#ef4444")
                        ),
                        "products_by_status_pie_chart_data", List.of(
                                pieSlice("Draft", productStatusCount(0), "#f59e0b"),
                                pieSlice("Active", activeProductCount(), "#10b981"),
                                pieSlice("Archived", productStatusCount(2), "#64748b")
                        )
                )
        );
    }

    public Map<String, Object> salesReport() {
        return salesReport(1, 1, null);
    }

    public Map<String, Object> salesReport(Integer revenueRange, Integer productsBreakdownRange, String categoryId) {
        UUID selectedCategoryId = parseUuid(categoryId);
        if (selectedCategoryId == null) {
            selectedCategoryId = productCategoryRepository.findAll().stream()
                    .filter(category -> category.getDeletedAt() == null)
                    .sorted(java.util.Comparator.comparing(com.shoptopus.core.catalog.ProductCategory::getCreatedAt).reversed())
                    .findFirst()
                    .map(category -> category.getId())
                    .orElse(null);
        }

        BigDecimal unrealizedRevenue = decimal("""
                select coalesce(sum(total_price), 0)
                from orders
                where deleted_at is null
                  and status in (0, 1)
                """);
        BigDecimal totalRevenue = decimal("""
                select coalesce(sum(total_price), 0)
                from orders
                where deleted_at is null
                  and status = 2
                """);
        BigDecimal totalDelivery = decimal("""
                select coalesce(sum(delivery_cost), 0)
                from orders
                where deleted_at is null
                  and status = 2
                """);
        BigDecimal totalDiscount = decimal("""
                select coalesce(sum(total_discount), 0)
                from orders
                where deleted_at is null
                  and status = 2
                """);

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("unrealized_revenue", unrealizedRevenue);
        totals.put("total_revenue", totalRevenue);
        totals.put("total_delivery", totalDelivery);
        totals.put("total_discount", totalDiscount);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pending_orders", orderStatusCount(0));
        stats.put("processing_orders", orderStatusCount(1));
        stats.put("completed_orders", orderStatusCount(2));
        stats.put("cancelled_orders", orderStatusCount(3));

        Map<String, Object> productsBreakdown = new LinkedHashMap<>();
        productsBreakdown.put("categories", productCategoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .sorted(java.util.Comparator.comparing(com.shoptopus.core.catalog.ProductCategory::getCreatedAt).reversed())
                .map(category -> Map.<String, Object>of(
                        "id", category.getId(),
                        "name", category.getName(),
                        "slug", category.getSlug()
                ))
                .toList());
        productsBreakdown.put("selected_category_id", selectedCategoryId);
        productsBreakdown.put("data", productsBreakdown(rangeStart(productsBreakdownRange), selectedCategoryId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "sales");
        data.put("totals", totals);
        data.put("stats", stats);
        data.put("series", ((Map<String, Object>) dailyRevenueSeries(rangeStart(revenueRange))).get("series"));
        data.put("revenue_over_time", dailyRevenueSeries(rangeStart(revenueRange)));
        data.put("products_breakdown", productsBreakdown);

        return Map.of(
                "message", "report",
                "data", data
        );
    }

    public Map<String, Object> productRatingsReport(String productId) {
        UUID id = UUID.fromString(productId);
        Long total = jdbc.queryForObject("""
                select count(*) from ratings
                where deleted_at is null and ratable_type = 'product' and ratable_id = ?::uuid
                """, Long.class, id.toString());
        BigDecimal average = jdbc.queryForObject("""
                select coalesce(avg(rating), 0) from ratings
                where deleted_at is null and ratable_type = 'product' and ratable_id = ?::uuid
                """, BigDecimal.class, id.toString());
        List<Map<String, Object>> breakdown = jdbc.query("""
                select rating, count(*) as total
                from ratings
                where deleted_at is null and ratable_type = 'product' and ratable_id = ?::uuid
                group by rating
                order by rating desc
                """, (rs, rowNum) -> Map.<String, Object>of(
                "rating", rs.getInt("rating"),
                "total", rs.getLong("total")
        ), id.toString());
        return Map.of(
                "message", "report",
                "data", Map.of(
                        "type", "product-ratings",
                        "product_id", id,
                        "total_ratings", total == null ? 0L : total,
                        "average_rating", average == null ? BigDecimal.ZERO : average,
                        "series", breakdown
                )
        );
    }

    public Map<String, Object> productInsights(String productId) {
        UUID id;
        try {
            id = UUID.fromString(productId);
        } catch (IllegalArgumentException ex) {
            return Map.of("product_id", productId, "insights", Map.of());
        }
        Long variants = jdbc.queryForObject("""
                select count(*) from product_variants
                where deleted_at is null and product_id = ?::uuid
                """, Long.class, productId);
        Long paidContents = jdbc.queryForObject("""
                select count(*) from paid_file_contents
                where fileable_type = 'product' and fileable_id = ?::uuid
                """, Long.class, productId);
        Long ratings = jdbc.queryForObject("""
                select count(*) from ratings
                where deleted_at is null and ratable_type = 'product' and ratable_id = ?::uuid
                """, Long.class, productId);
        return Map.of(
                "product_id", id,
                "insights", Map.of(
                        "variants", variants == null ? 0L : variants,
                        "paid_contents", paidContents == null ? 0L : paidContents,
                        "ratings", ratings == null ? 0L : ratings
                )
        );
    }

    private Map<String, Object> dailyRevenueSeries(OffsetDateTime start) {
        List<Map<String, Object>> rows = jdbc.query("""
                select date_trunc('day', created_at)::date as day,
                       coalesce(sum(total_price), 0) as revenue
                from orders
                where deleted_at is null
                  and status = 2
                  and created_at >= ?
                group by 1
                order by 1
                """, (rs, rowNum) -> Map.<String, Object>of(
                "day", rs.getObject("day", LocalDate.class),
                "value", rs.getBigDecimal("revenue")
        ), start);
        return denseDailySeries(rows, start.toLocalDate(), "Revenue");
    }

    private Map<String, Object> dailySeries(String sql, OffsetDateTime start, String seriesName) {
        List<Map<String, Object>> rows = jdbc.query(sql, (rs, rowNum) -> Map.<String, Object>of(
                "day", rs.getObject("day", LocalDate.class),
                "value", rs.getLong("total")
        ), start);
        return denseDailySeries(rows, start.toLocalDate(), seriesName);
    }

    private Map<String, Object> denseDailySeries(List<Map<String, Object>> rows, LocalDate startDay, String seriesName) {
        Map<LocalDate, Number> byDay = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byDay.put((LocalDate) row.get("day"), (Number) row.get("value"));
        }
        List<String> categories = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        LocalDate cursor = startDay;
        LocalDate today = LocalDate.now();
        while (!cursor.isAfter(today)) {
            categories.add(cursor.format(DAY_FORMAT));
            values.add(byDay.getOrDefault(cursor, 0));
            cursor = cursor.plusDays(1);
        }
        return Map.of(
                "categories", categories,
                "series", List.of(Map.of("name", seriesName, "data", values))
        );
    }

    private Map<String, Object> productsBreakdown(OffsetDateTime start, UUID categoryId) {
        if (categoryId == null) {
            return Map.of("categories", List.of(), "series", List.of());
        }
        List<Map<String, Object>> rows = jdbc.query("""
                select oi.name as name, coalesce(sum(oi.final_price), 0) as revenue
                from order_product oi
                join orders o on o.id = oi.order_id
                join product_product_category ppc on ppc.product_id = oi.product_id
                where o.deleted_at is null
                  and o.status = 2
                  and o.created_at >= ?
                  and ppc.product_category_id = ?::uuid
                group by oi.name
                order by revenue desc, oi.name asc
                limit 8
                """, (rs, rowNum) -> Map.<String, Object>of(
                "name", rs.getString("name"),
                "revenue", rs.getBigDecimal("revenue")
        ), start, categoryId.toString());
        return Map.of(
                "categories", rows.stream().map(row -> row.get("name")).toList(),
                "series", List.of(Map.of(
                        "name", "Revenue",
                        "color", "#10b981",
                        "data", rows.stream().map(row -> row.get("revenue")).toList()
                ))
        );
    }

    private Map<String, Object> pieSlice(String name, long value, String color) {
        return Map.of(
                "name", name,
                "data", value,
                "color", color
        );
    }

    private long orderStatusCount(int status) {
        Long value = jdbc.queryForObject("""
                select count(*) from orders where deleted_at is null and status = ?
                """, Long.class, status);
        return value == null ? 0L : value;
    }

    private long productStatusCount(int status) {
        Long value = jdbc.queryForObject("""
                select count(*) from products where deleted_at is null and status = ?
                """, Long.class, status);
        return value == null ? 0L : value;
    }

    private long activeProductCount() {
        Long value = jdbc.queryForObject("""
                select count(*) from products where deleted_at is null and status <> 2 and status <> 0
                """, Long.class);
        return value == null ? 0L : value;
    }

    private Long scalar(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal decimal(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private OffsetDateTime rangeStart(Integer range) {
        int days = switch (range == null ? 1 : range) {
            case 2 -> 30;
            case 3 -> 90;
            default -> 7;
        };
        return OffsetDateTime.now().minusDays(days - 1L).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
