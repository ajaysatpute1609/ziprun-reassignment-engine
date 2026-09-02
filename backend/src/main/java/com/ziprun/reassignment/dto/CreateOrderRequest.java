package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.WeightClass;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderRequest {
  @NotBlank private String description;
  @NotBlank private String assignedAgentId;

  // Sprint 2 — all optional so existing callers (and the seed-style usage)
  // keep working unchanged.
  private String pickupZone;
  private String dropoffZone;
  private WeightClass weightClass;
}
