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
}
