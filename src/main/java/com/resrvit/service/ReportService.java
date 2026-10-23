package com.resrvit.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.resrvit.domain.*;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Utilisation, defaulters, assistant response times and society usage, as tables, CSV or PDF. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    public enum Kind {
        UTILISATION("Utilisation"), DEFAULTERS("Defaulters"), RESPONSE_TIMES("Assistant response times"), SOCIETY_USAGE("Society usage");
        private final String label;
        Kind(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public record Table(String title, String subtitle, List<String> headers, List<List<String>> rows) {}

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final Set<BookingStatus> USED = EnumSet.of(BookingStatus.APPROVED, BookingStatus.CHECKED_OUT, BookingStatus.RETURNED);

    private final BookingRepository bookings;
    private final EquipmentRepository equipment;
    private final LabRepository labs;
    private final RoomRepository rooms;
    private final StrikeRepository strikes;
    private final CheckoutRepository checkouts;
    private final IncidentRepository incidents;
    private final ConversationRepository conversations;
    private final SocietyRepository societies;

    public Table build(Kind kind, LocalDate from, LocalDate to) {
        return switch (kind) {
            case UTILISATION -> utilisation(from, to);
            case DEFAULTERS -> defaulters();
            case RESPONSE_TIMES -> responseTimes(from, to);
            case SOCIETY_USAGE -> societyUsage(from, to);
        };
    }

    private Table utilisation(LocalDate from, LocalDate to) {
        List<Booking> all = bookings.overlappingRange(from, to, USED);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        List<List<String>> rows = new ArrayList<>();

        // Equipment: booked item-days against item-days available.
        for (Lab lab : labs.findAllByOrderByNameAsc()) {
            for (EquipmentType type : equipment.typesInLab(lab)) {
                long items = equipment.findByLabAndType(lab, type).stream().filter(e -> e.getStatus() != EquipmentStatus.RETIRED).count();
                long booked = all.stream().filter(b -> b.isForEquipment() && b.getLab().sameAs(lab) && b.getEquipmentType().sameAs(type)).count();
                long requests = bookings.equipmentBetween(lab, type, from, to, EnumSet.allOf(BookingStatus.class)).size();
                rows.add(List.of("Equipment", type.getName() + " — " + lab.getName(), String.valueOf(requests), String.valueOf(booked),
                        booked + " of " + items * days + " item-days", pct(booked, items * days)));
            }
        }
        // Rooms: booked hours against open hours (8 AM–8 PM) on the days in range.
        for (Room room : rooms.findAllByOrderByNameAsc()) {
            if (!room.isBookable()) continue;
            List<Booking> rb = all.stream().filter(b -> b.getRoom() != null && b.getRoom().sameAs(room)).toList();
            if (room.getType() == RoomType.SOCIETY_ROOM) {
                long heldDays = 0;
                for (Booking b : rb) {
                    LocalDate s = b.getSlotDate().isBefore(from) ? from : b.getSlotDate();
                    LocalDate e = b.getEndDate().isAfter(to) ? to : b.getEndDate();
                    heldDays += ChronoUnit.DAYS.between(s, e) + 1 - b.getExcludedDateList().stream().filter(d -> !d.isBefore(s) && !d.isAfter(e)).count();
                }
                rows.add(List.of(room.getType().getLabel(), room.getName(), String.valueOf(rb.size()), String.valueOf(rb.size()),
                        heldDays + " of " + days + " evenings", pct(heldDays, days)));
            } else {
                double hours = rb.stream().mapToDouble(b -> Duration.between(b.getStartTime(), b.getEndTime()).toMinutes() / 60.0).sum();
                long open = days * 12;
                rows.add(List.of(room.getType().getLabel(), room.getName(), String.valueOf(rb.size()), String.valueOf(rb.size()),
                        String.format("%.1f of %d h", hours, open), pct((long) hours, open)));
            }
        }
        return new Table("Utilisation", range(from, to),
                List.of("Kind", "Resource", "Requests", "Bookings used", "Booked", "Utilisation"), rows);
    }

    private Table defaulters() {
        Map<Long, List<Strike>> byUser = strikes.findAllByOrderByCreatedAtDesc().stream()
                .collect(Collectors.groupingBy(s -> s.getUser().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Checkout> late = checkouts.findByLateTrue();
        List<Incident> openIncidents = incidents.findByStatusOrderByCreatedAtDesc(IncidentStatus.OPEN);
        List<List<String>> rows = new ArrayList<>();
        for (List<Strike> list : byUser.values()) {
            User u = list.get(0).getUser();
            long active = list.stream().filter(s -> !s.isWaived() && !s.isConsumed()).count();
            long waived = list.stream().filter(Strike::isWaived).count();
            long lateCount = late.stream().filter(c -> c.getBooking().getRequester().sameAs(u)).count();
            long inc = openIncidents.stream().filter(i -> i.getBooking() != null && i.getBooking().getRequester().sameAs(u)).count();
            rows.add(List.of(u.getFullName(), u.getRollNo() == null ? "" : u.getRollNo(), u.getEmail(), String.valueOf(list.size()),
                    String.valueOf(active), String.valueOf(waived), String.valueOf(lateCount), String.valueOf(inc),
                    u.getBlockedUntil() == null ? "" : D.format(u.getBlockedUntil())));
        }
        rows.sort(Comparator.comparing((List<String> r) -> -Integer.parseInt(r.get(3))));
        return new Table("Defaulters", "All time",
                List.of("Name", "Roll no", "Email", "Strikes", "Active", "Waived", "Late returns", "Open damage", "Blocked until"), rows);
    }

    private Table responseTimes(LocalDate from, LocalDate to) {
        List<Conversation> all = conversations.findAll().stream()
                .filter(c -> !c.getCreatedAt().toLocalDate().isBefore(from) && !c.getCreatedAt().toLocalDate().isAfter(to)).toList();
        List<List<String>> rows = new ArrayList<>();
        for (Lab lab : labs.findAllByOrderByNameAsc()) {
            List<Conversation> cs = all.stream().filter(c -> !c.isDirect() && c.getLab() != null && c.getLab().sameAs(lab)).toList();
            if (cs.isEmpty()) continue;
            List<Long> mins = cs.stream().filter(c -> c.getFirstResponseAt() != null)
                    .map(c -> Duration.between(c.getCreatedAt(), c.getFirstResponseAt()).toMinutes()).sorted().toList();
            List<Long> urgentMins = cs.stream().filter(c -> c.getFirstResponseAt() != null && c.getUrgentAt() != null)
                    .map(c -> Duration.between(c.getCreatedAt(), c.getFirstResponseAt()).toMinutes()).toList();
            OptionalDouble rating = cs.stream().filter(c -> c.getRating() != null).mapToInt(Conversation::getRating).average();
            rows.add(List.of(lab.getName(), String.valueOf(cs.size()), String.valueOf(mins.size()),
                    mins.isEmpty() ? "-" : fmtMinutes(mins.stream().mapToLong(Long::longValue).average().orElse(0)),
                    mins.isEmpty() ? "-" : fmtMinutes(mins.get(mins.size() / 2)),
                    urgentMins.isEmpty() ? "-" : fmtMinutes(urgentMins.stream().mapToLong(Long::longValue).average().orElse(0)),
                    String.valueOf(cs.stream().filter(c -> c.getStatus() == ConversationStatus.RESOLVED).count()),
                    rating.isPresent() ? String.format("%.1f / 5", rating.getAsDouble()) : "-"));
        }
        return new Table("Assistant response times", range(from, to),
                List.of("Lab", "Threads", "Answered", "Avg first reply", "Median first reply", "Avg (urgent)", "Resolved", "Rating"), rows);
    }

    private Table societyUsage(LocalDate from, LocalDate to) {
        List<List<String>> rows = new ArrayList<>();
        for (Society s : societies.findAllByOrderByNameAsc()) {
            List<Booking> list = bookings.findBySocietyOrderBySlotDateDesc(s).stream()
                    .filter(b -> !b.getSlotDate().isAfter(to) && !b.getEndDate().isBefore(from)).toList();
            long approved = list.stream().filter(b -> USED.contains(b.getStatus())).count();
            long renewals = list.stream().filter(b -> b.getPreviousBooking() != null).count();
            long evenings = 0;
            for (Booking b : list) {
                if (!USED.contains(b.getStatus())) continue;
                LocalDate st = b.getSlotDate().isBefore(from) ? from : b.getSlotDate();
                LocalDate en = b.getEndDate().isAfter(to) ? to : b.getEndDate();
                evenings += ChronoUnit.DAYS.between(st, en) + 1 - b.getExcludedDateList().stream().filter(d -> !d.isBefore(st) && !d.isAfter(en)).count();
            }
            String roomsUsed = list.stream().map(b -> b.getRoom().getName()).distinct().collect(Collectors.joining(", "));
            rows.add(List.of(s.getName(), s.isActive() ? "Active" : "Inactive", String.valueOf(list.size()), String.valueOf(approved),
                    String.valueOf(renewals), String.valueOf(evenings), roomsUsed));
        }
        return new Table("Society usage", range(from, to),
                List.of("Society", "Status", "Requests", "Approved", "Renewals", "Days held", "Rooms"), rows);
    }

    // ------------------------------------------------------------------ output

    public byte[] csv(Table t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.headers().stream().map(Csv::escape).collect(Collectors.joining(","))).append("\r\n");
        for (List<String> r : t.rows()) sb.append(r.stream().map(Csv::escape).collect(Collectors.joining(","))).append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] pdf(Table t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(t.headers().size() > 6 ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x1f, 0x2a, 0x44));
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        doc.add(new Paragraph("ResrvIt — " + t.title(), title));
        doc.add(new Paragraph(t.subtitle() + " · generated " + D.format(LocalDate.now(TimeService.ZONE)), small));
        doc.add(Chunk.NEWLINE);
        PdfPTable table = new PdfPTable(t.headers().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : t.headers()) {
            PdfPCell c = new PdfPCell(new Phrase(h, head));
            c.setBackgroundColor(new Color(0x2b, 0x4c, 0x7e));
            c.setPadding(4);
            table.addCell(c);
        }
        boolean shade = false;
        for (List<String> r : t.rows()) {
            for (String v : r) {
                PdfPCell c = new PdfPCell(new Phrase(v, cell));
                c.setPadding(4);
                if (shade) c.setBackgroundColor(new Color(0xf1, 0xf4, 0xf9));
                table.addCell(c);
            }
            shade = !shade;
        }
        if (t.rows().isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("No data for this period.", cell));
            c.setColspan(t.headers().size());
            c.setPadding(6);
            table.addCell(c);
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    private static String pct(long part, long whole) {
        return whole <= 0 ? "-" : Math.round(100.0 * part / whole) + "%";
    }

    private static String fmtMinutes(double m) {
        if (m < 60) return Math.round(m) + " min";
        return String.format("%.1f h", m / 60.0);
    }

    private static String range(LocalDate from, LocalDate to) {
        return D.format(from) + " – " + D.format(to);
    }
}
