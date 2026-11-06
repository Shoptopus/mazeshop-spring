package com.shoptopus.core.user;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "addresses")
public class Address extends SoftDeleteEntity {

    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;

    @Column(nullable = false)
    private String town;

    @Column(name = "post_code", nullable = false)
    private String postCode;

    @Column(nullable = false)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String country = "UK";

    @Column private String name;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column private String note;

    @Column private String lat;

    @Column private String lon;
}
