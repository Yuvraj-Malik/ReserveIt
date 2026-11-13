package com.resrvit.config;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

/**
 * A lived-in demo campus for presentations: about 45 people across every role, four labs,
 * fourteen rooms, ~50 items, a timetable, holidays and exams, evaluations, societies and groups,
 * a month of booking history (returns, late returns, strikes, damage), today's live queues,
 * pick-ups and loans, chats, announcements and notifications. Every dashboard and report has data.
 * Demo password for everyone: "password".
 */
@Component
@RequiredArgsConstructor
public class DemoData {

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
    private final ApprovalRepository approvals;
    private final QueueAuditRepository queueAudit;
    private final SlotOfferRepository offers;
    private final CheckoutRepository checkouts;
    private final IncidentRepository incidents;
    private final StrikeRepository strikes;
    private final MaintenanceLogRepository maintenance;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final NotificationRepository notifications;
    private final AnnouncementRepository announcements;
    private final CryptoService crypto;
    private final CalendarService calendar;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final TimeService time;

    private String dom;
    private String passwordHash;
    private LocalDate today;
    private LocalDateTime now;
    private final Random rnd = new Random(42);

    // People
    private User admin, gd1, gd2, soc1, soc2;
    private User rkumar, asharma, vsingh, nkaur, pgupta, mjain;
    private User labElec, labComp, labRobo, labEmb;
    private final List<User> students = new ArrayList<>();
    // Places and things
    private Lab elab, clab, rlab, emlab;
    private Room gd1Room, gd2Room, gd3Room, eRoom, cRoom, rRoom, sem1, sem2, soc101, soc102, soc103;
    private EquipmentType osc, fg, psu, dmm, la, ard, rpi, jetson, arm, servo, lidar, stm, esp;
    private final Map<EquipmentType, List<Equipment>> items = new HashMap<>();
    private Society coding, music, robotics, drama, photo;
    private StudyGroup volt, byteForce, roboRangers, sparkNet;
    private EvaluationSchedule evalOsc, evalArd, evalStm;

    public void load() {
        dom = "@" + settings.emailDomain();
        passwordHash = encoder.encode("password");
        today = time.today();
        now = time.now();

        people();
        campus();
        calendarAndTimetable();
        evaluationsSocietiesGroups();
        history();
        liveToday();
        chats();
        announcementsAndNotifications();
    }

    // ================================================================== people

    private void people() {
        Department csed = dept("CSED", "Computer Science and Engineering");
        Department eced = dept("ECED", "Electronics and Communication Engineering");
        Department med = dept("MED", "Mechanical Engineering");
        Department eied = dept("EIED", "Electrical and Instrumentation Engineering");

        admin = user("admin", "Anita Verma", Role.SUPER_ADMIN, null, null, null, "+91 98140 11001");
        gd1 = user("gd.admin1", "Gaurav Dhillon", Role.GD_ADMIN, null, null, null, "+91 98140 11002");
        gd2 = user("gd.admin2", "Gita Das", Role.GD_ADMIN, null, null, null, null);
        soc1 = user("society.admin1", "Simran Kaur", Role.SOCIETY_ADMIN, null, null, null, "+91 98140 11004");
        soc2 = user("society.admin2", "Sahil Bedi", Role.SOCIETY_ADMIN, null, null, null, null);

        rkumar = user("rkumar", "Dr. Rajesh Kumar", Role.FACULTY, eced, null, null, "+91 98140 22001");
        asharma = user("asharma", "Dr. Asha Sharma", Role.FACULTY, csed, null, null, "+91 98140 22002");
        vsingh = user("vsingh", "Dr. Vikram Singh", Role.FACULTY, med, null, null, null);
        nkaur = user("nkaur", "Dr. Navneet Kaur", Role.FACULTY, eied, null, null, null);
        pgupta = user("pgupta", "Dr. Priya Gupta", Role.FACULTY, csed, null, null, null);
        mjain = user("mjain", "Dr. Manish Jain", Role.FACULTY, eced, null, null, null);
        mjain.setOnLeave(true);
        users.save(mjain);

        elab = lab("ELAB", "Electronics Lab", eced, rkumar);
        clab = lab("CLAB", "Computer Lab", csed, asharma);
        rlab = lab("RLAB", "Robotics Lab", med, vsingh);
        emlab = lab("EMLAB", "Embedded Systems Lab", eied, nkaur);

        labElec = user("lab.elec", "Harpreet Singh", Role.LAB_ASSISTANT, eced, elab, null, "+91 98140 33001");
        labComp = user("lab.comp", "Manoj Yadav", Role.LAB_ASSISTANT, csed, clab, null, "+91 98140 33002");
        labRobo = user("lab.robo", "Kuldeep Saini", Role.LAB_ASSISTANT, med, rlab, null, null);
        labEmb = user("lab.emb", "Ritu Bhatia", Role.LAB_ASSISTANT, eied, emlab, null, null);

        String[][] s = {
                {"aarav", "Aarav Mehta", "CSED"}, {"diya", "Diya Arora", "CSED"}, {"kabir", "Kabir Sethi", "ECED"},
                {"meera", "Meera Nair", "ECED"}, {"rohan", "Rohan Bansal", "CSED"}, {"ishita", "Ishita Garg", "ECED"},
                {"arjun", "Arjun Malhotra", "MED"}, {"sanya", "Sanya Kapoor", "CSED"}, {"vivaan", "Vivaan Chopra", "EIED"},
                {"ananya", "Ananya Bhardwaj", "ECED"}, {"karan", "Karan Gill", "MED"}, {"tanvi", "Tanvi Sood", "CSED"},
                {"aditya", "Aditya Rana", "EIED"}, {"riya", "Riya Jain", "CSED"}, {"harsh", "Harsh Vardhan", "ECED"},
                {"nikita", "Nikita Thakur", "MED"}, {"yash", "Yash Ahuja", "CSED"}, {"pooja", "Pooja Rawat", "EIED"},
                {"dev", "Dev Khurana", "ECED"}, {"simar", "Simarpreet Kaur", "CSED"}, {"aryan", "Aryan Kalra", "MED"},
                {"kritika", "Kritika Mittal", "ECED"}, {"manav", "Manav Arora", "EIED"}, {"zoya", "Zoya Khan", "CSED"}};
        Map<String, Department> byCode = Map.of("CSED", csed, "ECED", eced, "MED", med, "EIED", eied);
        int roll = 102303001;
        for (String[] row : s) {
            User u = user(row[0], row[1], Role.STUDENT, byCode.get(row[2]), null, String.valueOf(roll++), null);
            students.add(u);
        }
        // A few accounts the super admin still has to activate.
        pendingUser("neha", "Neha Rao", "102303101", csed);
        pendingUser("parth", "Parth Sharma", "102303102", eced);
        User newFaculty = pendingUser("akaur", "Dr. Amandeep Kaur", null, med);
        newFaculty.setRole(Role.FACULTY);
        users.save(newFaculty);
    }

    private User st(String handle) {
        return students.stream().filter(u -> u.getEmail().startsWith(handle + "@")).findFirst().orElseThrow();
    }

    // ================================================================== campus

