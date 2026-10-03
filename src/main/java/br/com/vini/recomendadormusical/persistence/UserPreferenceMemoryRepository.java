package br.com.vini.recomendadormusical.persistence;

import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.domain.ExtractedMusicPreferences;

import java.util.Optional;

/**
 * Porta simples para a memória de longo prazo do usuário.
 * O projeto original usa SQLite para esse papel e a migração mantém a mesma ideia.
 */
public interface UserPreferenceMemoryRepository {

    Optional<ConversationSummary> findSummaryByUserId(String userId);

    ConversationSummary mergeExtractedPreferences(String userId, ExtractedMusicPreferences preferences);

    ConversationSummary storeConversationSummary(String userId, ConversationSummary summary);
}
