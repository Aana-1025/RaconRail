import { describe, expect, it } from 'vitest';
import { exactMoney } from './exact-scalars';
describe('exact money', () => {
  it.each([['99999999999999999999999999999999999999','usd','999999999999999999999999999999999999.99 USD'],['-1','INR','-0.01 INR'],['0','JPY','0 JPY'],['1','usd','0.01 USD']])('preserves %s %s', (n,c,expected) => expect(exactMoney(n,c)).toBe(expected));
  it.each(['-0','01','1e2','1.5','+1',' 1','1\n'])('rejects noncanonical %s', n => expect(() => exactMoney(n,'USD')).toThrow());
  it('labels unsupported currency without inventing precision', () => expect(exactMoney('123','ABC')).toBe('123 ABC (minor units; unsupported currency)'));
});
