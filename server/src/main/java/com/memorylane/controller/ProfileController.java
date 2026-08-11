package com.memorylane.controller;

import com.memorylane.entity.UserProfile;
import com.memorylane.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<UserProfile> getProfile() {
        return ResponseEntity.ok(profileService.getOrCreate());
    }

    @PutMapping
    public ResponseEntity<UserProfile> saveProfile(@RequestBody UserProfile profile) {
        return ResponseEntity.ok(profileService.save(profile));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, String>> analyze() {
        return ResponseEntity.ok(profileService.analyze());
    }
}
