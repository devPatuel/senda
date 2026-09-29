import { useEffect, useState } from 'react'
import { listLots } from '../../api/investments'
import { formatCurrency, formatDate, formatPrice, formatQuantity } from '../../lib/format'

const KIND_LABELS = { BUY: 'Compra', REWARD: 'Recompensa' }

/** Lots of one holding, newest first. Loaded when mounted (i.e. when expanded). */
export default function LotHistory({ holdingId }) {
  const [lots, setLots] = useState(null)
  const [error, setError] = useState(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    listLots(holdingId)
      .then((result) => {
        if (!cancelled) {
          setLots(result)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se ha podido cargar el historial')
      })
    return () => {
      cancelled = true
    }
  }, [holdingId, attempt])

  if (error) {
    return (
      <div role="alert" className="flex items-center justify-between gap-3 px-4 py-3 text-sm text-red-600">
        <span>{error}</span>
        <button
          type="button"
          onClick={() => {
            setError(null)
            setAttempt((n) => n + 1)
          }}
          className="font-medium underline"
        >
          Reintentar
        </button>
      </div>
    )
  }

  if (!lots) {
    return <p className="px-4 py-3 text-sm text-slate-400">Cargando historial…</p>
  }

  if (lots.length === 0) {
    return <p className="px-4 py-3 text-sm text-slate-400">Sin compras registradas</p>
  }

  return (
    <div className="overflow-x-auto px-4 pb-3">
      <table className="w-full text-xs tabular-nums">
        <thead>
          <tr className="text-left text-slate-400">
            <th className="py-1.5 pr-3 font-medium">Fecha</th>
            <th className="py-1.5 pr-3 font-medium">Tipo</th>
            <th className="py-1.5 pr-3 text-right font-medium">Cantidad</th>
            <th className="py-1.5 pr-3 text-right font-medium">Precio</th>
            <th className="py-1.5 text-right font-medium">Total</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {lots.map((lot) => {
            const reward = lot.kind === 'REWARD'
            return (
              <tr key={lot.id} className={reward ? 'text-slate-400' : 'text-slate-700'}>
                <td className="py-1.5 pr-3">{formatDate(lot.date)}</td>
                <td className="py-1.5 pr-3">{KIND_LABELS[lot.kind] ?? lot.kind}</td>
                <td className="py-1.5 pr-3 text-right" title={String(lot.quantity)}>
                  {formatQuantity(lot.quantity)}
                </td>
                <td className="py-1.5 pr-3 text-right">{formatPrice(lot.unitPrice)}</td>
                <td className="py-1.5 text-right">{formatCurrency(Number(lot.quantity) * Number(lot.unitPrice))}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
