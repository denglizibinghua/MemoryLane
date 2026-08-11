package com.memorylane.scheduler;

import com.memorylane.service.ReminderService;
import com.memorylane.service.ReminderService.PromiseCandidate;
import com.memorylane.service.TimeExpressionParserService;
import com.memorylane.service.TimeExpressionParserService.ParsedTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic reminder sweeper.
 *
 * <p>Runs every 60 seconds with a three-phase pipeline:
 * <ol>
 *   <li><b>Load candidates</b> — inside a read-only transaction, collect
 *       unprocessed promise memories with their contact names</li>
 *   <li><b>LLM time parsing</b> — outside any transaction, call the time-
 *       expression parser for each candidate (may take seconds per call)</li>
 *   <li><b>Individual saves</b> — each parsed candidate gets its own short-lived
 *       write transaction via {@link ReminderService#createReminder}</li>
 * </ol>
 *
 * <p>After scanning, triggers any due reminders.
 */
@Slf4j
@Component
public class ReminderScheduler {

    private final ReminderService reminderService;
    private final TimeExpressionParserService timeParser;

    public ReminderScheduler(ReminderService reminderService,
                             TimeExpressionParserService timeParser) {
        this.reminderService = reminderService;
        this.timeParser = timeParser;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void sweep() {
        log.debug("Reminder sweep started");

        // ── Phase 1: load candidates (inside transaction) ──
        var candidates = reminderService.loadCandidates();
        if (candidates.isEmpty()) {
            log.debug("No new promise memories to process");
        } else {
            int created = 0;
            // ── Phase 2: LLM parsing (outside any transaction) ──
            for (PromiseCandidate c : candidates) {
                ParsedTime parsed = timeParser.parse(c.memoryId(), c.content(), c.contactName());
                if (!parsed.hasTime() || parsed.eventTime() == null) continue;

                // ── Phase 3: persist (individual short-lived tx) ──
                if (reminderService.createReminder(c.memoryId(), parsed) != null) {
                    created++;
                }
            }
            log.info("Sweep scan: candidates={}, created={}", candidates.size(), created);
        }

        // ── Trigger due reminders ──
        try {
            reminderService.triggerDueReminders();
        } catch (Exception e) {
            log.error("triggerDueReminders failed", e);
        }
    }
}
