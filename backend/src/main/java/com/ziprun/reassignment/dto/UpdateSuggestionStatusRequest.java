package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.SuggestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSuggestionStatusRequest {
  @NotNull private SuggestionStatus status;
}
