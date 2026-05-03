package com.shoptopus.core.note;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "notes")
public class Note extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "noteable_type", length = 120)
    private String noteableType;

    @Column(name = "noteable_id")
    private UUID noteableId;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "slug", nullable = false)
    private String slug;
}
