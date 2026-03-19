package com.event.tickets.domain.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * L-09 FIX: Java default is now null, not ApprovalStatus.PENDING.
     *
     * Previously the Java field initializer set PENDING while the DB column
     * defaulted to APPROVED. This created an inconsistency:
     * - New objects created in Java started as PENDING (correct for new registrations)
     * - But existing rows with null approval_status were read back as null, not PENDING
     * - DatabaseInitializer then tried to migrate nulls → APPROVED, but when the
     *   field had a Java default of PENDING, JPA would sometimes write PENDING on
     *   flush before the migration ran.
     *
     * Fix: Java default is null. RegistrationServiceImpl explicitly sets PENDING
     * on new registrations. DatabaseInitializer migrates nulls to APPROVED.
     * The DB column default of APPROVED handles any edge cases at the SQL level.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = true, columnDefinition = "VARCHAR(255) DEFAULT 'APPROVED'")
    private ApprovalStatus approvalStatus;

    /**
     * Timestamp when the user was approved by an admin.
     * Only set when status transitions to APPROVED.
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * FIX #7: Added rejectedAt field.
     * Previously rejectUser() incorrectly stamped approvedAt on rejection,
     * making approved and rejected records look identical in the database.
     * Now: approvedAt only set on APPROVED, rejectedAt only set on REJECTED.
     */
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    /**
     * Reference to the admin user who reviewed this account.
     */
    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Keycloak sync flag - set to true when DB approval state changes but
     * the corresponding Keycloak call failed.
     */
    @Column(name = "keycloak_sync_pending", nullable = false)
    private boolean keycloakSyncPending = false;

    @OneToMany(mappedBy = "organizer", cascade = CascadeType.ALL)
    private List<Event> organizedEvents = new ArrayList<>();

    @OneToMany(mappedBy = "purchaser", cascade = CascadeType.ALL)
    private List<Ticket> purchasedTickets = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "user_attending_events",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "event_id")
    )
    private List<Event> attendingEvents = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "user_staffing_events",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "event_id")
    )
    private List<Event> staffingEvents = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = true)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    /**
     * FIX #14: equals/hashCode based on ID only (standard JPA practice).
     * Previous implementation compared id, name, email, createdAt, updatedAt.
     * If updatedAt changed between when a User was loaded and when it was compared
     * (e.g. a flush elsewhere in the transaction), two references to the same user
     * would compare as unequal — causing event.getStaff().contains(user) to return
     * false and the same user to be added to staff more than once.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}