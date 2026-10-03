package br.com.vini.recomendadormusical.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

/**
 * Preferências que a IA conseguiu extrair explicitamente da mensagem do usuário.
 * Campos nulos/vazios significam que aquela informação não foi dita.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedMusicPreferences(
        String name,
        Integer age,
        List<String> favoriteGenres,
        List<String> favoriteBands,
        String mood,
        String listeningContext,
        String additionalInfo
) implements Serializable {

    public boolean hasAnyInformation() {
        return hasText(name)
                || age != null
                || hasItems(favoriteGenres)
                || hasItems(favoriteBands)
                || hasText(mood)
                || hasText(listeningContext)
                || hasText(additionalInfo);
    }

    private static boolean hasItems(List<String> values) {
        return values != null && values.stream().anyMatch(ExtractedMusicPreferences::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
