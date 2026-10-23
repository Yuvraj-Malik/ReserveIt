package com.resrvit;

import com.resrvit.engine.Slot;
import com.resrvit.engine.SlotEngine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SlotEngineTest {

    private final LocalDate mon = LocalDate.of(2026, 11, 2);

    @Test
    void nearestFreeDaysPrefersCloserThenEarlier() {
        Map<LocalDate, Integer> cap = new HashMap<>(), used = new HashMap<>();
        for (int i = 0; i < 7; i++) { cap.put(mon.plusDays(i), 1); used.put(mon.plusDays(i), 0); }
        used.put(mon.plusDays(3), 1); // the target day is full
        List<LocalDate> out = SlotEngine.nearestFreeDays(mon.plusDays(3), mon, mon.plusDays(6), cap, used, Set.of(mon.plusDays(3)), 3);
        assertThat(out).containsExactly(mon.plusDays(2), mon.plusDays(4), mon.plusDays(1));
    }

    @Test
    void blockedAndFullDaysAreSkipped() {
        Map<LocalDate, Integer> cap = Map.of(mon, 2, mon.plusDays(1), 2, mon.plusDays(2), 0);
        Map<LocalDate, Integer> used = Map.of(mon, 2, mon.plusDays(1), 0, mon.plusDays(2), 0);
        List<LocalDate> out = SlotEngine.nearestFreeDays(mon, mon, mon.plusDays(2), cap, used, Set.of(mon.plusDays(1)), 5);
        assertThat(out).isEmpty();
    }

    @Test
    void nearestFreeSlotsAvoidsBusy() {
        Slot target = new Slot(mon, LocalTime.of(9, 0), LocalTime.of(11, 0));
        List<Slot> candidates = List.of(target, new Slot(mon, LocalTime.of(11, 0), LocalTime.of(13, 0)), new Slot(mon, LocalTime.of(14, 0), LocalTime.of(16, 0)));
        List<Slot> busy = List.of(new Slot(mon, LocalTime.of(10, 0), LocalTime.of(12, 0)));
        assertThat(SlotEngine.nearestFreeSlots(target, candidates, busy, 2)).containsExactly(candidates.get(2));
        assertThat(SlotEngine.isFree(new Slot(mon, LocalTime.of(12, 0), LocalTime.of(13, 0)), busy)).isTrue();
    }
}
