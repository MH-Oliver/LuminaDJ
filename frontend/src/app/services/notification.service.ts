import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  constructor(private snackBar: MatSnackBar) {}

  showError(message: string): void {
    // Leerer String '' blendet den Button aus
    this.snackBar.open(message, '', {
      duration: 3000,
      panelClass: ['custom-snackbar', 'error-snackbar'],
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
    });
  }

  showSuccess(message: string): void {
    this.snackBar.open(message, '', {
      duration: 3000,
      panelClass: ['custom-snackbar', 'success-snackbar'],
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
    });
  }
}
