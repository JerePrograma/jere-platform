package com.jereplatform.platform;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public final class ReliabilityDatabaseIsolationExtension
    implements BeforeTestExecutionCallback {

    private static final String RELIABILITY_TEST_CLASS =
        "com.jereplatform.platform.ReliabilityIntegrationIT";

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        if (!context.getRequiredTestClass().getName().equals(RELIABILITY_TEST_CLASS)) {
            return;
        }

        var applicationContext = SpringExtension.getApplicationContext(context);
        var jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);
        jdbcTemplate.execute("""
            truncate table
                platform.outbox_event,
                platform.idempotency_record,
                platform.audit_event
            """);
    }
}
