package com.flownetworks.bot.reminder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class ReminderStore {

    private static final Logger log = LoggerFactory.getLogger(ReminderStore.class);

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Object lock = new Object();

    public ReminderStore(ObjectMapper objectMapper,
                         @Value("${reminder.file-path:./reminders.json}") String filePath) {
        this.objectMapper = objectMapper;
        this.file = Path.of(filePath);
    }

    public Reminder add(long chatId, String task, long remindAtEpochMs) {
        Reminder r = new Reminder(
                UUID.randomUUID().toString(),
                chatId,
                task,
                remindAtEpochMs,
                System.currentTimeMillis()
        );
        synchronized (lock) {
            List<Reminder> all = readAll();
            all.add(r);
            writeAll(all);
        }
        log.info("Reminder added: chat={} at={} task='{}'", chatId, remindAtEpochMs, task);
        return r;
    }

    public List<Reminder> listPendingDue(long nowEpochMs) {
        synchronized (lock) {
            return readAll().stream()
                    .filter(r -> !r.sent && r.remindAtEpochMs <= nowEpochMs)
                    .toList();
        }
    }

    public List<Reminder> listPendingForChat(long chatId) {
        synchronized (lock) {
            return readAll().stream()
                    .filter(r -> !r.sent && r.chatId == chatId)
                    .sorted(Comparator.comparingLong(r -> r.remindAtEpochMs))
                    .toList();
        }
    }

    public void markSent(String id) {
        synchronized (lock) {
            List<Reminder> all = readAll();
            for (Reminder r : all) {
                if (id.equals(r.id)) {
                    r.sent = true;
                    break;
                }
            }
            writeAll(all);
        }
    }

    private List<Reminder> readAll() {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return new ArrayList<>();
            return objectMapper.readValue(bytes, new TypeReference<List<Reminder>>() {});
        } catch (IOException e) {
            log.error("Failed to read reminders file {}: {}", file, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeAll(List<Reminder> all) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(all);
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Failed to write reminders file {}: {}", file, e.getMessage());
        }
    }
}
