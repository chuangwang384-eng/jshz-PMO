package com.jshz.expert;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 专家库系统启动类。
 *
 * <p>依赖公司内网部署，服务启动时通过 application.yml 中的
 * 环境变量注入数据库连接等内网配置。</p>
 *
 * <p>@EnableScheduling/@EnableAsync：企微通讯录同步首次引入定时任务与异步执行
 * （每日 02:30 由 sync-cron-enabled 开关控制）。</p>
 */
@SpringBootApplication
@MapperScan("com.jshz.expert.mapper")
@EnableScheduling
@EnableAsync
public class ExpertSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpertSystemApplication.class, args);
    }
}
