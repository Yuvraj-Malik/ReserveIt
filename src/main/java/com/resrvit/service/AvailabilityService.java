package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.engine.Slot;
import com.resrvit.engine.SlotEngine;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Answers "is this free?" and "what is free nearby?" for equipment and rooms. */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final EquipmentRepository equipment;
    private final BookingRepository bookings;
    private final MaintenanceLogRepository maintenance;
    private final CheckoutRepository checkouts;
    private final CalendarService calendar;
    private final TimeService time;

    // ---------- equipment ----------

    /** Items of this type in this lab that can be lent on that day (not retired, not under maintenance). */
    public List<Equipment> usableItems(Lab lab, EquipmentType type, LocalDate day) {
        List<Equipment> out = new ArrayList<>();
        for (Equipment e : equipment.findByLabAndType(lab, type)) {
            if (e.getStatus() == EquipmentStatus.RETIRED) continue;
            if (e.getStatus() == EquipmentStatus.MAINTENANCE) continue;
            if (!maintenance.activeForEquipment(e, day).isEmpty()) continue;
            out.add(e);
        }
        return out;
    }

    public int capacity(Lab lab, EquipmentType type, LocalDate day) {
        return usableItems(lab, type, day).size();
    }

    public int used(Lab lab, EquipmentType type, LocalDate day) {
        return bookings.equipmentOnDay(lab, type, day, Booking.OCCUPYING).size();
    }

    public int free(Lab lab, EquipmentType type, LocalDate day) {
        return Math.max(0, capacity(lab, type, day) - used(lab, type, day));
    }

    /** A specific usable item nobody holds that day, or empty. */
    public Optional<Equipment> freeItem(Lab lab, EquipmentType type, LocalDate day, Long ignoreBookingId) {
        Set<Long> taken = new HashSet<>();
        for (Booking b : bookings.equipmentOnDay(lab, type, day, Booking.OCCUPYING)) {
            if (b.getEquipment() != null && !b.getId().equals(ignoreBookingId)) taken.add(b.getEquipment().getId());
        }
        boolean today = day.equals(time.today());
        for (Equipment e : usableItems(lab, type, day)) {
            if (taken.contains(e.getId())) continue;
            // An item still out from an earlier booking can't be issued again today.
            if (today && !checkouts.findByEquipmentAndReturnedAtIsNull(e).isEmpty()) continue;
            return Optional.of(e);
        }
        return Optional.empty();
    }

    /**
     * Nearest days (other than `exclude`) in [from, to] with a free item of this type,
     * found by the Scala slot engine. Holidays are skipped.
     */
    public List<LocalDate> nearestFreeDays(Lab lab, EquipmentType type, LocalDate target,
                                           LocalDate from, LocalDate to, LocalDate exclude, int k) {
        if (to.isBefore(from)) return List.of();
        Map<LocalDate, Integer> cap = new HashMap<>();
        Map<LocalDate, Integer> used = new HashMap<>();
        Set<LocalDate> blocked = new HashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            cap.put(d, capacity(lab, type, d));
            used.put(d, 0);
            if (calendar.isHoliday(d)) blocked.add(d);
        }
        for (Booking b : bookings.equipmentBetween(lab, type, from, to, Booking.OCCUPYING)) {
            used.merge(b.getSlotDate(), 1, Integer::sum);
        }
        if (exclude != null) blocked.add(exclude);
        return SlotEngine.nearestFreeDays(target, from, to, cap, used, blocked, k);
    }

    // ---------- rooms ----------

    /** Everything that occupies a room on a day: classes, approved bookings, society windows, maintenance. */
    public List<Slot> roomBusy(Room room, LocalDate day, Long ignoreBookingId) {
        List<Slot> busy = new ArrayList<>(calendar.timetableBusy(room, day));
        for (Booking b : bookings.roomOnDay(room, day, Booking.OCCUPYING)) {
            if (!b.getId().equals(ignoreBookingId)) busy.add(new Slot(day, b.getStartTime(), b.getEndTime()));
        }
        for (Booking b : bookings.societyOverlapping(room, day, day, Booking.OCCUPYING)) {
            if (!b.getId().equals(ignoreBookingId) && !b.getExcludedDateList().contains(day)) {
                busy.add(calendar.societyWindow(day));
            }
        }
        if (!maintenance.activeForRoom(room, day, day).isEmpty()) {
            busy.add(new Slot(day, LocalTime.MIN, LocalTime.MAX));
        }
        return busy;
    }

    public boolean isRoomFree(Room room, LocalDate day, LocalTime start, LocalTime end, Long ignoreBookingId) {
        return SlotEngine.isFree(new Slot(day, start, end), roomBusy(room, day, ignoreBookingId));
    }

    /** Nearest free slots of the same length in this room over the next `days` days (GD rooms use the fixed slots). */
    public List<Slot> nearestRoomSlots(Room room, LocalDate day, LocalTime start, LocalTime end,
                                       int days, int k, Long ignoreBookingId) {
        List<Slot> candidates = new ArrayList<>();
        List<Slot> busy = new ArrayList<>();
        LocalDate first = day.minusDays(days).isBefore(time.today()) ? time.today() : day.minusDays(days);
        long minutes = java.time.Duration.between(start, end).toMinutes();
        for (LocalDate d = first; !d.isAfter(day.plusDays(days)); d = d.plusDays(1)) {
            if (room.getType() == RoomType.GD_ROOM) {
                for (LocalTime[] s : CalendarService.GD_SLOTS) candidates.add(new Slot(d, s[0], s[1]));
            } else {
                for (LocalTime t = LocalTime.of(8, 0); !t.plusMinutes(minutes).isAfter(LocalTime.of(20, 0)); t = t.plusMinutes(30)) {
                    candidates.add(new Slot(d, t, t.plusMinutes(minutes)));
                }
            }
            busy.addAll(roomBusy(room, d, ignoreBookingId));
        }
        candidates.removeIf(s -> s.startAt().isBefore(time.now()));
        candidates.removeIf(s -> s.date().equals(day) && s.start().equals(start));
        return SlotEngine.nearestFreeSlots(new Slot(day, start, end), candidates, busy, k);
    }
}
