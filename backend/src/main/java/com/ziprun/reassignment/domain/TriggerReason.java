package com.ziprun.reassignment.domain;

public enum TriggerReason {
  INITIAL,
  AGENT_OFFLINE,
  // Sprint 3: proactive trigger — fired by a scheduled monitor when an
  // order is approaching its SLA deadline, not in response to any agent
  // status change.
  SLA_AT_RISK
}
