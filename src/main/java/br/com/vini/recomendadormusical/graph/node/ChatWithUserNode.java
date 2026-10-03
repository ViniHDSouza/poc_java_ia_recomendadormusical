package br.com.vini.recomendadormusical.graph.node;

import br.com.vini.recomendadormusical.config.ApplicationProperties;
import br.com.vini.recomendadormusical.domain.ConversationMessage;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.ExtractedMusicPreferences;
import br.com.vini.recomendadormusical.domain.StructuredMusicAssistantResponse;
import br.com.vini.recomendadormusical.graph.MusicRecommendationState;
import br.com.vini.recomendadormusical.observability.LangSmithTracingService;
import br.com.vini.recomendadormusical.persistence.UserPreferenceMemoryRepository;
import br.com.vini.recomendadormusical.prompt.MusicRecommendationPromptFactory;
import br.com.vini.recomendadormusical.service.OpenRouterMusicAssistantClient;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatWithUserNode implements NodeAction<MusicRecommendationState> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatWithUserNode.class);

    private final UserPreferenceMemoryRepository preferenceMemoryRepository;
    private final MusicRecommendationPromptFactory promptFactory;
    private final OpenRouterMusicAssistantClient musicAssistantClient;
    private final ApplicationProperties properties;
    private final LangSmithTracingService tracingService;

    public ChatWithUserNode(
            UserPreferenceMemoryRepository preferenceMemoryRepository,
            MusicRecommendationPromptFactory promptFactory,
            OpenRouterMusicAssistantClient musicAssistantClient,
            ApplicationProperties properties,
            LangSmithTracingService tracingService) {
        this.preferenceMemoryRepository = preferenceMemoryRepository;
        this.promptFactory = promptFactory;
        this.musicAssistantClient = musicAssistantClient;
        this.properties = properties;
        this.tracingService = tracingService;
    }

    @Override
    public Map<String, Object> apply(MusicRecommendationState state) {
        try (LangSmithTracingService.TraceScope trace = tracingService.startChildSpan(
                "langgraph.node.chat-with-user",
                state.traceParent())) {
            trace.attribute("langgraph.node", "chatWithUser")
                    .attribute("app.user_id", state.userId())
                    .attribute("app.messages_before_turn", state.messages().size());

            try {
                String userId = state.userId();
                String currentMessage = state.currentUserMessage();

                ConversationSummary savedLongTermMemory = preferenceMemoryRepository
                        .findSummaryByUserId(userId)
                        .orElse(null);

                List<ConversationMessage> messagesBeforeCurrentTurn = state.messages();

                String systemPrompt = promptFactory.createChatSystemPrompt(savedLongTermMemory);
                String userPrompt = promptFactory.createChatUserPrompt(currentMessage, messagesBeforeCurrentTurn);

                StructuredMusicAssistantResponse response = musicAssistantClient
                        .generateStructuredChatResponse(systemPrompt, userPrompt);

                if (response.message() == null || response.message().isBlank()) {
                    throw new IllegalStateException("A IA retornou um JSON sem o campo message preenchido.");
                }

                List<ConversationMessage> updatedMessages = new ArrayList<>(messagesBeforeCurrentTurn);
                updatedMessages.add(ConversationMessage.fromUser(currentMessage));
                updatedMessages.add(ConversationMessage.fromAssistant(response.message()));

                ExtractedMusicPreferences extractedPreferences = response.preferences();
                boolean shouldSavePreferences = response.shouldSavePreferences()
                        && extractedPreferences != null
                        && extractedPreferences.hasAnyInformation();

                boolean needsSummarization = updatedMessages.size()
                        >= properties.getMemory().getMaxMessagesBeforeSummary();

                trace.attribute("app.should_save_preferences", shouldSavePreferences)
                        .attribute("app.needs_summarization", needsSummarization)
                        .attribute("app.messages_after_turn", updatedMessages.size());

                LOGGER.info(
                        "Chat processado para userId={}. mensagensNoContexto={}, salvarPreferencias={}, resumir={}",
                        userId,
                        updatedMessages.size(),
                        shouldSavePreferences,
                        needsSummarization
                );

                Map<String, Object> updates = new HashMap<>();
                updates.put(MusicRecommendationState.MESSAGES, List.copyOf(updatedMessages));
                updates.put(MusicRecommendationState.ASSISTANT_MESSAGE, response.message());
                updates.put(MusicRecommendationState.SHOULD_SAVE_PREFERENCES, shouldSavePreferences);
                updates.put(MusicRecommendationState.NEEDS_SUMMARIZATION, needsSummarization);
                updates.put(MusicRecommendationState.PREFERENCES_WERE_UPDATED, false);
                updates.put(MusicRecommendationState.SUMMARIZATION_PERFORMED, false);

                if (extractedPreferences != null) {
                    updates.put(MusicRecommendationState.EXTRACTED_PREFERENCES, extractedPreferences);
                }
                if (savedLongTermMemory != null) {
                    updates.put(MusicRecommendationState.CONVERSATION_SUMMARY, savedLongTermMemory);
                }

                return updates;
            } catch (RuntimeException exception) {
                trace.recordFailure(exception);
                throw exception;
            }
        }
    }
}
