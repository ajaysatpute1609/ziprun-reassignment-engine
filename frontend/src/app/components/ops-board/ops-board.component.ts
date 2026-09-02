import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subscription, interval, startWith, switchMap } from 'rxjs';
import { Agent, AgentStatus, Order, ReassignmentSuggestion } from '../../models/models';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-ops-board',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ops-board.component.html',
  styleUrls: ['./ops-board.component.css'],
})
export class OpsBoardComponent implements OnInit, OnDestroy {
  agents: Agent[] = [];
  suggestions: ReassignmentSuggestion[] = [];
  orders: Order[] = [];

  loading = true;
  error = '';
  actionInFlight: number | null = null;

  // SSE streaming bonus state
  streamingOrderId: string | null = null;
  streamingText = '';
  streamingSuggestion: ReassignmentSuggestion | null = null;
  streamingNote = '';

  private pollSub?: Subscription;
  private readonly apiBaseUrl = 'http://localhost:8080';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  startPolling(): void {
    this.pollSub = interval(5000)
      .pipe(
        startWith(0),
        switchMap(() => this.loadAll())
      )
      .subscribe();
  }

  refresh(): void {
    this.loadAll().subscribe();
  }

  private loadAll() {
    this.loading = true;
    this.error = '';
    return new Observable<void>((subscriber) => {
      Promise.all([
        this.api.getAgents().toPromise(),
        this.api.getSuggestions().toPromise(),
        this.api.getOrders().toPromise(),
      ])
        .then(([agents, suggestions, orders]) => {
          this.agents = agents ?? [];
          this.suggestions = (suggestions ?? [])
            .filter((s) => s.status === 'PENDING')
            .sort((a, b) => b.id - a.id);
          this.orders = orders ?? [];
          this.loading = false;
          subscriber.next();
          subscriber.complete();
        })
        .catch((err) => {
          this.error = 'Failed to load data — is the backend running on :8080?';
          this.loading = false;
          subscriber.error(err);
        });
    });
  }

  badgeClass(s: ReassignmentSuggestion): string {
    if (s.triggerReason === 'AGENT_OFFLINE') return 'badge-auto';
    if (s.triggerReason === 'SLA_AT_RISK') return 'badge-sla';
    return 'badge-manual';
  }

  badgeLabel(s: ReassignmentSuggestion): string {
    if (s.triggerReason === 'AGENT_OFFLINE') return 'Auto re-plan';
    if (s.triggerReason === 'SLA_AT_RISK') return 'SLA risk';
    return 'Manual';
  }

  orderFor(orderId: string): Order | undefined {
    return this.orders.find((o) => o.id === orderId);
  }

  agentName(agentId: string): string {
    return this.agents.find((a) => a.id === agentId)?.name ?? agentId;
  }

  setAgentOffline(agent: Agent): void {
    this.api.updateAgentStatus(agent.id, 'OFFLINE').subscribe(() => this.refresh());
  }

  setAgentStatus(agent: Agent, status: AgentStatus): void {
    this.api.updateAgentStatus(agent.id, status).subscribe(() => this.refresh());
  }

  accept(suggestion: ReassignmentSuggestion): void {
    this.actionInFlight = suggestion.id;
    this.api.resolveSuggestion(suggestion.id, 'ACCEPTED').subscribe({
      next: () => {
        this.actionInFlight = null;
        this.refresh();
      },
      error: () => (this.actionInFlight = null),
    });
  }

  reject(suggestion: ReassignmentSuggestion): void {
    this.actionInFlight = suggestion.id;
    this.api.resolveSuggestion(suggestion.id, 'REJECTED').subscribe({
      next: () => {
        this.actionInFlight = null;
        this.refresh();
      },
      error: () => (this.actionInFlight = null),
    });
  }

  assignedOrders(): Order[] {
    return this.orders.filter((o) => o.status === 'ASSIGNED');
  }

  /**
   * SSE streaming bonus (T-3, +5 pts): opens a streaming connection to
   * /orders/{id}/suggest/stream and renders the AI's reasoning token by
   * token as it arrives, before the final persisted suggestion lands.
   * Uses fetch + a manual ReadableStream reader (rather than the native
   * EventSource API) specifically so the request can be a POST, matching
   * the endpoint verb used everywhere else in this app.
   */
  async streamSuggestion(orderId: string): Promise<void> {
    this.streamingOrderId = orderId;
    this.streamingText = '';
    this.streamingSuggestion = null;
    this.streamingNote = '';

    try {
      const response = await fetch(`${this.apiBaseUrl}/orders/${orderId}/suggest/stream`, {
        method: 'POST',
      });

      if (!response.body) {
        this.streamingNote = 'Streaming not supported by this browser/response.';
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let boundary: number;
        while ((boundary = buffer.indexOf('\n\n')) !== -1) {
          const rawEvent = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          this.handleSseEvent(rawEvent);
        }
      }
    } catch {
      this.streamingNote = 'Streaming failed — is the backend reachable on :8080?';
    }
  }

  closeStreamingPanel(): void {
    this.streamingOrderId = null;
  }

  private handleSseEvent(rawEvent: string): void {
    const lines = rawEvent.split('\n');
    let eventName = 'message';
    const dataLines: string[] = [];

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice('event:'.length).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice('data:'.length).trim());
      }
    }
    const data = dataLines.join('\n');

    switch (eventName) {
      case 'token':
        this.streamingText += data;
        break;
      case 'suggestion':
        try {
          this.streamingSuggestion = JSON.parse(data);
        } catch {
          // ignore malformed final payload; token text is still shown
        }
        this.refresh();
        break;
      case 'fallback':
      case 'error':
        this.streamingNote = data;
        break;
    }
  }
}
