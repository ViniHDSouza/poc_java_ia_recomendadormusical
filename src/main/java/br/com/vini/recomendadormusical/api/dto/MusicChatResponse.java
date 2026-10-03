package br.com.vini.recomendadormusical.api.dto;

import br.com.vini.recomendadormusical.domain.ConversationMessage;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.MusicChatResult;

import java.util.List;

public record MusicChatResponse(
        String userId,
        String threadId,
        String answer,
        boolean preferencesWereUpdated,
        boolean conversationWasSummarized,
        int messagesKeptInCurrentContext,
        ConversationSummary longTermMemory,
        List<ConversationMessage> currentContextMessages
) {
    public static MusicChatResponse from(MusicChatResult result) {
        return new MusicChatResponse(
                result.userId(),
                result.threadId(),
                result.answer(),
                result.preferencesWereUpdated(),
                result.conversationWasSummarized(),
                result.messagesKeptInCurrentContext(),
                result.longTermSummary(),
                result.currentContextMessages()
        );
    }
}
