# ResrvIt

Campus room and lab-equipment booking for students, faculty and societies: requests, approvals, fair-use limits, visible queues, QR check-out/in, strikes, damage reports, maintenance blocks and encrypted student ↔ lab-assistant chat.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA + Flyway · PostgreSQL (H2 locally) · Thymeleaf + Bootstrap 5 + FullCalendar · STOMP WebSockets · Spring Mail · ZXing · OpenPDF · Scala 3 (slot-suggestion engine) · Maven · Docker · Render.

---

## Run it locally

You need **JDK 21**. Maven is not needed; the wrapper downloads it.

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

Open http://localhost:8080. Locally the app uses an in-memory H2 database and loads a demo campus, so **data resets on every restart**. The H2 console is at `/h2-console` (JDBC URL `jdbc:h2:mem:resrvit`, user `sa`, no password).

Email isn't configured locally, so emails are written to the console log and the signup page shows the OTP on screen.

### Demo accounts (password `password`)

| Role | Email | Notes |
|---|---|---|
| Super admin | `admin@thapar.edu` | Everything: users, campus data, timetable, calendar, reports |
| GD Room Admins | `gd.admin1@thapar.edu`, `gd.admin2@thapar.edu` | Share one GD queue |
| Society Room Admins | `society.admin1@thapar.edu`, `society.admin2@thapar.edu` | Share one society queue |
| Faculty (in-charge, Electronics Lab) | `rkumar@thapar.edu` | Approves Electronics Lab equipment and room E-201 |
| Faculty (in-charge, Computer Lab + Seminar Hall) | `asharma@thapar.edu` | |
| Faculty | `pgupta@thapar.edu` | Not in charge of anything |
| Lab assistants | `lab.elec@thapar.edu`, `lab.comp@thapar.edu` | Electronics Lab / Computer Lab |
| Students | `aarav@`, `diya@`, `kabir@`, `meera@`, `rohan@thapar.edu` | Aarav leads Team Volt and is Coding Club President; Diya is Music Society Secretary |

The demo includes two students queued for the same oscilloscope, an approved Raspberry Pi pick-up for today (open it as Aarav to see the QR code, then scan it as `lab.comp`), a pending GD request, a pending society booking and a chat thread.

### Tests

```bash
mvnw.cmd test        # or ./mvnw test
```

27 tests: the Scala slot engine, AES-GCM chat encryption, and every booking rule end to end (rolling weekly limit, group limits, evaluation dates, queue order, bumping with offers, the 2-hour fallback, emergency override, reorder audit, strikes, damage blocks, GD and society rules, timetable/holiday/maintenance collisions, signup/OTP/activation, deactivation, and the chat rules).

---

## Deploy to Render

1. Push this repo to GitHub.
2. In Render: **New → Blueprint**, pick the repo. `render.yaml` creates a Docker web service and a PostgreSQL database and wires `DATABASE_URL` for you.
3. Fill in the variables Render asks for:
   - `RESRVIT_CHAT_KEY`: run `openssl rand -base64 32` and paste the result. **Never change it** once chat messages exist, or old messages can't be decrypted.
   - `ADMIN_EMAIL` / `ADMIN_PASSWORD`: the first super admin, created on first start (8+ character password).
   - `RESRVIT_BASE_URL`: your Render URL, used in email links.
   - `MAIL_*`: an SMTP account (for Gmail, an App Password). Without it, signups can't receive OTPs in production.
   - Set `RESRVIT_SEED=true` only if you want the demo campus on an empty database.
   - `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` (optional): turns on "Continue with Google". See below.
4. Deploy. Flyway creates the schema on first start; `/actuator/health` is the health check.

Free Render instances sleep when idle, so scheduled jobs (expiry, reminders) only run while the service is awake.

### Google sign-in (optional)

