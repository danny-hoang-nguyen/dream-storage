package com.flownetworks.bot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramMessage {
    private Long messageId;
    private TelegramUser from;
    private TelegramChat chat;

    @JsonProperty("text")
    private String text;

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public TelegramUser getFrom() {
        return from;
    }

    public void setFrom(TelegramUser from) {
        this.from = from;
    }

    public TelegramChat getChat() {
        return chat;
    }

    public void setChat(TelegramChat chat) {
        this.chat = chat;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
