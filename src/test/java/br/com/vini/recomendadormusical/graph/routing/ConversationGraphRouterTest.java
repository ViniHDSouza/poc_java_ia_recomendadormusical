package br.com.vini.recomendadormusical.graph.routing;

import br.com.vini.recomendadormusical.graph.MusicRecommendationState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationGraphRouterTest {

    private final ConversationGraphRouter router = new ConversationGraphRouter();

    @Test
    void shouldSavePreferencesBeforeSummarizingWhenBothActionsAreNeeded() {
        MusicRecommendationState state = new MusicRecommendationState(Map.of(
                MusicRecommendationState.SHOULD_SAVE_PREFERENCES, true,
                MusicRecommendationState.NEEDS_SUMMARIZATION, true
        ));

        assertThat(router.decideNextStepAfterChat(state))
                .isEqualTo(ConversationRoutingDecision.SAVE_PREFERENCES);
        assertThat(router.decideNextStepAfterSavingPreferences(state))
                .isEqualTo(ConversationRoutingDecision.SUMMARIZE);
    }

    @Test
    void shouldFinishWhenNothingElseIsRequired() {
        MusicRecommendationState state = new MusicRecommendationState(Map.of(
                MusicRecommendationState.SHOULD_SAVE_PREFERENCES, false,
                MusicRecommendationState.NEEDS_SUMMARIZATION, false
        ));

        assertThat(router.decideNextStepAfterChat(state))
                .isEqualTo(ConversationRoutingDecision.FINISH);
    }
}
