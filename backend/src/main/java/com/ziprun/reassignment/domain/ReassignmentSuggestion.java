package com.ziprun.reassignment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reassignment_suggestions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReassignmentSuggestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  @Column(name = "recommended_agent_id", nullable = false)
  private String recommendedAgentId;

  @Column(nullable = false)
  private double confidence;

  @Column(columnDefinition = "TEXT")
  private String reasoning;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SuggestionStatus status = SuggestionStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_reason", nullable = false)
  private TriggerReason triggerReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
