package br.com.vini.recomendadormusical.config;

import br.com.vini.recomendadormusical.graph.GraphNodeNames;
import br.com.vini.recomendadormusical.graph.MusicRecommendationState;
import br.com.vini.recomendadormusical.graph.node.ChatWithUserNode;
import br.com.vini.recomendadormusical.graph.node.SaveUserPreferencesNode;
import br.com.vini.recomendadormusical.graph.node.SummarizeConversationNode;
import br.com.vini.recomendadormusical.graph.routing.ConversationGraphRouter;
import br.com.vini.recomendadormusical.graph.routing.ConversationRoutingDecision;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.PostgresSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class MusicRecommendationGraphConfiguration {

    /**
     * O PostgresSaverV2 é o equivalente Java do checkpointer PostgreSQL usado
     * no projeto JavaScript. Ele permite que o histórico/estado de uma thread
     * sobreviva ao restart da aplicação.
     */
    @Bean
    public PostgresSaverV2 conversationCheckpointSaver(ApplicationProperties properties) throws Exception {
        ApplicationProperties.Postgres postgres = properties.getPostgres();

        return PostgresSaverV2.builder()
                .host(postgres.getHost())
                .port(postgres.getPort())
                .database(postgres.getDatabase())
                .user(postgres.getUser())
                .password(postgres.getPassword())
                .stateSerializer(new ObjectStreamStateSerializer<>(MusicRecommendationState::new))
                .createTables(true)
                .build();
    }

    @Bean
    public CompiledGraph<MusicRecommendationState> musicRecommendationConversationGraph(
            ChatWithUserNode chatWithUserNode,
            SaveUserPreferencesNode saveUserPreferencesNode,
            SummarizeConversationNode summarizeConversationNode,
            ConversationGraphRouter router,
            PostgresSaverV2 checkpointSaver) throws Exception {

        StateGraph<MusicRecommendationState> stateGraph = new StateGraph<>(MusicRecommendationState::new)
                .addNode(GraphNodeNames.CHAT_WITH_USER, node_async(chatWithUserNode))
                .addNode(GraphNodeNames.SAVE_USER_PREFERENCES, node_async(saveUserPreferencesNode))
                .addNode(GraphNodeNames.SUMMARIZE_CONVERSATION, node_async(summarizeConversationNode))
                .addEdge(START, GraphNodeNames.CHAT_WITH_USER)
                .addConditionalEdges(
                        GraphNodeNames.CHAT_WITH_USER,
                        edge_async(router::decideNextStepAfterChat),
                        Map.of(
                                ConversationRoutingDecision.SAVE_PREFERENCES, GraphNodeNames.SAVE_USER_PREFERENCES,
                                ConversationRoutingDecision.SUMMARIZE, GraphNodeNames.SUMMARIZE_CONVERSATION,
                                ConversationRoutingDecision.FINISH, END
                        )
                )
                .addConditionalEdges(
                        GraphNodeNames.SAVE_USER_PREFERENCES,
                        edge_async(router::decideNextStepAfterSavingPreferences),
                        Map.of(
                                ConversationRoutingDecision.SUMMARIZE, GraphNodeNames.SUMMARIZE_CONVERSATION,
                                ConversationRoutingDecision.FINISH, END
                        )
                )
                .addEdge(GraphNodeNames.SUMMARIZE_CONVERSATION, END);

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                // false = não apague a thread ao chegar no END; queremos memória entre requisições.
                .releaseThread(false)
                .build();

        return stateGraph.compile(compileConfig);
    }
}
