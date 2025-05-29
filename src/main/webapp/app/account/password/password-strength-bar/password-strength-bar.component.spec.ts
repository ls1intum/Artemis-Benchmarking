import { ComponentFixture, TestBed } from '@angular/core/testing';

import PasswordStrengthBarComponent from './password-strength-bar.component';

describe('PasswordStrengthBarComponent', () => {
  let comp: PasswordStrengthBarComponent;
  let fixture: ComponentFixture<PasswordStrengthBarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PasswordStrengthBarComponent],
    })
      .overrideTemplate(PasswordStrengthBarComponent, '')
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PasswordStrengthBarComponent);
    comp = fixture.componentInstance;
  });

  describe('PasswordStrengthBarComponents', () => {
    it('should initialize with default values', () => {
      expect(comp.measurePasswordStrength('')).toBe(0);
      expect(comp.measurePasswordStrength('T')).toBe(10);
      expect(comp.measurePasswordStrength('Te')).toBe(10);
      expect(comp.measurePasswordStrength('test')).toBe(10);
      expect(comp.measurePasswordStrength('Test')).toBe(10);
      expect(comp.measurePasswordStrength('123456')).toBe(10);
      expect(comp.measurePasswordStrength('1234567')).toBe(10);
      expect(comp.measurePasswordStrength('1234567t')).toBe(20);
      expect(comp.measurePasswordStrength('test123')).toBe(20);
      expect(comp.measurePasswordStrength('Test123')).toBe(40);
      expect(comp.measurePasswordStrength('Test1234')).toBe(40);
      expect(comp.measurePasswordStrength('Test1234%')).toBe(58);
      expect(comp.measurePasswordStrength('Test1234%$_123')).toBe(69);
      expect(comp.passwordStrengthColors).toEqual(['#F00', '#F90', '#FF0', '#9F0', '#0F0']);
      expect(comp.getStrengthColorIndex(0).index).toBe(1);
      expect(comp.getStrengthColorIndex(0).color).toBe(comp.passwordStrengthColors[0]);
    });

    it('should increase strength upon password value change', () => {
      expect(comp.measurePasswordStrength('aa')).toBeGreaterThanOrEqual(comp.measurePasswordStrength(''));
      expect(comp.measurePasswordStrength('aa^6')).toBeGreaterThanOrEqual(comp.measurePasswordStrength('aa'));
      expect(comp.measurePasswordStrength('Aa090(**)')).toBeGreaterThanOrEqual(comp.measurePasswordStrength('aa^6'));
      expect(comp.measurePasswordStrength('Aa090(**)+-07365')).toBeGreaterThanOrEqual(comp.measurePasswordStrength('Aa090(**)'));
    });

    it('should change the color based on strength', () => {
      expect(comp.getStrengthColorIndex(0).color).toBe(comp.passwordStrengthColors[0]);
      expect(comp.getStrengthColorIndex(0).index).toBe(1);
      expect(comp.getStrengthColorIndex(10).color).toBe(comp.passwordStrengthColors[0]);
      expect(comp.getStrengthColorIndex(10).index).toBe(1);
      expect(comp.getStrengthColorIndex(11).color).toBe(comp.passwordStrengthColors[1]);
      expect(comp.getStrengthColorIndex(11).index).toBe(2);
      expect(comp.getStrengthColorIndex(22).color).toBe(comp.passwordStrengthColors[2]);
      expect(comp.getStrengthColorIndex(22).index).toBe(3);
      expect(comp.getStrengthColorIndex(33).color).toBe(comp.passwordStrengthColors[3]);
      expect(comp.getStrengthColorIndex(33).index).toBe(4);
      expect(comp.getStrengthColorIndex(44).color).toBe(comp.passwordStrengthColors[4]);
      expect(comp.getStrengthColorIndex(44).index).toBe(5);
    });
  });
});
