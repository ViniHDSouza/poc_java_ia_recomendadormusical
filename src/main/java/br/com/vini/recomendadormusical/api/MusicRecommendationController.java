package br.com.vini.recomendadormusical.api;

import br.com.vini.recomendadormusical.api.dto.MusicChatRequest;
import br.com.vini.recomendadormusical.api.dto.MusicChatResponse;
import br.com.vini.recomendadormusical.domain.ConversationSummary;
import br.com.vini.recomendadormusical.service.MusicRecommendationConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class MusicRecommendationController {

    private final MusicRecommendationConversationService conversationService;

    public MusicRecommendationController(MusicRecommendationConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/music/chat")
    public ResponseEntity<MusicChatResponse> chat(@Valid @RequestBody MusicChatRequest request) {
        return ResponseEntity.ok(MusicChatResponse.from(
                conversationService.chat(request.userId(), request.threadId(), request.message())
        ));
    }

    @GetMapping("/users/{userId}/preferences")
    public ResponseEntity<ConversationSummary> getLongTermPreferences(
            @PathVariable @NotBlank String userId) {
        return ResponseEntity.ok(conversationService.getLongTermMemory(userId));
    }
}
