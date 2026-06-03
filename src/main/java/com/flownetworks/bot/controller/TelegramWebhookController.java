package com.flownetworks.bot.controller;

import com.flownetworks.bot.config.TelegramProperties;
import com.flownetworks.bot.dto.telegram.TelegramMessage;
import com.flownetworks.bot.dto.telegram.TelegramUpdate;
import com.flownetworks.bot.service.ClaudeService;
import com.flownetworks.bot.service.ConversationService;
import com.flownetworks.bot.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String TELEGRAM_CONVERSATION_PREFIX = "telegram:";

    private final TelegramService telegramService;
    private final TelegramProperties telegramProperties;
    private final ClaudeService claudeService;
    private final ConversationService conversationService;
    private final Executor botTaskExecutor;

    public TelegramWebhookController(TelegramService telegramService,
                                      TelegramProperties telegramProperties,
                                      ClaudeService claudeService,
                                      ConversationService conversationService,
                                      @Qualifier("botTaskExecutor") Executor botTaskExecutor) {
        this.telegramService = telegramService;
        this.telegramProperties = telegramProperties;
        this.claudeService = claudeService;
        this.conversationService = conversationService;
        this.botTaskExecutor = botTaskExecutor;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleUpdate(@RequestBody(required = false) TelegramUpdate update,
                                             @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken) {

        if (!telegramService.isEnabled()) {
            log.warn("Received Telegram update but bot token is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (telegramProperties.isSecretTokenConfigured()) {
            if (secretToken == null || !telegramProperties.getSecretToken().equals(secretToken)) {
                log.warn("Rejected Telegram webhook due to invalid secret token");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        if (update == null) {
            return ResponseEntity.ok().build();
        }

        TelegramMessage message = update.getMessage();
        if (message == null || message.getChat() == null || message.getChat().getId() == null) {
            return ResponseEntity.ok().build();
        }

        CompletableFuture.runAsync(() -> processMessage(message), botTaskExecutor);
        return ResponseEntity.ok().build();
    }

    private void processMessage(TelegramMessage message) {
        long chatId = message.getChat().getId();
        String conversationId = TELEGRAM_CONVERSATION_PREFIX + chatId;
        String text = message.getText();

        if (text == null || text.isBlank()) {
            log.debug("Telegram message without text from chat {} ignored", chatId);
            return;
        }

        text = text.trim();
        log.info("[telegram:{}] {}", chatId, text);

        if ("/clear".equalsIgnoreCase(text)) {
            conversationService.clearHistory(conversationId);
            telegramService.sendMessage(chatId, "Đã reset conversation. Hỏi lại từ đầu đi!");
            return;
        }

        telegramService.sendTypingAction(chatId);

        List<Map<String, String>> history = conversationService.getHistory(conversationId);
        try {
            String reply = claudeService.chat(history, text);
            conversationService.appendMessages(conversationId, List.of(
                Map.entry("user", text),
                Map.entry("assistant", reply)
            ));
            telegramService.sendMessage(chatId, reply);
        } catch (Exception e) {
            log.error("Telegram processing failed for chat {}: {}", chatId, e.getMessage(), e);
            telegramService.sendMessage(chatId, "Xin lỗi, bot đang gặp sự cố. Vui lòng thử lại sau.");
        }
    }
}
