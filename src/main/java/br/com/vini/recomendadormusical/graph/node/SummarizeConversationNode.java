package br.com.vini.recomendadormusical.graph.node;

import br.com.vini.recomendadormusical.domain.ConversationMessage;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.graph.MusicRecommendationState;
import br.com.vini.recomendadormusical.observability.LangSmithTracingService;
import br.com.vini.recomendadormusical.persistence.UserPreferenceMemoryRepository;
import br.com.vini.recomendadormusical.prompt.MusicRecommendationPromptFactory;
import br.com.vini.recomendadormusical.service.OpenRouterMusicAssistantClient;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SummarizeConversationNode implements NodeAction<MusicRecommendationState> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SummarizeConversationNode.class);
    private static final int RECENT_MESSAGES_TO_KEEP = 2;

    private final UserPreferenceMemoryRepository preferenceMemoryRepository;
    private final MusicRecommendationPromptFactory promptFactory;
    private final OpenRouterMusicAssistantClient musicAssistantClient;
    private final LangSmithTracingService tracingService;

    public SummarizeConversationNode(
            UserPreferenceMemoryRepository preferenceMemoryRepository,
            MusicRecommendationPromptFactory promptFactory,
            OpenRouterMusicAssistantClient musicAssistantClient,
            LangSmithTracingService tracingService) {
        this.preferenceMemoryRepository = preferenceMemoryRepository;
        this.promptFactory = promptFactory;
        this.musicAssistantClient = musicAssistantClient;
        this.tracingService = tracingService;
    }

    @Override
    public Map<String, Object> apply(MusicRecommendationState state) {
        try (LangSmithTracingService.TraceScope trace = tracingService.startChildSpan(
                "langgraph.node.summarize-conversation",
                state.traceParent())) {
            trace.attribute("langgraph.node", "summarizeConversation")
                    .attribute("app.user_id", state.userId())
                    .attribute("app.messages_to_summarize", state.messages().size());

            try {
                ConversationSummary previousSummary = preferenceMemoryRepository
                        .findSummaryByUserId(state.userId())
                        .orElse(state.conversationSummary().orElse(null));

                List<ConversationMessage> messagesToSummarize = state.messages();

                ConversationSummary generatedSummary = musicAssistantClient.generateConversationSummary(
                        promptFactory.createSummarizationSystemPrompt(),
                        promptFactory.createSummarizationUserPrompt(previousSummary, messagesToSummarize)
                );

                ConversationSummary savedSummary = preferenceMemoryRepository
                        .storeConversationSummary(state.userId(), generatedSummary);

                List<ConversationMessage> recentMessages = keepOnlyRecentMessages(messagesToSummarize);

                trace.attribute("app.messages_kept_after_summary", recentMessages.size());

                LOGGER.info(
                        "Conversa resumida para userId={}. mensagensAntes={}, mensagensMantidas={}",
                        state.userId(),
                        messagesToSummarize.size(),
                        recentMessages.size()
                );

                Map<String, Object> updates = new HashMap<>();
                updates.put(MusicRecommendationState.CONVERSATION_SUMMARY, savedSummary);
                updates.put(MusicRecommendationState.MESSAGES, recentMessages);
                updates.put(MusicRecommendationState.NEEDS_SUMMARIZATION, false);
                updates.put(MusicRecommendationState.SUMMARIZATION_PERFORMED, true);
                return updates;
            } catch (RuntimeException exception) {
                trace.recordFailure(exception);
                throw exception;
            }
        }
    }

    private List<ConversationMessage> keepOnlyRecentMessages(List<ConversationMessage> messages) {
        if (messages.size() <= RECENT_MESSAGES_TO_KEEP) {
            return List.copyOf(messages);
        }

        int firstMessageToKeep = messages.size() - RECENT_MESSAGES_TO_KEEP;
        return List.copyOf(messages.subList(firstMessageToKeep, messages.size()));
    }
}
