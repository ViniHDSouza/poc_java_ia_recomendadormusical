package br.com.vini.recomendadormusical.util;

import br.com.vini.recomendadormusical.exception.InvalidArtificialIntelligenceResponseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonStructuredResponseParser {

    private final ObjectMapper objectMapper;

    public JsonStructuredResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Alguns modelos gratuitos devolvem o JSON dentro de ```json ... ``` mesmo
     * quando pedimos JSON puro. Este método tolera esse comportamento sem esconder
     * respostas realmente inválidas.
     */
    public <T> T parse(String rawResponse, Class<T> responseType) {
        try {
            String json = extractFirstJsonObject(rawResponse);
            T parsedResponse = objectMapper.readValue(json, responseType);
            if (parsedResponse == null) {
                throw new IllegalArgumentException("A resposta JSON foi convertida para null.");
            }
            return parsedResponse;
        } catch (Exception exception) {
            throw new InvalidArtificialIntelligenceResponseException(
                    "A IA não devolveu o JSON esperado. Trecho recebido: " + abbreviate(rawResponse),
                    exception
            );
        }
    }

    String extractFirstJsonObject(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalArgumentException("A resposta da IA está vazia.");
        }

        String cleaned = rawResponse
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace < 0 || lastBrace < firstBrace) {
            throw new IllegalArgumentException("Não foi encontrado um objeto JSON na resposta.");
        }

        return cleaned.substring(firstBrace, lastBrace + 1);
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "<nulo>";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300) + "...";
    }
}
