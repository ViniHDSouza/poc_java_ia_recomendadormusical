package br.com.vini.recomendadormusical.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * Contrato JSON esperado do LLM. A resposta para o usuário e a extração de
 * preferências chegam juntas, como no projeto JavaScript original.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StructuredMusicAssistantResponse(
        String message,
        ExtractedMusicPreferences preferences,
        boolean shouldSavePreferences
) implements Serializable {
}
