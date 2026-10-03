package br.com.vini.recomendadormusical.graph.routing;

import br.com.vini.recomendadormusical.graph.MusicRecommendationState;
import org.springframework.stereotype.Component;

@Component
public class ConversationGraphRouter {

    /**
     * Mesmo raciocínio do edgeConditions.ts original:
     * 1) se a IA encontrou preferência nova, primeiro persistimos;
     * 2) senão, se o contexto cresceu demais, resumimos;
     * 3) caso contrário, encerramos esta execução do grafo.
     */
    public String decideNextStepAfterChat(MusicRecommendationState state) {
        if (state.shouldSavePreferences()) {
            return ConversationRoutingDecision.SAVE_PREFERENCES;
        }
        if (state.needsSummarization()) {
            return ConversationRoutingDecision.SUMMARIZE;
        }
        return ConversationRoutingDecision.FINISH;
    }

    public String decideNextStepAfterSavingPreferences(MusicRecommendationState state) {
        return state.needsSummarization()
                ? ConversationRoutingDecision.SUMMARIZE
                : ConversationRoutingDecision.FINISH;
    }
}
