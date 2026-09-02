import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { Agent, Order } from '../../models/models';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-dispatch-board',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dispatch-board.component.html',
  styleUrls: ['./dispatch-board.component.css'],
})
export class DispatchBoardComponent implements OnInit, OnDestroy {
  agents: Agent[] = [];
  orders: Order[] = [];
  loading = true;
  error = '';

  private pollSub?: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.pollSub = interval(5000)
      .pipe(
        startWith(0),
        switchMap(() => this.load())
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  private async load() {
    try {
      const [agents, orders] = await Promise.all([
        this.api.getAgents().toPromise(),
        this.api.getOrders().toPromise(),
      ]);
      this.agents = agents ?? [];
      this.orders = orders ?? [];
      this.loading = false;
      this.error = '';
    } catch {
      this.error = 'Failed to load dispatch data';
      this.loading = false;
    }
  }

  agentName(agentId: string | null): string {
    if (!agentId) return '—';
    return this.agents.find((a) => a.id === agentId)?.name ?? agentId;
  }

  loadPercent(agent: Agent): number {
    if (!agent.maxCapacity) return 0;
    return Math.min(100, Math.round((agent.activeOrderCount / agent.maxCapacity) * 100));
  }

  loadClass(agent: Agent): string {
    const pct = this.loadPercent(agent);
    if (pct >= 90) return 'load-red';
    if (pct >= 60) return 'load-amber';
    return 'load-green';
  }

  /** Minutes remaining until SLA deadline; negative if breached. */
  minutesRemaining(order: Order): number | null {
    if (!order.slaDeadline) return null;
    const diffMs = new Date(order.slaDeadline).getTime() - Date.now();
    return Math.round(diffMs / 60000);
  }

  slaClass(order: Order): string {
    const mins = this.minutesRemaining(order);
    if (mins === null) return 'sla-unknown';
    if (mins < 0) return 'sla-red';
    if (mins <= 10) return 'sla-amber';
    return 'sla-green';
  }

  slaLabel(order: Order): string {
    const mins = this.minutesRemaining(order);
    if (mins === null) return 'No SLA';
    if (mins < 0) return `Breached ${Math.abs(mins)}m ago`;
    return `${mins}m left`;
  }

  statusClass(status: string): string {
    return 'status-' + status.toLowerCase().replace(/_/g, '-');
  }
}
