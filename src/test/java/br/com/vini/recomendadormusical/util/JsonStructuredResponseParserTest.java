package br.com.vini.recomendadormusical.util;

import br.com.vini.recomendadormusical.domain.StructuredMusicAssistantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonStructuredResponseParserTest {

    private final JsonStructuredResponseParser parser = new JsonStructuredResponseParser(new ObjectMapper());

    @Test
    void shouldParseJsonEvenWhenModelWrapsItInMarkdownFence() {
        String response = """
                ```json
                {
                  "message": "Experimente Everlong - Foo Fighters",
                  "preferences": {
                    "name": null,
                    "age": null,
                    "favoriteGenres": ["rock"],
                    "favoriteBands": [],
                    "mood": null,
                    "listeningContext": null,
                    "additionalInfo": null
                  },
                  "shouldSavePreferences": true
                }
                ```
                """;

        StructuredMusicAssistantResponse parsed = parser.parse(response, StructuredMusicAssistantResponse.class);

        assertThat(parsed.message()).contains("Everlong");
        assertThat(parsed.shouldSavePreferences()).isTrue();
        assertThat(parsed.preferences().favoriteGenres()).containsExactly("rock");
    }
}
