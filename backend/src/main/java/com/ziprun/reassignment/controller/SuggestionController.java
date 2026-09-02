package com.ziprun.reassignment.controller;

import com.ziprun.reassignment.domain.ReassignmentSuggestion;
import com.ziprun.reassignment.dto.UpdateSuggestionStatusRequest;
import com.ziprun.reassignment.repository.ReassignmentSuggestionRepository;
import com.ziprun.reassignment.service.ReassignmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/suggestions")
public class SuggestionController {

  private final ReassignmentSuggestionRepository suggestionRepository;
  private final ReassignmentService reassignmentService;

  public SuggestionController(
      ReassignmentSuggestionRepository suggestionRepository,
      ReassignmentService reassignmentService) {
    this.suggestionRepository = suggestionRepository;
    this.reassignmentService = reassignmentService;
  }

  @GetMapping
  public List<ReassignmentSuggestion> listSuggestions() {
    return suggestionRepository.findAll();
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ReassignmentSuggestion> updateStatus(
      @PathVariable Long id, @Valid @RequestBody UpdateSuggestionStatusRequest request) {
    return ResponseEntity.ok(reassignmentService.resolveSuggestion(id, request.getStatus()));
  }
}
