package br.com.vini.recomendadormusical.graph.node;

import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.ExtractedMusicPreferences;
import br.com.vini.recomendadormusical.graph.MusicRecommendationState;
import br.com.vini.recomendadormusical.observability.LangSmithTracingService;
import br.com.vini.recomendadormusical.persistence.UserPreferenceMemoryRepository;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SaveUserPreferencesNode implements NodeAction<MusicRecommendationState> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaveUserPreferencesNode.class);

    private final UserPreferenceMemoryRepository preferenceMemoryRepository;
    private final LangSmithTracingService tracingService;

    public SaveUserPreferencesNode(
            UserPreferenceMemoryRepository preferenceMemoryRepository,
            LangSmithTracingService tracingService) {
        this.preferenceMemoryRepository = preferenceMemoryRepository;
        this.tracingService = tracingService;
    }

    @Override
    public Map<String, Object> apply(MusicRecommendationState state) {
        try (LangSmithTracingService.TraceScope trace = tracingService.startChildSpan(
                "langgraph.node.save-user-preferences",
                state.traceParent())) {
            trace.attribute("langgraph.node", "saveUserPreferences")
                    .attribute("app.user_id", state.userId());

            try {
                ExtractedMusicPreferences preferences = state.extractedPreferences().orElse(null);

                Map<String, Object> updates = new HashMap<>();
                updates.put(MusicRecommendationState.SHOULD_SAVE_PREFERENCES, false);

                if (preferences == null || !preferences.hasAnyInformation()) {
                    trace.attribute("app.preferences_saved", false);
                    updates.put(MusicRecommendationState.PREFERENCES_WERE_UPDATED, false);
                    return updates;
                }

                ConversationSummary updatedLongTermMemory = preferenceMemoryRepository
                        .mergeExtractedPreferences(state.userId(), preferences);

                LOGGER.info("Preferências de longo prazo atualizadas no SQLite para userId={}", state.userId());

                trace.attribute("app.preferences_saved", true);
                updates.put(MusicRecommendationState.CONVERSATION_SUMMARY, updatedLongTermMemory);
                updates.put(MusicRecommendationState.PREFERENCES_WERE_UPDATED, true);
                return updates;
            } catch (RuntimeException exception) {
                trace.recordFailure(exception);
                throw exception;
            }
        }
    }
}
