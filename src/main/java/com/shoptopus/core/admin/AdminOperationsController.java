package com.shoptopus.core.admin;

import com.shoptopus.core.common.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminOperationsController {

    private final AdminSupportService adminSupportService;
    private final AdminVoucherCodeService adminVoucherCodeService;
    private final AdminDiscountRuleService adminDiscountRuleService;
    private final AdminAccessService adminAccessService;
    private final AdminDeliveryTypeService adminDeliveryTypeService;
    private final AdminDeliveryRuleService adminDeliveryRuleService;
    private final AdminOrderService adminOrderService;
    private final AdminBulkMutationService adminBulkMutationService;
    private final AdminPaymentService adminPaymentService;
    private final AdminReportingService adminReportingService;

    public AdminOperationsController(
            AdminSupportService adminSupportService,
            AdminVoucherCodeService adminVoucherCodeService,
            AdminDiscountRuleService adminDiscountRuleService,
            AdminAccessService adminAccessService,
            AdminDeliveryTypeService adminDeliveryTypeService,
            AdminDeliveryRuleService adminDeliveryRuleService,
            AdminOrderService adminOrderService,
            AdminBulkMutationService adminBulkMutationService,
            AdminPaymentService adminPaymentService,
            AdminReportingService adminReportingService
    ) {
        this.adminSupportService = adminSupportService;
        this.adminVoucherCodeService = adminVoucherCodeService;
        this.adminDiscountRuleService = adminDiscountRuleService;
        this.adminAccessService = adminAccessService;
        this.adminDeliveryTypeService = adminDeliveryTypeService;
        this.adminDeliveryRuleService = adminDeliveryRuleService;
        this.adminOrderService = adminOrderService;
        this.adminBulkMutationService = adminBulkMutationService;
        this.adminPaymentService = adminPaymentService;
        this.adminReportingService = adminReportingService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "reports.view");
        return adminReportingService.dashboard();
    }

    @PostMapping("/report")
    public Map<String, Object> report(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "reports.view");
        return adminReportingService.genericReport();
    }

    @PostMapping("/reports/overview")
    public Map<String, Object> reportOverview(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "reports.view");
        return adminReportingService.overviewReport();
    }

    @PostMapping("/reports/sales")
    public Map<String, Object> reportSales(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "reports.view");
        return adminReportingService.salesReport();
    }

    @PostMapping("/reports/product-ratings/{product}")
    public Map<String, Object> reportProductRatings(Authentication authentication, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "reports.view");
        return adminReportingService.productRatingsReport(product);
    }

    @GetMapping("/orders")
    public Map<String, Object> orders(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "orders.view");
        return adminOrderService.list();
    }

    @PostMapping("/orders/bulk/status")
    public ApiResponse<?> orderBulkStatus(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "orders.manage");
        return ApiResponse.ok("Updated", adminOrderService.bulkStatus(body));
    }

    @GetMapping("/order/{order}")
    public ApiResponse<?> showOrder(Authentication authentication, @PathVariable String order) {
        adminAccessService.requirePermission(authentication, "orders.view");
        return ApiResponse.ok("Order", adminOrderService.show(order));
    }

    @PatchMapping("/order/{order}")
    public ApiResponse<?> updateOrder(Authentication authentication, @PathVariable String order, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "orders.manage");
        return ApiResponse.ok("Updated", adminOrderService.updateStatus(order, body));
    }

    @PostMapping("/order/{order}")
    public ApiResponse<?> updateOrderViaPost(Authentication authentication, @PathVariable String order, @RequestBody Map<String, Object> body) { return updateOrder(authentication, order, body); }

    @DeleteMapping("/order/{order}")
    public ApiResponse<?> deleteOrder(Authentication authentication, @PathVariable String order) {
        adminAccessService.requirePermission(authentication, "orders.manage");
        return ApiResponse.ok("Deleted", adminOrderService.delete(order));
    }

    @GetMapping("/payments")
    public Map<String, Object> payments(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "payments.manage");
        return adminPaymentService.list();
    }

    @PostMapping("/payments/bulk/status")
    public ApiResponse<?> paymentsBulkStatus(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "payments.manage");
        return ApiResponse.ok("Updated", adminBulkMutationService.paymentsStatus(body));
    }

    @PostMapping("/payment")
    public ApiResponse<?> createPayment(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "payments.manage");
        return ApiResponse.ok("Created", adminPaymentService.create(body));
    }

    @GetMapping("/payment/{payment}")
    public ApiResponse<?> showPayment(Authentication authentication, @PathVariable String payment) {
        adminAccessService.requirePermission(authentication, "payments.manage");
        return ApiResponse.ok("Payment", adminPaymentService.show(payment));
    }

    @PatchMapping("/payment/{payment}")
    public ApiResponse<?> updatePayment(Authentication authentication, @PathVariable String payment, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "payments.manage");
        return ApiResponse.ok("Updated", adminPaymentService.update(payment, body));
    }

    @PostMapping("/payment/{payment}")
    public ApiResponse<?> updatePaymentViaPost(Authentication authentication, @PathVariable String payment, @RequestBody Map<String, Object> body) { return updatePayment(authentication, payment, body); }

    @DeleteMapping("/payment/{payment}")
    public ApiResponse<?> deletePayment(Authentication authentication, @PathVariable String payment) {
        adminAccessService.requirePermission(authentication, "payments.manage");
        return ApiResponse.ok("Deleted", adminPaymentService.delete(payment));
    }

    @DeleteMapping("/payment/{payment}/refund")
    public ApiResponse<?> refundPayment(Authentication authentication, @PathVariable String payment) {
        adminAccessService.requirePermission(authentication, "payments.manage");
        return ApiResponse.ok("Refunded", adminPaymentService.refund(payment, null));
    }

    @GetMapping("/voucher-codes")
    public Map<String, Object> vouchers(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "voucher_codes.view");
        return adminVoucherCodeService.list();
    }

    @PostMapping("/voucher-codes/bulk/expire")
    public ApiResponse<?> vouchersExpire(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "voucher_codes.manage");
        return ApiResponse.ok("Expired", adminVoucherCodeService.bulkExpire(body));
    }

    @PostMapping("/voucher-codes/bulk/start")
    public ApiResponse<?> vouchersStart(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "voucher_codes.manage");
        return ApiResponse.ok("Started", adminVoucherCodeService.bulkStart(body));
    }

    @PostMapping("/voucher-codes/bulk/activate")
    public ApiResponse<?> vouchersActivate(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "voucher_codes.manage");
        return ApiResponse.ok("Activated", adminVoucherCodeService.bulkActivate(body));
    }

    @DeleteMapping("/voucher-codes/bulk")
    public ApiResponse<?> vouchersDelete(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "voucher_codes.manage");
        return ApiResponse.ok("Deleted", adminVoucherCodeService.bulkDelete(body));
    }

    @PostMapping("/voucher-code")
    public ApiResponse<?> voucherCreate(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "voucher_codes.manage");
        return ApiResponse.ok("Created", adminVoucherCodeService.create(body));
    }

    @GetMapping("/voucher-code/{voucherCode}")
    public ApiResponse<?> voucherShow(Authentication authentication, @PathVariable String voucherCode) {
        adminAccessService.requirePermission(authentication, "voucher_codes.view");
        return ApiResponse.ok("Voucher", adminVoucherCodeService.show(voucherCode));
    }

    @PatchMapping("/voucher-code/{voucherCode}")
    public ApiResponse<?> voucherUpdate(Authentication authentication, @PathVariable String voucherCode, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "voucher_codes.manage");
        return ApiResponse.ok("Updated", adminVoucherCodeService.update(voucherCode, body));
    }

    @PostMapping("/voucher-code/{voucherCode}")
    public ApiResponse<?> voucherUpdateViaPost(Authentication authentication, @PathVariable String voucherCode, @RequestBody Map<String, Object> body) {
        return voucherUpdate(authentication, voucherCode, body);
    }

    @DeleteMapping("/voucher-code/{voucherCode}")
    public ApiResponse<?> voucherDelete(Authentication authentication, @PathVariable String voucherCode) {
        adminAccessService.requirePermission(authentication, "voucher_codes.manage");
        return ApiResponse.ok("Deleted", adminVoucherCodeService.delete(voucherCode));
    }

    @GetMapping("/delivery-types")
    public Map<String, Object> deliveryTypes(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "delivery_types.view");
        return adminDeliveryTypeService.list();
    }

    @PostMapping("/delivery-types/bulk/availability")
    public ApiResponse<?> deliveryTypesBulk(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Updated", adminDeliveryTypeService.bulkAvailability(body));
    }

    @DeleteMapping("/delivery-types/bulk")
    public ApiResponse<?> deliveryTypesDelete(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Deleted", adminDeliveryTypeService.bulkDelete(body));
    }

    @PostMapping("/delivery-type")
    public ApiResponse<?> deliveryTypeCreate(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Created", adminDeliveryTypeService.create(body));
    }

    @GetMapping("/delivery-type/{deliveryType}")
    public ApiResponse<?> deliveryTypeShow(Authentication authentication, @PathVariable String deliveryType) {
        adminAccessService.requirePermission(authentication, "delivery_types.view");
        return ApiResponse.ok("Delivery type", adminDeliveryTypeService.show(deliveryType));
    }

    @PatchMapping("/delivery-type/{deliveryType}")
    public ApiResponse<?> deliveryTypeUpdate(Authentication authentication, @PathVariable String deliveryType, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Updated", adminDeliveryTypeService.update(deliveryType, body));
    }

    @PostMapping("/delivery-type/{deliveryType}")
    public ApiResponse<?> deliveryTypeUpdateViaPost(Authentication authentication, @PathVariable String deliveryType, @RequestBody Map<String, Object> body) {
        return deliveryTypeUpdate(authentication, deliveryType, body);
    }

    @DeleteMapping("/delivery-type/{deliveryType}")
    public ApiResponse<?> deliveryTypeDelete(Authentication authentication, @PathVariable String deliveryType) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Deleted", adminDeliveryTypeService.delete(deliveryType));
    }

    @GetMapping("/delivery-type/{deliveryType}/delivery-rules")
    public Map<String, Object> deliveryRules(Authentication authentication, @PathVariable String deliveryType) {
        adminAccessService.requirePermission(authentication, "delivery_types.view");
        return adminDeliveryRuleService.list(deliveryType);
    }

    @PostMapping("/delivery-type/{deliveryType}/delivery-rule")
    public ApiResponse<?> deliveryRuleCreate(Authentication authentication, @PathVariable String deliveryType, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Created", adminDeliveryRuleService.create(deliveryType, body));
    }

    @GetMapping("/delivery-type/{deliveryType}/delivery-rule/{deliveryRule}")
    public ApiResponse<?> deliveryRuleShow(Authentication authentication, @PathVariable String deliveryType, @PathVariable String deliveryRule) {
        adminAccessService.requirePermission(authentication, "delivery_types.view");
        return ApiResponse.ok("Delivery rule", adminDeliveryRuleService.show(deliveryType, deliveryRule));
    }

    @PatchMapping("/delivery-type/{deliveryType}/delivery-rule/{deliveryRule}")
    public ApiResponse<?> deliveryRuleUpdate(Authentication authentication, @PathVariable String deliveryType, @PathVariable String deliveryRule, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Updated", adminDeliveryRuleService.update(deliveryType, deliveryRule, body));
    }

    @PostMapping("/delivery-type/{deliveryType}/delivery-rule/{deliveryRule}")
    public ApiResponse<?> deliveryRuleUpdateViaPost(Authentication authentication, @PathVariable String deliveryType, @PathVariable String deliveryRule, @RequestBody Map<String, Object> body) {
        return deliveryRuleUpdate(authentication, deliveryType, deliveryRule, body);
    }

    @DeleteMapping("/delivery-type/{deliveryType}/delivery-rule/{deliveryRule}")
    public ApiResponse<?> deliveryRuleDelete(Authentication authentication, @PathVariable String deliveryType, @PathVariable String deliveryRule) {
        adminAccessService.requirePermission(authentication, "delivery_types.manage");
        return ApiResponse.ok("Deleted", adminDeliveryRuleService.delete(deliveryType, deliveryRule));
    }

    @GetMapping("/discount-rules")
    public Map<String, Object> discounts(Authentication authentication) {
        adminAccessService.requirePermission(authentication, "discount_rules.view");
        return adminDiscountRuleService.list();
    }

    @PostMapping("/discount-rules/bulk/expire")
    public ApiResponse<?> discountsExpire(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Expired", adminDiscountRuleService.bulkExpire(body));
    }

    @PostMapping("/discount-rules/bulk/start")
    public ApiResponse<?> discountsStart(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Started", adminDiscountRuleService.bulkStart(body));
    }

    @PostMapping("/discount-rules/bulk/activate")
    public ApiResponse<?> discountsActivate(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Activated", adminDiscountRuleService.bulkActivate(body));
    }

    @DeleteMapping("/discount-rules/bulk")
    public ApiResponse<?> discountsDelete(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Deleted", adminDiscountRuleService.bulkDelete(body));
    }

    @PostMapping("/discount-rule")
    public ApiResponse<?> discountCreate(Authentication authentication, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Created", adminDiscountRuleService.create(body));
    }

    @GetMapping("/discount-rule/{discountRule}")
    public ApiResponse<?> discountShow(Authentication authentication, @PathVariable String discountRule) {
        adminAccessService.requirePermission(authentication, "discount_rules.view");
        return ApiResponse.ok("Discount rule", adminDiscountRuleService.show(discountRule));
    }

    @PatchMapping("/discount-rule/{discountRule}")
    public ApiResponse<?> discountUpdate(Authentication authentication, @PathVariable String discountRule, @RequestBody Map<String, Object> body) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Updated", adminDiscountRuleService.update(discountRule, body));
    }

    @PostMapping("/discount-rule/{discountRule}")
    public ApiResponse<?> discountUpdateViaPost(Authentication authentication, @PathVariable String discountRule, @RequestBody Map<String, Object> body) {
        return discountUpdate(authentication, discountRule, body);
    }

    @DeleteMapping("/discount-rule/{discountRule}")
    public ApiResponse<?> discountDelete(Authentication authentication, @PathVariable String discountRule) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Deleted", adminDiscountRuleService.delete(discountRule));
    }

    @GetMapping("/discount-rule/{discountRule}/available-categories")
    public ApiResponse<?> discountAvailableCategories(Authentication authentication, @PathVariable String discountRule) {
        adminAccessService.requirePermission(authentication, "discount_rules.view");
        return ApiResponse.ok("Available categories", adminDiscountRuleService.availableCategories(discountRule));
    }

    @GetMapping("/discount-rule/{discountRule}/available-products")
    public ApiResponse<?> discountAvailableProducts(Authentication authentication, @PathVariable String discountRule) {
        adminAccessService.requirePermission(authentication, "discount_rules.view");
        return ApiResponse.ok("Available products", adminDiscountRuleService.availableProducts(discountRule));
    }

    @PostMapping("/discount-rule/{discountRule}/products/{product}")
    public ApiResponse<?> discountAttachProduct(Authentication authentication, @PathVariable String discountRule, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Attached", adminDiscountRuleService.attachProduct(discountRule, product));
    }

    @DeleteMapping("/discount-rule/{discountRule}/products/{product}")
    public ApiResponse<?> discountDetachProduct(Authentication authentication, @PathVariable String discountRule, @PathVariable String product) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Detached", adminDiscountRuleService.detachProduct(discountRule, product));
    }

    @PostMapping("/discount-rule/{discountRule}/product-categories/{productCategory}")
    public ApiResponse<?> discountAttachCategory(Authentication authentication, @PathVariable String discountRule, @PathVariable String productCategory) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Attached", adminDiscountRuleService.attachCategory(discountRule, productCategory));
    }

    @DeleteMapping("/discount-rule/{discountRule}/product-categories/{productCategory}")
    public ApiResponse<?> discountDetachCategory(Authentication authentication, @PathVariable String discountRule, @PathVariable String productCategory) {
        adminAccessService.requirePermission(authentication, "discount_rules.manage");
        return ApiResponse.ok("Detached", adminDiscountRuleService.detachCategory(discountRule, productCategory));
    }
}
