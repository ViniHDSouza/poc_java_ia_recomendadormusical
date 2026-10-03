package br.com.vini.recomendadormusical.api;

import br.com.vini.recomendadormusical.api.dto.ApiErrorResponse;
import br.com.vini.recomendadormusical.exception.InvalidArtificialIntelligenceResponseException;
import br.com.vini.recomendadormusical.exception.MissingOpenRouterApiKeyException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Requisição inválida.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Requisição inválida.", List.of(exception.getMessage()));
    }

    @ExceptionHandler(MissingOpenRouterApiKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingApiKey(MissingOpenRouterApiKeyException exception) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidArtificialIntelligenceResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAiResponse(InvalidArtificialIntelligenceResponseException exception) {
        LOGGER.warn("Resposta inválida recebida da IA", exception);
        return buildResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(Exception exception) {
        LOGGER.error("Erro inesperado na API", exception);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno ao processar a solicitação.",
                List.of(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            List<String> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details
        ));
    }
}
