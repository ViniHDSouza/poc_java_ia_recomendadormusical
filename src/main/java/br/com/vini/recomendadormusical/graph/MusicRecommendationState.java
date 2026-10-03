package br.com.vini.recomendadormusical.graph;

import br.com.vini.recomendadormusical.domain.ConversationMessage;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.ExtractedMusicPreferences;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Estado compartilhado entre os nós do LangGraph4j.
 *
 * Pense neste objeto como uma "mochila" que viaja pelo grafo. Cada nó lê o que
 * precisa e devolve somente as alterações que quer aplicar à mochila.
 */
public class MusicRecommendationState extends AgentState {

    public static final String USER_ID = "userId";
    public static final String CURRENT_USER_MESSAGE = "currentUserMessage";
    public static final String MESSAGES = "messages";
    public static final String ASSISTANT_MESSAGE = "assistantMessage";
    public static final String EXTRACTED_PREFERENCES = "extractedPreferences";
    public static final String SHOULD_SAVE_PREFERENCES = "shouldSavePreferences";
    public static final String NEEDS_SUMMARIZATION = "needsSummarization";
    public static final String CONVERSATION_SUMMARY = "conversationSummary";
    public static final String PREFERENCES_WERE_UPDATED = "preferencesWereUpdated";
    public static final String SUMMARIZATION_PERFORMED = "summarizationPerformed";
    public static final String TRACE_PARENT = "traceParent";

    public MusicRecommendationState(Map<String, Object> initData) {
        super(initData);
    }

    public String userId() {
        return this.<String>value(USER_ID).orElseThrow(() -> new IllegalStateException("userId ausente no estado do grafo."));
    }

    public String currentUserMessage() {
        return this.<String>value(CURRENT_USER_MESSAGE)
                .orElseThrow(() -> new IllegalStateException("currentUserMessage ausente no estado do grafo."));
    }

    public List<ConversationMessage> messages() {
        return this.<List<ConversationMessage>>value(MESSAGES).orElse(List.of());
    }

    public Optional<String> assistantMessage() {
        return this.<String>value(ASSISTANT_MESSAGE);
    }

    public Optional<ExtractedMusicPreferences> extractedPreferences() {
        return this.<ExtractedMusicPreferences>value(EXTRACTED_PREFERENCES);
    }

    public boolean shouldSavePreferences() {
        return this.<Boolean>value(SHOULD_SAVE_PREFERENCES).orElse(false);
    }

    public boolean needsSummarization() {
        return this.<Boolean>value(NEEDS_SUMMARIZATION).orElse(false);
    }

    public Optional<ConversationSummary> conversationSummary() {
        return this.<ConversationSummary>value(CONVERSATION_SUMMARY);
    }

    public boolean preferencesWereUpdated() {
        return this.<Boolean>value(PREFERENCES_WERE_UPDATED).orElse(false);
    }

    public boolean summarizationPerformed() {
        return this.<Boolean>value(SUMMARIZATION_PERFORMED).orElse(false);
    }

    public String traceParent() {
        return this.<String>value(TRACE_PARENT).orElse(null);
    }
}
