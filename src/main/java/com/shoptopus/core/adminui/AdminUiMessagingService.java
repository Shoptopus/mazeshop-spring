package com.shoptopus.core.adminui;

import com.shoptopus.core.messaging.OrderMessagingService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiMessagingService {

    private final OrderMessagingService orderMessagingService;

    public AdminUiMessagingService(OrderMessagingService orderMessagingService) {
        this.orderMessagingService = orderMessagingService;
    }

    public Map<String, Object> threads(Integer page, Integer perPage) {
        return orderMessagingService.adminThreads(page, perPage);
    }

    public Map<String, Object> ensureThread(UUID orderId) {
        return orderMessagingService.ensureAdminThread(orderId);
    }

    public Map<String, Object> thread(UUID threadId) {
        return orderMessagingService.adminThreadDetail(threadId);
    }

    public Map<String, Object> postMessage(String senderEmail, UUID threadId, Map<String, Object> body) {
        return orderMessagingService.postAdminMessage(senderEmail, threadId, body);
    }

    public Map<String, Object> uploadImage(MultipartFile file) {
        return orderMessagingService.uploadMessageImage(file, false);
    }
}
