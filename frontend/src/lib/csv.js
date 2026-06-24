// Generic, tolerant CSV parsing for bank statement imports.
// - Separator: comma or semicolon (auto-detected from the first line)
// - Quoted fields with embedded separators and doubled quotes ("") are honored
// - Dates: ISO (YYYY-MM-DD) or dd/mm/yyyy
// - Amounts: dot or comma decimals, thousands separators, minus or (parentheses)

export function detectSeparator(line) {
  const semis = (line.match(/;/g) || []).length
  const commas = (line.match(/,/g) || []).length
  return semis > commas ? ';' : ','
}

function splitLine(line, sep) {
  const cells = []
  let cur = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (inQuotes) {
      if (ch === '"') {
        if (line[i + 1] === '"') {
          cur += '"'
          i++
        } else {
          inQuotes = false
        }
      } else {
        cur += ch
      }
    } else if (ch === '"') {
      inQuotes = true
    } else if (ch === sep) {
      cells.push(cur.trim())
      cur = ''
    } else {
      cur += ch
    }
  }
  cells.push(cur.trim())
  return cells
}

/**
 * Parses CSV text into an array of string-cell rows. Blank lines are skipped.
 * @param {string} text
 * @returns {string[][]}
 */
export function parseCsv(text) {
  const normalized = String(text).replace(/\r\n?/g, '\n').trim()
  if (!normalized) return []
  const lines = normalized.split('\n').filter((l) => l.trim() !== '')
  const sep = detectSeparator(lines[0])
  return lines.map((line) => splitLine(line, sep))
}

/**
 * Normalizes a date to ISO (YYYY-MM-DD), or null if unrecognized.
 * @param {string} raw
 * @returns {string|null}
 */
export function parseDate(raw) {
  const s = String(raw).trim()
  let y
  let mo
  let d
  const iso = s.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  const dmy = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/)
  if (iso) {
    ;[, y, mo, d] = iso
  } else if (dmy) {
    ;[, d, mo, y] = dmy
  } else {
    return null
  }
  const year = Number(y)
  const month = Number(mo)
  const day = Number(d)
  // Reject impossible dates (e.g. 31/02) instead of forwarding them to the API.
  const probe = new Date(year, month - 1, day)
  if (probe.getFullYear() !== year || probe.getMonth() !== month - 1 || probe.getDate() !== day) {
    return null
  }
  return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

/**
 * Parses a signed amount, tolerant of locale formatting. Returns NaN when the
 * value cannot be read.
 * @param {string} raw
 * @returns {number}
 */
export function parseAmount(raw) {
  let s = String(raw).trim()
  if (!s) return NaN
  let neg = false
  if (/^\(.*\)$/.test(s)) {
    neg = true
    s = s.slice(1, -1)
  }
  s = s.replace(/[€$\s]/g, '')
  if (s.startsWith('-')) {
    neg = true
    s = s.slice(1)
  } else if (s.startsWith('+')) {
    s = s.slice(1)
  }
  const lastComma = s.lastIndexOf(',')
  const lastDot = s.lastIndexOf('.')
  if (lastComma > -1 && lastDot > -1) {
    // The right-most of the two is the decimal separator
    if (lastComma > lastDot) s = s.replace(/\./g, '').replace(',', '.')
    else s = s.replace(/,/g, '')
  } else if (lastComma > -1) {
    s = s.replace(',', '.')
  }
  const n = Number(s)
  if (Number.isNaN(n)) return NaN
  return neg ? -n : n
}