    private void campus() {
        gd1Room = room("GD-1", "GD Room 1", RoomType.GD_ROOM, null, null, 12);
        gd2Room = room("GD-2", "GD Room 2", RoomType.GD_ROOM, null, null, 12);
        gd3Room = room("GD-3", "GD Room 3 (Library)", RoomType.GD_ROOM, null, null, 10);
        eRoom = room("E-201", "Electronics Evaluation Lab E-201", RoomType.EVALUATION_LAB, elab, null, 40);
        cRoom = room("C-305", "Computer Evaluation Lab C-305", RoomType.EVALUATION_LAB, clab, null, 60);
        rRoom = room("M-110", "Robotics Bay M-110", RoomType.EVALUATION_LAB, rlab, null, 30);
        sem1 = room("SEM-1", "Seminar Hall 1", RoomType.SEMINAR_ROOM, null, asharma, 120);
        sem2 = room("SEM-2", "Seminar Hall 2 (ECED)", RoomType.SEMINAR_ROOM, null, rkumar, 80);
        soc101 = room("SOC-101", "Society Room 101", RoomType.SOCIETY_ROOM, null, null, 30);
        soc102 = room("SOC-102", "Society Room 102", RoomType.SOCIETY_ROOM, null, null, 30);
        soc103 = room("SOC-103", "Music Room 103", RoomType.SOCIETY_ROOM, null, null, 25);
        Room hod = room("HOD-CS", "HOD Office (CSED)", RoomType.PERMANENT, null, null, 4);
        hod.setBookable(false);
        rooms.save(hod);
        Room dean = room("DEAN-1", "Dean's Conference Room", RoomType.PERMANENT, null, null, 16);
        dean.setBookable(false);
        rooms.save(dean);

        osc = type("Oscilloscope", "Digital storage oscilloscope, 100 MHz, 2 channels");
        fg = type("Function Generator", "Arbitrary waveform generator, 25 MHz");
        psu = type("DC Power Supply", "Dual-channel 0-30 V / 0-5 A");
        dmm = type("Digital Multimeter", "True-RMS handheld multimeter");
        la = type("Logic Analyzer", "16-channel USB logic analyzer");
        ard = type("Arduino Kit", "Arduino Uno starter kit with sensors");
        rpi = type("Raspberry Pi 4", "4 GB with case, power supply and SD card");
        jetson = type("Jetson Nano", "NVIDIA Jetson Nano 4 GB developer kit");
        arm = type("Robotic Arm Kit", "6-DOF desktop robotic arm with controller");
        servo = type("Servo Motor Kit", "Set of 6 MG996R servos with driver board");
        lidar = type("LiDAR Sensor", "RPLIDAR A1 360-degree scanner");
        stm = type("STM32 Board", "STM32 Nucleo-64 development board");
        esp = type("ESP32 Kit", "ESP32 DevKit with breadboard and sensors");

        items(osc, elab, "OSC", 3, "Good");
        items(fg, elab, "FG", 2, "Good");
        items(psu, elab, "PSU", 3, "Good");
        items(dmm, elab, "DMM", 5, "Good");
        items(la, elab, "LA", 1, "Good");
        items(ard, clab, "ARD", 6, "Good, all sensors present");
        items(rpi, clab, "RPI", 4, "Good");
        items(jetson, clab, "JET", 2, "Good");
        items(arm, rlab, "ARM", 2, "Good, gripper recalibrated in October");
        items(servo, rlab, "SRV", 4, "Good");
        items(lidar, rlab, "LDR", 1, "Good");
        items(stm, emlab, "STM", 4, "Good");
        items(esp, emlab, "ESP", 6, "Good");
        Equipment retired = items.get(dmm).get(4);
        retired.setStatus(EquipmentStatus.RETIRED);
        retired.setConditionNote("Display cracked, written off");
        equipment.save(retired);
    }

    // ================================================================== calendar, timetable

    private void calendarAndTimetable() {
        slot(eRoom, DayOfWeek.MONDAY, 9, 0, 11, 0, "UEC404", "2EC1");
        slot(eRoom, DayOfWeek.WEDNESDAY, 9, 0, 11, 0, "UEC404", "2EC2");
        slot(eRoom, DayOfWeek.FRIDAY, 14, 0, 16, 0, "UEC612", "3EC1");
        slot(cRoom, DayOfWeek.TUESDAY, 14, 0, 16, 0, "UCS503", "3CS1");
        slot(cRoom, DayOfWeek.THURSDAY, 14, 0, 16, 0, "UCS503", "3CS2");
        slot(cRoom, DayOfWeek.MONDAY, 11, 0, 13, 0, "UCS301", "2CS4");
        slot(rRoom, DayOfWeek.WEDNESDAY, 14, 0, 17, 0, "UME709", "4ME1");
        slot(sem1, DayOfWeek.FRIDAY, 9, 0, 13, 0, "UHU005", "ALL");
        slot(sem2, DayOfWeek.TUESDAY, 10, 0, 12, 0, "UEC701", "4EC1");
        slot(gd1Room, DayOfWeek.MONDAY, 11, 0, 13, 0, "UHU003", "2CS3");
        slot(gd2Room, DayOfWeek.THURSDAY, 9, 0, 11, 0, "UHU003", "2EC3");
        slot(soc101, DayOfWeek.WEDNESDAY, 17, 0, 18, 30, "UCS701", "4CS1");

        day(nextWorking(today.plusDays(9)), DayType.HOLIDAY, "Campus holiday");
        day(nextWorking(today.plusDays(23)), DayType.HOLIDAY, "Foundation Day");
        LocalDate exams = nextWorking(today.plusDays(30));
        for (int i = 0, n = 0; n < 5; i++) {
            LocalDate d = exams.plusDays(i);
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            day(d, DayType.EXAM, "End-semester examinations");
            n++;
        }
        day(workingBefore(today.minusDays(12)), DayType.HOLIDAY, "Festival holiday");
    }

    // ================================================================== evaluations, societies, groups

