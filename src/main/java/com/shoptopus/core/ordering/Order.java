package com.shoptopus.core.ordering;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "delivery_type_id")
    private java.util.UUID deliveryTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_type_id", insertable = false, updatable = false)
    private DeliveryType deliveryType;

    @Column(name = "voucher_code_id")
    private java.util.UUID voucherCodeId;

    @Column(name = "original_price", nullable = false)
    private BigDecimal originalPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(name = "total_discount", nullable = false)
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "delivery_cost", nullable = false)
    private BigDecimal deliveryCost = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "GBP";

    @Column(nullable = false)
    private Integer status = 0;

    @Column(nullable = false)
    private String slug;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
