package com.resrvit.service;

import com.resrvit.domain.CalendarDay;
import com.resrvit.domain.DayType;
import com.resrvit.domain.Room;
import com.resrvit.domain.TimetableSlot;
import com.resrvit.engine.Slot;
import com.resrvit.repo.CalendarDayRepository;
import com.resrvit.repo.TimetableSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Academic calendar and timetable. Dates not in the uploaded calendar default to
 * working days Monday–Friday and holidays at the weekend.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    public static final LocalTime SOCIETY_WORKING_START = LocalTime.of(17, 30);
    public static final LocalTime SOCIETY_WORKING_END = LocalTime.of(21, 30);
    public static final LocalTime SOCIETY_HOLIDAY_START = LocalTime.of(11, 0);
    public static final LocalTime SOCIETY_HOLIDAY_END = LocalTime.of(20, 0);

    /** Equipment pick-up window. A pending request expires when the window closes unapproved. */
    public static final LocalTime LAB_OPEN = LocalTime.of(9, 0);
    public static final LocalTime LAB_CLOSE = LocalTime.of(17, 0);

    /** GD rooms run four fixed two-hour slots. */
    public static final List<LocalTime[]> GD_SLOTS = List.of(
            new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(11, 0)},
            new LocalTime[]{LocalTime.of(11, 0), LocalTime.of(13, 0)},
            new LocalTime[]{LocalTime.of(14, 0), LocalTime.of(16, 0)},
            new LocalTime[]{LocalTime.of(16, 0), LocalTime.of(18, 0)});

    private final CalendarDayRepository days;
    private final TimetableSlotRepository timetable;

    public DayType dayType(LocalDate d) {
        return days.findByCalDate(d).map(CalendarDay::getDayType).orElseGet(() ->
                d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY
                        ? DayType.HOLIDAY : DayType.WORKING);
    }

    public boolean isHoliday(LocalDate d) {
        return dayType(d) == DayType.HOLIDAY;
    }

    /** The fixed society window for a day: 5:30–9:30 PM on working days, 11 AM–8 PM on holidays. */
    public Slot societyWindow(LocalDate d) {
        return isHoliday(d)
                ? new Slot(d, SOCIETY_HOLIDAY_START, SOCIETY_HOLIDAY_END)
                : new Slot(d, SOCIETY_WORKING_START, SOCIETY_WORKING_END);
    }

    /** Classes only run on working and exam days. */
    public List<Slot> timetableBusy(Room room, LocalDate d) {
        List<Slot> out = new ArrayList<>();
        if (isHoliday(d)) return out;
        for (TimetableSlot t : timetable.findByRoomAndDayOfWeek(room, d.getDayOfWeek().getValue())) {
            out.add(new Slot(d, t.getStartTime(), t.getEndTime()));
        }
        return out;
    }

    public static boolean isGdSlot(LocalTime start, LocalTime end) {
        return GD_SLOTS.stream().anyMatch(s -> s[0].equals(start) && s[1].equals(end));
    }
}
