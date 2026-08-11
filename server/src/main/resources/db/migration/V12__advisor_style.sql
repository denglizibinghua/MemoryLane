-- V12: AI advisor style — user-selectable conversation tone.
-- Built-in styles: default, humorous, cute, gentle, cool, tsundere.
-- AdvisorService reads this field and injects style-specific personality
-- into the system prompt. No user-facing prompt editing required.

ALTER TABLE ai_settings
    ADD COLUMN IF NOT EXISTS advisor_style VARCHAR(20) NOT NULL DEFAULT 'default';
