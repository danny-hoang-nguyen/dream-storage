package danny.project.chatbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BotApplication {
    private static final Logger log = LoggerFactory.getLogger(BotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
        log.info("Telegram Claude Bot is running");
    }
}
