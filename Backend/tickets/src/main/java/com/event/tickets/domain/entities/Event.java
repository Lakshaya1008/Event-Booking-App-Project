package com.event.tickets.domain.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "event_start")
  private LocalDateTime start;

  @Column(name = "event_end")
  private LocalDateTime end;

  @Column(name = "venue", nullable = false)
  private String venue;

  @Column(name = "sales_start")
  private LocalDateTime salesStart;

  @Column(name = "sales_end")
  private LocalDateTime salesEnd;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private EventStatusEnum status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organizer_id")
  private User organizer;

  @ManyToMany(mappedBy = "attendingEvents")
  @Builder.Default
  private List<User> attendees = new ArrayList<>();

  @ManyToMany(mappedBy = "staffingEvents")
  @Builder.Default
  private List<User> staff = new ArrayList<>();

  @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<TicketType> ticketTypes = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Event event = (Event) o;
    return Objects.equals(id, event.id) && Objects.equals(name, event.name) && Objects.equals(start,
        event.start) && Objects.equals(end, event.end) && Objects.equals(venue, event.venue)
        && Objects.equals(salesStart, event.salesStart) && Objects.equals(salesEnd, event.salesEnd)
        && status == event.status && Objects.equals(createdAt, event.createdAt) && Objects.equals(
        updatedAt, event.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, start, end, venue, salesStart, salesEnd, status, createdAt,
        updatedAt);
  }
}
