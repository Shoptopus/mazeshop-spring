package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminSupportChannelsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminUiSupportChannelsService {

    private final AdminSupportChannelsService adminSupportChannelsService;

    public AdminUiSupportChannelsService(AdminSupportChannelsService adminSupportChannelsService) {
        this.adminSupportChannelsService = adminSupportChannelsService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> emailTargets() {
        Object data = adminSupportChannelsService.emailUsers().get("data");
        List<?> items = data instanceof List<?> list ? list : List.of();
        return Map.of(
                "items", items,
                "meta", Map.of("total", items.size())
        );
    }

    public Map<String, Object> sendEmail(Map<String, Object> body) {
        return new LinkedHashMap<>(adminSupportChannelsService.sendEmail(body));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> emails() {
        return adminSupportChannelsService.outgoingEmails();
    }

    public Map<String, Object> processEmails() {
        return adminSupportChannelsService.processEmails();
    }

    public Map<String, Object> testEmailSettings(Map<String, Object> body) {
        return adminSupportChannelsService.testEmailSettings(body);
    }

    public Map<String, Object> gmailAuthorization(Map<String, Object> body) {
        return adminSupportChannelsService.gmailAuthorization(body);
    }

    public java.net.URI gmailCallback(String code, String state, String error) {
        return adminSupportChannelsService.gmailCallback(code, state, error);
    }

    public Map<String, Object> disconnectGmail() {
        return adminSupportChannelsService.disconnectGmail();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> notifications(String email) {
        Object data = adminSupportChannelsService.notifications(email).get("data");
        List<?> items = data instanceof List<?> list ? list : List.of();
        return Map.of(
                "items", items,
                "meta", Map.of("total", items.size())
        );
    }

    public Map<String, Object> clearNotifications(String email) {
        return adminSupportChannelsService.clearNotifications(email);
    }
}
