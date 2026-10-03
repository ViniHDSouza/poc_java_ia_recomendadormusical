package br.com.vini.recomendadormusical.domain;

import java.io.Serializable;
import java.time.Instant;

/**
 * Uma mensagem simples da conversa. Mantemos nosso próprio tipo em vez de
 * guardar objetos internos do provedor de IA para deixar o estado do grafo
 * fácil de entender e serializar no PostgreSQL.
 */
public record ConversationMessage(
        MessageRole role,
        String content,
        Instant createdAt
) implements Serializable {

    public static ConversationMessage fromUser(String content) {
        return new ConversationMessage(MessageRole.USER, content, Instant.now());
    }

    public static ConversationMessage fromAssistant(String content) {
        return new ConversationMessage(MessageRole.ASSISTANT, content, Instant.now());
    }
}
