// Reads an Excel (.xlsx) bank statement into the same string[][] shape parseCsv
// produces, so the import flow does not care which format the bank exported.

function cellToString(value) {
  if (value === null || value === undefined) return ''
  if (value instanceof Date) {
    // Excel dates carry no time zone; the reader hands them over as UTC midnight
    const day = String(value.getUTCDate()).padStart(2, '0')
    const month = String(value.getUTCMonth() + 1).padStart(2, '0')
    return `${day}/${month}/${value.getUTCFullYear()}`
  }
  return String(value)
}

/**
 * @param {unknown[][]} rows
 * @returns {string[][]}
 */
export function cellsToStrings(rows) {
  return rows.map((cells) => cells.map(cellToString))
}

/**
 * @param {File} file
 * @returns {Promise<string[][]>} rows of the first sheet
 */
export async function readXlsx(file) {
  // Loaded on demand: only users who import an Excel file pay for the parser.
  // Known limit: sheets whose XML exceeds ~320 KB (well over a thousand rows)
  // are unzipped in a worker created from a blob: URL, which the production CSP
  // (script-src 'self') blocks. Statements that large fail to read until the CSP
  // gains `worker-src 'self' blob:`; ordinary monthly statements stay far below.
  const { readSheet } = await import('read-excel-file/browser')
  // Numeric cells stay as the text stored in the file, so parseAmount reads
  // them exactly as it would read a CSV cell.
  const rows = await readSheet(file, { parseNumber: (s) => s })
  return cellsToStrings(rows)
}
