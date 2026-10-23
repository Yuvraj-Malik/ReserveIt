package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.BookingRepository;
import com.resrvit.repo.StudyGroupRepository;
import com.resrvit.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Groups: faculty-created groups are active at once; student-created ones need every member
 * to confirm, then the course faculty to approve. One group per student per course.
 * Membership is locked while the group has a live booking.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final StudyGroupRepository groups;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final TimeService time;

    @Transactional
    public StudyGroup createByFaculty(User faculty, String name, String course, String project,
                                      List<Long> memberIds, Long leaderId) {
        requireFacultyOrAdmin(faculty);
        List<User> members = loadStudents(memberIds);
        User leader = members.stream().filter(u -> u.getId().equals(leaderId)).findFirst()
                .orElseThrow(() -> new BookingException("The leader must be one of the members."));
        StudyGroup g = newGroup(name, course, project, leader, faculty);
        g.setCourseFaculty(faculty);
        g.setStatus(GroupStatus.ACTIVE);
        for (User u : members) addMemberRow(g, u, true);
        groups.save(g);
        notifications.notifyAll(members, "You were added to group " + g.getName() + " (" + g.getCourseCode() + ") by "
                + faculty.getFullName() + ". Leader: " + leader.getFullName() + ".", "/groups/" + g.getId());
        return g;
    }

    @Transactional
    public StudyGroup createByStudent(User student, String name, String course, String project,
                                      List<Long> memberIds, Long facultyId) {
        if (student.getRole() != Role.STUDENT) throw new BookingException("Only students create groups this way.");
        User faculty = users.findById(facultyId == null ? -1 : facultyId)
                .filter(u -> u.getRole() == Role.FACULTY && u.isActive())
                .orElseThrow(() -> new BookingException("Pick the course faculty who will approve the group."));
        Set<Long> ids = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        ids.add(student.getId());
        List<User> members = loadStudents(new ArrayList<>(ids));
        StudyGroup g = newGroup(name, course, project, student, student);
        g.setCourseFaculty(faculty);
        for (User u : members) addMemberRow(g, u, u.sameAs(student));
        g.setStatus(g.isAllConfirmed() ? GroupStatus.PENDING_FACULTY : GroupStatus.PENDING_CONFIRMATION);
        groups.save(g);
        List<User> others = members.stream().filter(u -> !u.sameAs(student)).toList();
        notifications.notifyAll(others, student.getFullName() + " added you to group " + g.getName() + " (" + g.getCourseCode()
                + "). Please confirm.", "/groups/" + g.getId());
        if (g.getStatus() == GroupStatus.PENDING_FACULTY) notifyFacultyToApprove(g);
        return g;
    }

    @Transactional
    public void confirm(StudyGroup g, User u) {
        if (g.getStatus() != GroupStatus.PENDING_CONFIRMATION) throw new BookingException("This group isn't waiting for confirmations.");
        GroupMember m = memberRow(g, u).orElseThrow(() -> new BookingException("You aren't in this group."));
        m.setConfirmed(true);
        if (g.isAllConfirmed()) {
            g.setStatus(GroupStatus.PENDING_FACULTY);
            notifyFacultyToApprove(g);
        }
        groups.save(g);
    }

    @Transactional
    public void decline(StudyGroup g, User u) {
        if (g.getStatus() != GroupStatus.PENDING_CONFIRMATION) throw new BookingException("This group isn't waiting for confirmations.");
        GroupMember m = memberRow(g, u).orElseThrow(() -> new BookingException("You aren't in this group."));
        g.getMembers().remove(m);
        if (g.getLeader().sameAs(u) || g.getMembers().isEmpty()) {
            g.setStatus(GroupStatus.DISSOLVED);
        } else if (g.isAllConfirmed()) {
            g.setStatus(GroupStatus.PENDING_FACULTY);
            notifyFacultyToApprove(g);
        }
        groups.save(g);
        notifications.notify(g.getLeader(), u.getFullName() + " declined to join " + g.getName() + ".", "/groups/" + g.getId());
    }

    @Transactional
    public void facultyDecision(StudyGroup g, User faculty, boolean approve) {
        if (!isCourseFaculty(g, faculty) && faculty.getRole() != Role.SUPER_ADMIN) {
            throw new BookingException("Only the course faculty can approve this group.");
        }
        if (g.getStatus() != GroupStatus.PENDING_FACULTY) throw new BookingException("This group isn't waiting for approval.");
        g.setStatus(approve ? GroupStatus.ACTIVE : GroupStatus.DISSOLVED);
        groups.save(g);
        notifications.notifyAll(g.getMembers().stream().map(GroupMember::getUser).toList(),
                "Group " + g.getName() + " was " + (approve ? "approved" : "not approved") + " by " + faculty.getFullName() + ".",
                "/groups/" + g.getId());
    }

    @Transactional
    public void addMember(StudyGroup g, User actor, Long userId) {
        requireCourseFaculty(g, actor);
        requireUnlocked(g);
        User u = loadStudents(List.of(userId)).get(0);
        if (g.hasMember(u)) throw new BookingException(u.getFullName() + " is already in the group.");
        checkOneGroupPerCourse(u, g.getCourseCode(), g.getId());
        addMemberRow(g, u, true);
        groups.save(g);
        notifications.notify(u, actor.getFullName() + " added you to group " + g.getName() + ".", "/groups/" + g.getId());
    }

    @Transactional
    public void removeMember(StudyGroup g, User actor, Long userId) {
        requireCourseFaculty(g, actor);
        requireUnlocked(g);
        GroupMember m = g.getMembers().stream().filter(x -> x.getUser().getId().equals(userId)).findFirst()
                .orElseThrow(() -> new BookingException("Not a member."));
        if (g.getLeader().sameAs(m.getUser())) throw new BookingException("Transfer leadership before removing the leader.");
        g.getMembers().remove(m);
        groups.save(g);
        notifications.notify(m.getUser(), "You were removed from group " + g.getName() + ".", "/groups");
    }

    @Transactional
    public void transferLeadership(StudyGroup g, User actor, Long newLeaderId) {
        boolean allowed = g.getLeader().sameAs(actor) || isCourseFaculty(g, actor) || actor.getRole() == Role.SUPER_ADMIN;
        if (!allowed) throw new BookingException("Only the leader or the course faculty can transfer leadership.");
        GroupMember m = g.getMembers().stream().filter(x -> x.getUser().getId().equals(newLeaderId)).findFirst()
                .orElseThrow(() -> new BookingException("The new leader must be a member."));
        g.setLeader(m.getUser());
        groups.save(g);
        notifications.notifyAll(g.getMembers().stream().map(GroupMember::getUser).toList(),
                m.getUser().getFullName() + " now leads " + g.getName() + ".", "/groups/" + g.getId());
    }

    @Transactional
    public void dissolve(StudyGroup g, User actor) {
        requireCourseFaculty(g, actor);
        if (bookings.existsByGroupAndStatusIn(g, Booking.LIVE)) {
            throw new BookingException("The group has live bookings. Cancel them (or wait for them to finish) before dissolving.");
        }
        g.setStatus(GroupStatus.DISSOLVED);
        groups.save(g);
        notifications.notifyAll(g.getMembers().stream().map(GroupMember::getUser).toList(),
                "Group " + g.getName() + " was dissolved by " + actor.getFullName() + ".", "/groups");
    }

    /**
     * A member left (account deactivated). They leave every group; if they led one, the next
     * member takes over automatically, or the course faculty is asked to pick (setting leader_succession).
     */
    @Transactional
    public void handleLeaver(User leaver) {
        for (StudyGroup g : groups.findForMember(leaver)) {
            boolean wasLeader = g.getLeader().sameAs(leaver);
            g.getMembers().removeIf(m -> m.getUser().sameAs(leaver));
            if (g.getMembers().isEmpty()) {
                g.setStatus(GroupStatus.DISSOLVED);
            } else if (wasLeader) {
                boolean auto = !"FACULTY_PICKS".equalsIgnoreCase(settings.get(SettingsService.LEADER_SUCCESSION, "NEXT_MEMBER"));
                User next = g.getMembers().get(0).getUser();
                g.setLeader(next);
                if (auto) {
                    notifications.notify(next, leaver.getFullName() + " left; you now lead " + g.getName() + ".", "/groups/" + g.getId());
                } else {
                    notifications.notify(g.getCourseFaculty(), leaver.getFullName() + " left " + g.getName()
                            + ". " + next.getFullName() + " leads for now; pick a new leader if needed.", "/groups/" + g.getId());
                }
            }
            groups.save(g);
        }
    }

    public boolean isLocked(StudyGroup g) {
        return bookings.existsByGroupAndStatusIn(g, Booking.LIVE);
    }

    public boolean isCourseFaculty(StudyGroup g, User u) {
        return u.getRole() == Role.FACULTY && ((g.getCourseFaculty() != null && g.getCourseFaculty().sameAs(u)) || g.getCreatedBy().sameAs(u));
    }

    public boolean canView(StudyGroup g, User u) {
        return g.hasMember(u) || u.getRole() == Role.FACULTY || u.getRole() == Role.SUPER_ADMIN || u.getRole() == Role.LAB_ASSISTANT;
    }

    // ------------------------------------------------------------------ helpers

    private StudyGroup newGroup(String name, String course, String project, User leader, User creator) {
        if (name == null || name.isBlank()) throw new BookingException("Give the group a name.");
        if (course == null || course.isBlank()) throw new BookingException("Give the course code.");
        StudyGroup g = new StudyGroup();
        g.setName(name.trim());
        g.setCourseCode(course.trim().toUpperCase());
        g.setProjectTitle(project == null || project.isBlank() ? null : project.trim());
        g.setLeader(leader);
        g.setCreatedBy(creator);
        g.setCreatedAt(time.now());
        g.setStatus(GroupStatus.PENDING_CONFIRMATION);
        return g;
    }

    private void addMemberRow(StudyGroup g, User u, boolean confirmed) {
        checkOneGroupPerCourse(u, g.getCourseCode(), g.getId());
        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUser(u);
        m.setConfirmed(confirmed);
        g.getMembers().add(m);
    }

    private void checkOneGroupPerCourse(User u, String course, Long exceptGroupId) {
        boolean clash = groups.findActiveForMemberAndCourse(u, course.trim().toUpperCase()).stream()
                .anyMatch(g -> !g.getId().equals(exceptGroupId));
        if (clash) throw new BookingException(u.getFullName() + " is already in a group for " + course.toUpperCase() + ". One group per student per course.");
    }

    private List<User> loadStudents(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BookingException("Add at least one member.");
        List<User> list = users.findAllById(ids);
        if (list.size() != new HashSet<>(ids).size()) throw new BookingException("Some members weren't found.");
        for (User u : list) {
            if (u.getRole() != Role.STUDENT || !u.isActive()) throw new BookingException(u.getFullName() + " is not an active student.");
        }
        return list;
    }

    private Optional<GroupMember> memberRow(StudyGroup g, User u) {
        return g.getMembers().stream().filter(m -> m.getUser().sameAs(u)).findFirst();
    }

    private void requireUnlocked(StudyGroup g) {
        if (isLocked(g)) throw new BookingException("Membership is locked while the group has an active booking.");
    }

    private void requireFacultyOrAdmin(User u) {
        if (u.getRole() != Role.FACULTY && u.getRole() != Role.SUPER_ADMIN) throw new BookingException("Only faculty can do this.");
    }

    /** Any faculty member may edit or dissolve any group (spec), as may the super admin. */
    private void requireCourseFaculty(StudyGroup g, User u) {
        if (u.getRole() == Role.SUPER_ADMIN) return;
        if (u.getRole() != Role.FACULTY) throw new BookingException("Only faculty can change groups.");
    }

    private void notifyFacultyToApprove(StudyGroup g) {
        notifications.notify(g.getCourseFaculty(), "Group " + g.getName() + " (" + g.getCourseCode() + ") is waiting for your approval.", "/groups/" + g.getId());
    }
}
