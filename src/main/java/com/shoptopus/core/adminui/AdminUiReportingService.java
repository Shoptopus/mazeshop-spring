package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminReportingService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdminUiReportingService {

    private final AdminReportingService adminReportingService;

    public AdminUiReportingService(AdminReportingService adminReportingService) {
        this.adminReportingService = adminReportingService;
    }

    public Map<String, Object> overview(Integer signupsRange, Integer ordersRange) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) adminReportingService.overviewReport(signupsRange, ordersRange).get("data");
        return data;
    }

    public Map<String, Object> sales(Integer revenueRange, Integer productsBreakdownRange, String categoryId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) adminReportingService.salesReport(revenueRange, productsBreakdownRange, categoryId).get("data");
        return data;
    }
}
