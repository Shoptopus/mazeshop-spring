package com.shoptopus.core.page;

import com.shoptopus.core.common.jpa.AuditableEntity;
import com.shoptopus.core.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "page_menu_items")
public class PageMenuItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id")
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PageMenuItem parent;

    @Column(name = "item_type", nullable = false)
    private String itemType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
