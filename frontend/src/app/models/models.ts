export type AgentStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type OrderStatus = 'ASSIGNED' | 'REASSIGNMENT_PENDING' | 'REASSIGNED' | 'DELIVERED';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';
export type TriggerReason = 'INITIAL' | 'AGENT_OFFLINE' | 'SLA_AT_RISK';

export interface Agent {
  id: string;
  name: string;
  activeOrderCount: number;
  status: AgentStatus;
  currentZone?: string | null;
  maxCapacity?: number | null;
}

export interface Order {
  id: string;
  description: string;
  assignedAgentId: string | null;
  status: OrderStatus;
  createdAt: string;
  slaDeadline?: string | null;
}

export interface ReassignmentSuggestion {
  id: number;
  orderId: string;
  recommendedAgentId: string;
  confidence: number;
  reasoning: string;
  status: SuggestionStatus;
  triggerReason: TriggerReason;
  createdAt: string;
}
