package com.shoptopus.core.page;

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

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "pages")
public class Page extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> title = new HashMap<>();

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "show_in_menu", nullable = false)
    private boolean showInMenu;

    @Column(name = "home_page", nullable = false)
    private boolean homePage;

    @Column(name = "add_to_footer", nullable = false)
    private boolean addToFooter;

    @Column(nullable = false)
    private Integer version = 1;
}
