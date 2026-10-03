package br.com.vini.recomendadormusical.service;

import br.com.vini.recomendadormusical.config.ApplicationProperties;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.StructuredMusicAssistantResponse;
import br.com.vini.recomendadormusical.exception.MissingOpenRouterApiKeyException;
import br.com.vini.recomendadormusical.observability.LangSmithTracingService;
import br.com.vini.recomendadormusical.util.JsonStructuredResponseParser;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

/**
 * Cliente do LLM usando LangChain4j sobre a API OpenAI-compatible do OpenRouter.
 *
 * <p>Esta classe é o equivalente Java do OpenRouterService do projeto original.
 * Ela deixa o restante da aplicação independente de detalhes HTTP do provedor.</p>
 */
@Service
public class OpenRouterMusicAssistantClient {

    /**
     * Pede JSON Mode de forma explícita. Isso é mais robusto do que depender
     * somente da instrução textual no prompt e permite que o roteador do
     * OpenRouter escolha modelos compatíveis com saída estruturada.
     */
    private static final ResponseFormat JSON_RESPONSE_FORMAT = ResponseFormat.builder()
            .type(JSON)
            .build();

    private final ApplicationProperties properties;
    private final JsonStructuredResponseParser responseParser;
    private final LangSmithTracingService tracingService;
    private volatile ChatModel chatModel;

    public OpenRouterMusicAssistantClient(
            ApplicationProperties properties,
            JsonStructuredResponseParser responseParser,
            LangSmithTracingService tracingService) {
        this.properties = properties;
        this.responseParser = responseParser;
        this.tracingService = tracingService;
    }

    public StructuredMusicAssistantResponse generateStructuredChatResponse(
            String systemPrompt,
            String userPrompt) {
        String rawResponse = sendPromptsToModel("music-assistant-response", systemPrompt, userPrompt);
        return responseParser.parse(rawResponse, StructuredMusicAssistantResponse.class);
    }

    public ConversationSummary generateConversationSummary(
            String systemPrompt,
            String userPrompt) {
        String rawResponse = sendPromptsToModel("conversation-summary", systemPrompt, userPrompt);
        return responseParser.parse(rawResponse, ConversationSummary.class);
    }

    private String sendPromptsToModel(String operationName, String systemPrompt, String userPrompt) {
        ApplicationProperties.OpenRouter openRouter = properties.getOpenrouter();

        try (LangSmithTracingService.TraceScope trace = tracingService.startSpan("llm.openrouter." + operationName)) {
            trace.attribute("langsmith.span.kind", "llm")
                    .attribute("gen_ai.system", "openrouter")
                    .attribute("gen_ai.operation.name", "chat")
                    .attribute("gen_ai.request.model", openRouter.getModel())
                    .attribute("llm.request.type", "chat")
                    .attribute("app.llm_operation", operationName);

            try {
                ChatRequest request = ChatRequest.builder()
                        .messages(
                                SystemMessage.from(systemPrompt),
                                UserMessage.from(userPrompt)
                        )
                        .responseFormat(JSON_RESPONSE_FORMAT)
                        .build();

                String response = getOrCreateChatModel()
                        .chat(request)
                        .aiMessage()
                        .text();

                // Por padrão, não colocamos prompt/resposta completos no tracing para evitar
                // enviar conteúdo sensível. Os logs detalhados do LangChain4j também ficam
                // desligados por padrão e podem ser ativados pelo .env.
                trace.attribute("app.response_received", response != null && !response.isBlank());
                return response;
            } catch (RuntimeException exception) {
                trace.recordFailure(exception);
                throw exception;
            }
        }
    }

    private ChatModel getOrCreateChatModel() {
        ChatModel localModel = chatModel;
        if (localModel == null) {
            synchronized (this) {
                localModel = chatModel;
                if (localModel == null) {
                    chatModel = localModel = createChatModel();
                }
            }
        }
        return localModel;
    }

    private ChatModel createChatModel() {
        ApplicationProperties.OpenRouter openRouter = properties.getOpenrouter();
        if (openRouter.getApiKey() == null || openRouter.getApiKey().isBlank()) {
            throw new MissingOpenRouterApiKeyException();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (hasText(openRouter.getHttpReferer())) {
            headers.put("HTTP-Referer", openRouter.getHttpReferer());
        }
        if (hasText(openRouter.getXTitle())) {
            headers.put("X-Title", openRouter.getXTitle());
        }

        // Mantém a política de roteamento usada pelo projeto JavaScript original:
        // o modelo principal também é enviado em `models` e o OpenRouter prioriza
        // provedores por throughput, sem particionar a ordenação por modelo.
        Map<String, Object> providerSort = Map.of(
                "by", openRouter.getProviderSortBy(),
                "partition", openRouter.getProviderPartition()
        );
        Map<String, Object> customParameters = Map.of(
                "models", List.of(openRouter.getModel()),
                "provider", Map.of("sort", providerSort)
        );

        return OpenAiChatModel.builder()
                .baseUrl(openRouter.getBaseUrl())
                .apiKey(openRouter.getApiKey())
                .modelName(openRouter.getModel())
                .temperature(openRouter.getTemperature())
                .timeout(Duration.ofSeconds(openRouter.getTimeoutSeconds()))
                .maxRetries(openRouter.getMaxRetries())
                .customHeaders(headers)
                .customParameters(customParameters)
                .logRequests(openRouter.isLogRequests())
                .logResponses(openRouter.isLogResponses())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
