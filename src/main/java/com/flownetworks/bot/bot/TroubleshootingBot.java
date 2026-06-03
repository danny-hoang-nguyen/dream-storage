package com.flownetworks.bot.bot;

import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.ChannelAccount;
import com.flownetworks.bot.service.ClaudeService;
import com.flownetworks.bot.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class TroubleshootingBot extends ActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(TroubleshootingBot.class);

    private final ClaudeService claudeService;
    private final ConversationService conversationService;
    private final Executor botTaskExecutor;

    public TroubleshootingBot(
            ClaudeService claudeService,
            ConversationService conversationService,
            @Qualifier("botTaskExecutor") Executor botTaskExecutor) {
        this.claudeService = claudeService;
        this.conversationService = conversationService;
        this.botTaskExecutor = botTaskExecutor;
    }

    @Override
    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
        String rawMessage = turnContext.getActivity().getText();
        if (rawMessage == null || rawMessage.isBlank()) {
            return turnContext.sendActivity(
                MessageFactory.text("Bạn gửi tin trống rồi 😅 Hỏi gì đi!")
            ).thenApply(result -> null);
        }

        final String userMessage = rawMessage.trim();
        final String userId = turnContext.getActivity().getFrom().getId();
        // Key history theo conversationId để hoạt động đúng trong cả 1-1 và group chat
        final String conversationId = turnContext.getActivity().getConversation().getId();

        log.info("[{}] {}", conversationId, userMessage);
        log.debug("[user={}] conversationId={} channel={} serviceUrl={} messageLen={}",
            userId,
            conversationId,
            turnContext.getActivity().getChannelId(),
            turnContext.getActivity().getServiceUrl(),
            userMessage.length());

        // Lệnh đặc biệt
        if ("/clear".equalsIgnoreCase(userMessage)) {
            conversationService.clearHistory(conversationId);
            return turnContext.sendActivity(
                MessageFactory.text("Đã reset conversation. Hỏi lại từ đầu đi!")
            ).thenApply(result -> null);
        }

        // Gọi Claude (blocking) trên executor riêng để không block adapter thread.
        return CompletableFuture
            .supplyAsync(() -> {
                List<Map<String, String>> history = conversationService.getHistory(conversationId);
                String reply = claudeService.chat(history, userMessage);

                // Lưu cả 2 turn trong 1 lần ghi Redis
                conversationService.appendMessages(conversationId, List.of(
                    Map.entry("user", userMessage),
                    Map.entry("assistant", reply)
                ));
                log.debug("[{}] reply length={} chars", conversationId, reply.length());
                return reply;
            }, botTaskExecutor)
            .exceptionally(ex -> {
                log.error("[{}] Failed to process message: {}", conversationId, ex.getMessage(), ex);
                return "Có lỗi xảy ra khi xử lý câu hỏi của bạn 😞 Vui lòng thử lại sau.";
            })
            .thenCompose(reply ->
                turnContext.sendActivity(MessageFactory.text(reply))
                    .thenApply(result -> null));
    }

    @Override
    protected CompletableFuture<Void> onMembersAdded(
            List<ChannelAccount> membersAdded, TurnContext turnContext) {

        String welcome = """
                👋 Hey! Mình là **Backend Dev Assistant**.

                Mình có thể giúp bạn:
                • Tìm tài liệu kỹ thuật trên **Coda** (API docs, ADR, runbook...)
                • Tra cứu **Jira tickets** liên quan đến bug/feature bạn đang làm
                • Giải đáp câu hỏi về code, architecture, debugging

                Cứ hỏi tự nhiên, mình sẽ tìm trong Coda + Jira trước khi trả lời.

                *(Gõ `/clear` để reset conversation)*
                """;

        String botId = turnContext.getActivity().getRecipient().getId();

        // Chào tất cả member mới (trừ chính bot)
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ChannelAccount member : membersAdded) {
            if (!member.getId().equals(botId)) {
                chain = chain.thenCompose(ignored ->
                    turnContext.sendActivity(MessageFactory.text(welcome))
                        .thenApply(r -> null));
            }
        }
        return chain;
    }
}
