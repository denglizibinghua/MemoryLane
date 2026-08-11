package com.memorylane.controller;

import com.memorylane.dto.CreateReminderRequest;
import com.memorylane.dto.ReminderDTO;
import com.memorylane.service.ReminderService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reminders")
@Validated
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public ResponseEntity<List<ReminderDTO>> listReminders(
            @RequestParam(required = false) Long contactId,
            @RequestParam(required = false)
            @Pattern(regexp = "pending|confirmed|triggered|dismissed")
            String status) {
        return ResponseEntity.ok(reminderService.getReminders(contactId, status));
    }

    @GetMapping("/due")
    public ResponseEntity<Map<String, Object>> getDueReminders() {
        List<ReminderDTO> triggered = reminderService.getReminders(null, "triggered");
        long pendingCount = reminderService.countByStatus("pending");
        long confirmedCount = reminderService.countByStatus("confirmed");
        return ResponseEntity.ok(Map.of(
                "triggered", triggered,
                "pendingCount", pendingCount,
                "confirmedCount", confirmedCount
        ));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReminderDTO> confirmReminder(@PathVariable Long id) {
        return reminderService.confirmReminder(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<ReminderDTO> dismissReminder(@PathVariable Long id) {
        return reminderService.dismissReminder(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createReminder(@Valid @RequestBody CreateReminderRequest req) {
        try {
            return ResponseEntity.ok(reminderService.createManual(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReminderDTO> updateReminder(@PathVariable Long id,
                                                       @Valid @RequestBody CreateReminderRequest req) {
        return reminderService.updateReminder(id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ConstraintViolationException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid parameter: " + e.getMessage()));
    }
}