    private void evaluationsSocietiesGroups() {
        evalOsc = eval(rkumar, elab, osc, "UEC404", "Lab evaluation 2: filters", nextWorking(today.plusDays(4)));
        evalArd = eval(asharma, clab, ard, "UCS503", "Mini-project demo", nextWorking(today.plusDays(6)));
        evalStm = eval(nkaur, emlab, stm, "UEI602", "Embedded lab viva", nextWorking(today.plusDays(8)));
        eval(vsingh, rlab, arm, "UME709", "Robot kinematics demo", nextWorking(today.plusDays(12)));
        eval(rkumar, elab, osc, "UEC404", "Lab evaluation 1", workingBefore(today.minusDays(14)));
        eval(asharma, clab, rpi, "UCS503", "IoT checkpoint", workingBefore(today.minusDays(7)));

        coding = society("Coding Club", "Competitive programming, hack nights and workshops");
        music = society("Music Society", "Jam sessions, open mics and the annual concert");
        robotics = society("Robotics Society", "Robocon prep and drone builds");
        drama = society("Dramatics Society", "Street plays and stage productions");
        photo = society("Photography Club", "Photo walks and editing sessions");
        photo.setActive(false);
        societies.save(photo);
        bearer(coding, st("aarav"), "President");
        bearer(coding, st("sanya"), "General Secretary");
        bearer(music, st("diya"), "Secretary");
        bearer(robotics, st("arjun"), "President");
        bearer(robotics, st("karan"), "Treasurer");
        bearer(drama, st("tanvi"), "President");

        volt = group("Team Volt", "UEC404", "Smart energy meter", rkumar, rkumar, GroupStatus.ACTIVE, st("aarav"), st("kabir"), st("meera"));
        byteForce = group("ByteForce", "UCS503", "Campus lost-and-found app", asharma, asharma, GroupStatus.ACTIVE, st("rohan"), st("sanya"), st("yash"), st("zoya"));
        roboRangers = group("Robo Rangers", "UME709", "Line-following delivery robot", vsingh, vsingh, GroupStatus.ACTIVE, st("arjun"), st("karan"), st("nikita"));
        sparkNet = group("SparkNet", "UEI602", "LoRa weather station", nkaur, nkaur, GroupStatus.ACTIVE, st("vivaan"), st("aditya"), st("pooja"));
        // One waiting for members to confirm, one waiting for the course faculty.
        StudyGroup g1 = group("Circuit Breakers", "UEC612", "Audio amplifier design", st("ishita"), rkumar, GroupStatus.PENDING_CONFIRMATION, st("ishita"), st("ananya"), st("harsh"));
        g1.getMembers().stream().filter(m -> m.getUser().sameAs(st("harsh"))).forEach(m -> m.setConfirmed(false));
        groups.save(g1);
        group("Pixel Pushers", "UCS301", "AR campus tour", st("riya"), asharma, GroupStatus.PENDING_FACULTY, st("riya"), st("tanvi"), st("simar"));
    }

    // ================================================================== a month of history

    private void history() {
        Object[][] pool = {
                {elab, osc}, {elab, fg}, {elab, psu}, {elab, dmm}, {clab, ard}, {clab, rpi}, {clab, jetson},
                {rlab, servo}, {rlab, arm}, {emlab, esp}, {emlab, stm}};
        BookingReason[] reasons = {BookingReason.COURSEWORK, BookingReason.PROJECT_WORK, BookingReason.EVALUATION_PRACTICE, BookingReason.PROJECT_WORK};
        // ~70 returned loans over the past 30 working days.
        int made = 0;
        for (int back = 30; back >= 2 && made < 130; back--) {
            LocalDate d = today.minusDays(back);
            if (calendar.isHoliday(d)) continue;
            int perDay = 4 + rnd.nextInt(4);
            Set<Long> usedItems = new HashSet<>();
            for (int k = 0; k < perDay; k++) {
                Object[] p = pool[rnd.nextInt(pool.length)];
                Lab l = (Lab) p[0];
                EquipmentType t = (EquipmentType) p[1];
                Equipment item = items.get(t).stream().filter(e -> e.getStatus() == EquipmentStatus.AVAILABLE && !usedItems.contains(e.getId())).findFirst().orElse(null);
                if (item == null) continue;
                usedItems.add(item.getId());
                User u = students.get(rnd.nextInt(students.size()));
                Booking b = equip(u, l, t, d, reasons[rnd.nextInt(reasons.length)], BookingStatus.RETURNED, item, null);
                b.setCreatedAt(d.minusDays(1 + rnd.nextInt(3)).atTime(10 + rnd.nextInt(7), rnd.nextInt(60)));
                bookings.save(b);
                User approver = l.getInCharge();
                log(b, u, ApprovalAction.REQUESTED, null, b.getCreatedAt());
                log(b, approver, ApprovalAction.APPROVED, null, b.getCreatedAt().plusHours(1 + rnd.nextInt(5)));
                User assistant = assistantOf(l);
                LocalDateTime out = d.atTime(9 + rnd.nextInt(6), rnd.nextInt(60));
                LocalDateTime returned = out.plusHours(4 + rnd.nextInt(18));
                checkout(b, item, assistant, out, returned, false, "Good", "Good");
                made++;
            }
        }

        // Late returns: two strikes for Rohan, three for Yash (now blocked), one waived for Riya.
        lateReturn(st("rohan"), elab, osc, workingBefore(today.minusDays(20)), 6);
        lateReturn(st("rohan"), clab, rpi, workingBefore(today.minusDays(9)), 3);
        lateReturn(st("yash"), clab, ard, workingBefore(today.minusDays(18)), 11);
        lateReturn(st("yash"), clab, jetson, workingBefore(today.minusDays(11)), 5);
        lateReturn(st("yash"), emlab, esp, workingBefore(today.minusDays(4)), 26);
        List<Strike> yash = strikes.findByUserAndWaivedFalseAndConsumedFalse(st("yash"));
        yash.forEach(s -> s.setConsumed(true));
        strikes.saveAll(yash);
        User y = st("yash");
        y.setBlockedUntil(workingBefore(today.minusDays(4)).plusDays(13));
        users.save(y);
        Booking riyaLate = lateReturn(st("riya"), elab, fg, workingBefore(today.minusDays(15)), 2);
        Strike rs = strikes.findByUserOrderByCreatedAtDesc(st("riya")).get(0);
        rs.setWaived(true);
        rs.setWaivedBy(labElec);
        strikes.save(rs);
        log(riyaLate, labElec, ApprovalAction.RETURNED, "Strike waived: bus strike that day, informed in advance", now.minusDays(14));

        // Damage still under investigation: Robo Rangers can't book Robotics Lab equipment.
        LocalDate dmgDay = workingBefore(today.minusDays(3));
        Equipment armItem = items.get(arm).get(1);
        Booking dmg = equip(st("arjun"), rlab, arm, dmgDay, BookingReason.PROJECT_WORK, BookingStatus.RETURNED, armItem, roboRangers);
        log(dmg, st("arjun"), ApprovalAction.REQUESTED, null, dmgDay.minusDays(2).atTime(11, 20));
        log(dmg, vsingh, ApprovalAction.APPROVED, null, dmgDay.minusDays(2).atTime(15, 5));
        checkout(dmg, armItem, labRobo, dmgDay.atTime(10, 10), dmgDay.atTime(17, 40), true, "Good", "Gripper servo stripped, base cracked");
        Incident inc = new Incident();
        inc.setBooking(dmg);
        inc.setEquipment(armItem);
        inc.setLab(rlab);
        inc.setReportedBy(labRobo);
        inc.setDescription("Gripper servo gears stripped and the base plate is cracked. Needs inspection before it goes out again.");
        inc.setStatus(IncidentStatus.OPEN);
        inc.setCreatedAt(dmgDay.atTime(17, 45));
        incidents.save(inc);
        armItem.setStatus(EquipmentStatus.MAINTENANCE);
        armItem.setConditionNote("Gripper servo stripped, base cracked: under inspection");
        equipment.save(armItem);

        // A resolved incident from earlier.
        LocalDate oldDay = workingBefore(today.minusDays(22));
        Equipment dmmItem = items.get(dmm).get(2);
        Booking old = equip(st("harsh"), elab, dmm, oldDay, BookingReason.COURSEWORK, BookingStatus.RETURNED, dmmItem, null);
        checkout(old, dmmItem, labElec, oldDay.atTime(9, 30), oldDay.atTime(16, 0), true, "Good", "Probe lead torn");
        Incident inc2 = new Incident();
        inc2.setBooking(old);
        inc2.setEquipment(dmmItem);
        inc2.setLab(elab);
        inc2.setReportedBy(labElec);
        inc2.setDescription("One probe lead torn near the connector.");
        inc2.setStatus(IncidentStatus.RESOLVED);
        inc2.setCreatedAt(oldDay.atTime(16, 5));
        inc2.setResolvedAt(oldDay.plusDays(2).atTime(12, 0));
        inc2.setResolvedBy(labElec);
        inc2.setResolution("Lead replaced from spares; student paid ₹150.");
        incidents.save(inc2);

        // Past room bookings (for utilisation), rejected and expired requests.
        for (int back = 25; back >= 2; back -= 2) {
            LocalDate d = today.minusDays(back);
            if (calendar.isHoliday(d)) continue;
            Room r = List.of(gd1Room, gd2Room, gd3Room).get(rnd.nextInt(3));
            LocalTime[] s = CalendarService.GD_SLOTS.get(2 + rnd.nextInt(2));
            Booking g = room(students.get(rnd.nextInt(students.size())), r, BookingKind.GD_ROOM, d, s[0], s[1], "Placement GD practice", BookingStatus.APPROVED, null);
            g.setDecidedBy(rnd.nextBoolean() ? gd1 : gd2);
            g.setDecidedAt(d.minusDays(1).atTime(12, 0));
            bookings.save(g);
            Room evalRoom = List.of(eRoom, cRoom, rRoom).get(rnd.nextInt(3));
            Booking er = room(students.get(rnd.nextInt(students.size())), evalRoom, BookingKind.LAB_ROOM, d, LocalTime.of(17, 0), LocalTime.of(19, 0), "Extra practice before evaluation", BookingStatus.APPROVED, null);
            er.setDecidedBy(evalRoom.getApprover());
            bookings.save(er);
            if (back % 4 == 1 || back % 6 == 0) {
                Booking lr = room(pgupta, sem1, BookingKind.LAB_ROOM, d, LocalTime.of(15, 0), LocalTime.of(17, 0), "Guest lecture: cloud careers", BookingStatus.APPROVED, null);
                lr.setDecidedBy(asharma);
                bookings.save(lr);
            }
        }
        Booking rej = equip(st("dev"), clab, jetson, workingBefore(today.minusDays(6)), BookingReason.OTHER, BookingStatus.REJECTED, null, null);
        rej.setReasonText("Personal side project");
        rej.setDecidedBy(asharma);
        rej.setDecidedAt(rej.getCreatedAt().plusHours(3));
        rej.setDecisionNote("Jetsons are reserved for coursework and evaluations this month.");
        bookings.save(rej);
        log(rej, asharma, ApprovalAction.REJECTED, rej.getDecisionNote(), rej.getDecidedAt());
        Booking exp = room(st("kritika"), gd3Room, BookingKind.GD_ROOM, workingBefore(today.minusDays(5)), LocalTime.of(16, 0), LocalTime.of(18, 0), "Mock interview prep", BookingStatus.EXPIRED, null);
        exp.setDecisionNote("Nobody approved it before the slot started.");
        bookings.save(exp);
        log(exp, null, ApprovalAction.EXPIRED, null, exp.getStartsAt());
        Booking cancelledPast = equip(st("manav"), emlab, stm, workingBefore(today.minusDays(8)), BookingReason.COURSEWORK, BookingStatus.CANCELLED, null, null);
        log(cancelledPast, st("manav"), ApprovalAction.CANCELLED, "Lab moved to next week", cancelledPast.getCreatedAt().plusHours(5));

        // Society bookings: two active (one ends in 4 days), one finished earlier.
        Booking codingNow = society(coding, st("aarav"), soc101, today.minusDays(16), today.plusDays(4), "Weekly hack nights", BookingStatus.APPROVED, null);
        codingNow.setDecidedBy(soc1);
        bookings.save(codingNow);
        Booking musicNow = society(music, st("diya"), soc103, today.minusDays(6), today.plusDays(22), "Concert rehearsals", BookingStatus.APPROVED, null);
        musicNow.setDecidedBy(soc2);
        bookings.save(musicNow);
        Booking dramaOld = society(drama, st("tanvi"), soc102, today.minusDays(45), today.minusDays(16), "Street play practice", BookingStatus.APPROVED, null);
        dramaOld.setDecidedBy(soc1);
        bookings.save(dramaOld);
    }

