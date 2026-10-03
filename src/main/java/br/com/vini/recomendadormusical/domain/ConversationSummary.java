package br.com.vini.recomendadormusical.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

/**
 * Memória condensada de longo prazo. O objetivo não é copiar a conversa inteira,
 * mas guardar somente fatos musicais úteis para futuras recomendações.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversationSummary(
        String name,
        Integer age,
        List<String> favoriteGenres,
        List<String> favoriteBands,
        String keyPreferences,
        String importantContext
) implements Serializable {
}