Only `@thapar.edu` Google accounts get in (checked against both the email and Google's hosted-domain claim). A first-time Google user gets a student account that waits for admin activation.

1. Google Cloud Console → APIs & Services → **OAuth consent screen**: set it up (Internal if your Google Workspace allows it, otherwise External).
2. **Credentials → Create credentials → OAuth client ID → Web application.**
3. Authorised redirect URIs: `https://<your-render-url>/login/oauth2/code/google` (and `http://localhost:8080/login/oauth2/code/google` for local testing).
4. Put the client ID and secret into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. The button appears on the sign-in page; without them it's hidden.

---

## How the rules are implemented

| Rule | Where |
|---|---|
| Rolling 7-day limit per student per equipment type (a Wednesday booking unlocks next Wednesday) | `BookingService.checkWeeklyLimit` |
| Group shares the limit; members can't book individually that week; only the leader books | `BookingService.checkWeeklyLimit`, `resolveGroup` |
| Evaluation practice: pick a faculty-entered date; slot must be before it; booking becomes protected | `BookingService.requestEquipment`, `EvaluationService` |
| Strict queue order, item assignment on approval | `ApprovalService.approve` / `aheadOf` |
| Slot full → everyone waiting gets the next free day plus up to 2 alternatives for 24 h (first kept if no reply); nothing free within 7 days → stays first in line | `ApprovalService.bumpQueue`, `offerAlternatives`, `ScheduledJobs.settleOffers` |
| Lab-assistant fallback (in-charge on leave, or urgent/evaluation request waiting 2 h) and emergency approval | `ApprovalService.modeFor`, `emergencyApprove` |
| Emergency booking overriding the weekly limit (reason + audit) | `BookingService.emergencyBooking` |
| Drag-to-reorder pending requests with mandatory reason and audit | `ApprovalService.reorder`, `assistant/queue.html` |
| GD and society admins share a queue; claiming locks a request (optimistic locking) | `ApprovalService.claim` |
| GD: fixed slots, 3 days ahead, one per day per student or group | `BookingService.requestGdRoom` |
| Society: office-bearers only, ≤ 30 days, fixed window, timetable days excluded, one live booking per society per room, renewals with history, reminder 5 days before expiry | `BookingService.requestSocietyRoom`, `ScheduledJobs.remindRenewals` |
| Requests expire when the slot starts unapproved (equipment: when the 9–5 pick-up window closes) | `ScheduledJobs.expirePending` |
| Timetable / holiday / maintenance changes: dry-run impact preview, then replacement item/day/slot or "critical" flag; protected evaluations are never moved | `CollisionService` |
| QR check-out/in, 24 h loans, late return = strike, 3 strikes = 14-day block, waivers | `CheckoutService`, `StrikeService` |
| Damage report blocks the student and their whole group from that lab's equipment until resolved | `BookingService.requireNoOpenIncident` |
| Chat: staff chat with each other any time; a student adds one note when requesting, and the booking chat opens for both sides once approved. Encrypted at rest with AES-256-GCM | `ChatService`, `CryptoService` |
| Announcements from admins and lab assistants to everyone, a role, one person, a study group or a society | `AnnouncementService` |
| Slot suggestions (nearest free days / slots) | Scala: `src/main/scala/com/resrvit/engine/SlotEngine.scala` |

## Decisions on the open items

These are settings (super admin → Settings), so they can change without code:

- **College email domain:** `thapar.edu` (`email_domain`).
- **Equipment lead time:** 7 days (`equipment_lead_days`); also used for lab and seminar rooms.
- **Leavers:** deactivating an account cancels its pending and approved bookings and keeps its history. If they led a group, the next member takes over automatically (`leader_succession = NEXT_MEMBER`); set `FACULTY_PICKS` to notify the course faculty instead.

Other choices worth knowing:

- Holidays close the labs (no equipment pick-ups) but don't block rooms; dates not in the calendar are working days Monday–Friday and holidays at weekends.
- Lab and seminar rooms can be booked 8 AM–8 PM.
- The plan's `groups` table is named `study_groups` (`GROUPS` is reserved in H2), and queue order lives in `bookings.queue_rank` with every manual change in `queue_audit`, rather than a separate `queue_entries` table.
- Bootstrap, FullCalendar, STOMP and the QR scanner load from the jsDelivr CDN, so the browser needs internet access.

## Project layout

```
src/main/java/com/resrvit/
  config/    security, WebSocket, Render DATABASE_URL conversion, demo seeder
  domain/    JPA entities and enums
  repo/      Spring Data repositories
  service/   all business rules (booking, approval, collisions, chat, reports, jobs)
  web/       controllers
src/main/scala/com/resrvit/engine/   slot-suggestion engine
src/main/resources/
  db/migration/V1__init.sql          schema (PostgreSQL and H2)
  templates/                         Thymeleaf pages
  static/                            CSS, JS, favicon
samples/                             CSV examples for user import, timetable and calendar uploads
```

## CSV formats

- **Users** (`Manage → Import users`): `email,full_name,role,department_code,roll_no,lab_code`
- **Timetable** (`Manage → Timetable`, replaces the whole timetable): `room_code,day,start,end,course_code,batch` with day `MON`–`SUN`
- **Calendar** (`Manage → Academic calendar`): `date,type,description` with type `WORKING`, `HOLIDAY` or `EXAM`

See `samples/` for examples matching the demo campus.
