package com.shoptopus.core.logging;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.adminui.AdminRoleScopeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;
import java.util.UUID;
import com.shoptopus.core.store.repository.StoreRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import com.shoptopus.core.logging.repository.EventLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DualDatabaseLoggingIntegrationTest extends IntegrationTestBase {

    @Autowired
    @Qualifier("mainDataSource")
    private DataSource mainDataSource;

    @Autowired
    @Qualifier("logsDataSource")
    private DataSource logsDataSource;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private AdminRoleScopeService adminRoleScopeService;

    @Test
    @Transactional("mainTransactionManager")
    void entity_updates_can_flush_when_the_default_store_is_also_dirty() {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(rootUserUsername).orElseThrow();
        var store = storeRepository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc().orElseThrow();
        user.setName("Audit flush user");
        store.setName("Audit flush store");

        // Store attribution must not run a JPA query from an entity update callback.
        // Both changes are rolled back after exercising the actual Hibernate flush.
        userRepository.flush();
        assertThat(user.getName()).isEqualTo("Audit flush user");
        assertThat(store.getName()).isEqualTo("Audit flush store");
    }

    @Test
    @Transactional("mainTransactionManager")
    void audit_store_attribution_uses_management_permissions_before_accepting_a_header() {
        JdbcTemplate jdbc = new JdbcTemplate(mainDataSource);
        UUID assigned = UUID.randomUUID();
        UUID requested = UUID.randomUUID();
        for (UUID storeId : List.of(assigned, requested)) {
            jdbc.update("insert into stores(id, name, slug, status, created_at, updated_at) "
                            + "values (?::uuid, ?, ?, 'ACTIVE', now(), now())",
                    storeId.toString(), "Audit store", "audit-" + storeId);
        }
        String email = "audit-scope-" + UUID.randomUUID() + "@example.com";
        jdbc.update("insert into users(id, first_name, last_name, email, slug, store_id, temporary, is_favorite, created_at, updated_at) "
                        + "values (?::uuid, 'Audit', 'Scope', ?, ?, ?::uuid, false, false, now(), now())",
                UUID.randomUUID().toString(), email, "audit-" + UUID.randomUUID(), assigned.toString());
        AdminStoreContext context = new AdminStoreContext(true, userRepository, storeRepository, jdbc, adminRoleScopeService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminStoreContext.STORE_HEADER, requested.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
            assertThat(context.currentStoreIdForAuditOrNull()).isEqualTo(assigned);
            assertThat(context.currentStoreIdForAuditOrNull()).isEqualTo(context.currentStoreIdOrNull());

            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(rootUserUsername, "", List.of()));
            assertThat(context.currentStoreIdForAuditOrNull()).isEqualTo(requested);
            assertThat(context.currentStoreIdForAuditOrNull()).isEqualTo(context.currentStoreIdOrNull());
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void event_logs_exist_only_in_logs_database_and_aspect_writes_records() throws Exception {
        String mainTable = scalar(mainDataSource, "select to_regclass('public.event_logs')");
        String logsTable = scalar(logsDataSource, "select to_regclass('public.event_logs')");
        assertThat(mainTable).isNull();
        assertThat(logsTable).isEqualTo("event_logs");

        long before = eventLogRepository.count();
        var res = restTemplate.getForEntity("/api/meta", Object.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        long after = eventLogRepository.count();
        assertThat(after).isGreaterThan(before);

        var newest = eventLogRepository.findAll().stream()
                .max(java.util.Comparator.comparing(EventLog::getCreatedAt))
                .orElseThrow();
        assertThat(newest.getData()).contains("\"path\":\"/api/meta\"");
        assertThat(newest.getData()).contains("\"status\":\"success\"");
    }

    private String scalar(DataSource dataSource, String sql) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            return rs.getString(1);
        }
    }
}
