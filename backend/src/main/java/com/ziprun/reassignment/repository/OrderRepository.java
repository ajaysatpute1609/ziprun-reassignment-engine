package com.ziprun.reassignment.repository;

import com.ziprun.reassignment.domain.Order;
import com.ziprun.reassignment.domain.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
  List<Order> findByStatus(OrderStatus status);

  List<Order> findByAssignedAgentIdAndStatusIn(String agentId, List<OrderStatus> statuses);
}
