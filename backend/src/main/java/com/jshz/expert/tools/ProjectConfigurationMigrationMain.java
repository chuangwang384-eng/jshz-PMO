package com.jshz.expert.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.service.ProjectConfigurationService;
import com.jshz.expert.service.ProjectConfigurationMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Explicit offline command; no HTTP exposure, application startup hook or implicit production target. */
public final class ProjectConfigurationMigrationMain {
    public static void main(String[] args) {
        Set<String> options=Set.of(args);
        if(!Set.of("--preflight","--apply","--review-legacy-differences","--allow-deleted-orphans").containsAll(options)) throw new IllegalArgumentException("Unsupported migration option");
        String url=required("PC_MIGRATION_URL"), database=required("PC_MIGRATION_DATABASE");
        var source=new DriverManagerDataSource(url,required("PC_MIGRATION_USER"),required("PC_MIGRATION_PASSWORD"));
        JdbcTemplate db=new JdbcTemplate(source);
        if(!database.equals(db.queryForObject("SELECT DATABASE()",String.class))) throw new IllegalStateException("Database identity mismatch");
        ProjectConfigurationService config=new ProjectConfigurationService(db,new ObjectMapper());
        ProjectConfigurationMigration migration=new ProjectConfigurationMigration(db,config);
        Map<String,Object> report;
        if(options.contains("--apply")) {
            if(!database.equals(required("PC_MIGRATION_APPROVED_DATABASE"))) throw new IllegalStateException("Explicit approved database is required");
            report=new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status->migration.migrate(options.contains("--review-legacy-differences"),options.contains("--allow-deleted-orphans")));
        } else report=migration.preflight(options.contains("--allow-deleted-orphans"));
        System.out.println(config.encode(report));
    }
    private static String required(String key) {
        String value=System.getenv(key);if(value==null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable "+key);return value;
    }
}
