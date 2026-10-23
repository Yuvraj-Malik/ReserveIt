package com.resrvit;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fair-use rules from the spec, end to end through the services.
 * The clock is fixed at Monday 2 Nov 2026, 10:00 IST; each test rolls back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingRulesTest {

    static final LocalDate MON = LocalDate.of(2026, 11, 2);
    static final LocalDate TUE = MON.plusDays(1);
    static final LocalDate WED = MON.plusDays(2);
    static final LocalDate THU = MON.plusDays(3);

    @Autowired TimeService time;
    @Autowired BookingService bookingService;
    @Autowired ApprovalService approvals;
    @Autowired CheckoutService checkouts;
    @Autowired StrikeService strikes;
    @Autowired CollisionService collisions;
    @Autowired AccountService accounts;
    @Autowired EvaluationService evaluations;
    @Autowired ChatService chat;
    @Autowired SettingsService settings;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired LabRepository labs;
    @Autowired RoomRepository rooms;
    @Autowired EquipmentTypeRepository types;
    @Autowired EquipmentRepository equipment;
    @Autowired BookingRepository bookings;
    @Autowired ApprovalRepository approvalLog;
    @Autowired StudyGroupRepository groups;
    @Autowired SocietyRepository societies;
    @Autowired SocietyOfficeBearerRepository bearers;
    @Autowired TimetableSlotRepository timetable;
    @Autowired MessageRepository messages;
    @Autowired SlotOfferRepository offers;
    @Autowired PasswordEncoder encoder;

    User faculty, assistant, s1, s2, s3, gd1, gd2;
    Lab lab;
    EquipmentType osc;
    Room gdRoom, socRoom;

    @BeforeEach
    void setUp() {
        setNow(MON.atTime(10, 0));
        Department d = new Department();
        d.setCode("T");
        d.setName("Test");
        departments.save(d);
        faculty = user("f@thapar.edu", Role.FACULTY, null);
        lab = new Lab();
        lab.setCode("TL");
        lab.setName("Test Lab");
        lab.setInCharge(faculty);
        labs.save(lab);
        assistant = user("a@thapar.edu", Role.LAB_ASSISTANT, lab);
        s1 = user("s1@thapar.edu", Role.STUDENT, null);
        s2 = user("s2@thapar.edu", Role.STUDENT, null);
        s3 = user("s3@thapar.edu", Role.STUDENT, null);
        gd1 = user("g1@thapar.edu", Role.GD_ADMIN, null);
        gd2 = user("g2@thapar.edu", Role.GD_ADMIN, null);
        osc = new EquipmentType();
        osc.setName("Scope");
        types.save(osc);
        item("SC-1");
        item("SC-2");
        gdRoom = room("GDX", RoomType.GD_ROOM);
        socRoom = room("SOX", RoomType.SOCIETY_ROOM);
    }

    @AfterEach
    void resetClock() {
        time.setClock(Clock.system(TimeService.ZONE));
    }

    // ------------------------------------------------------------------ equipment rules

    @Test
    void weeklyLimitIsRollingSevenDays() {
        requestOsc(s1, TUE);
        assertThatThrownBy(() -> requestOsc(s1, MON.plusDays(7)))
                .isInstanceOf(BookingException.class).hasMessageContaining("one per rolling week");
        settings.set(SettingsService.LEAD_DAYS, "14");
        assertThat(requestOsc(s1, TUE.plusDays(7)).getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void groupUseBlocksMembersIndividually() {
        StudyGroup g = group(s1, s2);
        bookingService.requestEquipment(s1, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), TUE,
                BookingReason.PROJECT_WORK, null, null, g.getId(), false));
        assertThatThrownBy(() -> requestOsc(s2, WED)).hasMessageContaining("Your group used");
        assertThatThrownBy(() -> bookingService.requestEquipment(s2, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), WED,
                BookingReason.PROJECT_WORK, null, null, g.getId(), false))).hasMessageContaining("Only the group leader");
    }

    @Test
    void evaluationPracticeMustBeBeforeTheEvaluationAndIsProtected() {
        EvaluationSchedule e = evaluations.create(faculty, lab.getId(), osc.getId(), "UEC404", "Eval 1", WED).evaluation();
        assertThatThrownBy(() -> bookingService.requestEquipment(s1, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), THU,
                BookingReason.EVALUATION_PRACTICE, null, e.getId(), null, false))).hasMessageContaining("before the evaluation");
        Booking b = bookingService.requestEquipment(s1, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), TUE,
                BookingReason.EVALUATION_PRACTICE, null, e.getId(), null, false));
        assertThat(b.isProtectedEval()).isTrue();
    }

    @Test
    void addingAnEvaluationLaterProtectsMatchingBookings() {
        Booking b = bookingService.requestEquipment(s1, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), TUE,
                BookingReason.COURSEWORK, null, null, null, false));
        assertThat(b.isProtectedEval()).isFalse();
        EvaluationService.Created c = evaluations.create(faculty, lab.getId(), osc.getId(), "UEC404", "Eval 1", THU);
        assertThat(c.upgraded()).isEqualTo(1);
        assertThat(bookings.findById(b.getId()).orElseThrow().isProtectedEval()).isTrue();
    }

    @Test
    void approvalsFollowQueueOrderAndAFullSlotBumpsTheRestWithOffers() {
        Booking a = requestOsc(s1, TUE), b = requestOsc(s2, TUE), c = requestOsc(s3, TUE);
        assertThatThrownBy(() -> approvals.approve(b, faculty, null)).hasMessageContaining("queue order");
        approvals.approve(a, faculty, null);
        approvals.approve(b, faculty, null);
        assertThat(a.getEquipment()).isNotNull();
        assertThat(b.getEquipment()).isNotEqualTo(a.getEquipment());
        // Two items, both taken: the third request gets alternatives, the first held for 24 hours.
        List<SlotOffer> open = approvals.openOffers(c);
        assertThat(open).isNotEmpty();
        assertThat(open.get(0).getOfferedDate()).isNotEqualTo(TUE);
        Booking moved = approvals.acceptOffer(open.get(0), s3, false);
        assertThat(moved.getStatus()).isEqualTo(BookingStatus.APPROVED);
        assertThat(moved.getSlotDate()).isEqualTo(open.get(0).getOfferedDate());
    }

    @Test
    void unansweredOfferIsKeptAfter24Hours() {
        Booking a = requestOsc(s1, TUE), b = requestOsc(s2, TUE), c = requestOsc(s3, TUE);
        approvals.approve(a, faculty, null);
        approvals.approve(b, faculty, null);
        SlotOffer first = approvals.openOffers(c).get(0);
        approvals.acceptOffer(first, null, true);
        assertThat(c.getStatus()).isEqualTo(BookingStatus.APPROVED);
        assertThat(offers.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.AUTO_ACCEPTED);
    }

    @Test
    void labAssistantFallbackAfterTwoHoursForUrgentRequests() {
        Booking b = bookingService.requestEquipment(s1, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), TUE,
                BookingReason.PROJECT_WORK, null, null, null, true));
        assertThat(approvals.modeFor(assistant, b)).isEqualTo(ApprovalService.Mode.EMERGENCY);
        assertThatThrownBy(() -> approvals.approve(b, assistant, null)).hasMessageContaining("fallback");
        setNow(MON.atTime(12, 30));
        approvals.approve(b, assistant, null);
        assertThat(approvalLog.findByBookingOrderByCreatedAtAsc(b)).extracting(Approval::getAction).contains(ApprovalAction.FALLBACK_APPROVED);
    }

    @Test
    void inChargeOnLeaveLetsAssistantApproveAtOnce() {
        faculty.setOnLeave(true);
        Booking b = requestOsc(s1, TUE);
        assertThat(approvals.modeFor(assistant, b)).isEqualTo(ApprovalService.Mode.FALLBACK);
        approvals.approve(b, assistant, null);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.APPROVED);
    }

    @Test
    void emergencyBookingOverridesTheWeeklyLimitAndIsAudited() {
        requestOsc(s1, TUE);
        Booking e = bookingService.emergencyBooking(assistant, s1, osc.getId(), WED, "Project demo moved up");
        assertThat(e.getStatus()).isEqualTo(BookingStatus.APPROVED);
        assertThat(approvalLog.findByBookingOrderByCreatedAtAsc(e)).extracting(Approval::getAction)
                .contains(ApprovalAction.LIMIT_OVERRIDE, ApprovalAction.EMERGENCY_APPROVED);
    }

    @Test
    void reorderNeedsAReasonAndChangesQueueOrder() {
        Booking a = requestOsc(s1, TUE), b = requestOsc(s2, TUE);
        assertThatThrownBy(() -> approvals.reorder(assistant, List.of(b.getId(), a.getId()), " ")).hasMessageContaining("reason");
        approvals.reorder(assistant, List.of(b.getId(), a.getId()), "Evaluation tomorrow");
        assertThat(approvals.queueOf(a).get(0).getId()).isEqualTo(b.getId());
        assertThatThrownBy(() -> approvals.approve(a, faculty, null)).hasMessageContaining("queue order");
    }

    @Test
    void lateReturnIsAStrikeAndThreeStrikesBlockBooking() {
        Booking b = requestOsc(s1, MON);
        approvals.approve(b, faculty, null);
        checkouts.issue(b, assistant, "good");
        setNow(MON.atTime(10, 0).plusHours(26));
        Checkout c = checkouts.receive(b, assistant, "good", false, null, false);
        assertThat(c.isLate()).isTrue();
        assertThat(strikes.activeCount(s1)).isEqualTo(1);
        strikes.add(s1, null, "late");
        strikes.add(s1, null, "late");
        assertThat(s1.getBlockedUntil()).isEqualTo(time.today().plusDays(13));
        assertThatThrownBy(() -> requestOsc(s1, THU)).hasMessageContaining("blocked");
    }

    @Test
    void damageBlocksTheWholeGroupFromThatLab() {
        StudyGroup g = group(s1, s2);
        Booking b = bookingService.requestEquipment(s1, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), MON,
                BookingReason.PROJECT_WORK, null, null, g.getId(), false));
        approvals.approve(b, faculty, null);
        checkouts.issue(b, assistant, "good");
        checkouts.receive(b, assistant, "cracked screen", true, "Screen cracked", true);
        settings.set(SettingsService.LEAD_DAYS, "14");
        assertThatThrownBy(() -> requestOsc(s2, MON.plusDays(9))).hasMessageContaining("damage report");
    }

    // ------------------------------------------------------------------ rooms

    @Test
    void gdAdminsShareAQueueButAClaimLocksARequest() {
        Booking b = gd(s1, TUE, 14);
        approvals.claim(b, gd1);
        assertThatThrownBy(() -> approvals.approve(b, gd2, null)).hasMessageContaining("Claimed by");
        approvals.approve(b, gd1, null);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.APPROVED);
    }

    @Test
    void gdIsOneSlotPerDayAndAtMostThreeDaysAhead() {
        gd(s1, TUE, 9);
        assertThatThrownBy(() -> gd(s1, TUE, 14)).hasMessageContaining("One slot per day");
        assertThatThrownBy(() -> gd(s2, MON.plusDays(4), 9)).hasMessageContaining("up to 3 days");
    }

    @Test
    void societyRoomsNeedAnOfficeBearerAndSkipTimetabledDays() {
        Society soc = new Society();
        soc.setName("Club");
        soc.setActive(true);
        soc.setCreatedAt(time.now());
        societies.save(soc);
        BookingService.SocietyRequest req = new BookingService.SocietyRequest(soc.getId(), socRoom.getId(), TUE, TUE.plusDays(13), "Practice", null);
        assertThatThrownBy(() -> bookingService.requestSocietyRoom(s1, req)).hasMessageContaining("office-bearers");
        SocietyOfficeBearer ob = new SocietyOfficeBearer();
        ob.setSociety(soc);
        ob.setUser(s1);
        ob.setPosition("President");
        bearers.save(ob);
        assertThatThrownBy(() -> bookingService.requestSocietyRoom(s1, new BookingService.SocietyRequest(soc.getId(), socRoom.getId(),
                TUE, TUE.plusDays(30), "x", null))).hasMessageContaining("at most 30");
        TimetableSlot t = new TimetableSlot();
        t.setRoom(socRoom);
        t.setDayOfWeek(DayOfWeek.WEDNESDAY.getValue());
        t.setStartTime(LocalTime.of(17, 0));
        t.setEndTime(LocalTime.of(18, 0));
        timetable.save(t);
        Booking b = bookingService.requestSocietyRoom(s1, req);
        assertThat(b.getExcludedDateList()).containsExactly(WED, WED.plusDays(7));
        assertThatThrownBy(() -> bookingService.requestSocietyRoom(s1, req)).hasMessageContaining("One per room");
    }

    // ------------------------------------------------------------------ collisions

    @Test
    void timetablePreviewReportsTheClash() {
        Booking b = gd(s1, TUE, 14);
        approvals.approve(b, gd1, null);
        List<CollisionService.TimetableRow> rows = List.of(new CollisionService.TimetableRow("GDX", 2, LocalTime.of(14, 0), LocalTime.of(16, 0), "X", "Y"));
        CollisionService.Result preview = collisions.applyTimetable(rows, gd1, true);
        assertThat(preview.impacts()).hasSize(1);
        assertThat(preview.impacts().get(0).outcome()).isEqualTo(CollisionService.Outcome.NEW_SLOT);
    }

    @Test
    void newTimetableMovesAClashingGdBooking() {
        Booking b = gd(s1, TUE, 14);
        approvals.approve(b, gd1, null);
        List<CollisionService.TimetableRow> rows = List.of(new CollisionService.TimetableRow("GDX", 2, LocalTime.of(14, 0), LocalTime.of(16, 0), "X", "Y"));
        CollisionService.Result applied = collisions.applyTimetable(rows, gd1, false);
        assertThat(applied.impacts()).hasSize(1);
        Booking after = bookings.findById(b.getId()).orElseThrow();
        assertThat(after.getStartTime()).isNotEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void holidayMovesOrdinaryBookingsButFlagsProtectedOnesCritical() {
        EvaluationSchedule e = evaluations.create(faculty, lab.getId(), osc.getId(), "UEC404", "Eval 1", THU).evaluation();
        Booking prot = bookingService.requestEquipment(s1, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), TUE,
                BookingReason.EVALUATION_PRACTICE, null, e.getId(), null, false));
        Booking plain = requestOsc(s2, TUE);
        approvals.approve(prot, faculty, null);
        approvals.approve(plain, faculty, null);
        CollisionService.Result r = collisions.applyCalendar(List.of(new CollisionService.CalendarRow(TUE, DayType.HOLIDAY, "Surprise")), faculty, false);
        assertThat(r.impacts()).extracting(CollisionService.Impact::outcome)
                .containsExactlyInAnyOrder(CollisionService.Outcome.CRITICAL, CollisionService.Outcome.NEW_DAY);
        assertThat(bookings.findById(prot.getId()).orElseThrow().isCritical()).isTrue();
        assertThat(bookings.findById(plain.getId()).orElseThrow().getSlotDate()).isNotEqualTo(TUE);
    }

    @Test
    void maintenanceSwapsInAnEquivalentItem() {
        Booking b = requestOsc(s1, TUE);
        approvals.approve(b, faculty, null);
        Equipment held = b.getEquipment();
        CollisionService.Result r = collisions.applyMaintenance(held.getId(), null, TUE, TUE, "Calibration", assistant, false);
        assertThat(r.impacts()).extracting(CollisionService.Impact::outcome).containsExactly(CollisionService.Outcome.NEW_ITEM);
        assertThat(bookings.findById(b.getId()).orElseThrow().getEquipment()).isNotEqualTo(held);
    }

    // ------------------------------------------------------------------ accounts and chat

    @Test
    void signupNeedsCollegeDomainOtpAndActivation() {
        assertThatThrownBy(() -> accounts.signup("x@gmail.com", "X", null, null, Role.STUDENT, "password1"))
                .hasMessageContaining("@thapar.edu");
        AccountService.SignupResult r = accounts.signup("new@thapar.edu", "New Student", "1", null, Role.STUDENT, "password1");
        assertThat(r.devOtp()).hasSize(6);
        assertThatThrownBy(() -> accounts.verify("new@thapar.edu", "000000".equals(r.devOtp()) ? "111111" : "000000")).hasMessageContaining("wrong");
        User u = accounts.verify("new@thapar.edu", r.devOtp());
        assertThat(u.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        accounts.activate(u, faculty);
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deactivationCancelsLiveBookingsAndPassesLeadership() {
        StudyGroup g = group(s1, s2);
        Booking b = requestOsc(s1, TUE);
        accounts.deactivate(s1, faculty);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(groups.findById(g.getId()).orElseThrow().getLeader()).isEqualTo(s2);
    }

    @Test
    void studentChatIsOneNoteUntilApprovalThenOpens() {
        Booking b = requestOsc(s1, TUE);
        chat.postRequestNote(b, s1, "Do you have x10 probes?");
        Conversation c = chat.bookingThread(b);
        Message stored = messages.findByConversationOrderByCreatedAtAsc(c).get(0);
        assertThat(stored.getCiphertext()).doesNotContain("probes");
        assertThat(chat.messages(c).get(0).getPlaintext()).isEqualTo("Do you have x10 probes?");
        assertThatThrownBy(() -> chat.postRequestNote(b, s1, "again")).hasMessageContaining("already added a note");
        assertThatThrownBy(() -> chat.send(c, s1, "Hello?")).hasMessageContaining("opens once the request is approved");
        assertThatThrownBy(() -> chat.send(c, assistant, "Hi")).hasMessageContaining("opens once the request is approved");
        assertThat(chat.canView(c, assistant)).isTrue();
        assertThat(chat.canView(c, s2)).isFalse();
        assertThat(chat.unread(c, assistant)).isEqualTo(1);
        approvals.approve(b, faculty, null);
        chat.send(c, assistant, "Yes, they're in the drawer.");
        chat.send(c, s1, "Thanks!");
        assertThat(chat.messages(c)).hasSize(3);
    }

    @Test
    void staffCanChatDirectlyButStudentsCannotStartChats() {
        Conversation c = chat.direct(gd1, gd2.getId());
        assertThat(chat.direct(gd2, gd1.getId()).getId()).isEqualTo(c.getId());
        chat.send(c, gd1, "Can you cover the queue tomorrow?");
        assertThat(chat.unread(c, gd2)).isEqualTo(1);
        assertThat(chat.canView(c, s1)).isFalse();
        assertThatThrownBy(() -> chat.direct(s1, gd1.getId())).hasMessageContaining("Students");
        assertThatThrownBy(() -> chat.direct(gd1, s1.getId())).hasMessageContaining("staff");
    }

    // ------------------------------------------------------------------ helpers

    private void setNow(LocalDateTime t) {
        time.setClock(Clock.fixed(t.atZone(TimeService.ZONE).toInstant(), TimeService.ZONE));
    }

    private Booking requestOsc(User u, LocalDate d) {
        return bookingService.requestEquipment(u, new BookingService.EquipmentRequest(lab.getId(), osc.getId(), d,
                BookingReason.PROJECT_WORK, null, null, null, false));
    }

    private Booking gd(User u, LocalDate d, int startHour) {
        return bookingService.requestGdRoom(u, new BookingService.RoomRequest(gdRoom.getId(), d,
                LocalTime.of(startHour, 0), LocalTime.of(startHour + 2, 0), "Practice", null));
    }

    private User user(String email, Role role, Lab lab) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(email.substring(0, email.indexOf('@')));
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setLab(lab);
        u.setPasswordHash(encoder.encode("password"));
        u.setCreatedAt(time.now());
        return users.save(u);
    }

    private void item(String tag) {
        Equipment e = new Equipment();
        e.setAssetTag(tag);
        e.setType(osc);
        e.setLab(lab);
        e.setStatus(EquipmentStatus.AVAILABLE);
        equipment.save(e);
    }

    private Room room(String code, RoomType type) {
        Room r = new Room();
        r.setCode(code);
        r.setName(code);
        r.setType(type);
        r.setBookable(true);
        r.setCapacity(10);
        return rooms.save(r);
    }

    private StudyGroup group(User leader, User other) {
        StudyGroup g = new StudyGroup();
        g.setName("G");
        g.setCourseCode("C1");
        g.setLeader(leader);
        g.setCreatedBy(faculty);
        g.setCourseFaculty(faculty);
        g.setStatus(GroupStatus.ACTIVE);
        g.setCreatedAt(time.now());
        for (User u : List.of(leader, other)) {
            GroupMember m = new GroupMember();
            m.setGroup(g);
            m.setUser(u);
            m.setConfirmed(true);
            g.getMembers().add(m);
        }
        return groups.save(g);
    }
}
