package com.shoptopus.core.mail;

import com.shoptopus.core.email.EmailArchive;
import com.shoptopus.core.email.OutgoingEmail;
import com.shoptopus.core.email.repository.EmailArchiveRepository;
import com.shoptopus.core.email.repository.OutgoingEmailRepository;
import com.shoptopus.core.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OutgoingEmailQueueService {

    private final OutgoingEmailRepository outgoingEmailRepository;
    private final EmailArchiveRepository emailArchiveRepository;
    private final EmailDeliveryService emailDeliveryService;

    public OutgoingEmailQueueService(
            OutgoingEmailRepository outgoingEmailRepository,
            EmailArchiveRepository emailArchiveRepository,
            EmailDeliveryService emailDeliveryService
    ) {
        this.outgoingEmailRepository = outgoingEmailRepository;
        this.emailArchiveRepository = emailArchiveRepository;
        this.emailDeliveryService = emailDeliveryService;
    }

    @Transactional("mainTransactionManager")
    public OutgoingEmail queue(User user, String recipient, String subject, String content) {
        OutgoingEmail email = new OutgoingEmail();
        email.setId(UUID.randomUUID());
        email.setUser(user);
        email.setRecipient(recipient);
        email.setSubject(subject);
        email.setContent(content);
        email.setStatus("queued");
        email.setQueuedAt(OffsetDateTime.now());
        return outgoingEmailRepository.save(email);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> processQueued() {
        List<OutgoingEmail> queued = outgoingEmailRepository.findByStatusOrderByQueuedAtAsc("queued");
        int sent = 0;
        int failed = 0;
        for (OutgoingEmail email : queued) {
            try {
                emailDeliveryService.sendText(email.getRecipient(), email.getSubject(), email.getContent());

                email.setAttempts(email.getAttempts() + 1);
                email.setStatus("sent");
                email.setSentAt(OffsetDateTime.now());
                email.setErrorMessage(null);
                outgoingEmailRepository.save(email);

                EmailArchive archive = new EmailArchive();
                archive.setAddress(email.getRecipient());
                archive.setSubject(email.getSubject());
                archive.setTemplateId("admin.manual");
                archive.setContent(email.getContent());
                emailArchiveRepository.save(archive);
                sent++;
            } catch (Exception ex) {
                email.setAttempts(email.getAttempts() + 1);
                email.setStatus("failed");
                email.setErrorMessage(ex.getMessage());
                outgoingEmailRepository.save(email);
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processed", queued.size());
        result.put("sent", sent);
        result.put("failed", failed);
        return result;
    }
}
