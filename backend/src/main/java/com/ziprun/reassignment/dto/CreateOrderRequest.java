package com.ziprun.reassignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderRequest {
  @NotBlank private String description;
  @NotBlank private String assignedAgentId;
}
