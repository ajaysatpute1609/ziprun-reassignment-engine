package com.ziprun.reassignment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

  @Id
  private String id;

  @Column(nullable = false)
  private String description;

  @Column(name = "assigned_agent_id")
  private String assignedAgentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.ASSIGNED;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  // Sprint 3 extension seam: proactive SLA-breach detection will read this
  // and fire the agentic loop before a deadline is missed, not just on
  // agent OFFLINE. Currently only surfaced for the dispatch board's
  // countdown display — nothing schedules against it yet.
  @Column(name = "sla_deadline")
  private Instant slaDeadline;

  // Sprint 2: read by ZoneAffinityStrategy to prefer agents already near
  // the pickup zone. Nullable — orders without zone data still route fine
  // via load-based strategies.
  @Column(name = "pickup_zone")
  private String pickupZone;

  @Column(name = "dropoff_zone")
  private String dropoffZone;

  // Sprint 2: enforced by AgentEligibilityFilter — HEAVY orders only route
  // to agents with canHandleHeavy = true. Defaults to LIGHT so existing
  // orders/tests are unaffected.
  @Enumerated(EnumType.STRING)
  @Column(name = "weight_class", nullable = false)
  private WeightClass weightClass = WeightClass.LIGHT;
}
