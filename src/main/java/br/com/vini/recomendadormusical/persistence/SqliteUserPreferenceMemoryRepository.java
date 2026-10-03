package br.com.vini.recomendadormusical.persistence;

import br.com.vini.recomendadormusical.config.ApplicationProperties;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.ExtractedMusicPreferences;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class SqliteUserPreferenceMemoryRepository implements UserPreferenceMemoryRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final String sqliteJdbcUrl;
    private final Path sqliteFilePath;

    public SqliteUserPreferenceMemoryRepository(
            ApplicationProperties properties,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.sqliteFilePath = Path.of(properties.getMemory().getSqlitePreferencesPath()).toAbsolutePath().normalize();
        this.sqliteJdbcUrl = "jdbc:sqlite:" + sqliteFilePath;
    }

    @PostConstruct
    public void initializePreferenceDatabase() {
        try {
            Path parentDirectory = sqliteFilePath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS user_preferences (
                            user_id TEXT PRIMARY KEY,
                            name TEXT,
                            age INTEGER,
                            favorite_genres TEXT NOT NULL DEFAULT '[]',
                            favorite_bands TEXT NOT NULL DEFAULT '[]',
                            key_preferences TEXT,
                            important_context TEXT,
                            updated_at TEXT NOT NULL
                        )
                        """);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Não foi possível inicializar o SQLite em " + sqliteFilePath,
                    exception
            );
        }
    }

    @Override
    public synchronized Optional<ConversationSummary> findSummaryByUserId(String userId) {
        String sql = """
                SELECT name, age, favorite_genres, favorite_bands, key_preferences, important_context
                FROM user_preferences
                WHERE user_id = ?
                """;

        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSummary(resultSet));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Erro ao consultar preferências do usuário no SQLite.", exception);
        }
    }

    @Override
    public synchronized ConversationSummary mergeExtractedPreferences(
            String userId,
            ExtractedMusicPreferences preferences) {
        ConversationSummary current = findSummaryByUserId(userId).orElse(emptySummary());

        ConversationSummary merged = new ConversationSummary(
                firstMeaningful(preferences.name(), current.name()),
                preferences.age() != null ? preferences.age() : current.age(),
                mergeDistinctValues(current.favoriteGenres(), preferences.favoriteGenres()),
                mergeDistinctValues(current.favoriteBands(), preferences.favoriteBands()),
                current.keyPreferences(),
                mergeContext(current.importantContext(), buildPreferenceContext(preferences))
        );

        upsert(userId, merged);
        return merged;
    }

    @Override
    public synchronized ConversationSummary storeConversationSummary(
            String userId,
            ConversationSummary newSummary) {
        ConversationSummary current = findSummaryByUserId(userId).orElse(emptySummary());

        // Se o LLM deixar algum campo ausente, preservamos a memória antiga em vez de apagá-la.
        ConversationSummary merged = new ConversationSummary(
                firstMeaningful(newSummary.name(), current.name()),
                newSummary.age() != null ? newSummary.age() : current.age(),
                mergeDistinctValues(current.favoriteGenres(), newSummary.favoriteGenres()),
                mergeDistinctValues(current.favoriteBands(), newSummary.favoriteBands()),
                firstMeaningful(newSummary.keyPreferences(), current.keyPreferences()),
                firstMeaningful(newSummary.importantContext(), current.importantContext())
        );

        upsert(userId, merged);
        return merged;
    }

    private void upsert(String userId, ConversationSummary summary) {
        String sql = """
                INSERT INTO user_preferences (
                    user_id, name, age, favorite_genres, favorite_bands,
                    key_preferences, important_context, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    name = excluded.name,
                    age = excluded.age,
                    favorite_genres = excluded.favorite_genres,
                    favorite_bands = excluded.favorite_bands,
                    key_preferences = excluded.key_preferences,
                    important_context = excluded.important_context,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, summary.name());
            if (summary.age() == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setInt(3, summary.age());
            }
            statement.setString(4, objectMapper.writeValueAsString(safeList(summary.favoriteGenres())));
            statement.setString(5, objectMapper.writeValueAsString(safeList(summary.favoriteBands())));
            statement.setString(6, summary.keyPreferences());
            statement.setString(7, summary.importantContext());
            statement.setString(8, Instant.now().toString());
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Erro ao salvar preferências do usuário no SQLite.", exception);
        }
    }

    private ConversationSummary mapSummary(ResultSet resultSet) throws Exception {
        Integer age = resultSet.getObject("age") == null ? null : resultSet.getInt("age");
        return new ConversationSummary(
                resultSet.getString("name"),
                age,
                parseStringList(resultSet.getString("favorite_genres")),
                parseStringList(resultSet.getString("favorite_bands")),
                resultSet.getString("key_preferences"),
                resultSet.getString("important_context")
        );
    }

    private List<String> parseStringList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, STRING_LIST_TYPE);
    }

    private List<String> mergeDistinctValues(List<String> existing, List<String> incoming) {
        Map<String, String> byNormalizedValue = new LinkedHashMap<>();
        addValues(byNormalizedValue, existing);
        addValues(byNormalizedValue, incoming);
        return List.copyOf(byNormalizedValue.values());
    }

    private void addValues(Map<String, String> values, List<String> candidates) {
        if (candidates == null) {
            return;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                String trimmed = candidate.trim();
                values.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
    }

    private String buildPreferenceContext(ExtractedMusicPreferences preferences) {
        List<String> parts = new ArrayList<>();
        if (hasText(preferences.mood())) {
            parts.add("Humor/momento: " + preferences.mood().trim());
        }
        if (hasText(preferences.listeningContext())) {
            parts.add("Contexto para ouvir música: " + preferences.listeningContext().trim());
        }
        if (hasText(preferences.additionalInfo())) {
            parts.add(preferences.additionalInfo().trim());
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String mergeContext(String existing, String incoming) {
        if (!hasText(incoming)) {
            return existing;
        }
        if (!hasText(existing)) {
            return incoming;
        }
        if (existing.toLowerCase(Locale.ROOT).contains(incoming.toLowerCase(Locale.ROOT))) {
            return existing;
        }
        return existing + " | " + incoming;
    }

    private String firstMeaningful(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private ConversationSummary emptySummary() {
        return new ConversationSummary(null, null, List.of(), List.of(), null, null);
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(sqliteJdbcUrl);
    }
}