    // ================================================================== today: queues, pick-ups, loans

    private void liveToday() {
        LocalDate d1 = nextWorking(today.plusDays(1));
        LocalDate d2 = nextWorking(d1.plusDays(1));
        LocalDate pick = calendar.isHoliday(today) ? nextWorking(today) : today;

        // Oscilloscope queue for tomorrow: 3 items, 4 requests (one protected evaluation booking).
        Booking q1 = equip(st("ishita"), elab, osc, d1, BookingReason.EVALUATION_PRACTICE, BookingStatus.PENDING, null, null);
        q1.setEvaluation(evalOsc);
        q1.setProtectedEval(true);
        bookings.save(q1);
        Booking q2 = equip(st("dev"), elab, osc, d1, BookingReason.PROJECT_WORK, BookingStatus.PENDING, null, null);
        q2.setReasonText("Measuring ripple on the amplifier board");
        q2.setUrgent(true);
        q2.setCreatedAt(now.minusHours(3));
        bookings.save(q2);
        Booking q3 = equip(st("kritika"), elab, osc, d1, BookingReason.EVALUATION_PRACTICE, BookingStatus.PENDING, null, null);
        q3.setEvaluation(evalOsc);
        q3.setProtectedEval(true);
        bookings.save(q3);
        equip(st("harsh"), elab, osc, d1, BookingReason.COURSEWORK, BookingStatus.PENDING, null, null);
        Booking moved = equip(st("ananya"), elab, osc, d1, BookingReason.COURSEWORK, BookingStatus.PENDING, null, null);
        QueueAudit qa = new QueueAudit();
        qa.setBooking(q3);
        qa.setActor(labElec);
        qa.setOldRank(q3.getQueueRank());
        qa.setNewRank(q2.getQueueRank() - 1);
        qa.setReason("Evaluation on Thursday; Dev's request isn't evaluation-related");
        qa.setCreatedAt(now.minusMinutes(40));
        queueAudit.save(qa);

        // Arduino and ESP32 queues; group booking; faculty direct booking.
        Booking a1 = equip(st("rohan"), clab, ard, d1, BookingReason.PROJECT_WORK, BookingStatus.PENDING, null, byteForce);
        a1.setReasonText("Sensor integration for the lost-and-found kiosk");
        bookings.save(a1);
        Booking a2 = equip(st("zoya"), clab, ard, d2, BookingReason.EVALUATION_PRACTICE, BookingStatus.PENDING, null, null);
        a2.setEvaluation(evalArd);
        a2.setProtectedEval(true);
        bookings.save(a2);
        equip(st("manav"), emlab, esp, d1, BookingReason.COURSEWORK, BookingStatus.PENDING, null, null);
        Booking s1 = equip(st("vivaan"), emlab, stm, d1, BookingReason.EVALUATION_PRACTICE, BookingStatus.PENDING, null, sparkNet);
        s1.setEvaluation(evalStm);
        s1.setProtectedEval(true);
        bookings.save(s1);
        Booking direct = equip(rkumar, elab, la, d2, BookingReason.OTHER, BookingStatus.APPROVED, items.get(la).get(0), null);
        direct.setReasonText("Preparing next week's lab handout");
        direct.setDirectBooking(true);
        direct.setDecidedBy(rkumar);
        direct.setQrToken(token());
        bookings.save(direct);

        // A full Jetson day: a bumped student holding offers.
        Booking j1 = equip(st("simar"), clab, jetson, d1, BookingReason.PROJECT_WORK, BookingStatus.APPROVED, items.get(jetson).get(0), null);
        approve(j1, asharma);
        Booking j2 = equip(st("aditya"), clab, jetson, d1, BookingReason.PROJECT_WORK, BookingStatus.APPROVED, items.get(jetson).get(1), null);
        approve(j2, asharma);
        Booking bumped = equip(st("pooja"), clab, jetson, d1, BookingReason.PROJECT_WORK, BookingStatus.PENDING, null, null);
        int rank = 1;
        for (LocalDate alt : List.of(d2, nextWorking(d2.plusDays(1)), nextWorking(d2.plusDays(3)))) {
            SlotOffer o = new SlotOffer();
            o.setBooking(bumped);
            o.setOfferedDate(alt);
            o.setOptionRank(rank++);
            o.setStatus(OfferStatus.OPEN);
            o.setExpiresAt(now.plusHours(20));
            o.setCreatedAt(now.minusHours(4));
            offers.save(o);
        }
        log(bumped, null, ApprovalAction.OFFERED, "Slot full; offered the next free days", now.minusHours(4));

        // Today's pick-ups (approved, with QR) in every lab.
        Booking p1 = equip(st("aarav"), clab, rpi, pick, BookingReason.COURSEWORK, BookingStatus.APPROVED, items.get(rpi).get(0), null);
        approve(p1, asharma);
        Booking p2 = equip(st("meera"), elab, fg, pick, BookingReason.COURSEWORK, BookingStatus.APPROVED, items.get(fg).get(0), volt);
        approve(p2, rkumar);
        Booking p3 = equip(st("tanvi"), clab, ard, pick, BookingReason.PROJECT_WORK, BookingStatus.APPROVED, items.get(ard).get(0), null);
        approve(p3, asharma);
        Booking p4 = equip(st("pooja"), emlab, esp, pick, BookingReason.PROJECT_WORK, BookingStatus.APPROVED, items.get(esp).get(0), null);
        approve(p4, nkaur);

        // Out on loan right now (one overdue).
        Booking o1 = equip(st("kabir"), elab, psu, pick, BookingReason.PROJECT_WORK, BookingStatus.CHECKED_OUT, items.get(psu).get(0), volt);
        approve(o1, rkumar);
        checkout(o1, items.get(psu).get(0), labElec, now.minusHours(3), null, false, "Good", null);
        Booking o2 = equip(st("sanya"), clab, rpi, pick, BookingReason.PROJECT_WORK, BookingStatus.CHECKED_OUT, items.get(rpi).get(1), byteForce);
        approve(o2, asharma);
        checkout(o2, items.get(rpi).get(1), labComp, now.minusHours(1), null, false, "Good, case scratched", null);
        LocalDate yday = workingBefore(today.minusDays(1));
        Booking o3 = equip(st("karan"), rlab, servo, yday, BookingReason.PROJECT_WORK, BookingStatus.CHECKED_OUT, items.get(servo).get(0), roboRangers);
        approve(o3, vsingh);
        checkout(o3, items.get(servo).get(0), labRobo, now.minusHours(28), null, false, "Good", null);

        // Cancelled approved booking (freed slot) and a critical one from a timetable change.
        Booking freed = equip(st("nikita"), elab, dmm, d1, BookingReason.COURSEWORK, BookingStatus.CANCELLED, items.get(dmm).get(0), null);
        freed.setDecidedBy(rkumar);
        freed.setDecidedAt(now.minusHours(26));
        freed.setDecisionNote("Lab rescheduled");
        bookings.save(freed);
        log(freed, st("nikita"), ApprovalAction.CANCELLED, "Lab rescheduled", now.minusHours(2));
        Booking crit = equip(st("ananya"), elab, osc, nextWorking(today.plusDays(3)), BookingReason.EVALUATION_PRACTICE, BookingStatus.APPROVED, items.get(osc).get(2), null);
        crit.setEvaluation(evalOsc);
        crit.setProtectedEval(true);
        approve(crit, rkumar);
        crit.setCritical(true);
        crit.setCriticalNote("Protected evaluation booking: its oscilloscope went for calibration and no equivalent item is free that day.");
        bookings.save(crit);
        log(crit, admin, ApprovalAction.FLAGGED_CRITICAL, crit.getCriticalNote(), now.minusHours(5));
        MaintenanceLog m = new MaintenanceLog();
        m.setEquipment(items.get(osc).get(2));
        m.setStartDate(nextWorking(today.plusDays(3)));
        m.setEndDate(nextWorking(today.plusDays(3)).plusDays(2));
        m.setReason("Annual calibration by the vendor");
        m.setCreatedBy(labElec);
        m.setActive(true);
        m.setCreatedAt(now.minusHours(5));
        maintenance.save(m);
        MaintenanceLog m2 = new MaintenanceLog();
        m2.setRoom(gd3Room);
        m2.setStartDate(nextWorking(today.plusDays(5)));
        m2.setEndDate(nextWorking(today.plusDays(5)));
        m2.setReason("AC repair");
        m2.setCreatedBy(admin);
        m2.setActive(true);
        m2.setCreatedAt(now.minusDays(1));
        maintenance.save(m2);

        // GD rooms: pending (one claimed), approved today and tomorrow.
        Booking g1 = room(st("diya"), gd1Room, BookingKind.GD_ROOM, d1, LocalTime.of(14, 0), LocalTime.of(16, 0), "Placement GD practice", BookingStatus.PENDING, null);
        Booking g2 = room(st("riya"), gd2Room, BookingKind.GD_ROOM, d1, LocalTime.of(9, 0), LocalTime.of(11, 0), "Case interview practice", BookingStatus.PENDING, byteForce);
        g2.setGroup(null);
        g2.setClaimedBy(gd1);
        g2.setClaimedAt(now.minusMinutes(25));
        bookings.save(g2);
        log(g2, gd1, ApprovalAction.CLAIMED, null, now.minusMinutes(25));
        room(st("harsh"), gd3Room, BookingKind.GD_ROOM, d2, LocalTime.of(11, 0), LocalTime.of(13, 0), "Debate society prep", BookingStatus.PENDING, null);
        room(st("aditya"), gd1Room, BookingKind.GD_ROOM, d1, LocalTime.of(16, 0), LocalTime.of(18, 0), "Hackathon pitch rehearsal", BookingStatus.PENDING, null);
        if (!calendar.isHoliday(today)) {
            Booking gt = room(st("sanya"), gd2Room, BookingKind.GD_ROOM, today, LocalTime.of(16, 0), LocalTime.of(18, 0), "Placement GD practice", BookingStatus.APPROVED, null);
            gt.setDecidedBy(gd2);
            bookings.save(gt);
            Booking gt2 = room(st("kabir"), gd1Room, BookingKind.GD_ROOM, today, LocalTime.of(14, 0), LocalTime.of(16, 0), "Interview prep", BookingStatus.APPROVED, null);
            gt2.setDecidedBy(gd1);
            bookings.save(gt2);
        }
        Booking gtm = room(st("zoya"), gd2Room, BookingKind.GD_ROOM, d1, LocalTime.of(11, 0), LocalTime.of(13, 0), "Mock GD with seniors", BookingStatus.APPROVED, null);
        gtm.setDecidedBy(gd2);
        bookings.save(gtm);

        // Lab / seminar room requests.
        room(st("arjun"), rRoom, BookingKind.LAB_ROOM, d1, LocalTime.of(10, 0), LocalTime.of(12, 0), "Robot testing before the demo", BookingStatus.PENDING, roboRangers);
        room(pgupta, sem2, BookingKind.LAB_ROOM, d2, LocalTime.of(14, 0), LocalTime.of(16, 0), "Department seminar on VLSI careers", BookingStatus.PENDING, null);
        Booking semOk = room(st("aarav"), sem1, BookingKind.LAB_ROOM, d2, LocalTime.of(15, 0), LocalTime.of(17, 0), "Coding Club workshop: Git basics", BookingStatus.APPROVED, null);
        semOk.setDecidedBy(asharma);
        bookings.save(semOk);

        // Society requests: a renewal and a new one.
        Booking renewal = society(coding, st("aarav"), soc101, today.plusDays(5), today.plusDays(30), "Hack nights, next month", BookingStatus.PENDING,
                bookings.findBySocietyOrderBySlotDateDesc(coding).get(0));
        renewal.setCreatedAt(now.minusHours(6));
        bookings.save(renewal);
        society(robotics, st("arjun"), soc102, d1, d1.plusDays(20), "Robocon build sessions", BookingStatus.PENDING, null);
    }

