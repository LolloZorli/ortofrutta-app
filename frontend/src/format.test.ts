import { describe, expect, it } from 'vitest'
import { parseDecimal } from './format'

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
