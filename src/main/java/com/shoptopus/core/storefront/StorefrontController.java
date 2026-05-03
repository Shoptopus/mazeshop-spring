package com.shoptopus.core.storefront;

import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.invoice.InvoiceAccessService;
import com.shoptopus.core.invoice.InvoicePdfService;
import com.shoptopus.core.publicapi.PublicPageService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront")
public class StorefrontController {

    private final StorefrontConfigService storefrontConfigService;
    private final EngagementService engagementService;
    private final PublicPageService publicPageService;
    private final StorefrontShopService storefrontShopService;
    private final InvoiceAccessService invoiceAccessService;
    private final InvoicePdfService invoicePdfService;

    public StorefrontController(
            StorefrontConfigService storefrontConfigService,
            EngagementService engagementService,
            PublicPageService publicPageService,
            StorefrontShopService storefrontShopService,
            InvoiceAccessService invoiceAccessService,
            InvoicePdfService invoicePdfService
    ) {
        this.storefrontConfigService = storefrontConfigService;
        this.engagementService = engagementService;
        this.publicPageService = publicPageService;
        this.storefrontShopService = storefrontShopService;
        this.invoiceAccessService = invoiceAccessService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping("/config")
    public ApiResponse<?> config(@RequestParam(name = "store", required = false) String store) {
        return ApiResponse.ok("Storefront config", storefrontConfigService.config(store));
    }

    @GetMapping("/invoice/{token}")
    public ResponseEntity<byte[]> invoice(@PathVariable String token) {
        var invoice = invoiceAccessService.resolveInvoiceByToken(token);
        byte[] body = invoicePdfService.renderReceiptPdf(invoice);
        String filename = (invoice.getSlug() == null || invoice.getSlug().isBlank() ? invoice.getId().toString() : invoice.getSlug()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(body);
    }

    @GetMapping("/home")
    public ApiResponse<?> home(
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        var data = new LinkedHashMap<String, Object>();
        data.put("banners", engagementService.getBanners(locale, store));
        data.put("featured_products", storefrontShopService.featuredProducts(8, locale, store));
        data.put("featured_categories", List.of());
        data.put("menu_pages", publicPageService.list(true, locale, store));
        data.put("menu_tree", publicPageService.menuTree(locale, store));
        data.put("footer_pages", publicPageService.footerPages(locale, store));
        data.put("home_page", publicPageService.homePage(locale, store));
        return ApiResponse.ok("Storefront home", data);
    }

    @PostMapping("/banners/{bannerId}/click")
    public ApiResponse<?> clickBanner(@PathVariable java.util.UUID bannerId) {
        engagementService.clickBanner(bannerId);
        return ApiResponse.ok("Storefront banner clicked", null);
    }

    @GetMapping("/pages")
    public ApiResponse<?> pages(
            @RequestParam(name = "menu_only", defaultValue = "true") boolean menuOnly,
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        var rows = publicPageService.list(menuOnly, locale, store);
        return ApiResponse.paged("Storefront pages", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/pages/home")
    public ApiResponse<?> homePage(
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        return ApiResponse.ok("Storefront home page", publicPageService.homePage(locale, store));
    }

    @GetMapping("/pages/{slug}")
    public ApiResponse<?> page(
            @org.springframework.web.bind.annotation.PathVariable String slug,
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        return ApiResponse.ok("Storefront page", publicPageService.detail(slug, locale, store));
    }

    @GetMapping("/shop/filters")
    public ApiResponse<?> shopFilters(
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        return ApiResponse.ok("Storefront shop filters", storefrontShopService.filters(locale, store));
    }

    @GetMapping("/shop")
    public ApiResponse<?> shop(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "category_ids", required = false) String categoryIds,
            @RequestParam(name = "tag_ids", required = false) String tagIds,
            @RequestParam(name = "attribute_option_ids", required = false) String attributeOptionIds,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage,
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        return ApiResponse.ok("Storefront shop", storefrontShopService.shop(
                search,
                categoryIds,
                tagIds,
                attributeOptionIds,
                sort,
                page,
                perPage,
                locale,
                store
        ));
    }

    @GetMapping("/products/{slug}")
    public ApiResponse<?> product(
            @org.springframework.web.bind.annotation.PathVariable String slug,
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        return ApiResponse.ok("Storefront product", storefrontShopService.productDetail(slug, locale, store));
    }

    @GetMapping("/ratings")
    public ApiResponse<?> ratings(
            @RequestParam(name = "ratable_type", required = false) String ratableType,
            @RequestParam(name = "ratable_id", required = false) UUID ratableId,
            @RequestParam(name = "star_rating", required = false) Integer starRating,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage
    ) {
        return ApiResponse.ok("Storefront ratings", engagementService.ratingFeed(ratableType, ratableId, starRating, page, perPage));
    }

    @GetMapping("/products/{slug}/related")
    public ApiResponse<?> relatedProducts(
            @org.springframework.web.bind.annotation.PathVariable String slug,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "store", required = false) String store,
            Locale locale
    ) {
        return ApiResponse.ok("Storefront related products", storefrontShopService.relatedProducts(slug, limit, locale, store));
    }
}
