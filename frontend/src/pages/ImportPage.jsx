import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listCategories } from '../api/categories'
import { previewImport, commitImport } from '../api/imports'
import { listSpaces } from '../api/spaces'
import { parseCsv, parseDate, parseAmount } from '../lib/csv'
import { formatCurrency, formatDate } from '../lib/format'
import { FormError, SubmitButton } from '../components/form'
import { ErrorState, Notice, SelectField } from '../components/ui'

// Best-effort guess of which column holds each field, by header name.
function guessColumns(header) {
  const find = (re) => {
    const i = header.findIndex((h) => re.test(h))
    return i === -1 ? null : i
  }
  return {
    date: find(/fecha|date/i) ?? 0,
    description: find(/concepto|descrip|detalle|description|movimiento/i) ?? 1,
    amount: find(/importe|amount|cantidad|monto|cargo/i) ?? 2,
  }
}

export default function ImportPage() {
  const [categories, setCategories] = useState([])
  const [spaces, setSpaces] = useState([])
  // '' = personal ledger; otherwise the id of the space the statement belongs to
  const [spaceId, setSpaceId] = useState('')
  const [rows, setRows] = useState(null) // string[][]
  const [fileName, setFileName] = useState('')
  const [hasHeader, setHasHeader] = useState(true)
  const [mapping, setMapping] = useState({ date: 0, description: 1, amount: 2 })
  const [preview, setPreview] = useState(null) // [{...row, categoryId, include}]
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)

  // Categories belong to a scope: reload them whenever the destination changes,
  // since a row can only be filed under a category of the ledger it goes into.
  useEffect(() => {
    let cancelled = false
    listCategories(spaceId ? { spaceId: Number(spaceId) } : {})
      .then((cats) => {
        if (!cancelled) setCategories(cats)
      })
      .catch(() => {
        // The per-row category selectors will simply be empty
      })
    return () => {
      cancelled = true
    }
  }, [spaceId])

  useEffect(() => {
    let cancelled = false
    listSpaces()
      .then((all) => {
        if (!cancelled) setSpaces(all.filter((s) => s.myStatus === 'ACTIVE'))
      })
      .catch(() => {
        // Without spaces the import simply stays personal
      })
    return () => {
      cancelled = true
    }
  }, [])

  const targetSpaceId = spaceId ? Number(spaceId) : null

  // Switching destination invalidates a preview built against the other ledger.
  function handleDestinationChange(e) {
    setSpaceId(e.target.value)
    setPreview(null)
    setResult(null)
    setError(null)
  }

  const columnCount = rows && rows.length > 0 ? Math.max(...rows.map((r) => r.length)) : 0
  const columnLabels = useMemo(() => {
    if (!rows || rows.length === 0) return []
    const header = rows[0]
    return Array.from({ length: columnCount }, (_, i) =>
      hasHeader ? header[i] || `Columna ${i + 1}` : `Columna ${i + 1}`,
    )
  }, [rows, hasHeader, columnCount])

  async function handleFile(e) {
    const file = e.target.files?.[0]
    if (!file) return
    setError(null)
    setResult(null)
    setPreview(null)
    setFileName(file.name)
    const text = await file.text()
    const parsed = parseCsv(text)
    if (parsed.length === 0) {
      setError('El archivo está vacío o no se ha podido leer.')
      setRows(null)
      return
    }
    setRows(parsed)
    setMapping(guessColumns(parsed[0]))
  }

  function buildInputs() {
    const dataRows = hasHeader ? rows.slice(1) : rows
    const inputs = []
    for (const cells of dataRows) {
      const date = parseDate(cells[mapping.date] ?? '')
      const amount = parseAmount(cells[mapping.amount] ?? '')
      const description = (cells[mapping.description] ?? '').trim() || null
      // Skip rows we cannot read or that net to zero
      if (!date || Number.isNaN(amount) || amount === 0) continue
      inputs.push({ date, description, amount })
    }
    return inputs
  }

  async function handlePreview() {
    setError(null)
    const inputs = buildInputs()
    if (inputs.length === 0) {
      setError('No se han podido leer filas con la asignación de columnas elegida.')
      return
    }
    setBusy(true)
    try {
      const rowsPreview = await previewImport(inputs, targetSpaceId)
      setPreview(
        rowsPreview.map((r) => ({
          ...r,
          categoryId: r.suggestedCategoryId ? String(r.suggestedCategoryId) : '',
          // Duplicates start unchecked so they are not re-imported by default
          include: !r.duplicate,
        })),
      )
    } catch (err) {
      setError(err.message || 'No se ha podido previsualizar la importación')
    } finally {
      setBusy(false)
    }
  }

  function updateRow(index, patch) {
    setPreview((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  const importable = (preview ?? []).filter((r) => r.include && r.categoryId)
  const includedWithoutCategory = (preview ?? []).some((r) => r.include && !r.categoryId)

  async function handleCommit() {
    setError(null)
    setBusy(true)
    try {
      const payload = importable.map((r) => ({
        date: r.date,
        description: r.description,
        amount: r.amount,
        type: r.type,
        categoryId: Number(r.categoryId),
      }))
      const res = await commitImport(payload, targetSpaceId)
      setResult(res)
      setPreview(null)
      setRows(null)
      setFileName('')
    } catch (err) {
      setError(err.message || 'No se ha podido completar la importación')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Importar extracto</h1>
        <Link to="/reglas" className="text-sm font-medium text-emerald-600 hover:text-emerald-700">
          Gestionar reglas
        </Link>
      </div>

      {result && (
        <Notice onClose={() => setResult(null)}>
          Importación completada: {result.imported} movimiento(s) creados
          {result.skipped > 0 ? `, ${result.skipped} duplicado(s) omitidos` : ''}.
        </Notice>
      )}

      <FormError message={error} />

      {/* Step 1: destination + file */}
      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        {spaces.length > 0 && (
          <div className="mb-4">
            <SelectField label="Destino" name="destino" value={spaceId} onChange={handleDestinationChange}>
              <option value="">Mis cuentas</option>
              {spaces.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </SelectField>
          </div>
        )}
        <label className="block text-sm font-medium text-slate-700">Archivo CSV</label>
        <input
          type="file"
          accept=".csv,text/csv"
          aria-label="Archivo CSV"
          onChange={handleFile}
          className="mt-2 block w-full text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-emerald-600 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-emerald-700"
        />
        {fileName && <p className="mt-2 text-xs text-slate-500">{fileName}</p>}
      </section>

      {/* Step 2: mapping */}
      {rows && !preview && (
        <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-semibold text-slate-700">Asigna las columnas</h2>
          <label className="flex w-fit cursor-pointer items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={hasHeader}
              onChange={(e) => setHasHeader(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300 accent-emerald-600"
            />
            La primera fila es una cabecera
          </label>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {['date', 'description', 'amount'].map((field) => (
              <SelectField
                key={field}
                label={{ date: 'Fecha', description: 'Descripción', amount: 'Importe' }[field]}
                name={field}
                value={String(mapping[field])}
                onChange={(e) => setMapping((m) => ({ ...m, [field]: Number(e.target.value) }))}
              >
                {Array.from({ length: columnCount }, (_, i) => (
                  <option key={i} value={i}>
                    {columnLabels[i]}
                  </option>
                ))}
              </SelectField>
            ))}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <tbody>
                {(hasHeader ? rows.slice(1) : rows).slice(0, 4).map((cells, i) => (
                  <tr key={i} className="border-t border-slate-100">
                    {Array.from({ length: columnCount }, (_, c) => (
                      <td key={c} className="max-w-[10rem] truncate px-2 py-1.5 text-slate-500">
                        {cells[c]}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <button
            type="button"
            onClick={handlePreview}
            disabled={busy}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:opacity-60"
          >
            {busy ? 'Procesando…' : 'Previsualizar'}
          </button>
        </section>
      )}

      {/* Step 3: preview */}
      {preview && (
        <section className="space-y-4">
          {preview.length === 0 ? (
            <ErrorState message="No hay filas para importar." />
          ) : (
            <>
              <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
                {preview.map((r, i) => {
                  const options = categories.filter((c) => c.type === r.type)
                  return (
                    <li key={i} className="flex flex-wrap items-center gap-3 px-4 py-3">
                      <input
                        type="checkbox"
                        checked={r.include}
                        onChange={(e) => updateRow(i, { include: e.target.checked })}
                        aria-label="Incluir movimiento"
                        className="h-4 w-4 rounded border-slate-300 accent-emerald-600"
                      />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">
                          {r.description || '(sin descripción)'}
                        </p>
                        <p className="truncate text-xs text-slate-500">
                          {formatDate(r.date)}
                          {r.duplicate && (
                            <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                              Duplicado
                            </span>
                          )}
                        </p>
                      </div>
                      <select
                        value={r.categoryId}
                        onChange={(e) => updateRow(i, { categoryId: e.target.value })}
                        aria-label="Categoría"
                        className={[
                          'rounded-lg border bg-white px-2 py-1.5 text-sm text-slate-900',
                          r.include && !r.categoryId ? 'border-red-300' : 'border-slate-300',
                        ].join(' ')}
                      >
                        <option value="">Sin categoría</option>
                        {options.map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.name}
                          </option>
                        ))}
                      </select>
                      <p
                        className={[
                          'w-24 shrink-0 text-right text-sm font-semibold tabular-nums',
                          r.type === 'INCOME' ? 'text-emerald-600' : 'text-slate-900',
                        ].join(' ')}
                      >
                        {r.type === 'INCOME' ? '+' : '−'}
                        {formatCurrency(r.amount)}
                      </p>
                    </li>
                  )
                })}
              </ul>

              {includedWithoutCategory && (
                <p className="text-sm text-red-600">
                  Hay movimientos marcados sin categoría: asígnales una o desmárcalos.
                </p>
              )}

              <div className="flex items-center justify-between">
                <button
                  type="button"
                  onClick={() => setPreview(null)}
                  className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                >
                  Volver
                </button>
                <SubmitButtonLike
                  disabled={busy || importable.length === 0 || includedWithoutCategory}
                  onClick={handleCommit}
                  busy={busy}
                  count={importable.length}
                />
              </div>
            </>
          )}
        </section>
      )}
    </div>
  )
}

// A button styled like SubmitButton but for an onClick action (no form submit).
function SubmitButtonLike({ disabled, onClick, busy, count }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {busy ? 'Importando…' : `Importar ${count} movimiento(s)`}
    </button>
  )
}
