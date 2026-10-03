package br.com.vini.recomendadormusical.observability;

import br.com.vini.recomendadormusical.config.ApplicationProperties;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Integra a aplicação Java com o LangSmith usando OpenTelemetry/OTLP.
 *
 * <p>No projeto JavaScript da aula, LangChain/LangGraph reconhecem as variáveis
 * LANGSMITH_* e produzem tracing automaticamente. Em Java, a integração equivalente
 * é feita explicitamente: criamos spans OpenTelemetry e os enviamos para o endpoint
 * OTLP do LangSmith.</p>
 */
@Service
public class LangSmithTracingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LangSmithTracingService.class);
    private static final String INSTRUMENTATION_SCOPE = "poc-java-ia-recomendadormusical";

    private final boolean tracingEnabled;
    private final Tracer tracer;
    private final SdkTracerProvider tracerProvider;

    public LangSmithTracingService(ApplicationProperties properties) {
        ApplicationProperties.LangSmith langSmith = properties.getLangsmith();

        this.tracingEnabled = langSmith.isTracingEnabled()
                && hasText(langSmith.getApiKey());

        if (!langSmith.isTracingEnabled()) {
            this.tracerProvider = null;
            this.tracer = OpenTelemetry.noop().getTracer(INSTRUMENTATION_SCOPE);
            LOGGER.info("LangSmith tracing desabilitado.");
            return;
        }

        if (!hasText(langSmith.getApiKey())) {
            this.tracerProvider = null;
            this.tracer = OpenTelemetry.noop().getTracer(INSTRUMENTATION_SCOPE);
            LOGGER.warn("LANGSMITH_TRACING está habilitado, mas LANGSMITH_API_KEY não foi informada. Tracing ficará inativo.");
            return;
        }

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(langSmith.getOtlpEndpoint())
                .addHeader("x-api-key", langSmith.getApiKey())
                .addHeader("Langsmith-Project", langSmith.getProject())
                .build();

        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), langSmith.getServiceName(),
                        AttributeKey.stringKey("langsmith.project"), langSmith.getProject()
                ))
        );

        this.tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);

        LOGGER.info(
                "LangSmith tracing habilitado via OpenTelemetry. projeto={}, endpoint={}",
                langSmith.getProject(),
                langSmith.getOtlpEndpoint()
        );
    }

    /**
     * Inicia um span raiz para uma nova requisição da API.
     */
    public TraceScope startRootSpan(String spanName) {
        return startSpan(spanName)
                .attribute("langsmith.trace.name", spanName);
    }

    /**
     * Inicia um span filho do Context.current(), quando houver um span atual.
     */
    public TraceScope startSpan(String spanName) {
        Span span = tracer.spanBuilder(spanName).startSpan();
        Scope scope = span.makeCurrent();
        return new TraceScope(span, scope)
                .attribute("langsmith.span.kind", "chain");
    }

    /**
     * Inicia um span usando o traceparent recebido do span raiz da requisição.
     *
     * <p>Isso é importante porque os nós do LangGraph4j podem ser executados em
     * outra thread. O Context.current() de OpenTelemetry não é garantido nesses
     * saltos assíncronos, então carregamos o traceparent no próprio estado do grafo.</p>
     */
    public TraceScope startChildSpan(String spanName, String traceParent) {
        Context parentContext = parseTraceParent(traceParent);
        Span span = tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .startSpan();
        Scope scope = span.makeCurrent();
        return new TraceScope(span, scope)
                .attribute("langsmith.span.kind", "chain");
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    @PreDestroy
    public void close() {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }

    private Context parseTraceParent(String traceParent) {
        if (!hasText(traceParent)) {
            return Context.current();
        }

        try {
            String[] parts = traceParent.split("-");
            if (parts.length != 4) {
                return Context.current();
            }

            TraceFlags flags = "01".equals(parts[3])
                    ? TraceFlags.getSampled()
                    : TraceFlags.getDefault();

            SpanContext parentSpanContext = SpanContext.createFromRemoteParent(
                    parts[1],
                    parts[2],
                    flags,
                    TraceState.getDefault()
            );

            if (!parentSpanContext.isValid()) {
                return Context.current();
            }

            return Context.root().with(Span.wrap(parentSpanContext));
        } catch (RuntimeException exception) {
            LOGGER.debug("traceparent inválido recebido; iniciando span sem pai explícito.", exception);
            return Context.current();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Escopo AutoCloseable para evitar spans abertos por esquecimento.
     */
    public static final class TraceScope implements AutoCloseable {

        private final Span span;
        private final Scope scope;
        private boolean closed;

        private TraceScope(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }

        public TraceScope attribute(String name, String value) {
            if (value != null) {
                span.setAttribute(name, value);
            }
            return this;
        }

        public TraceScope attribute(String name, boolean value) {
            span.setAttribute(name, value);
            return this;
        }

        public TraceScope attribute(String name, long value) {
            span.setAttribute(name, value);
            return this;
        }

        public String traceParent() {
            SpanContext context = span.getSpanContext();
            String flags = context.isSampled() ? "01" : "00";
            return "00-" + context.getTraceId() + "-" + context.getSpanId() + "-" + flags;
        }

        public void recordFailure(Throwable throwable) {
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR, throwable.getMessage() == null ? "erro" : throwable.getMessage());
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            scope.close();
            span.end();
        }
    }
}
