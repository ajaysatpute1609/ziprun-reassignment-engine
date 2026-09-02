package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.AgentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAgentStatusRequest {
  @NotNull private AgentStatus status;
}
