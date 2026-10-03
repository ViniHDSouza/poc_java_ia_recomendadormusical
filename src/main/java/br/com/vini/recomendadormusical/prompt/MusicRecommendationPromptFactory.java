package br.com.vini.recomendadormusical.prompt;

import br.com.vini.recomendadormusical.domain.ConversationMessage;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centraliza os prompts para que o código do fluxo não fique misturado com
 * instruções de linguagem natural. Isto torna mais fácil comparar o Java com
 * os prompts do projeto JavaScript original.
 */
@Component
public class MusicRecommendationPromptFactory {

    public String createChatSystemPrompt(ConversationSummary savedSummary) {
        return """
                Você é um assistente musical amigável e entusiasmado.

                OBJETIVOS:
                1. Conversar naturalmente sobre música.
                2. Quando fizer sentido, recomendar músicas ESPECÍFICAS (nome da música + artista/banda).
                3. Extrair somente preferências que o usuário declarou explicitamente.
                4. Nunca registrar como preferência do usuário algo que foi apenas uma recomendação sua.

                MEMÓRIA DE LONGO PRAZO JÁ CONHECIDA:
                %s

                RESPONDA SOMENTE com um objeto JSON válido neste formato:
                {
                  "message": "resposta natural para o usuário",
                  "preferences": {
                    "name": null,
                    "age": null,
                    "favoriteGenres": [],
                    "favoriteBands": [],
                    "mood": null,
                    "listeningContext": null,
                    "additionalInfo": null
                  },
                  "shouldSavePreferences": false
                }

                REGRAS PARA shouldSavePreferences:
                - true somente quando houver pelo menos uma preferência/fato NOVO explicitamente dito pelo usuário.
                - false quando a mensagem não trouxer preferência nova.
                - se não houver preferências novas, mantenha preferences com campos nulos/listas vazias.

                Exemplos do que pode ser salvo:
                - "Meu nome é Ana" -> name=Ana.
                - "Tenho 30 anos" -> age=30.
                - "Gosto de rock e jazz" -> favoriteGenres=[rock,jazz].
                - "Minha banda favorita é Queen" -> favoriteBands=[Queen].
                - "Hoje quero algo calmo para estudar" -> mood=calmo e/ou listeningContext=estudar.

                Exemplo do que NÃO pode ser salvo:
                - Você recomenda "Everlong - Foo Fighters" e o usuário ainda não disse que gosta da banda.
                  Foo Fighters NÃO vira preferência apenas porque você recomendou.
                """.formatted(formatSummary(savedSummary));
    }

    public String createChatUserPrompt(String currentUserMessage, List<ConversationMessage> messagesBeforeCurrentTurn) {
        return """
                HISTÓRICO RECENTE DA CONVERSA:
                %s

                MENSAGEM ATUAL DO USUÁRIO:
                %s

                Responda ao usuário e faça a extração de preferências seguindo rigorosamente o JSON solicitado.
                """.formatted(formatConversation(messagesBeforeCurrentTurn), currentUserMessage);
    }

    public String createSummarizationSystemPrompt() {
        return """
                Você resume conversas musicais para criar uma memória útil de longo prazo.

                Extraia e preserve SOMENTE informações que o usuário realmente declarou.
                Não transforme recomendações do assistente em gosto do usuário.
                Quando já existir um resumo anterior, preserve informações úteis antigas e atualize com fatos novos.

                RESPONDA SOMENTE com JSON válido no formato:
                {
                  "name": null,
                  "age": null,
                  "favoriteGenres": [],
                  "favoriteBands": [],
                  "keyPreferences": null,
                  "importantContext": null
                }

                keyPreferences pode condensar gostos como estilos, épocas, características sonoras e preferências de descoberta.
                importantContext pode condensar contexto como estudar, treinar, relaxar, humor recorrente ou outros fatos úteis.
                """;
    }

    public String createSummarizationUserPrompt(
            ConversationSummary previousSummary,
            List<ConversationMessage> conversationMessages) {
        return """
                RESUMO ANTERIOR:
                %s

                CONVERSA A SER RESUMIDA/ATUALIZADA:
                %s

                Gere o novo resumo consolidado em JSON.
                """.formatted(formatSummary(previousSummary), formatConversation(conversationMessages));
    }

    private String formatConversation(List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "<sem histórico ainda>";
        }

        return messages.stream()
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n"));
    }

    private String formatSummary(ConversationSummary summary) {
        if (summary == null) {
            return "<nenhuma memória de longo prazo cadastrada>";
        }

        return """
                nome: %s
                idade: %s
                gêneros favoritos: %s
                bandas/artistas favoritos: %s
                preferências principais: %s
                contexto importante: %s
                """.formatted(
                nullSafe(summary.name()),
                summary.age() == null ? "não informado" : summary.age(),
                listOrEmpty(summary.favoriteGenres()),
                listOrEmpty(summary.favoriteBands()),
                nullSafe(summary.keyPreferences()),
                nullSafe(summary.importantContext())
        );
    }

    private Object listOrEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "não informado" : value;
    }
}