    // ================================================================== chats

    private void chats() {
        List<Booking> all = bookings.findAll();
        Booking p1 = find(all, st("aarav"), BookingStatus.APPROVED, rpi);
        convo(p1, List.of(
                msg(st("aarav"), "I'll pick it up around 2 PM, is that fine?", 180),
                msg(labComp, "Sure, it's on the shelf by the door. Bring your ID card.", 172),
                msg(st("aarav"), "Does it come with the 32 GB card or should I bring mine?", 30),
                msg(labComp, "32 GB card is in the case, already flashed with Raspberry Pi OS.", 22)));
        Booking o2 = find(all, st("sanya"), BookingStatus.CHECKED_OUT, rpi);
        convo(o2, List.of(
                msg(st("sanya"), "Note: we need the Pi for the ByteForce kiosk demo.", 300),
                msg(labComp, "Approved. Please keep it in the case, the last one came back scratched.", 240),
                msg(st("sanya"), "Will do, thanks!", 235)));
        Booking o3 = find(all, st("karan"), BookingStatus.CHECKED_OUT, servo);
        convo(o3, List.of(
                msg(st("karan"), "Can we keep the servos till tomorrow evening? Demo got shifted.", 90),
                msg(labRobo, "The loan is 24 hours, so it's already overdue. Return it by 5 PM today to avoid a strike.", 75),
                msg(st("karan"), "Okay, coming at 4.", 70)));
        Booking q1 = find(all, st("ishita"), BookingStatus.PENDING, osc);
        convo(q1, List.of(msg(st("ishita"), "Could I get the x10 probes with the oscilloscope? Practising for the evaluation.", 50)));
        Booking g2 = all.stream().filter(b -> b.getKind() == BookingKind.GD_ROOM && b.getRequester().sameAs(st("riya")) && b.isPending()).findFirst().orElseThrow();
        convo(g2, List.of(msg(st("riya"), "We're 8 people, so GD Room 2 works best for us.", 35)));
        Booking music = all.stream().filter(b -> b.getSociety() != null && b.getSociety().getName().equals("Music Society")).findFirst().orElseThrow();
        convo(music, List.of(
                msg(st("diya"), "Can we leave the drum kit in the room overnight?", 2000),
                msg(soc2, "Yes, but lock the cupboard and sign the register.", 1900)));

        direct(admin, gd1, List.of(
                msg(admin, "Hi Gaurav, the placement cell wants GD-2 every Friday next month. Can you keep it free?", 600),
                msg(gd1, "Sure, I'll block it. Should I reject any student requests that clash?", 540),
                msg(admin, "Offer them GD-1 or GD-3 instead. Thanks!", 530)));
        direct(labElec, rkumar, List.of(
                msg(labElec, "Sir, OSC-03 goes for calibration on Thursday. Ananya's evaluation booking is on that day.", 320),
                msg(rkumar, "Let me check if Dr. Jain's lab can lend us one. Keep it flagged for now.", 300)));
        direct(soc1, soc2, List.of(
                msg(soc1, "Coding Club's renewal came in. I'll take it unless you've started.", 120),
                msg(soc2, "Go ahead, I'm handling Robotics Society's request.", 110)));
        direct(labRobo, vsingh, List.of(
                msg(labRobo, "Robotic arm ARM-02 came back damaged. I've filed a report and pulled it from service.", 4300),
                msg(vsingh, "Thanks Kuldeep. Get a repair quote; Robo Rangers stay blocked till we decide.", 4200)));
    }

