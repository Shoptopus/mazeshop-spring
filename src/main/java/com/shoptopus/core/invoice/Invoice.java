package com.shoptopus.core.invoice;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "invoices")
public class Invoice extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> address = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payment = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> products = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "voucher_code", columnDefinition = "jsonb")
    private Map<String, Object> voucherCode = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_type", columnDefinition = "jsonb")
    private Map<String, Object> deliveryType = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> totals = new HashMap<>();

    @Column(nullable = false)
    private boolean valid = true;

    @Column(nullable = false)
    private String slug;
}
