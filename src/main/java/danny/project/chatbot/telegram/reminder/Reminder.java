package danny.project.chatbot.telegram.reminder;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Reminder {
    public String id;
    public long chatId;
    public String task;
    public long remindAtEpochMs;
    public long createdAtEpochMs;
    public boolean sent;

    public Reminder() {}

    public Reminder(String id, long chatId, String task, long remindAtEpochMs, long createdAtEpochMs) {
        this.id = id;
        this.chatId = chatId;
        this.task = task;
        this.remindAtEpochMs = remindAtEpochMs;
        this.createdAtEpochMs = createdAtEpochMs;
        this.sent = false;
    }
}