    // ================================================================== announcements, notifications

    private void announcementsAndNotifications() {
        announce(admin, "Everyone", "ResrvIt is live for the semester. Book equipment up to a week ahead and return it within 24 hours.", users.count(), 9000);
        announce(labElec, "All students", "Electronics Lab will be closed on Saturday for the annual safety audit.", students.size(), 2000);
        announce(soc1, "Coding Club office-bearers", "Please submit your renewal at least 5 days before your booking ends.", 2, 300);

        note(st("aarav"), "Approved: Raspberry Pi 4 · RPI-01 — Computer Lab. Show the QR code at the lab to collect it.", "/bookings", 170, false);
        note(st("aarav"), "Manoj Yadav: 32 GB card is in the case, already flashed with Raspberry Pi OS.", "/chat", 22, false);
        note(st("aarav"), "Coding Club's booking of Society Room 101 ends soon. Request a renewal to keep it.", "/bookings/new/society", 1440, true);
        note(st("pooja"), "Your Jetson Nano slot is full. We've held another day for you; accept it or pick another within 24 hours.", "/bookings", 240, false);
        note(st("yash"), "You have 3 strikes, so booking is blocked for 14 days.", "/settings#strikes", 5700, false);
        note(st("karan"), "Overdue: SRV-01 was due back today. Return it now; late returns are strikes.", "/bookings", 60, false);
        note(rkumar, "New equipment request: Oscilloscope from Ishita Garg", "/faculty/approvals", 400, false);
        note(rkumar, "Harpreet Singh: Sir, OSC-03 goes for calibration on Thursday.", "/chat", 320, true);
        note(admin, "Some bookings could not be moved and are flagged critical.", "/admin/critical", 300, false);
        note(admin, "New account waiting for activation: Neha Rao (neha" + dom + ")", "/admin/users?status=PENDING_ACTIVATION", 800, false);
        note(gd1, "New GD room request: GD Room 1 from Diya Arora", "/gd", 200, false);
        note(soc1, "Renewal request: Coding Club, Society Room 101", "/society-admin", 360, false);
        note(labElec, "Damage reported on ARM-02 by Kuldeep Saini.", "/assistant/incidents", 4300, true);
        note(st("arjun"), "Damage reported on ARM-02. You and your group can't book Robotics Lab equipment until it's resolved.", "/bookings", 4300, false);
    }

