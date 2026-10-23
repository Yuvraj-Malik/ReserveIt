package com.resrvit.service;

import com.resrvit.domain.Approval;
import com.resrvit.domain.ApprovalAction;
import com.resrvit.domain.Booking;
import com.resrvit.domain.User;
import com.resrvit.repo.ApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Every decision on a booking is written to the approvals table (the booking's audit trail). */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final ApprovalRepository repo;
    private final TimeService time;

    public void log(Booking booking, User actor, ApprovalAction action, String note) {
        Approval a = new Approval();
        a.setBooking(booking);
        a.setActor(actor);
        a.setAction(action);
        a.setNote(note != null && note.length() > 495 ? note.substring(0, 495) : note);
        a.setCreatedAt(time.now());
        repo.save(a);
    }
}
