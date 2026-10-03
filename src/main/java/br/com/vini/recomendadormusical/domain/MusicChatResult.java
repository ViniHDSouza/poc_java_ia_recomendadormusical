package br.com.vini.recomendadormusical.domain;

import java.util.List;

public record MusicChatResult(
        String userId,
        String threadId,
        String answer,
        boolean preferencesWereUpdated,
        boolean conversationWasSummarized,
        int messagesKeptInCurrentContext,
        ConversationSummary longTermSummary,
        List<ConversationMessage> currentContextMessages
) {
}
