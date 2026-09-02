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

  private pollSub?: Subscription;

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
}
