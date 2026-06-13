package danny.project.chatbot.telegram.reminder;

import danny.project.chatbot.telegram.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderStore store;
    private final TelegramService telegramService;

    public ReminderScheduler(ReminderStore store, TelegramService telegramService) {
        this.store = store;
        this.telegramService = telegramService;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 10_000L)
    public void deliverDueReminders() {
        long now = System.currentTimeMillis();
        List<Reminder> due = store.listPendingDue(now);
        if (due.isEmpty()) return;
        log.info("Delivering {} due reminder(s)", due.size());
        for (Reminder r : due) {
            try {
                telegramService.sendMessage(r.chatId, "⏰ Nhắc bạn: " + r.task);
                store.markSent(r.id);
            } catch (Exception e) {
                log.error("Failed to deliver reminder {}: {}", r.id, e.getMessage(), e);
            }
        }
    }
}
