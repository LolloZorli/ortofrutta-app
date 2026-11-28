import { describe, expect, it } from 'vitest'
import { eur, parseDecimal, roundMoney } from './format'

describe('roundMoney', () => {
  it.each([
    [1.005, 1.01],
    [2.675, 2.68],
    [-1.005, -1.01],
    [1.004, 1],
    [-0.004, 0],
    [1e-3, 0],
    [1e3 + 0.005, 1000.01],
  ])('rounds %s to %s using half-up cents', (value, expected) => {
    expect(roundMoney(value)).toBe(expected)
  })

  it('formats currency with the same cent rounding', () => {
    expect(eur(1.005)).toBe('1,01 €')
  })
})

describe('parseDecimal', () => {
  it.each([
    ['12.5', 12.5],
    ['12,5', 12.5],
    [' 0 ', 0],
    ['-3,25', -3.25],
  ])('parses %s as %s', (input, expected) => {
    expect(parseDecimal(input)).toBe(expected)
  })

  it.each(['', '   ', 'abc', 'Infinity', '-Infinity', '1e309'])('rejects %s', (input) => {
    expect(Number.isNaN(parseDecimal(input))).toBe(true)
  })
})
