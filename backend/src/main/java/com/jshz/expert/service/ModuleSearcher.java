package com.jshz.expert.service;

import com.jshz.expert.security.AuthUser;
import java.time.LocalDateTime;
import java.util.List;

/** Code-owned SQL specification; preview and complete lists use exactly the same predicate. */
@FunctionalInterface
public interface ModuleSearcher {
    QuerySpec query(AuthUser user, LocalDateTime now);
    record QuerySpec(String table, String title, String extra, List<String> fields,
                     String condition, List<Object> args, String updated) {}
}
