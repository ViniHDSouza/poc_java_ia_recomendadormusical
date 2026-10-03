package br.com.vini.recomendadormusical.service;

import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.MusicChatResult;
import br.com.vini.recomendadormusical.exception.ConversationWorkflowException;
import br.com.vini.recomendadormusical.graph.MusicRecommendationState;
import br.com.vini.recomendadormusical.observability.LangSmithTracingService;
import br.com.vini.recomendadormusical.persistence.UserPreferenceMemoryRepository;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MusicRecommendationConversationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MusicRecommendationConversationService.class);

    private final CompiledGraph<MusicRecommendationState> conversationGraph;
    private final UserPreferenceMemoryRepository preferenceMemoryRepository;
    private final LangSmithTracingService tracingService;

    public MusicRecommendationConversationService(
            CompiledGraph<MusicRecommendationState> conversationGraph,
            UserPreferenceMemoryRepository preferenceMemoryRepository,
            LangSmithTracingService tracingService) {
        this.conversationGraph = conversationGraph;
        this.preferenceMemoryRepository = preferenceMemoryRepository;
        this.tracingService = tracingService;
    }

    public MusicChatResult chat(String userId, String requestedThreadId, String message) {
        String publicThreadId = normalizeOrCreateThreadId(requestedThreadId);

        try (LangSmithTracingService.TraceScope trace = tracingService.startRootSpan("music-recommendation.chat")) {
            trace.attribute("app.user_id", userId)
                    .attribute("app.thread_id", publicThreadId)
                    .attribute("app.langgraph", true)
                    .attribute("app.langchain4j", true);

            try {
                // O prefixo do userId impede que duas pessoas diferentes escolham o mesmo
                // threadId e acabem compartilhando por engano um checkpoint do PostgreSQL.
                String internalCheckpointThreadId = userId + "::" + publicThreadId;

                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(internalCheckpointThreadId)
                        .build();

                Map<String, Object> graphInput = new HashMap<>();
                graphInput.put(MusicRecommendationState.USER_ID, userId);
                graphInput.put(MusicRecommendationState.CURRENT_USER_MESSAGE, message);

                // O traceparent acompanha o estado para que nós assíncronos do LangGraph4j
                // continuem aparecendo dentro da mesma trace no LangSmith.
                graphInput.put(MusicRecommendationState.TRACE_PARENT, trace.traceParent());

                MusicRecommendationState finalState = conversationGraph
                        .invoke(GraphInput.args(graphInput), runnableConfig)
                        .orElseThrow(() -> new IllegalStateException("O LangGraph4j terminou sem devolver estado final."));

                ConversationSummary latestLongTermSummary = preferenceMemoryRepository
                        .findSummaryByUserId(userId)
                        .orElse(finalState.conversationSummary().orElse(null));

                String answer = finalState.assistantMessage()
                        .orElseThrow(() -> new IllegalStateException("O estado final não contém a resposta do assistente."));

                trace.attribute("app.preferences_updated", finalState.preferencesWereUpdated())
                        .attribute("app.conversation_summarized", finalState.summarizationPerformed())
                        .attribute("app.messages_in_context", finalState.messages().size());

                LOGGER.info("Thread processada com sucesso. userId={}, threadId={}", userId, publicThreadId);

                return new MusicChatResult(
                        userId,
                        publicThreadId,
                        answer,
                        finalState.preferencesWereUpdated(),
                        finalState.summarizationPerformed(),
                        finalState.messages().size(),
                        latestLongTermSummary,
                        finalState.messages()
                );
            } catch (RuntimeException exception) {
                trace.recordFailure(exception);
                throw exception;
            } catch (Exception exception) {
                trace.recordFailure(exception);
                throw new ConversationWorkflowException("Erro ao executar o fluxo de recomendação musical.", exception);
            }
        }
    }

    public ConversationSummary getLongTermMemory(String userId) {
        return preferenceMemoryRepository
                .findSummaryByUserId(userId)
                .orElse(new ConversationSummary(null, null, java.util.List.of(), java.util.List.of(), null, null));
    }

    private String normalizeOrCreateThreadId(String requestedThreadId) {
        if (requestedThreadId == null || requestedThreadId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestedThreadId.trim();
    }
}
