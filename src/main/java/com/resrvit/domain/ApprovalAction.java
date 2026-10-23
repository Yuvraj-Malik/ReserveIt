package com.resrvit.domain;

public enum ApprovalAction {
    REQUESTED("Requested"),
    CLAIMED("Claimed"),
    RELEASED("Released"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    FALLBACK_APPROVED("Approved by lab assistant (fallback)"),
    EMERGENCY_APPROVED("Emergency approval"),
    DIRECT_BOOKING("Direct booking"),
    LIMIT_OVERRIDE("Weekly limit overridden"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    MOVED("Moved"),
    FLAGGED_CRITICAL("Flagged critical"),
    PROTECTED("Marked protected evaluation"),
    OFFERED("Alternatives offered"),
    OFFER_ACCEPTED("Alternative accepted"),
    REASSIGNED("Reassigned"),
    RENEWAL_REQUESTED("Renewal requested"),
    CHECKED_OUT("Checked out"),
    RETURNED("Returned"),
    ESCALATED("Escalated");

    private final String label;

    ApprovalAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
