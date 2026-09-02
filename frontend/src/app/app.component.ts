import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { OpsBoardComponent } from './components/ops-board/ops-board.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, OpsBoardComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'ZipRun Ops';
}
