package com.shoptopus.core.email;

import com.shoptopus.core.common.jpa.AuditableEntity;
import com.shoptopus.core.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "outgoing_emails")
public class OutgoingEmail extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 20)
    private String status = "queued";

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "queued_at", nullable = false)
    private OffsetDateTime queuedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
}
