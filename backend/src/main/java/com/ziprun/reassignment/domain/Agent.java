package com.ziprun.reassignment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Agent {

  @Id
  private String id;

  @Column(nullable = false)
  private String name;

  @Column(name = "active_order_count", nullable = false)
  private int activeOrderCount = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentStatus status = AgentStatus.AVAILABLE;

  // Sprint 2 extension seam: zone-aware routing (ZoneAffinityStrategy) will
  // read this field. Nullable placeholder now avoids a migration later.
  @Column(name = "current_zone")
  private String currentZone;

  // Sprint 2 extension seam: capacity-aware routing will enforce this limit.
  // Currently only surfaced for the dispatch board's load visualization —
  // not yet enforced by any routing strategy.
  @Column(name = "max_capacity")
  private Integer maxCapacity;

  // Sprint 2: whether this agent's vehicle/equipment can carry HEAVY orders.
  // Enforced by AgentEligibilityFilter. Defaults true so existing agents
  // remain eligible for everything unless explicitly restricted.
  @Column(name = "can_handle_heavy", nullable = false)
  private boolean canHandleHeavy = true;
}
