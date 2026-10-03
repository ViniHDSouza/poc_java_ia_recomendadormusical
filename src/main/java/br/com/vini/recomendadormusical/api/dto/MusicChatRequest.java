package br.com.vini.recomendadormusical.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MusicChatRequest(
        @NotBlank(message = "userId é obrigatório")
        @Size(max = 100, message = "userId deve ter no máximo 100 caracteres")
        String userId,

        @Size(max = 150, message = "threadId deve ter no máximo 150 caracteres")
        String threadId,

        @NotBlank(message = "message é obrigatória")
        @Size(max = 10_000, message = "message deve ter no máximo 10000 caracteres")
        String message
) {
}
