package com.flownetworks.bot.config;

import com.microsoft.bot.integration.BotFrameworkHttpAdapter;
import com.microsoft.bot.integration.Configuration;
import com.microsoft.bot.integration.spring.BotController;
import com.microsoft.bot.integration.spring.BotDependencyConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@org.springframework.context.annotation.Configuration
@Import({BotController.class})
public class BotConfig extends BotDependencyConfiguration {

    @Value("${MicrosoftAppTenantId:}")
    private String tenantId;

    @Bean
    @Override
    public BotFrameworkHttpAdapter getBotFrameworkHttpAdaptor(Configuration configuration) {
        return new TenantAwareBotAdapter(configuration, tenantId);
    }
}
