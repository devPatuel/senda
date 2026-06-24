// API module for /api/imports (CSV statement import pipeline).
import { http } from './http'

/**
 * @typedef {Object} ImportPreviewRow
 * @property {string} date              ISO date
 * @property {string|null} description
 * @property {number} amount            positive
 * @property {'INCOME'|'EXPENSE'} type
 * @property {number|null} suggestedCategoryId
 * @property {string|null} suggestedCategoryName
 * @property {boolean} duplicate
 */

/**
 * Previews parsed rows: derives type, suggests categories from rules and flags
 * duplicates. Each input row is { date: ISO, description, amount: signed }.
 * @param {{date: string, description: string|null, amount: number}[]} rows
 * @returns {Promise<ImportPreviewRow[]>}
 */
export function previewImport(rows) {
  return http.post('/imports/preview', { rows })
}

/**
 * Imports the confirmed rows, skipping duplicates.
 * @param {{date: string, description: string|null, amount: number, type: 'INCOME'|'EXPENSE', categoryId: number}[]} rows
 * @returns {Promise<{imported: number, skipped: number}>}
 */
export function commitImport(rows) {
  return http.post('/imports/commit', { rows })
}
