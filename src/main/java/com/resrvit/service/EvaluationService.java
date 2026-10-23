package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Faculty enter evaluation dates; students pick from these for evaluation practice.
 * Adding a date upgrades matching existing bookings (same lab and item type, practice or
 * coursework, in the 7 days before) to protected evaluation bookings.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("EEE d MMM");

    private final EvaluationScheduleRepository evaluations;
    private final LabRepository labs;
    private final EquipmentTypeRepository types;
    private final BookingRepository bookings;
    private final AuditService audit;
    private final NotificationService notifications;
    private final TimeService time;

    @Transactional
    public Created create(User faculty, Long labId, Long typeId, String course, String title, java.time.LocalDate date) {
        if (faculty.getRole() != Role.FACULTY && faculty.getRole() != Role.SUPER_ADMIN) throw new BookingException("Only faculty add evaluations.");
        Lab lab = labs.findById(labId == null ? -1 : labId).orElseThrow(() -> new BookingException("Pick a lab."));
        if (date == null || date.isBefore(time.today())) throw new BookingException("Pick a date from today on.");
        if (course == null || course.isBlank() || title == null || title.isBlank()) throw new BookingException("Give the course and a title.");
        EvaluationSchedule e = new EvaluationSchedule();
        e.setFaculty(faculty);
        e.setLab(lab);
        e.setEquipmentType(typeId == null ? null : types.findById(typeId).orElse(null));
        e.setCourseCode(course.trim().toUpperCase());
        e.setTitle(title.trim());
        e.setEvalDate(date);
        e.setCreatedAt(time.now());
        evaluations.save(e);

        int upgraded = 0;
        for (Booking b : bookings.unprotectedEvalPractice(lab, date, Booking.LIVE)) {
            if (!b.getSlotDate().isBefore(date) || b.getSlotDate().isBefore(date.minusDays(7))) continue;
            if (e.getEquipmentType() != null && !e.getEquipmentType().sameAs(b.getEquipmentType())) continue;
            b.setEvaluation(e);
            b.setProtectedEval(true);
            b.setReason(BookingReason.EVALUATION_PRACTICE);
            bookings.save(b);
            audit.log(b, faculty, ApprovalAction.PROTECTED, "Linked to " + e.getCourseCode() + " " + e.getTitle() + " on " + D.format(date));
            notifications.notifyBookingParties(b, "Your booking on " + b.getWhenLabel() + " is now a protected evaluation booking ("
                    + e.getCourseCode() + " " + e.getTitle() + ", " + D.format(date) + ").");
            upgraded++;
        }
        return new Created(e, upgraded);
    }

    public record Created(EvaluationSchedule evaluation, int upgraded) {}

    public List<EvaluationSchedule> upcoming() {
        return evaluations.findByEvalDateGreaterThanEqualOrderByEvalDateAsc(time.today());
    }
}
