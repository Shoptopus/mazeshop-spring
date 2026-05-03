package com.shoptopus.core.user;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column
    private String password;

    @Column(length = 100)
    private String name;

    @Column(length = 5)
    private String initials;

    @Column(length = 5)
    private String prefix;

    @Column(length = 50)
    private String phone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> avatar = new HashMap<>();

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "client_ref", length = 12)
    private String clientRef;

    @Column(nullable = false)
    private boolean temporary;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    @Column(nullable = false)
    private String slug;

    @Column(name = "last_seen")
    private OffsetDateTime lastSeen;
}
