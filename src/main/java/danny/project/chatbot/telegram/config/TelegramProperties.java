package danny.project.chatbot.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    /**
     * Telegram bot token (required).
     */
    private String botToken;

    /**
     * Optional secret token used to validate webhook requests.
     */
    private String secretToken;

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public boolean hasValidBotToken() {
        return botToken != null && !botToken.isBlank();
    }

    public boolean isSecretTokenConfigured() {
        return secretToken != null && !secretToken.isBlank();
    }
}
