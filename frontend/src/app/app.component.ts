import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { OpsBoardComponent } from './components/ops-board/ops-board.component';
import { DispatchBoardComponent } from './components/dispatch-board/dispatch-board.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, OpsBoardComponent, DispatchBoardComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'ZipRun Ops';
  activeView: 'ops' | 'dispatch' = 'ops';

  setView(view: 'ops' | 'dispatch'): void {
    this.activeView = view;
  }
}
