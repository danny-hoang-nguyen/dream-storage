package com.flownetworks.bot.config;

import com.microsoft.bot.connector.authentication.AppCredentials;
import com.microsoft.bot.integration.BotFrameworkHttpAdapter;
import com.microsoft.bot.integration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class TenantAwareBotAdapter extends BotFrameworkHttpAdapter {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareBotAdapter.class);

    private final String tenantId;

    public TenantAwareBotAdapter(Configuration configuration, String tenantId) {
        super(configuration);
        this.tenantId = tenantId;
    }

    @Override
    protected CompletableFuture<AppCredentials> buildAppCredentials(String appId, String scope) {
        return super.buildAppCredentials(appId, scope).thenApply(creds -> {
            if (tenantId != null && !tenantId.isBlank()) {
                creds.setChannelAuthTenant(tenantId);
                log.debug("Set channel auth tenant={} for appId={}", tenantId, appId);
            }
            return creds;
        });
    }
}
