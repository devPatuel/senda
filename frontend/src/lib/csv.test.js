import { describe, it, expect } from 'vitest'
import { parseCsv, detectSeparator, parseDate, parseAmount, dropPreamble } from './csv'

describe('csv parsing', () => {
  it('detects the separator and splits rows', () => {
    const rows = parseCsv('fecha;concepto;importe\n2026-06-01;Nomina;1500,00')
    expect(rows).toHaveLength(2)
    expect(rows[1]).toEqual(['2026-06-01', 'Nomina', '1500,00'])
  })

  it('honors quoted fields with embedded separators', () => {
    const rows = parseCsv('a,b\n"Pago, Mercadona",10.00')
    expect(rows[1]).toEqual(['Pago, Mercadona', '10.00'])
  })

  it('picks comma when there are more commas than semicolons', () => {
    expect(detectSeparator('a,b,c')).toBe(',')
    expect(detectSeparator('a;b;c')).toBe(';')
  })

  it('skips blank lines and normalizes CRLF', () => {
    const rows = parseCsv('a,b\r\n\r\n1,2\r\n')
    expect(rows).toEqual([
      ['a', 'b'],
      ['1', '2'],
    ])
  })
})

describe('parseDate', () => {
  it('accepts ISO dates as-is', () => {
    expect(parseDate('2026-06-24')).toBe('2026-06-24')
  })
  it('converts dd/mm/yyyy to ISO with padding', () => {
    expect(parseDate('3/6/2026')).toBe('2026-06-03')
  })
  it('returns null for unknown formats', () => {
    expect(parseDate('junio 2026')).toBeNull()
  })
  it('rejects impossible calendar dates', () => {
    expect(parseDate('31/02/2026')).toBeNull()
    expect(parseDate('2026-13-01')).toBeNull()
  })
})

describe('parseAmount', () => {
  it('parses es-style decimals with thousands separators', () => {
    expect(parseAmount('1.234,56')).toBeCloseTo(1234.56)
  })
  it('parses en-style decimals', () => {
    expect(parseAmount('1234.56')).toBeCloseTo(1234.56)
  })
  it('treats a lone comma as the decimal separator', () => {
    expect(parseAmount('20,50')).toBeCloseTo(20.5)
  })
  it('reads negatives via minus and parentheses', () => {
    expect(parseAmount('-20,50')).toBeCloseTo(-20.5)
    expect(parseAmount('(20.50)')).toBeCloseTo(-20.5)
  })
  it('reads the typographic minus sign banks use in Excel exports', () => {
    expect(parseAmount('\u22129,03')).toBeCloseTo(-9.03)
  })
  it('strips currency symbols', () => {
    expect(parseAmount('1.500,00 €')).toBeCloseTo(1500)
  })
  it('returns NaN for non-numbers', () => {
    expect(Number.isNaN(parseAmount('abc'))).toBe(true)
  })
})

describe('dropPreamble', () => {
  it('drops the account details a bank puts above the table', () => {
    const rows = [
      ['', '', 'CUENTA ONLINE', 'FECHA'],
      ['', '', 'ES00 0000', '01/06/2026'],
      ['Movimientos'],
      ['Fecha operación', 'Fecha valor', 'Concepto', 'Importe'],
      ['01/06/2026', '01/06/2026', 'Compra', '-20,50'],
    ]
    expect(dropPreamble(rows)).toEqual(rows.slice(3))
  })
  it('leaves the rows untouched when the header is already first or missing', () => {
    const withHeader = [['fecha', 'concepto', 'importe'], ['2026-06-01', 'Compra', '-5']]
    const noHeader = [['2026-06-01', 'Compra', '-5']]
    expect(dropPreamble(withHeader)).toBe(withHeader)
    expect(dropPreamble(noHeader)).toBe(noHeader)
  })
})
