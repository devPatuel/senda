import { describe, it, expect } from 'vitest'
import { cellsToStrings } from './xlsx'

describe('cellsToStrings', () => {
  it('keeps text cells and turns empty ones into empty strings', () => {
    expect(cellsToStrings([['Concepto', null, undefined]])).toEqual([['Concepto', '', '']])
  })
  it('formats date cells as dd/mm/yyyy so parseDate can read them', () => {
    expect(cellsToStrings([[new Date(Date.UTC(2026, 8, 3))]])).toEqual([['03/09/2026']])
  })
  it('stringifies any other value', () => {
    expect(cellsToStrings([['-20.5', true]])).toEqual([['-20.5', 'true']])
  })
})
