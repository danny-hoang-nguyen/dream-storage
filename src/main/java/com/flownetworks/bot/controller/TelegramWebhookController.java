package com.flownetworks.bot.controller;

import com.flownetworks.bot.config.TelegramProperties;
import com.flownetworks.bot.dto.telegram.TelegramMessage;
import com.flownetworks.bot.dto.telegram.TelegramUpdate;
import com.flownetworks.bot.reminder.Reminder;
import com.flownetworks.bot.reminder.ReminderParser;
import com.flownetworks.bot.reminder.ReminderStore;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String TELEGRAM_CONVERSATION_PREFIX = "telegram:";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TelegramService telegramService;
    private final TelegramProperties telegramProperties;
    private final ClaudeService claudeService;
    private final ConversationService conversationService;
    private final ReminderParser reminderParser;
    private final ReminderStore reminderStore;
    private final Executor botTaskExecutor;

    public TelegramWebhookController(TelegramService telegramService,
                                      TelegramProperties telegramProperties,
                                      ClaudeService claudeService,
                                      ConversationService conversationService,
                                      ReminderParser reminderParser,
                                      ReminderStore reminderStore,
                                      @Qualifier("botTaskExecutor") Executor botTaskExecutor) {
        this.telegramService = telegramService;
        this.telegramProperties = telegramProperties;
        this.claudeService = claudeService;
        this.conversationService = conversationService;
        this.reminderParser = reminderParser;
        this.reminderStore = reminderStore;
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

        if ("/reminders".equalsIgnoreCase(text)) {
            sendReminderList(chatId);
            return;
        }

        telegramService.sendTypingAction(chatId);

        try {
            var parsed = reminderParser.parse(text);
            if (parsed.isPresent()) {
                var pr = parsed.get();
                reminderStore.add(chatId, pr.task(), pr.remindAtEpochMs());
                String when = DISPLAY_FMT.format(Instant.ofEpochMilli(pr.remindAtEpochMs()).atZone(VN_ZONE));
                String reply = pr.reply().isBlank()
                        ? "Đã đặt nhắc nhở: " + pr.task() + " lúc " + when + "."
                        : pr.reply() + " (" + when + ")";
                telegramService.sendMessage(chatId, reply);
                return;
            }

            List<Map<String, String>> history = conversationService.getHistory(conversationId);
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

    private void sendReminderList(long chatId) {
        List<Reminder> pending = reminderStore.listPendingForChat(chatId);
        if (pending.isEmpty()) {
            telegramService.sendMessage(chatId, "Bạn chưa có nhắc nhở nào đang chờ.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 Nhắc nhở đang chờ:\n");
        for (Reminder r : pending) {
            String when = DISPLAY_FMT.format(Instant.ofEpochMilli(r.remindAtEpochMs).atZone(VN_ZONE));
            sb.append("• ").append(when).append(" — ").append(r.task).append("\n");
        }
        telegramService.sendMessage(chatId, sb.toString().trim());
    }
}
