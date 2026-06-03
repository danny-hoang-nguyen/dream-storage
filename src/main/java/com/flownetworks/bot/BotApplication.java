package com.flownetworks.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class BotApplication {
    private static final Logger log = LoggerFactory.getLogger(BotApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(BotApplication.class, args);
        Environment env = ctx.getEnvironment();
        String appId = env.getProperty("MicrosoftAppId");
        String appType = env.getProperty("MicrosoftAppType");
        String tenantId = env.getProperty("MicrosoftAppTenantId");
        log.info("Bot credentials loaded — AppId={} AppType={} TenantId={}",
            maskId(appId),
            appType,
            maskId(tenantId));
    }

    /** Che bớt ID/secret khi log để tránh lộ thông tin nhạy cảm. */
    private static String maskId(String value) {
        if (value == null || value.isBlank()) return "NULL";
        return value.length() <= 8 ? "***" : value.substring(0, 8) + "...";
    }
}
