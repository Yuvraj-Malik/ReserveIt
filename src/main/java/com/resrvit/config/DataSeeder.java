package com.resrvit.config;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Creates the first super admin from ADMIN_EMAIL / ADMIN_PASSWORD, and (when RESRVIT_SEED is on,
 * the default locally) a small demo campus: one user per role, labs, rooms, equipment,
 * a timetable, an evaluation, a society, a group and a few bookings. Demo password: "password".
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final DepartmentRepository departments;
    private final LabRepository labs;
    private final RoomRepository rooms;
    private final EquipmentTypeRepository types;
    private final EquipmentRepository equipment;
    private final TimetableSlotRepository timetable;
    private final CalendarDayRepository calendarDays;
    private final EvaluationScheduleRepository evaluations;
    private final SocietyRepository societies;
    private final SocietyOfficeBearerRepository bearers;
    private final StudyGroupRepository groups;
    private final BookingRepository bookings;
    private final AccountService accounts;
    private final ChatService chat;
    private final AuditService audit;
    private final CalendarService calendar;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final TimeService time;

    @Value("${resrvit.seed-demo-data:false}")
    private boolean seed;

    @Value("${resrvit.bootstrap-admin-email:}")
    private String adminEmail;

    @Value("${resrvit.bootstrap-admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seed && users.count() == 0) {
            seedDemo();
            log.info("Demo data loaded. Sign in as admin@{} / password (see README for every role).", settings.emailDomain());
        }
        boolean hasSuperAdmin = !users.findByRoleAndStatus(Role.SUPER_ADMIN, UserStatus.ACTIVE).isEmpty();
        if (!hasSuperAdmin && adminEmail != null && !adminEmail.isBlank() && adminPassword != null && adminPassword.length() >= 8) {
            accounts.bootstrapSuperAdmin(adminEmail, adminPassword);
            log.info("Created super admin {}", adminEmail);
        } else if (!hasSuperAdmin) {
            log.warn("No super admin exists. Set ADMIN_EMAIL and ADMIN_PASSWORD (8+ characters) and restart.");
        }
    }

    private void seedDemo() {
        String dom = "@" + settings.emailDomain();
        Department csed = dept("CSED", "Computer Science and Engineering");
        Department eced = dept("ECED", "Electronics and Communication Engineering");
        dept("MED", "Mechanical Engineering");

        User admin = user("admin" + dom, "Anita Verma", Role.SUPER_ADMIN, null, null);
        User gdAdmin = user("gd.admin1" + dom, "Gaurav Dhillon", Role.GD_ADMIN, null, null);
        user("gd.admin2" + dom, "Gita Das", Role.GD_ADMIN, null, null);
        user("society.admin1" + dom, "Simran Kaur", Role.SOCIETY_ADMIN, null, null);
        user("society.admin2" + dom, "Sahil Bedi", Role.SOCIETY_ADMIN, null, null);
        User rkumar = user("rkumar" + dom, "Dr. Rajesh Kumar", Role.FACULTY, eced, null);
        User asharma = user("asharma" + dom, "Dr. Asha Sharma", Role.FACULTY, csed, null);
        user("pgupta" + dom, "Dr. Priya Gupta", Role.FACULTY, csed, null);

        Lab elab = lab("ELAB", "Electronics Lab", eced, rkumar);
        Lab clab = lab("CLAB", "Computer Lab", csed, asharma);
        user("lab.elec" + dom, "Harpreet Singh", Role.LAB_ASSISTANT, eced, elab);
        User labComp = user("lab.comp" + dom, "Manoj Yadav", Role.LAB_ASSISTANT, csed, clab);

        User aarav = student("aarav" + dom, "Aarav Mehta", "102303001", csed);
        User diya = student("diya" + dom, "Diya Arora", "102303002", csed);
        User kabir = student("kabir" + dom, "Kabir Sethi", "102303003", eced);
        student("meera" + dom, "Meera Nair", "102303004", eced);
        student("rohan" + dom, "Rohan Bansal", "102303005", csed);

        Room gd1 = room("GD-1", "GD Room 1", RoomType.GD_ROOM, null, null, null, 12);
        room("GD-2", "GD Room 2", RoomType.GD_ROOM, null, null, null, 12);
        Room elabRoom = room("E-201", "Electronics Evaluation Lab E-201", RoomType.EVALUATION_LAB, eced, elab, null, 40);
        Room clabRoom = room("C-305", "Computer Evaluation Lab C-305", RoomType.EVALUATION_LAB, csed, clab, null, 60);
        Room sem = room("SEM-1", "Seminar Hall 1", RoomType.SEMINAR_ROOM, csed, null, asharma, 120);
        Room soc1 = room("SOC-101", "Society Room 101", RoomType.SOCIETY_ROOM, null, null, null, 30);
        room("SOC-102", "Society Room 102", RoomType.SOCIETY_ROOM, null, null, null, 30);
        Room hod = room("HOD-CS", "HOD Office (CSED)", RoomType.PERMANENT, csed, null, null, 4);
        hod.setBookable(false);
        rooms.save(hod);

        EquipmentType osc = type("Oscilloscope", "Digital storage oscilloscope, 100 MHz");
        EquipmentType fg = type("Function Generator", "Arbitrary waveform generator");
        EquipmentType la = type("Logic Analyzer", "16-channel USB logic analyzer");
        EquipmentType ard = type("Arduino Kit", "Arduino Uno starter kit");
        EquipmentType rpi = type("Raspberry Pi 4", "4 GB with case and power supply");
        for (int i = 1; i <= 2; i++) item("OSC-0" + i, osc, elab);
        for (int i = 1; i <= 2; i++) item("FG-0" + i, fg, elab);
        item("LA-01", la, elab);
        for (int i = 1; i <= 4; i++) item("ARD-0" + i, ard, clab);
        for (int i = 1; i <= 3; i++) item("RPI-0" + i, rpi, clab);

        slot(elabRoom, DayOfWeek.MONDAY, 9, 0, 11, 0, "UEC404", "2EC1");
        slot(elabRoom, DayOfWeek.WEDNESDAY, 9, 0, 11, 0, "UEC404", "2EC2");
        slot(clabRoom, DayOfWeek.TUESDAY, 14, 0, 16, 0, "UCS503", "3CS1");
        slot(clabRoom, DayOfWeek.THURSDAY, 14, 0, 16, 0, "UCS503", "3CS2");
        slot(sem, DayOfWeek.FRIDAY, 9, 0, 13, 0, "UHU005", "ALL");
        slot(gd1, DayOfWeek.MONDAY, 11, 0, 13, 0, "UHU003", "2CS3");
        slot(soc1, DayOfWeek.WEDNESDAY, 17, 0, 18, 30, "UCS701", "4CS1");

        LocalDate today = time.today();
        LocalDate holiday = nextWorkingDay(today.plusDays(12));
        CalendarDay h = new CalendarDay();
        h.setCalDate(holiday);
        h.setDayType(DayType.HOLIDAY);
        h.setDescription("Campus holiday (demo)");
        calendarDays.save(h);

        LocalDate d1 = nextWorkingDay(today.plusDays(1));
        LocalDate evalDate = nextWorkingDay(d1.plusDays(4));
        EvaluationSchedule ev = new EvaluationSchedule();
        ev.setFaculty(rkumar);
        ev.setLab(elab);
        ev.setEquipmentType(osc);
        ev.setCourseCode("UEC404");
        ev.setTitle("Lab evaluation 1");
        ev.setEvalDate(evalDate);
        ev.setCreatedAt(time.now());
        evaluations.save(ev);

        Society coding = society("Coding Club", "Competitive programming and hack nights");
        Society music = society("Music Society", "Practice sessions and open mics");
        bearer(coding, aarav, "President");
        bearer(music, diya, "Secretary");

        StudyGroup volt = new StudyGroup();
        volt.setName("Team Volt");
        volt.setCourseCode("UEC404");
        volt.setProjectTitle("Smart energy meter");
        volt.setLeader(aarav);
        volt.setCreatedBy(rkumar);
        volt.setCourseFaculty(rkumar);
        volt.setStatus(GroupStatus.ACTIVE);
        volt.setCreatedAt(time.now());
        for (User u : new User[]{aarav, kabir}) {
            GroupMember m = new GroupMember();
            m.setGroup(volt);
            m.setUser(u);
            m.setConfirmed(true);
            volt.getMembers().add(m);
        }
        groups.save(volt);

        // Two students queue for the same oscilloscope day; one already approved today in the Computer Lab.
        Booking p1 = equipmentBooking(diya, elab, osc, d1, BookingReason.EVALUATION_PRACTICE, ev, BookingStatus.PENDING, null);
        Booking p2 = equipmentBooking(kabir, elab, osc, d1, BookingReason.PROJECT_WORK, null, BookingStatus.PENDING, null);
        p2.setReasonText("Measuring the meter's ripple");
        bookings.save(p2);
        LocalDate pickupDay = calendar.isHoliday(today) ? nextWorkingDay(today) : today;
        Booking ok = equipmentBooking(aarav, clab, rpi, pickupDay, BookingReason.COURSEWORK, null, BookingStatus.APPROVED,
                equipment.findByAssetTagIgnoreCase("RPI-01").orElseThrow());
        ok.setDecidedBy(asharma);
        ok.setDecidedAt(time.now());
        ok.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        bookings.save(ok);
        audit.log(ok, asharma, ApprovalAction.APPROVED, "Demo data");

        Booking gd = new Booking();
        gd.setKind(BookingKind.GD_ROOM);
        gd.setStatus(BookingStatus.PENDING);
        gd.setRequester(diya);
        gd.setRoom(gd1);
        gd.setSlotDate(d1);
        gd.setStartTime(LocalTime.of(14, 0));
        gd.setEndTime(LocalTime.of(16, 0));
        gd.setReasonText("Placement GD practice");
        gd.setQueueRank(BookingService.nextRank());
        bookings.save(gd);
        audit.log(gd, diya, ApprovalAction.REQUESTED, null);

        Booking so = new Booking();
        so.setKind(BookingKind.SOCIETY_ROOM);
        so.setStatus(BookingStatus.PENDING);
        so.setRequester(aarav);
        so.setSociety(coding);
        so.setRoom(soc1);
        so.setSlotDate(d1);
        so.setEndDate(d1.plusDays(20));
        so.setReasonText("Weekly hack nights");
        so.setQueueRank(BookingService.nextRank());
        java.util.List<LocalDate> excl = new java.util.ArrayList<>();
        for (LocalDate d = d1; !d.isAfter(d1.plusDays(20)); d = d.plusDays(1)) {
            if (!calendar.isHoliday(d) && d.getDayOfWeek() == DayOfWeek.WEDNESDAY) excl.add(d);
        }
        so.setExcludedDates(excl.isEmpty() ? null : String.join(",", excl.stream().map(LocalDate::toString).toList()));
        bookings.save(so);
        audit.log(so, aarav, ApprovalAction.REQUESTED, null);

        // Chat: a request note (locked until approval), an open booking chat, and a staff chat.
        chat.postRequestNote(p1, diya, "Could I get the x10 probes with the oscilloscope? Practising for Tuesday's evaluation.");
        chat.postRequestNote(ok, aarav, "I'll pick it up around 2 PM, is that fine?");
        chat.send(chat.bookingThread(ok), labComp, "Sure, it's on the shelf by the door. Bring your ID card.");
        var staffChat = chat.direct(admin, gdAdmin.getId());
        chat.send(staffChat, admin, "Hi Gaurav, the placement cell wants GD-2 every Friday next month. Can you keep it free?");
        log.debug("Seeded demo bookings, first #{}", p1.getId());
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate nextWorkingDay(LocalDate d) {
        while (calendar.isHoliday(d)) d = d.plusDays(1);
        return d;
    }

    private Department dept(String code, String name) {
        Department d = new Department();
        d.setCode(code);
        d.setName(name);
        return departments.save(d);
    }

    private User user(String email, String name, Role role, Department dept, Lab lab) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setDepartment(dept);
        u.setLab(lab);
        u.setPasswordHash(encoder.encode("password"));
        u.setCreatedAt(time.now());
        return users.save(u);
    }

    private User student(String email, String name, String roll, Department dept) {
        User u = user(email, name, Role.STUDENT, dept, null);
        u.setRollNo(roll);
        return users.save(u);
    }

    private Lab lab(String code, String name, Department dept, User inCharge) {
        Lab l = new Lab();
        l.setCode(code);
        l.setName(name);
        l.setDepartment(dept);
        l.setInCharge(inCharge);
        return labs.save(l);
    }

    private Room room(String code, String name, RoomType type, Department dept, Lab lab, User inCharge, int cap) {
        Room r = new Room();
        r.setCode(code);
        r.setName(name);
        r.setType(type);
        r.setDepartment(dept);
        r.setLab(lab);
        r.setInCharge(inCharge);
        r.setCapacity(cap);
        r.setBookable(true);
        return rooms.save(r);
    }

    private EquipmentType type(String name, String desc) {
        EquipmentType t = new EquipmentType();
        t.setName(name);
        t.setDescription(desc);
        return types.save(t);
    }

    private void item(String tag, EquipmentType type, Lab lab) {
        Equipment e = new Equipment();
        e.setAssetTag(tag);
        e.setType(type);
        e.setLab(lab);
        e.setStatus(EquipmentStatus.AVAILABLE);
        e.setConditionNote("Good");
        equipment.save(e);
    }

    private void slot(Room room, DayOfWeek day, int sh, int sm, int eh, int em, String course, String batch) {
        TimetableSlot t = new TimetableSlot();
        t.setRoom(room);
        t.setDayOfWeek(day.getValue());
        t.setStartTime(LocalTime.of(sh, sm));
        t.setEndTime(LocalTime.of(eh, em));
        t.setCourseCode(course);
        t.setBatchLabel(batch);
        timetable.save(t);
    }

    private Society society(String name, String desc) {
        Society s = new Society();
        s.setName(name);
        s.setDescription(desc);
        s.setActive(true);
        s.setCreatedAt(time.now());
        return societies.save(s);
    }

    private void bearer(Society s, User u, String position) {
        SocietyOfficeBearer b = new SocietyOfficeBearer();
        b.setSociety(s);
        b.setUser(u);
        b.setPosition(position);
        bearers.save(b);
    }

    private Booking equipmentBooking(User u, Lab lab, EquipmentType type, LocalDate day, BookingReason reason,
                                     EvaluationSchedule ev, BookingStatus status, Equipment item) {
        Booking b = new Booking();
        b.setKind(BookingKind.EQUIPMENT);
        b.setStatus(status);
        b.setRequester(u);
        b.setLab(lab);
        b.setEquipmentType(type);
        b.setEquipment(item);
        b.setSlotDate(day);
        b.setReason(reason);
        b.setEvaluation(ev);
        b.setProtectedEval(ev != null);
        b.setQueueRank(BookingService.nextRank());
        bookings.save(b);
        audit.log(b, u, ApprovalAction.REQUESTED, null);
        return b;
    }
}
