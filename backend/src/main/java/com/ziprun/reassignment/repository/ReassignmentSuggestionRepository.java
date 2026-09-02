package com.ziprun.reassignment.repository;

import com.ziprun.reassignment.domain.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.SuggestionStatus;
import com.ziprun.reassignment.domain.TriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReassignmentSuggestionRepository
    extends JpaRepository<ReassignmentSuggestion, Long> {

  // Idempotency check (AGT-4): skip creating a duplicate re-plan suggestion
  // for an order that already has a PENDING AGENT_OFFLINE suggestion.
  boolean existsByOrderIdAndTriggerReasonAndStatus(
      String orderId, TriggerReason triggerReason, SuggestionStatus status);

  // Generic guard: don't allow two PENDING suggestions for the same order,
  // which would double-reserve the recommended agent's capacity.
  boolean existsByOrderIdAndStatus(String orderId, SuggestionStatus status);
}
