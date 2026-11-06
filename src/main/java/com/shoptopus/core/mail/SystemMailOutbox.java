package com.shoptopus.core.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Mail is persisted with the business transaction and delivered only after commit. */
@Service
public class SystemMailOutbox {
    private static final Logger log = LoggerFactory.getLogger(SystemMailOutbox.class);
    private final JdbcTemplate jdbc;
    private final EmailDeliveryService delivery;
    private final boolean enabled;

    public SystemMailOutbox(
            JdbcTemplate jdbc,
            EmailDeliveryService delivery,
            @Value("${app.mail.outbox.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.delivery = delivery;
        this.enabled = enabled;
    }

    public void enqueue(String recipient, String subject, String html) {
        if (recipient == null || recipient.isBlank()) return;
        jdbc.update(
                "insert into system_mail_outbox(id,dedupe_key,recipient,subject,html_content)"
                    + " values(?,?,?,?,?) on conflict(dedupe_key) do nothing",
                UUID.randomUUID(),
                digest(recipient + "\n" + subject + "\n" + html),
                recipient,
                subject,
                html);
    }

    @Scheduled(
            initialDelayString = "${app.mail.outbox.initial-delay-ms:3000}",
            fixedDelayString = "${app.mail.outbox.delay-ms:5000}")
    public void scheduledDelivery() {
        if (enabled) processOnce();
    }

    public int processOnce() {
        // The atomic claim prevents two workers delivering the same committed row.
        var rows =
                jdbc.queryForList(
                        """
update system_mail_outbox set status='sending',claimed_at=now(),attempts=attempts+1
where id in (select id from system_mail_outbox
  where ((status in ('queued','failed') and next_attempt_at<=now())
     or (status='sending' and claimed_at<now()-interval '10 minutes'))
    and attempts<8 order by created_at for update skip locked limit 20)
returning id,recipient,subject,html_content,attempts
""");
        int sent = 0;
        for (var row : rows) {
            try {
                delivery.sendHtml(
                        (String) row.get("recipient"),
                        (String) row.get("subject"),
                        (String) row.get("html_content"));
                jdbc.update(
                        "update system_mail_outbox set status='sent',sent_at=now() where id=?",
                        row.get("id"));
                sent++;
            } catch (Exception ex) {
                int attempts = ((Number) row.get("attempts")).intValue();
                jdbc.update(
                        "update system_mail_outbox set status='failed',next_attempt_at=now()+(? *"
                            + " interval '1 second') where id=?",
                        Math.min(3600, 30 * (1 << Math.min(attempts, 6))),
                        row.get("id"));
                log.warn(
                        "System mail delivery failed for outbox {} (attempt {}); retry scheduled",
                        row.get("id"),
                        attempts);
            }
        }
        return sent;
    }

    public static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
