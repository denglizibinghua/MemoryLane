package com.memorylane.controller;

import com.memorylane.service.AdvisorService;
import com.memorylane.service.AdvisorService.SuggestResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/advisor")
@RequiredArgsConstructor
public class AdvisorController {

    private final AdvisorService advisorService;

    @PostMapping("/suggest")
    public ResponseEntity<SuggestResult> suggest(@Valid @RequestBody SuggestRequest request) {
        return ResponseEntity.ok(advisorService.suggest(
                request.contactId(), request.lastMessage(), request.recentContext()));
    }

    public record SuggestRequest(
            @NotNull Long contactId,
            @NotBlank String lastMessage,
            List<String> recentContext
    ) {}
}
