export type AgentStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type OrderStatus = 'ASSIGNED' | 'REASSIGNMENT_PENDING' | 'REASSIGNED' | 'DELIVERED';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';
export type TriggerReason = 'INITIAL' | 'AGENT_OFFLINE';

export interface Agent {
  id: string;
  name: string;
  activeOrderCount: number;
  status: AgentStatus;
  currentZone?: string | null;
}

export interface Order {
  id: string;
  description: string;
  assignedAgentId: string | null;
  status: OrderStatus;
  createdAt: string;
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
