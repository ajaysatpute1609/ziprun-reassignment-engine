package com.ziprun.reassignment.controller;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.AgentStatus;
import com.ziprun.reassignment.dto.UpdateAgentStatusRequest;
import com.ziprun.reassignment.event.AgentOfflineEvent;
import com.ziprun.reassignment.repository.AgentRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agents")
public class AgentController {

  private final AgentRepository agentRepository;
  private final ApplicationEventPublisher eventPublisher;

  public AgentController(
      AgentRepository agentRepository, ApplicationEventPublisher eventPublisher) {
    this.agentRepository = agentRepository;
    this.eventPublisher = eventPublisher;
  }

  @GetMapping
  public List<Agent> listAgents() {
    return agentRepository.findAll();
  }

  /**
   * Updates agent availability. When the new status is OFFLINE, publishes an
   * {@link AgentOfflineEvent} and returns immediately — the agentic
   * re-planning loop runs asynchronously (ADR-4) and never blocks this
   * response.
   */
  @PatchMapping("/{id}/status")
  public ResponseEntity<Agent> updateStatus(
      @PathVariable String id, @Valid @RequestBody UpdateAgentStatusRequest request) {
    Agent agent =
        agentRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown agent: " + id));

    agent.setStatus(request.getStatus());
    agentRepository.save(agent);

    if (request.getStatus() == AgentStatus.OFFLINE) {
      eventPublisher.publishEvent(new AgentOfflineEvent(id));
    }

    return ResponseEntity.ok(agent);
  }
}
