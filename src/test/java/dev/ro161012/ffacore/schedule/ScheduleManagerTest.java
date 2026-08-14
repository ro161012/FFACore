package dev.ro161012.ffacore.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ScheduleManager#parseTime(String)}.
 */
class ScheduleManagerTest {

    @Test
    void parsesSeconds() {
        assertEquals(30, ScheduleManager.parseTime("30s"));
    }

    @Test
    void parsesMinutes() {
        assertEquals(300, ScheduleManager.parseTime("5m"));
    }

    @Test
    void parsesHours() {
        assertEquals(3600, ScheduleManager.parseTime("1h"));
    }

    @Test
    void parsesDays() {
        assertEquals(86400, ScheduleManager.parseTime("1d"));
    }

    @Test
    void parsesWeeks() {
        assertEquals(604800, ScheduleManager.parseTime("1w"));
    }

    @Test
    void rejectsInvalidInput() {
        assertEquals(-1, ScheduleManager.parseTime("nope"));
        assertEquals(-1, ScheduleManager.parseTime(""));
        assertEquals(-1, ScheduleManager.parseTime("10x"));
    }
}
