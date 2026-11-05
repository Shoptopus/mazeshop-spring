package com.shoptopus.core.storefront;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.files.ManagedFileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontShopApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private ProductTagRepository productTagRepository;

    @Autowired
    private ProductAttributeRepository productAttributeRepository;

    @Autowired
    private ProductAttributeOptionRepository productAttributeOptionRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ManagedFileService managedFileService;

    @Value("${app.media.storage-path}")
    private String mediaStoragePath;

    @LocalServerPort
    private int port;

    @Test
    void privacy_notice_is_public_html_without_loading_the_storefront_application() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/privacy-policy", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(response.getBody()).contains("Fluentech LTD", "14380150", "mazeshopsales@gmail.com",
                "Google account connection and Gmail", "Limited Use requirements");
        assertThat(response.getBody()).doesNotContain("<script", "assets/");
    }

    @Test
    void storefront_shop_endpoints_return_filters_cards_and_product_detail() {
        ProductCategory category = new ProductCategory();
        category.setName(Map.of("en", "Shoes"));
        category.setSlug("shoes-" + UUID.randomUUID());
        category.setDescription(Map.of("en", "Shoes"));
        category.setEnabled(true);
        productCategoryRepository.save(category);

        ProductCategory childCategory = new ProductCategory();
        childCategory.setParent(category);
        childCategory.setName(Map.of("en", "Trail shoes"));
        childCategory.setSlug("trail-shoes-" + UUID.randomUUID());
        childCategory.setDescription(Map.of("en", "Trail shoes"));
        childCategory.setEnabled(true);
        productCategoryRepository.save(childCategory);

        ProductTag tag = new ProductTag();
        tag.setName(Map.of("en", "Featured"));
        tag.setSlug("featured-" + UUID.randomUUID());
        tag.setEnabled(true);
        productTagRepository.save(tag);

        Product product = new Product();
        product.setName(Map.of("en", "Trail Shoe"));
        product.setSlug("trail-shoe-" + UUID.randomUUID());
        product.setShortDescription(Map.of("en", "Short"));
        product.setDescription(Map.of("en", "Description"));
        product.setPrice(new BigDecimal("120.00"));
        product.setStatus(1);
        product.getCategories().add(childCategory);
        product.getTags().add(tag);
        productRepository.save(product);

        ProductAttribute attribute = new ProductAttribute();
        attribute.setName(Map.of("en", "Size"));
        attribute.setSlug("size-" + UUID.randomUUID());
        attribute.setEnabled(true);
        productAttributeRepository.save(attribute);

        ProductAttributeOption option = new ProductAttributeOption();
        option.setProductAttribute(attribute);
        option.setName(Map.of("en", "42"));
        option.setSlug("size-42-" + UUID.randomUUID());
        option.setValue("42");
        option.setEnabled(true);
        productAttributeOptionRepository.save(option);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSlug("trail-shoe-42-" + UUID.randomUUID());
        variant.setPrice(new BigDecimal("120.00"));
        variant.setStock(3L);
        variant.setDescription(Map.of("en", "Size 42"));
        variant.setAttributeOptions(Map.of(attribute.getId().toString(), option.getId().toString()));
        productVariantRepository.save(variant);

        ProductVariant pricierVariant = new ProductVariant();
        pricierVariant.setProduct(product);
        pricierVariant.setSlug("trail-shoe-42-premium-" + UUID.randomUUID());
        pricierVariant.setPrice(new BigDecimal("135.00"));
        pricierVariant.setStock(2L);
        pricierVariant.setDescription(Map.of("en", "Size 42 premium"));
        pricierVariant.setAttributeOptions(Map.of(attribute.getId().toString(), option.getId().toString()));
        productVariantRepository.save(pricierVariant);

        Product related = new Product();
        related.setName(Map.of("en", "Road Shoe"));
        related.setSlug("road-shoe-" + UUID.randomUUID());
        related.setShortDescription(Map.of("en", "Related"));
        related.setDescription(Map.of("en", "Related description"));
        related.setPrice(new BigDecimal("90.00"));
        related.setStatus(1);
        related.getCategories().add(category);
        related.getCategories().add(childCategory);
        productRepository.save(related);

        var filters = exchange(HttpMethod.GET, "/api/storefront/shop/filters");
        assertThat(filters.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataObject(filters)).containsKeys("categories", "tags", "attributes", "sort_options");
        var categoryRows = dataListFromObject(dataObject(filters), "categories");
        var createdCategoryRow = categoryRows.stream()
                .filter(row -> category.getId().toString().equals(String.valueOf(row.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(dataListFromObject(createdCategoryRow, "children")).hasSize(1);
        assertThat(dataListFromObject(createdCategoryRow, "children").get(0)).containsEntry("id", childCategory.getId().toString());

        var shop = exchange(HttpMethod.GET, "/api/storefront/shop?search=Trail");
        assertThat(shop.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> shopData = dataObject(shop);
        assertThat(shopData).containsKeys("items", "meta", "filters", "active_filters");
        assertThat((List<?>) shopData.get("items")).hasSize(1);
        Map<String, Object> shopItem = castMap(((List<?>) shopData.get("items")).get(0));
        assertThat(castMap(shopItem.get("variant_price_range"))).containsEntry("display", "GBP 120-135");

        var categoryShop = exchange(HttpMethod.GET, "/api/storefront/shop?category_ids=" + category.getId());
        assertThat(categoryShop.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> categoryShopData = dataObject(categoryShop);
        assertThat((List<?>) categoryShopData.get("items")).hasSize(2);
        assertThat(castMap(categoryShopData.get("selected_category"))).containsEntry("id", category.getId().toString());
        assertThat(castMap(categoryShopData.get("selected_category"))).containsKey("children");

        var detail = exchange(HttpMethod.GET, "/api/storefront/products/" + product.getSlug());
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> detailData = dataObject(detail);
        assertThat(detailData.get("slug")).isEqualTo(product.getSlug());
        assertThat(detailData).containsKeys("pricing", "cover_image", "gallery_images", "variant_attributes", "variant_combinations", "variant_selection", "related_items");
        assertThat(castMap(detailData.get("variant_price_range"))).containsEntry("display", "GBP 120-135");
        assertThat((List<?>) detailData.get("related_items")).hasSize(1);

        Map<String, Object> selection = castMap(detailData.get("variant_selection"));
        assertThat(selection.get("complete_selection_required")).isEqualTo(true);
        assertThat(selection.get("required_attribute_count")).isEqualTo(1);
        assertThat((List<?>) selection.get("available_combinations")).hasSize(2);

        var relatedResponse = exchange(HttpMethod.GET, "/api/storefront/products/" + product.getSlug() + "/related?limit=4");
        assertThat(relatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataList(relatedResponse)).hasSize(1);
    }

    @Test
    void storefront_categories_require_an_enabled_assigned_product() {
        ProductCategory hiddenParent = new ProductCategory();
        hiddenParent.setName(Map.of("en", "Hidden parent"));
        hiddenParent.setSlug("hidden-parent-" + UUID.randomUUID());
        hiddenParent.setDescription(Map.of("en", "Hidden parent"));
        hiddenParent.setEnabled(true);
        productCategoryRepository.save(hiddenParent);

        ProductCategory visibleChild = new ProductCategory();
        visibleChild.setParent(hiddenParent);
        visibleChild.setName(Map.of("en", "Visible child"));
        visibleChild.setSlug("visible-child-" + UUID.randomUUID());
        visibleChild.setDescription(Map.of("en", "Visible child"));
        visibleChild.setEnabled(true);
        productCategoryRepository.save(visibleChild);

        ProductCategory draftOnly = new ProductCategory();
        draftOnly.setName(Map.of("en", "Draft only"));
        draftOnly.setSlug("draft-only-" + UUID.randomUUID());
        draftOnly.setDescription(Map.of("en", "Draft only"));
        draftOnly.setEnabled(true);
        productCategoryRepository.save(draftOnly);

        Product visibleProduct = new Product();
        visibleProduct.setName(Map.of("en", "Visible product"));
        visibleProduct.setSlug("visible-product-" + UUID.randomUUID());
        visibleProduct.setShortDescription(Map.of("en", "Visible"));
        visibleProduct.setDescription(Map.of("en", "Visible"));
        visibleProduct.setPrice(new BigDecimal("10.00"));
        visibleProduct.setStatus(1);
        visibleProduct.getCategories().add(visibleChild);
        productRepository.save(visibleProduct);

        Product draftProduct = new Product();
        draftProduct.setName(Map.of("en", "Draft product"));
        draftProduct.setSlug("draft-product-" + UUID.randomUUID());
        draftProduct.setShortDescription(Map.of("en", "Draft"));
        draftProduct.setDescription(Map.of("en", "Draft"));
        draftProduct.setPrice(new BigDecimal("10.00"));
        draftProduct.setStatus(0);
        draftProduct.getCategories().add(draftOnly);
        productRepository.save(draftProduct);

        var filters = exchange(HttpMethod.GET, "/api/storefront/shop/filters");
        assertThat(filters.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> categories = dataListFromObject(dataObject(filters), "categories");
        assertThat(categories).extracting(item -> String.valueOf(item.get("id"))).contains(visibleChild.getId().toString());
        assertThat(categories).extracting(item -> String.valueOf(item.get("id"))).doesNotContain(hiddenParent.getId().toString(), draftOnly.getId().toString());
        assertThat(categories.stream().filter(item -> visibleChild.getId().toString().equals(String.valueOf(item.get("id")))).findFirst().orElseThrow())
                .containsEntry("parent_id", null);

        var selected = exchange(HttpMethod.GET, "/api/storefront/shop?category_ids=" + draftOnly.getId());
        assertThat(selected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(castMap(dataObject(selected).get("selected_category"))).isNull();
    }

    @Test
    void storefront_shop_only_returns_active_products_and_filterable_attributes() {
        ProductAttribute visibleAttribute = new ProductAttribute();
        visibleAttribute.setName(Map.of("en", "Material"));
        visibleAttribute.setSlug("material-" + UUID.randomUUID());
        visibleAttribute.setType(0);
        visibleAttribute.setEnabled(true);
        visibleAttribute.setShowInFilters(true);
        productAttributeRepository.save(visibleAttribute);

        ProductAttribute hiddenAttribute = new ProductAttribute();
        hiddenAttribute.setName(Map.of("en", "Internal finish"));
        hiddenAttribute.setSlug("internal-finish-" + UUID.randomUUID());
        hiddenAttribute.setType(0);
        hiddenAttribute.setEnabled(true);
        hiddenAttribute.setShowInFilters(false);
        productAttributeRepository.save(hiddenAttribute);

        ProductAttributeOption visibleOption = new ProductAttributeOption();
        visibleOption.setProductAttribute(visibleAttribute);
        visibleOption.setName(Map.of("en", "Oak"));
        visibleOption.setSlug("oak-" + UUID.randomUUID());
        visibleOption.setValue("oak");
        visibleOption.setEnabled(true);
        productAttributeOptionRepository.save(visibleOption);

        ProductAttributeOption hiddenOption = new ProductAttributeOption();
        hiddenOption.setProductAttribute(hiddenAttribute);
        hiddenOption.setName(Map.of("en", "Gloss"));
        hiddenOption.setSlug("gloss-" + UUID.randomUUID());
        hiddenOption.setValue("gloss");
        hiddenOption.setEnabled(true);
        productAttributeOptionRepository.save(hiddenOption);

        Product activeProduct = new Product();
        activeProduct.setName(Map.of("en", "Filterable active product"));
        activeProduct.setSlug("filterable-active-product-" + UUID.randomUUID());
        activeProduct.setShortDescription(Map.of("en", "Visible"));
        activeProduct.setDescription(Map.of("en", "Visible"));
        activeProduct.setPrice(new BigDecimal("25.00"));
        activeProduct.setStatus(1);
        productRepository.save(activeProduct);

        Product draftProduct = new Product();
        draftProduct.setName(Map.of("en", "Filterable draft product"));
        draftProduct.setSlug("filterable-draft-product-" + UUID.randomUUID());
        draftProduct.setShortDescription(Map.of("en", "Draft"));
        draftProduct.setDescription(Map.of("en", "Draft"));
        draftProduct.setPrice(new BigDecimal("30.00"));
        draftProduct.setStatus(0);
        productRepository.save(draftProduct);

        ProductVariant activeVariant = new ProductVariant();
        activeVariant.setProduct(activeProduct);
        activeVariant.setSlug("filterable-active-variant-" + UUID.randomUUID());
        activeVariant.setPrice(new BigDecimal("25.00"));
        activeVariant.setStock(2L);
        activeVariant.setDescription(Map.of("en", "Active variant"));
        activeVariant.setAttributeOptions(Map.of(
                visibleAttribute.getId().toString(), visibleOption.getId().toString(),
                hiddenAttribute.getId().toString(), hiddenOption.getId().toString()
        ));
        productVariantRepository.save(activeVariant);

        ProductVariant draftVariant = new ProductVariant();
        draftVariant.setProduct(draftProduct);
        draftVariant.setSlug("filterable-draft-variant-" + UUID.randomUUID());
        draftVariant.setPrice(new BigDecimal("30.00"));
        draftVariant.setStock(2L);
        draftVariant.setDescription(Map.of("en", "Draft variant"));
        draftVariant.setAttributeOptions(Map.of(
                visibleAttribute.getId().toString(), visibleOption.getId().toString()
        ));
        productVariantRepository.save(draftVariant);

        var filters = exchange(HttpMethod.GET, "/api/storefront/shop/filters");
        assertThat(filters.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> attributes = dataListFromObject(dataObject(filters), "attributes");
        assertThat(attributes).extracting(item -> String.valueOf(item.get("id")))
                .contains(visibleAttribute.getId().toString())
                .doesNotContain(hiddenAttribute.getId().toString());
        Map<String, Object> visibleAttributeRow = attributes.stream()
                .filter(item -> visibleAttribute.getId().toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(dataListFromObject(visibleAttributeRow, "options"))
                .extracting(item -> String.valueOf(item.get("id")))
                .contains(visibleOption.getId().toString())
                .doesNotContain(hiddenOption.getId().toString());

        var shop = exchange(HttpMethod.GET, "/api/storefront/shop?search=Filterable");
        assertThat(shop.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataListFromObject(dataObject(shop), "items"))
                .extracting(item -> String.valueOf(item.get("id")))
                .contains(activeProduct.getId().toString())
                .doesNotContain(draftProduct.getId().toString());

        var hiddenAttributeFilter = exchange(HttpMethod.GET, "/api/storefront/shop?attribute_option_ids=" + hiddenOption.getId());
        assertThat(hiddenAttributeFilter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataListFromObject(dataObject(hiddenAttributeFilter), "items"))
                .extracting(item -> String.valueOf(item.get("id")))
                .contains(activeProduct.getId().toString())
                .doesNotContain(draftProduct.getId().toString());
    }

    @Test
    void storefront_home_returns_featured_products_with_persisted_image_dimensions() throws Exception {
        Product product = new Product();
        product.setName(Map.of("en", "Home featured"));
        product.setSlug("home-featured-" + UUID.randomUUID());
        product.setShortDescription(Map.of("en", "Featured on home"));
        product.setDescription(Map.of("en", "Featured on home"));
        product.setPrice(new BigDecimal("19.99"));
        product.setStatus(1);
        productRepository.save(product);

        String relativePath = "products/" + UUID.randomUUID() + ".jpg";
        Path target = Path.of(mediaStoragePath).resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, jpgBytes(1600, 900));

        managedFileService.createUploadedFile(
                "home-featured",
                "home-featured.jpg",
                "image/jpeg",
                Files.size(target),
                relativePath,
                "cover_photo",
                "product",
                product.getId()
        );

        var response = exchange(HttpMethod.GET, "/api/storefront/home");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> featuredProducts = dataListFromObject(dataObject(response), "featured_products");
        Map<String, Object> featured = featuredProducts.stream()
                .filter(item -> product.getId().toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        Map<String, Object> coverImage = castMap(featured.get("cover_image"));
        assertThat(coverImage.get("width")).isEqualTo(1600);
        assertThat(coverImage.get("height")).isEqualTo(900);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataObject(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dataList(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dataListFromObject(Map<String, Object> object, String key) {
        return (List<Map<String, Object>>) object.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path) {
        return restTemplate.exchange("http://127.0.0.1:" + port + path, method, HttpEntity.EMPTY, Map.class);
    }

    private byte[] jpgBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(50, 120, 200));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
