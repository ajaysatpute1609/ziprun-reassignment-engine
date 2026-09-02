package com.ziprun.reassignment.routing;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the active {@link RoutingStrategy} by name at call time. Spring
 * auto-populates the injected map with every {@code RoutingStrategy} bean,
 * keyed by bean name — adding a new strategy (e.g. sprint 2's
 * ZoneAffinityStrategy) means implementing the interface and annotating the
 * bean with a name; no changes here or at either call site.
 *
 * The active strategy name is read from {@code routing.strategy}
 * (environment-variable overridable) on every call rather than cached at
 * startup, so it is switchable without a restart. A startup check fails fast
 * if the configured name doesn't exist in the map.
 */
@Component
public class RoutingStrategyResolver {

  private final Map<String, RoutingStrategy> strategies;

  @Value("${routing.strategy:rule}")
  private String activeStrategyName;

  public RoutingStrategyResolver(Map<String, RoutingStrategy> strategies) {
    this.strategies = strategies;
  }

  @PostConstruct
  void validateConfiguredStrategyExists() {
    if (!strategies.containsKey(activeStrategyName)) {
      throw new IllegalStateException(
          "routing.strategy='"
              + activeStrategyName
              + "' does not match any registered RoutingStrategy bean. Known: "
              + strategies.keySet());
    }
  }

  public RoutingStrategy active() {
    RoutingStrategy strategy = strategies.get(activeStrategyName);
    if (strategy == null) {
      throw new IllegalStateException("No RoutingStrategy registered for: " + activeStrategyName);
    }
    return strategy;
  }

  public RoutingStrategy fallback() {
    return strategies.get("rule");
  }
}
