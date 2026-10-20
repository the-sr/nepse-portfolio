import { describe, expect, it } from 'vitest';
import {
  actionLabel,
  formatNpr,
  formatNprCompact,
  formatPercent,
  formatSignedNpr,
  pnlClass,
} from './format';

describe('format helpers', () => {
  it('formats NPR with lakh grouping and two decimals', () => {
    expect(formatNpr(1234567.5)).toBe('रु 12,34,567.50');
  });

  it('formats signed amounts', () => {
    expect(formatSignedNpr(250)).toBe('+रु 250.00');
    expect(formatSignedNpr(-250)).toBe('-रु 250.00');
    expect(formatSignedNpr(0)).toBe('रु 0.00');
  });

  it('compacts large values using lakh and crore', () => {
    expect(formatNprCompact(125000)).toBe('रु 1.25 L');
    expect(formatNprCompact(23400000)).toBe('रु 2.34 Cr');
    expect(formatNprCompact(900)).toBe('रु 900');
  });

  it('formats percentages with a plus sign for gains', () => {
    expect(formatPercent(1.234)).toBe('+1.23%');
    expect(formatPercent(-2.5)).toBe('-2.50%');
    expect(formatPercent(0)).toBe('0.00%');
  });

  it('maps gains and losses to classes', () => {
    expect(pnlClass(10)).toBe('text-gain');
    expect(pnlClass(-10)).toBe('text-loss');
    expect(pnlClass(0)).toContain('text-slate-500');
  });

  it('humanises action codes', () => {
    expect(actionLabel('STRONG_BUY')).toBe('Strong buy');
    expect(actionLabel('HOLD')).toBe('Hold');
  });
});
