package com.aiclassrecovery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.dto.ChatMessage;
import com.aiclassrecovery.dto.ChatResponse;
import com.aiclassrecovery.service.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/classes/{id}")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(Authentication auth,
                                             @PathVariable String id,
                                             @Valid @RequestBody ChatMessage message) {
        String userId = auth.getName();
        return ResponseEntity.ok(chatService.chat(userId, id, message.getMessage()));
    }
}
