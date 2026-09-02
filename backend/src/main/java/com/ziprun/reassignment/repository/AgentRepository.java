package com.ziprun.reassignment.repository;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.AgentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {
  List<Agent> findByStatus(AgentStatus status);

  // Eligible for reassignment: any agent not OFFLINE. Capacity and weight
  // constraints are enforced separately by AgentEligibilityFilter so a BUSY
  // agent with spare capacity is not incorrectly excluded.
  List<Agent> findByStatusNot(AgentStatus status);
}
