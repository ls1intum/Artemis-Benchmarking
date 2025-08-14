import { Component, ElementRef, Renderer2, inject, input, computed, OnChanges } from '@angular/core';

import SharedModule from 'app/shared/shared.module';

type StrengthColorIndex = {
  index: number;
  color: PasswordStrengthColor;
};

/**
 * Colors representing password strength levels
 */
enum PasswordStrengthColor {
  // noinspection JSUnusedGlobalSymbols
  VeryWeak = '#F00', // Red
  Weak = '#F90', // Orange
  Fair = '#FF0', // Yellow
  Strong = '#9F0', // Lime Green
  VeryStrong = '#0F0', // Green
}

@Component({
  selector: 'jhi-password-strength-bar',
  imports: [SharedModule],
  templateUrl: './password-strength-bar.component.html',
  styleUrl: './password-strength-bar.component.scss',
})
export default class PasswordStrengthBarComponent implements OnChanges {
  readonly passwordStrengthColors: PasswordStrengthColor[] = Object.values(PasswordStrengthColor) as PasswordStrengthColor[];

  readonly passwordToCheck = input<string | undefined>();
  readonly strength = computed(() => this.measurePasswordStrength(this.passwordToCheck() ?? ''));

  private readonly renderer = inject(Renderer2);
  private readonly elementRef = inject(ElementRef);

  ngOnChanges(): void {
    this.updateStrengthBar();
  }

  /**
   * Measures the strength of a given password.
   * The password strength is determined based on length and variety of characters.
   *
   * Strength calculation:
   * - Length contributes to base score (2 points per character, +1 if length ≥ 10).
   * - Character variety adds to the score (10 points for each type: lowercase, uppercase, numbers, symbols).
   * - Penalties are applied for short passwords and poor character variety:
   *   - Length ≤ 6: Maximum score capped at 10.
   *   - Single character type: Maximum score capped at 10.
   *   - Two character types: Maximum score capped at 20.
   *   - Three character types: Maximum score capped at 40.
   *
   * @param password - The password to evaluate.
   * @returns The calculated strength score (0-100).
   */
  measurePasswordStrength(password: string): number {
    if (!password) return 0; // Handle empty passwords

    let strength = 0;

    // Regular expressions for character type detection
    const regexSymbols = /[$-/:-?{-~!"^_`[\]]/g;
    const hasLowercase = /[a-z]/.test(password);
    const hasUppercase = /[A-Z]/.test(password);
    const hasNumbers = /\d/.test(password);
    const hasSymbols = regexSymbols.test(password);

    // Count matched character types
    const flags = [hasLowercase, hasUppercase, hasNumbers, hasSymbols];
    const matchedTypes = flags.filter(Boolean).length;

    // Base strength calculation
    strength += 2 * password.length + (password.length >= 10 ? 1 : 0);
    strength += matchedTypes * 10;

    // Apply penalties
    if (password.length <= 6) {
      strength = Math.min(strength, 10); // Very short password
    } else if (matchedTypes === 1) {
      strength = Math.min(strength, 10); // Poor variety
    } else if (matchedTypes === 2) {
      strength = Math.min(strength, 20); // Low variety
    } else if (matchedTypes === 3) {
      strength = Math.min(strength, 40); // Moderate variety
    }

    return strength;
  }

  /**
   * Determines the color based on the password strength.
   * The strength index maps to a specific color in the `strengthColors` array.
   *
   * @param strength - The numeric strength of the password.
   * @returns An object containing the 1-based index and the corresponding color.
   */
  getStrengthColorIndex(strength: number): StrengthColorIndex {
    const index = Math.min(Math.max(0, Math.floor((strength - 1) / 10)), 4); // Determine index (0-4) based on strength
    return { index: index + 1, color: this.passwordStrengthColors[index] }; // Return 1-based index and color
  }

  /**
   * Updates the strength bar to visually represent the password strength.
   * Each segment of the strength bar is styled (red, yellow, green) based on the computed password strength.
   */
  private updateStrengthBar(): void {
    const password = this.passwordToCheck();
    if (!password) {
      return;
    }

    const strength = this.strength();
    const strengthColorIndex = this.getStrengthColorIndex(strength);
    const element: HTMLElement = this.elementRef.nativeElement;
    const listItems: HTMLCollectionOf<HTMLElement> = element.getElementsByTagName('li');

    // Iterate through the strength bar elements and update their background color
    Array.from(listItems).forEach((listItem, index: number) => {
      const backgroundColor = index < strengthColorIndex.index ? strengthColorIndex.color : '#DDD';
      this.renderer.setStyle(listItem, 'backgroundColor', backgroundColor);
    });
  }
}
