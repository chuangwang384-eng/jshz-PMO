package com.jshz.expert.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件（MySQL 8）。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 单页最大条数兜底（2026-11-24）：与前端 page-sizes 上限（100）一致。
     *
     * <p>PaginationInnerInterceptor 的语义是 {@code size < 0} 且 {@code maxLimit == null} 时
     * 不构造分页 SQL（等于返回全表），{@code size} 无限大时内存与 SQL 开销不受控。
     * Controller 侧已按 1..100 钳制，这里再加一道框架级护栏，防止新增接口遗漏校验。</p>
     */
    private static final long MAX_PAGE_SIZE = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
