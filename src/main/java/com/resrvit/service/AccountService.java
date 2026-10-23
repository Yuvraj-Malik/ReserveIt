package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accounts: self-signup restricted to the college domain, email OTP, admin activation,
 * bulk CSV import, role changes and deactivation (bookings cancelled, history kept).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    public static final int OTP_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final DepartmentRepository departments;
    private final LabRepository labs;
    private final BookingRepository bookings;
    private final PasswordEncoder encoder;
    private final SettingsService settings;
    private final MailService mail;
    private final NotificationService notifications;
    private final AuditService audit;
    private final GroupService groups;
    private final TimeService time;

    public record SignupResult(User user, String devOtp) {}

    @Transactional
    public SignupResult signup(String email, String fullName, String rollNo, Long departmentId, Role role, String password) {
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String domain = settings.emailDomain();
        if (!email.endsWith("@" + domain)) throw new BookingException("Use your college email (@" + domain + ").");
        if (fullName == null || fullName.isBlank()) throw new BookingException("Enter your name.");
        if (role != Role.STUDENT && role != Role.FACULTY && role != Role.LAB_ASSISTANT) {
            throw new BookingException("Sign up as a student, faculty member or lab assistant; admins are assigned by the super admin.");
        }
        validatePassword(password);
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u != null && u.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BookingException("An account with this email already exists. Sign in instead.");
        }
        if (u == null) {
            u = new User();
            u.setEmail(email);
            u.setCreatedAt(time.now());
        }
        u.setFullName(fullName.trim());
        u.setRollNo(rollNo == null || rollNo.isBlank() ? null : rollNo.trim().toUpperCase());
        u.setDepartment(departmentId == null ? null : departments.findById(departmentId).orElse(null));
        u.setRole(role);
        u.setStatus(UserStatus.PENDING_VERIFICATION);
        u.setPasswordHash(encoder.encode(password));
        String otp = issueOtp(u);
        users.save(u);
        return new SignupResult(u, mail.isConfigured() ? null : otp);
    }

    @Transactional
    public String resendOtp(String email) {
        User u = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(x -> x.getStatus() == UserStatus.PENDING_VERIFICATION)
                .orElseThrow(() -> new BookingException("No unverified signup for that email."));
        String otp = issueOtp(u);
        users.save(u);
        return mail.isConfigured() ? null : otp;
    }

    private String issueOtp(User u) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        u.setOtpCode(otp);
        u.setOtpExpiresAt(time.now().plusMinutes(OTP_MINUTES));
        mail.send(u.getEmail(), "Your verification code", "Your ResrvIt verification code is " + otp
                + ". It expires in " + OTP_MINUTES + " minutes.");
        return otp;
    }

    @Transactional
    public User verify(String email, String code) {
        User u = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new BookingException("No signup for that email."));
        if (u.getStatus() != UserStatus.PENDING_VERIFICATION) throw new BookingException("This email is already verified.");
        if (u.getOtpCode() == null || u.getOtpExpiresAt() == null || u.getOtpExpiresAt().isBefore(time.now())) {
            throw new BookingException("The code has expired. Request a new one.");
        }
        if (!u.getOtpCode().equals(code == null ? "" : code.trim())) throw new BookingException("That code is wrong.");
        u.setOtpCode(null);
        u.setOtpExpiresAt(null);
        u.setStatus(UserStatus.PENDING_ACTIVATION);
        users.save(u);
        notifications.notifyRole(Role.SUPER_ADMIN, "New account waiting for activation: " + u.getFullName() + " (" + u.getEmail()
                + ", " + u.getRole().getLabel() + ")", "/admin/users?status=PENDING_ACTIVATION");
        return u;
    }

    @Transactional
    public void activate(User u, User admin) {
        if (u.getStatus() == UserStatus.PENDING_VERIFICATION) throw new BookingException("The user hasn't verified their email yet.");
        if (u.getRole() == Role.LAB_ASSISTANT && u.getLab() == null) throw new BookingException("Assign the lab assistant to a lab first.");
        u.setStatus(UserStatus.ACTIVE);
        u.setDeactivatedAt(null);
        users.save(u);
        notifications.notify(u, "Your ResrvIt account is active. You can sign in now.", "/");
    }

    /** Deactivated accounts lose their live bookings but keep their history. */
    @Transactional
    public void deactivate(User u, User admin) {
        if (u.sameAs(admin)) throw new BookingException("You can't deactivate yourself.");
        u.setStatus(UserStatus.DEACTIVATED);
        u.setDeactivatedAt(time.now());
        users.save(u);
        for (Booking b : bookings.liveForRequester(u, List.of(BookingStatus.PENDING, BookingStatus.APPROVED))) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setDecisionNote("Account deactivated");
            bookings.save(b);
            audit.log(b, admin, ApprovalAction.CANCELLED, "Account deactivated");
        }
        groups.handleLeaver(u);
    }

    @Transactional
    public void changeRole(User u, Role role, Long labId, User admin) {
        if (role == Role.SUPER_ADMIN && u.getRole() != Role.SUPER_ADMIN) {
            throw new BookingException("There is exactly one super admin.");
        }
        if (u.getRole() == Role.SUPER_ADMIN && role != Role.SUPER_ADMIN) throw new BookingException("The super admin's role can't be changed.");
        if ((role == Role.GD_ADMIN || role == Role.SOCIETY_ADMIN) && u.getRole() != role) {
            long current = users.findByRoleAndStatus(role, UserStatus.ACTIVE).size();
            if (current >= 2) throw new BookingException("There are already two " + role.getLabel() + "s. Change one of them first.");
        }
        Lab lab = labId == null ? null : labs.findById(labId).orElse(null);
        if (role == Role.LAB_ASSISTANT && lab == null) throw new BookingException("Pick the lab this assistant works in.");
        u.setRole(role);
        u.setLab(role == Role.LAB_ASSISTANT ? lab : null);
        users.save(u);
    }

    @Transactional
    public void changePassword(User u, String current, String next) {
        if (!encoder.matches(current == null ? "" : current, u.getPasswordHash())) throw new BookingException("Your current password is wrong.");
        validatePassword(next);
        u.setPasswordHash(encoder.encode(next));
        users.save(u);
    }

    @Transactional
    public String resetPassword(User u) {
        String temp = tempPassword();
        u.setPasswordHash(encoder.encode(temp));
        users.save(u);
        mail.send(u.getEmail(), "Your password was reset", "An admin reset your ResrvIt password. Temporary password: " + temp
                + "\nChange it after signing in.");
        return temp;
    }

    @Transactional
    public void setOnLeave(User u, boolean onLeave) {
        u.setOnLeave(onLeave);
        users.save(u);
    }

    // ------------------------------------------------------------------ CSV import

    public record ImportRow(int line, String email, String name, String outcome, String tempPassword, boolean ok) {}

    /**
     * Columns: email,full_name,role,department_code,roll_no,lab_code (header row optional).
     * Imported accounts are active straight away with a temporary password (emailed).
     */
    @Transactional
    public List<ImportRow> importCsv(InputStream in, User admin) throws IOException {
        List<ImportRow> out = new ArrayList<>();
        String domain = settings.emailDomain();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                if (line.isBlank()) continue;
                String[] c = Csv.split(line);
                if (n == 1 && c[0].trim().equalsIgnoreCase("email")) continue;
                String email = Csv.col(c, 0).toLowerCase(Locale.ROOT);
                String name = Csv.col(c, 1);
                try {
                    if (!email.endsWith("@" + domain)) throw new BookingException("not an @" + domain + " address");
                    if (users.existsByEmailIgnoreCase(email)) throw new BookingException("already exists");
                    if (name.isBlank()) throw new BookingException("name missing");
                    Role role;
                    try {
                        role = Role.valueOf(Csv.col(c, 2).toUpperCase(Locale.ROOT).replace(' ', '_'));
                    } catch (IllegalArgumentException e) {
                        throw new BookingException("unknown role '" + Csv.col(c, 2) + "'");
                    }
                    if (role == Role.SUPER_ADMIN) throw new BookingException("can't import a super admin");
                    User u = new User();
                    u.setEmail(email);
                    u.setFullName(name);
                    u.setRole(role);
                    String dept = Csv.col(c, 3);
                    if (!dept.isBlank()) u.setDepartment(departments.findByCodeIgnoreCase(dept)
                            .orElseThrow(() -> new BookingException("unknown department " + dept)));
                    String roll = Csv.col(c, 4);
                    u.setRollNo(roll.isBlank() ? null : roll.toUpperCase());
                    String lab = Csv.col(c, 5);
                    if (!lab.isBlank()) u.setLab(labs.findByCodeIgnoreCase(lab).orElseThrow(() -> new BookingException("unknown lab " + lab)));
                    if (role == Role.LAB_ASSISTANT && u.getLab() == null) throw new BookingException("lab assistants need a lab_code");
                    String temp = tempPassword();
                    u.setPasswordHash(encoder.encode(temp));
                    u.setStatus(UserStatus.ACTIVE);
                    u.setCreatedAt(time.now());
                    users.save(u);
                    mail.send(email, "Your ResrvIt account", "An account was created for you. Sign in with " + email
                            + " and temporary password " + temp + ", then change it.");
                    out.add(new ImportRow(n, email, name, "Created as " + role.getLabel(), temp, true));
                } catch (BookingException e) {
                    out.add(new ImportRow(n, email, name, "Skipped: " + e.getMessage(), null, false));
                }
            }
        }
        return out;
    }

    @Transactional
    public User bootstrapSuperAdmin(String email, String password) {
        User u = new User();
        u.setEmail(email.trim().toLowerCase(Locale.ROOT));
        u.setFullName("Super Admin");
        u.setRole(Role.SUPER_ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash(encoder.encode(password));
        u.setCreatedAt(time.now());
        return users.save(u);
    }

    private static void validatePassword(String p) {
        if (p == null || p.length() < 8) throw new BookingException("Use a password of at least 8 characters.");
    }

    private static String tempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }
}
