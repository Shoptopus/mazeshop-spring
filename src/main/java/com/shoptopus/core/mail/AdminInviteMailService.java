package com.shoptopus.core.mail;

import com.shoptopus.core.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminInviteMailService {

    private final EmailDeliveryService emailDeliveryService;
    private final String adminUiPublicBaseUrl;
    private final boolean invitesEnabled;

    public AdminInviteMailService(
            EmailDeliveryService emailDeliveryService,
            @Value("${app.admin-ui.public-base-url}") String adminUiPublicBaseUrl,
            @Value("${app.mail.invites-enabled:true}") boolean invitesEnabled
    ) {
        this.emailDeliveryService = emailDeliveryService;
        this.adminUiPublicBaseUrl = adminUiPublicBaseUrl;
        this.invitesEnabled = invitesEnabled;
    }

    public void sendInvite(String invitedEmail, String role, String token) {
        if (!invitesEnabled) {
            return;
        }
        String inviteUrl = inviteUrl(token);
        String content = """
                You have been invited to join Shoptopus Admin.

                Role: %s

                Complete your signup using this single-use link:
                %s

                If you were not expecting this invitation, you can ignore this email.
                """.formatted(role, inviteUrl);
        try {
            emailDeliveryService.sendText(invitedEmail, "You have been invited to Shoptopus Admin", content);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to send invite email", ex.getMessage());
        }
    }

    public String inviteUrl(String token) {
        return adminUiPublicBaseUrl.replaceAll("/+$", "") + "/accept-invite/" + token;
    }

    public String fromLabel() {
        return emailDeliveryService.fromLabel();
    }
}
