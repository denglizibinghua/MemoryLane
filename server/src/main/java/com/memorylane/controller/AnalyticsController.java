package com.memorylane.controller;

import com.memorylane.service.AnalyticsService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@Validated
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/contact/{id}/trends")
    public ResponseEntity<List<AnalyticsService.TrendPoint>> getTrends(
            @PathVariable long id,
            @RequestParam(defaultValue = "week")
            @Pattern(regexp = "week|month", message = "granularity must be week or month")
            String granularity) {
        return ResponseEntity.ok(analyticsService.getTrends(id, granularity));
    }

    @GetMapping("/overview")
    public ResponseEntity<List<AnalyticsService.ContactStats>> getOverview() {
        return ResponseEntity.ok(analyticsService.getOverview());
    }
}
