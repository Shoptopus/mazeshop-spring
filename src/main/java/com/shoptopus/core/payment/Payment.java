package com.shoptopus.core.payment;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "payable_type", nullable = false)
    private String payableType;

    @Column(name = "payable_id", nullable = false)
    private String payableId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_source_id")
    private PaymentSource paymentSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> proof = new HashMap<>();

    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "payment_ref", length = 150)
    private String paymentRef;

    @Column(name = "method_ref", length = 150)
    private String methodRef;

    @Column(nullable = false)
    private Integer type;

    @Column(length = 250)
    private String description;
}
