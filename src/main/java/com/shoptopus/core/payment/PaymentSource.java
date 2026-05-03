package com.shoptopus.core.payment;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment_sources")
public class PaymentSource extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "exp_month")
    private String expMonth;

    @Column(name = "exp_year")
    private String expYear;

    @Column(name = "last_four")
    private String lastFour;

    @Column
    private String brand;

    @Column(name = "stripe_user_id")
    private String stripeUserId;

    @Column(name = "payment_method_id", nullable = false)
    private Integer paymentMethodId;
}
