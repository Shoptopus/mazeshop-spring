package com.shoptopus.core.logging;

import com.shoptopus.core.IntegrationTestBase;
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
