package br.com.vini.recomendadormusical.persistence;

import br.com.vini.recomendadormusical.config.ApplicationProperties;
import br.com.vini.recomendadormusical.domain.ExtractedMusicPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteUserPreferenceMemoryRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldMergePreferencesWithoutDuplicatingGenresIgnoringCase() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getMemory().setSqlitePreferencesPath(tempDirectory.resolve("preferences.db").toString());

        SqliteUserPreferenceMemoryRepository repository =
                new SqliteUserPreferenceMemoryRepository(properties, new ObjectMapper());
        repository.initializePreferenceDatabase();

        repository.mergeExtractedPreferences("vini", new ExtractedMusicPreferences(
                "Vini", 40, List.of("Rock"), List.of("Queen"), null, null, null
        ));
        repository.mergeExtractedPreferences("vini", new ExtractedMusicPreferences(
                null, null, List.of("rock", "Jazz"), List.of(), "calmo", "estudar", null
        ));

        var summary = repository.findSummaryByUserId("vini").orElseThrow();

        assertThat(summary.favoriteGenres()).containsExactly("Rock", "Jazz");
        assertThat(summary.favoriteBands()).containsExactly("Queen");
        assertThat(summary.importantContext()).contains("calmo").contains("estudar");
    }
}
