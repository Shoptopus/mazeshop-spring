package com.shoptopus.core.mail;

import com.shoptopus.core.common.error.ApiException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;

@Service
public class EmailDeliveryService {

    private static final String GMAIL_SEND_URI = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final EmailSettingsService emailSettingsService;
    private final RestClient restClient;

    public EmailDeliveryService(EmailSettingsService emailSettingsService) {
        this.emailSettingsService = emailSettingsService;
        this.restClient = RestClient.builder().build();
    }

    public void sendHtml(String recipient, String subject, String html) {
        EmailSettingsService.EmailSettings settings = emailSettingsService.readSettings();
        if ("gmail".equalsIgnoreCase(settings.provider())) {
            sendViaGmail(recipient, subject, html, true, settings);
            return;
        }
        sendViaSmtp(recipient, subject, html, true, settings);
    }

    public void sendText(String recipient, String subject, String text) {
        EmailSettingsService.EmailSettings settings = emailSettingsService.readSettings();
        if ("gmail".equalsIgnoreCase(settings.provider())) {
            sendViaGmail(recipient, subject, text, false, settings);
            return;
        }
        sendViaSmtp(recipient, subject, text, false, settings);
    }

    public String fromLabel() {
        EmailSettingsService.EmailSettings settings = emailSettingsService.readSettings();
        String fromAddress = effectiveFromAddress(settings);
        if (settings.fromName() == null || settings.fromName().isBlank()) {
            return fromAddress;
        }
        return settings.fromName() + " <" + fromAddress + ">";
    }

    private void sendViaSmtp(String recipient, String subject, String content, boolean html, EmailSettingsService.EmailSettings settings) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(settings.smtp().host());
            sender.setPort(settings.smtp().port());
            sender.setUsername(settings.smtp().username());
            sender.setPassword(settings.smtp().password());
            Properties properties = sender.getJavaMailProperties();
            properties.put("mail.smtp.auth", String.valueOf(settings.smtp().auth()));
            properties.put("mail.smtp.starttls.enable", String.valueOf(settings.smtp().starttls()));

            MimeMessage message = sender.createMimeMessage();
            populateMessage(message, recipient, subject, content, html, settings);
            sender.send(message);
        } catch (MailException | jakarta.mail.MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Email delivery failed", ex.getMessage());
        }
    }

    private void sendViaGmail(String recipient, String subject, String content, boolean html, EmailSettingsService.EmailSettings settings) {
        try {
            EmailSettingsService.GmailSettings gmail = emailSettingsService.gmailSettingsWithFreshAccessToken();
            Session session = Session.getInstance(new Properties());
            MimeMessage message = new MimeMessage(session);
            populateMessage(message, recipient, subject, content, html, settings.withGmail(gmail));
            message.saveChanges();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
            restClient.post()
                    .uri(GMAIL_SEND_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + gmail.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"raw\":\"" + raw + "\"}")
                    .retrieve()
                    .toBodilessEntity();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Email delivery failed", ex.getMessage());
        }
    }

    private void populateMessage(
            MimeMessage message,
            String recipient,
            String subject,
            String content,
            boolean html,
            EmailSettingsService.EmailSettings settings
    ) throws jakarta.mail.MessagingException, java.io.UnsupportedEncodingException {
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setTo(recipient);
        String fromAddress = effectiveFromAddress(settings);
        if (settings.fromName() == null || settings.fromName().isBlank()) {
            helper.setFrom(fromAddress);
        } else {
            helper.setFrom(fromAddress, settings.fromName());
        }
        helper.setSubject(subject);
        helper.setText(content == null ? "" : content, html);
    }

    private String effectiveFromAddress(EmailSettingsService.EmailSettings settings) {
        if (settings.fromAddress() != null && !settings.fromAddress().isBlank()) {
            return settings.fromAddress().trim();
        }
        if ("gmail".equalsIgnoreCase(settings.provider())
                && settings.gmail() != null
                && settings.gmail().connectedEmail() != null
                && !settings.gmail().connectedEmail().isBlank()) {
            return settings.gmail().connectedEmail().trim();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "A from address is required for email delivery.");
    }
}
