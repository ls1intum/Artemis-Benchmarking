import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import PasswordStrengthBarComponent from './password-strength-bar.component';

describe('PasswordStrengthBarComponent', () => {
  let comp: PasswordStrengthBarComponent;
  let fixture: ComponentFixture<PasswordStrengthBarComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [PasswordStrengthBarComponent],
    })
      .overrideTemplate(PasswordStrengthBarComponent, '')
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PasswordStrengthBarComponent);
    comp = fixture.componentInstance;
  });

  describe('PasswordStrengthBarComponents', () => {
    it('should initialize with default values', () => {
      expect(comp.measurePasswordStrength('')).toBe(0);
      expect(comp.passwordStrengthColors).toEqual(['#F00', '#F90', '#FF0', '#9F0', '#0F0']);
      expect(comp.getStrengthColorIndex(0).index).toBe(1);
      expect(comp.getStrengthColorIndex(0).color).toBe(comp.passwordStrengthColors[0]);
    });

    it('should increase strength upon password value change', () => {
      expect(comp.measurePasswordStrength('')).toBe(0);
      expect(comp.measurePasswordStrength('Test1234')).toBe(40);
      expect(comp.measurePasswordStrength('aa')).toBeGreaterThanOrEqual(comp.measurePasswordStrength(''));
      expect(comp.measurePasswordStrength('aa^6')).toBeGreaterThanOrEqual(comp.measurePasswordStrength('aa'));
      expect(comp.measurePasswordStrength('Aa090(**)')).toBeGreaterThanOrEqual(comp.measurePasswordStrength('aa^6'));
      expect(comp.measurePasswordStrength('Aa090(**)+-07365')).toBeGreaterThanOrEqual(comp.measurePasswordStrength('Aa090(**)'));
    });

    it('should change the color based on strength', () => {
      expect(comp.getStrengthColorIndex(0).color).toBe(comp.passwordStrengthColors[0]);
      expect(comp.getStrengthColorIndex(11).color).toBe(comp.passwordStrengthColors[1]);
      expect(comp.getStrengthColorIndex(22).color).toBe(comp.passwordStrengthColors[2]);
      expect(comp.getStrengthColorIndex(33).color).toBe(comp.passwordStrengthColors[3]);
      expect(comp.getStrengthColorIndex(44).color).toBe(comp.passwordStrengthColors[4]);
    });
  });
});
