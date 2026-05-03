package com.shoptopus.core.auth;

import com.shoptopus.core.common.jpa.AuditableEntity;
import com.shoptopus.core.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "access_tokens")
public class AccessToken extends AuditableEntity {

    @Column(nullable = false)
    private Integer type = 0;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "accessable_type", nullable = false)
    private String accessableType = "User";

    @Column(name = "accessable_id", nullable = false)
    private String accessableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_user_id")
    private User issuerUser;

    @Column
    private OffsetDateTime expiry;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> content;
}