    // ================================================================== builders

    private Department dept(String code, String name) {
        var existing = departments.findByCodeIgnoreCase(code);
        if (existing.isPresent()) return existing.get();
        Department d = new Department();
        d.setCode(code);
        d.setName(name);
        return departments.save(d);
    }

    private User user(String handle, String name, Role role, Department dept, Lab lab, String roll, String phone) {
        // An account with this email already exists (e.g. the bootstrap admin): reuse it untouched.
        var existing = users.findByEmailIgnoreCase(handle + dom);
        if (existing.isPresent()) return existing.get();
        User u = new User();
        u.setEmail(handle + dom);
        u.setFullName(name);
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setDepartment(dept);
        u.setLab(lab);
        u.setRollNo(roll);
        u.setPhone(phone);
        u.setPasswordHash(passwordHash);
        u.setCreatedAt(now.minusDays(60 + rnd.nextInt(30)));
        return users.save(u);
    }

    private User pendingUser(String handle, String name, String roll, Department dept) {
        var existing = users.findByEmailIgnoreCase(handle + dom);
        if (existing.isPresent()) return existing.get();
        User u = user(handle, name, Role.STUDENT, dept, null, roll, null);
        u.setStatus(UserStatus.PENDING_ACTIVATION);
        u.setCreatedAt(now.minusHours(5 + rnd.nextInt(40)));
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

    private Room room(String code, String name, RoomType type, Lab lab, User inCharge, int cap) {
        Room r = new Room();
        r.setCode(code);
        r.setName(name);
        r.setType(type);
        r.setLab(lab);
        r.setDepartment(lab != null ? lab.getDepartment() : null);
        r.setInCharge(inCharge);
        r.setCapacity(cap);
        r.setBookable(true);
        return rooms.save(r);
    }

    private EquipmentType type(String name, String desc) {
        var existing = types.findByNameIgnoreCase(name);
        if (existing.isPresent()) return existing.get();
        EquipmentType t = new EquipmentType();
        t.setName(name);
        t.setDescription(desc);
        return types.save(t);
    }

    private void items(EquipmentType t, Lab lab, String prefix, int n, String condition) {
        List<Equipment> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Equipment e = new Equipment();
            e.setAssetTag(prefix + "-" + (i < 10 ? "0" : "") + i);
            e.setType(t);
            e.setLab(lab);
            e.setStatus(EquipmentStatus.AVAILABLE);
            e.setConditionNote(condition);
            list.add(equipment.save(e));
        }
        items.put(t, list);
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

    private void day(LocalDate d, DayType type, String desc) {
        if (calendarDays.findByCalDate(d).isPresent()) return;
        CalendarDay c = new CalendarDay();
        c.setCalDate(d);
        c.setDayType(type);
        c.setDescription(desc);
        calendarDays.save(c);
    }

    private EvaluationSchedule eval(User f, Lab lab, EquipmentType t, String course, String title, LocalDate d) {
        EvaluationSchedule e = new EvaluationSchedule();
        e.setFaculty(f);
        e.setLab(lab);
        e.setEquipmentType(t);
        e.setCourseCode(course);
        e.setTitle(title);
        e.setEvalDate(d);
        e.setCreatedAt(now.minusDays(10));
        return evaluations.save(e);
    }

    private Society society(String name, String desc) {
        var existing = societies.findAllByOrderByNameAsc().stream().filter(x -> x.getName().equalsIgnoreCase(name)).findFirst();
        if (existing.isPresent()) return existing.get();
        Society s = new Society();
        s.setName(name);
        s.setDescription(desc);
        s.setActive(true);
        s.setCreatedAt(now.minusDays(80));
        return societies.save(s);
    }

    private void bearer(Society s, User u, String position) {
        SocietyOfficeBearer b = new SocietyOfficeBearer();
        b.setSociety(s);
        b.setUser(u);
        b.setPosition(position);
        bearers.save(b);
    }

    private StudyGroup group(String name, String course, String project, User leaderOrCreator, User faculty, GroupStatus status, User... memberList) {
        StudyGroup g = new StudyGroup();
        g.setName(name);
        g.setCourseCode(course);
        g.setProjectTitle(project);
        g.setLeader(memberList[0]);
        g.setCreatedBy(leaderOrCreator);
        g.setCourseFaculty(faculty);
        g.setStatus(status);
        g.setCreatedAt(now.minusDays(20 + rnd.nextInt(20)));
        for (User u : memberList) {
            GroupMember m = new GroupMember();
            m.setGroup(g);
            m.setUser(u);
            m.setConfirmed(true);
            g.getMembers().add(m);
        }
        return groups.save(g);
    }

    private Booking equip(User u, Lab lab, EquipmentType t, LocalDate day, BookingReason reason, BookingStatus status, Equipment item, StudyGroup group) {
        Booking b = new Booking();
        b.setKind(BookingKind.EQUIPMENT);
        b.setStatus(status);
        b.setRequester(u);
        b.setGroup(group);
        b.setLab(lab);
        b.setEquipmentType(t);
        b.setEquipment(item);
        b.setSlotDate(day);
        b.setReason(reason);
        b.setQueueRank(BookingService.nextRank());
        b.setCreatedAt(day.isBefore(today) ? day.minusDays(1).atTime(11, 0) : now.minusHours(1 + rnd.nextInt(30)));
        if (item != null && status != BookingStatus.PENDING) b.setQrToken(token());
        bookings.save(b);
        if (status == BookingStatus.PENDING) log(b, u, ApprovalAction.REQUESTED, null, b.getCreatedAt());
        return b;
    }

    private Booking room(User u, Room r, BookingKind kind, LocalDate day, LocalTime s, LocalTime e, String why, BookingStatus status, StudyGroup group) {
        Booking b = new Booking();
        b.setKind(kind);
        b.setStatus(status);
        b.setRequester(u);
        b.setGroup(group);
        b.setRoom(r);
        b.setSlotDate(day);
        b.setStartTime(s);
        b.setEndTime(e);
        b.setReasonText(why);
        b.setQueueRank(BookingService.nextRank());
        b.setCreatedAt(day.isBefore(today) ? day.minusDays(1).atTime(12, 0) : now.minusHours(1 + rnd.nextInt(20)));
        bookings.save(b);
        log(b, u, ApprovalAction.REQUESTED, null, b.getCreatedAt());
        return b;
    }

    private Booking society(Society s, User u, Room r, LocalDate from, LocalDate to, String why, BookingStatus status, Booking previous) {
        Booking b = new Booking();
        b.setKind(BookingKind.SOCIETY_ROOM);
        b.setStatus(status);
        b.setRequester(u);
        b.setSociety(s);
        b.setRoom(r);
        b.setSlotDate(from);
        b.setEndDate(to);
        b.setReasonText(why);
        b.setPreviousBooking(previous);
        b.setQueueRank(BookingService.nextRank());
        b.setCreatedAt(from.isBefore(today) ? from.minusDays(3).atTime(18, 0) : now.minusHours(2 + rnd.nextInt(20)));
        List<LocalDate> excl = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDate dd = d;
            boolean classClash = calendar.timetableBusy(r, d).stream().anyMatch(sl -> sl.overlaps(calendar.societyWindow(dd)));
            if (classClash) excl.add(d);
        }
        b.setExcludedDates(excl.isEmpty() ? null : String.join(",", excl.stream().map(LocalDate::toString).toList()));
        bookings.save(b);
        log(b, u, previous != null ? ApprovalAction.RENEWAL_REQUESTED : ApprovalAction.REQUESTED, null, b.getCreatedAt());
        return b;
    }

