package com.shoptopus.core.engagement;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ratings")
public class Rating extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ratable_type")
    private String ratableType;

    @Column(name = "ratable_id")
    private UUID ratableId;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "language_prefix")
    private String languagePrefix;

    @Column
    private String description;

    @Column
    private String title;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean verified = true;

    @Column(nullable = false)
    private String slug;
}
