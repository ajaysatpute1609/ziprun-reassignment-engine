package com.ziprun.reassignment.controller;

import com.ziprun.reassignment.domain.Order;
import com.ziprun.reassignment.domain.OrderStatus;
import com.ziprun.reassignment.domain.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.WeightClass;
import com.ziprun.reassignment.dto.CreateOrderRequest;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.routing.RoutingContext;
import com.ziprun.reassignment.service.ReassignmentService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderRepository orderRepository;
  private final AgentRepository agentRepository;
  private final ReassignmentService reassignmentService;

  public OrderController(
      OrderRepository orderRepository,
      AgentRepository agentRepository,
      ReassignmentService reassignmentService) {
    this.orderRepository = orderRepository;
    this.agentRepository = agentRepository;
    this.reassignmentService = reassignmentService;
  }

  @PostMapping
  public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    if (!agentRepository.existsById(request.getAssignedAgentId())) {
      return ResponseEntity.badRequest().build();
    }

    Order order = new Order();
    order.setId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    order.setDescription(request.getDescription());
    order.setAssignedAgentId(request.getAssignedAgentId());
    order.setStatus(OrderStatus.ASSIGNED);
    order.setCreatedAt(Instant.now());
    order.setSlaDeadline(Instant.now().plus(45, ChronoUnit.MINUTES));
    order.setPickupZone(request.getPickupZone());
    order.setDropoffZone(request.getDropoffZone());
    order.setWeightClass(
        request.getWeightClass() != null ? request.getWeightClass() : WeightClass.LIGHT);

    return ResponseEntity.status(HttpStatus.CREATED).body(orderRepository.save(order));
  }

  @GetMapping
  public List<Order> listOrders(@RequestParam(required = false) OrderStatus status) {
    return status == null ? orderRepository.findAll() : orderRepository.findByStatus(status);
  }

  @PostMapping("/{id}/suggest")
  public ResponseEntity<ReassignmentSuggestion> suggest(@PathVariable String id) {
    ReassignmentSuggestion suggestion =
        reassignmentService.suggestForOrder(id, RoutingContext.initial());
    return ResponseEntity.ok(suggestion);
  }

  /**
   * Bonus (T-3, +5 pts): streams the AI's reasoning token-by-token via SSE
   * before the final suggestion lands. Returns the emitter immediately —
   * {@code ReassignmentService.streamSuggestion} runs asynchronously and
   * pushes "token", "fallback" (if applicable), and a final "suggestion"
   * event onto it.
   */
  @PostMapping(path = "/{id}/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter suggestStream(@PathVariable String id) {
    SseEmitter emitter = new SseEmitter(120_000L);
    reassignmentService.streamSuggestion(id, RoutingContext.initial(), emitter);
    return emitter;
  }
}