    private void approve(Booking b, User approver) {
        b.setDecidedBy(approver);
        b.setDecidedAt(b.getCreatedAt().plusMinutes(30 + rnd.nextInt(200)));
        if (b.getQrToken() == null) b.setQrToken(token());
        bookings.save(b);
        log(b, approver, ApprovalAction.APPROVED, null, b.getDecidedAt());
    }

    private void checkout(Booking b, Equipment item, User assistant, LocalDateTime out, LocalDateTime back, boolean damaged, String condOut, String condIn) {
        Checkout c = new Checkout();
        c.setBooking(b);
        c.setEquipment(item);
        c.setIssuedBy(assistant);
        c.setIssuedAt(out);
        c.setConditionOut(condOut);
        c.setDueAt(out.plusHours(24));
        if (back != null) {
            c.setReturnedAt(back);
            c.setReceivedBy(assistant);
            c.setConditionIn(condIn);
            c.setLate(back.isAfter(c.getDueAt()));
            c.setDamaged(damaged);
        }
        checkouts.save(c);
        log(b, assistant, ApprovalAction.CHECKED_OUT, "Condition out: " + condOut, out);
        if (back != null) log(b, assistant, ApprovalAction.RETURNED, (c.isLate() ? "Returned late" : "Returned") + (damaged ? " with damage" : "") + "; condition: " + condIn, back);
    }

    private Booking lateReturn(User u, Lab lab, EquipmentType t, LocalDate day, int hoursLate) {
        Equipment item = items.get(t).stream().filter(e -> e.getStatus() == EquipmentStatus.AVAILABLE).reduce((a, b) -> b).orElseThrow();
        Booking b = equip(u, lab, t, day, BookingReason.PROJECT_WORK, BookingStatus.RETURNED, item, null);
        log(b, lab.getInCharge(), ApprovalAction.APPROVED, null, day.minusDays(1).atTime(14, 0));
        LocalDateTime out = day.atTime(10, 0);
        checkout(b, item, assistantOf(lab), out, out.plusHours(24 + hoursLate), false, "Good", "Good");
        Strike s = new Strike();
        s.setUser(u);
        s.setBooking(b);
        s.setReason("Late return of " + item.getAssetTag() + " (" + hoursLate + " h late)");
        s.setCreatedAt(out.plusHours(24 + hoursLate));
        strikes.save(s);
        return b;
    }

    private record Msg(User from, String text, int minutesAgo) {}

    private Msg msg(User from, String text, int minutesAgo) {
        return new Msg(from, text, minutesAgo);
    }

    private void convo(Booking b, List<Msg> list) {
        Conversation c = new Conversation();
        c.setKind(ConversationKind.BOOKING);
        c.setBooking(b);
        c.setStudent(b.getRequester());
        c.setLab(b.getLab());
        c.setSubject("#" + b.getId() + " · " + b.getResourceLabel());
        c.setStatus(ConversationStatus.OPEN);
        c.setCreatedAt(now.minusMinutes(list.get(0).minutesAgo()));
        c.setLastMessageAt(c.getCreatedAt());
        conversations.save(c);
        write(c, list);
    }

    private void direct(User a, User b, List<Msg> list) {
        Conversation c = new Conversation();
        c.setKind(ConversationKind.DIRECT);
        c.setSubject("Direct chat");
        c.setStatus(ConversationStatus.OPEN);
        c.setCreatedAt(now.minusMinutes(list.get(0).minutesAgo()));
        c.setLastMessageAt(c.getCreatedAt());
        conversations.save(c);
        for (User u : List.of(a, b)) {
            ConversationMember m = new ConversationMember();
            m.setConversation(c);
            m.setUser(u);
            members.save(m);
        }
        write(c, list);
    }

    private void write(Conversation c, List<Msg> list) {
        LocalDateTime last = null;
        for (Msg m : list) {
            CryptoService.Sealed sealed = crypto.encrypt(m.text(), c.getId());
            Message msg = new Message();
            msg.setConversation(c);
            msg.setSender(m.from());
            msg.setCiphertext(sealed.ciphertext());
            msg.setIv(sealed.iv());
            msg.setCreatedAt(now.minusMinutes(m.minutesAgo()));
            messages.save(msg);
            last = msg.getCreatedAt();
            if (!c.isDirect() && m.from().isStaff() && c.getFirstResponseAt() == null) c.setFirstResponseAt(msg.getCreatedAt());
            // Each sender has read everything up to their own message.
            ConversationMember read = members.findByConversationAndUser(c, m.from()).orElseGet(() -> {
                ConversationMember n = new ConversationMember();
                n.setConversation(c);
                n.setUser(m.from());
                return n;
            });
            read.setLastReadAt(msg.getCreatedAt());
            members.save(read);
        }
        c.setLastMessageAt(last);
        conversations.save(c);
    }

    private void announce(User sender, String audience, String text, long count, int minutesAgo) {
        Announcement a = new Announcement();
        a.setSender(sender);
        a.setAudience(audience);
        a.setMessage(text);
        a.setRecipientCount((int) count);
        a.setCreatedAt(now.minusMinutes(minutesAgo));
        announcements.save(a);
    }

    private void note(User u, String text, String link, int minutesAgo, boolean read) {
        Notification n = new Notification();
        n.setUser(u);
        n.setMessage(text);
        n.setLink(link);
        n.setRead(read);
        n.setCreatedAt(now.minusMinutes(minutesAgo));
        notifications.save(n);
    }

    private void log(Booking b, User actor, ApprovalAction action, String note, LocalDateTime when) {
        Approval a = new Approval();
        a.setBooking(b);
        a.setActor(actor);
        a.setAction(action);
        a.setNote(note);
        a.setCreatedAt(when);
        approvals.save(a);
    }

    private Booking find(List<Booking> all, User u, BookingStatus status, EquipmentType t) {
        return all.stream().filter(b -> b.getRequester().sameAs(u) && b.getStatus() == status
                && b.getEquipmentType() != null && b.getEquipmentType().sameAs(t)).findFirst().orElseThrow();
    }

    private User assistantOf(Lab l) {
        if (l.sameAs(elab)) return labElec;
        if (l.sameAs(clab)) return labComp;
        if (l.sameAs(rlab)) return labRobo;
        return labEmb;
    }

    private LocalDate nextWorking(LocalDate d) {
        while (calendar.isHoliday(d)) d = d.plusDays(1);
        return d;
    }

    private LocalDate workingBefore(LocalDate d) {
        while (calendar.isHoliday(d)) d = d.minusDays(1);
        return d;
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
