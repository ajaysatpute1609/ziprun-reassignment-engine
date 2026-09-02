import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Agent, AgentStatus, Order, OrderStatus, ReassignmentSuggestion, SuggestionStatus } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  getAgents(): Observable<Agent[]> {
    return this.http.get<Agent[]>(`${this.baseUrl}/agents`);
  }

  updateAgentStatus(id: string, status: AgentStatus): Observable<Agent> {
    return this.http.patch<Agent>(`${this.baseUrl}/agents/${id}/status`, { status });
  }

  getOrders(status?: OrderStatus): Observable<Order[]> {
    const url = status ? `${this.baseUrl}/orders?status=${status}` : `${this.baseUrl}/orders`;
    return this.http.get<Order[]>(url);
  }

  requestSuggestion(orderId: string): Observable<ReassignmentSuggestion> {
    return this.http.post<ReassignmentSuggestion>(`${this.baseUrl}/orders/${orderId}/suggest`, {});
  }

  getSuggestions(): Observable<ReassignmentSuggestion[]> {
    return this.http.get<ReassignmentSuggestion[]>(`${this.baseUrl}/suggestions`);
  }

  resolveSuggestion(id: number, status: SuggestionStatus): Observable<ReassignmentSuggestion> {
    return this.http.patch<ReassignmentSuggestion>(`${this.baseUrl}/suggestions/${id}`, { status });
  }
}
