import { describe, it, expect } from 'vitest'
import { formatQuantity, formatPrice, formatPercent } from './format'

// Intl uses a non-breaking space before € and %; normalise it for readable expectations
const plain = (text) => text.replace(/\u00a0/g, ' ')

describe('formatQuantity', () => {
  it.each([
    [0.00531942, '0,005319'],
    [1837.40215893, '1837,4'],
    [84.096, '84,1'],
    [6.30020417, '6,3'],
    [70, '70'],
    [0, '0'],
    [123456.7, '123.456,7'],
  ])('%s -> %s', (input, expected) => {
    expect(formatQuantity(input)).toBe(expected)
  })
})

describe('formatPrice', () => {
  it('uses 2 decimals from 1 €', () => {
    expect(plain(formatPrice(74034.64))).toBe('74.034,64 €')
  })

  it('keeps 4 significant digits below 1 €', () => {
    expect(plain(formatPrice(0.0633))).toBe('0,0633 €')
  })
})

describe('formatPercent', () => {
  it('signs gains', () => {
    expect(plain(formatPercent(12.3))).toBe('+12,3 %')
  })

  it('keeps the minus on losses', () => {
    expect(plain(formatPercent(-16.19))).toBe('-16,19 %')
  })
})
