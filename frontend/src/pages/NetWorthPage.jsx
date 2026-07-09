import { useEffect, useState } from 'react'
import { getNetWorth } from '../api/networth'
import { formatCurrency } from '../lib/format'
import { ErrorState, LoadingState } from '../components/ui'

// Mini-card used for each breakdown block
function Block({ label, value, tone = 'neutral' }) {
  const valueClasses = {
    positive: 'text-emerald-600',
    negative: 'text-red-600',
    neutral: 'text-slate-900',
  }[tone]

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={['mt-1 text-xl font-semibold', valueClasses].join(' ')}>
        {formatCurrency(value)}
      </p>
    </section>
  )
}

// Sub-breakdown shown inside the investments block
function InvestmentDetail({ holdings, nfts }) {
  return (
    <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
      <div className="flex items-center justify-between text-sm">
        <span className="text-slate-500">Holdings</span>
        <span className="font-medium text-slate-700">{formatCurrency(holdings)}</span>
      </div>
      <div className="flex items-center justify-between text-sm">
        <span className="text-slate-500">NFTs</span>
        <span className="font-medium text-slate-700">{formatCurrency(nfts)}</span>
      </div>
    </div>
  )
}

export default function NetWorthPage() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const loading = loadedKey !== reloadKey

  useEffect(() => {
    let cancelled = false
    const key = reloadKey
    getNetWorth()
      .then((result) => {
        if (!cancelled) {
          setData(result)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se ha podido cargar el patrimonio')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const net = data ? Number(data.net) : 0
  const isNegative = net < 0

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Patrimonio</h1>

      {loading && <LoadingState label="Calculando el patrimonio…" />}

      {!loading && error && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!loading && !error && data && (
        <>
          {/* Hero card: net worth */}
          <section className="rounded-2xl border border-slate-200 bg-white p-6 text-center">
            <p className="text-sm text-slate-500">Patrimonio neto</p>
            <p
              className={[
                'mt-1 text-5xl font-bold tracking-tight',
                isNegative ? 'text-red-600' : 'text-emerald-600',
              ].join(' ')}
            >
              {formatCurrency(data.net)}
            </p>
          </section>

          {/* Liquid + investments row */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Block label="Líquido (cuentas)" value={data.liquid} tone="neutral" />

            {/* Investments with holdings/NFTs sub-breakdown */}
            <section className="rounded-2xl border border-slate-200 bg-white p-4">
              <p className="text-sm text-slate-500">Inversiones</p>
              <p className="mt-1 text-xl font-semibold text-slate-900">
                {formatCurrency(data.investments)}
              </p>
              <InvestmentDetail
                holdings={data.investmentsHoldings}
                nfts={data.investmentsNfts}
              />
            </section>

            {/* Couple share: only shown when the user has a couple space */}
            {Number(data.coupleShare) > 0 && (
              <Block label="Pareja (50%)" value={data.coupleShare} tone="neutral" />
            )}
          </div>

          {/* Debts row */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Block label="Deudas a favor" value={data.debtsInFavor} tone="positive" />
            <Block label="Deudas en contra" value={data.debtsAgainst} tone="negative" />
          </div>

          {/* Breakdown legend */}
          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="mb-4 text-sm font-semibold text-slate-700">Desglose</h2>
            <ul className="space-y-3">
              <li className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-slate-600">
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" aria-hidden="true" />
                  Líquido
                </span>
                <span className="font-semibold text-slate-900">{formatCurrency(data.liquid)}</span>
              </li>
              <li className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-slate-600">
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-600" aria-hidden="true" />
                  Inversiones
                </span>
                <span className="font-semibold text-slate-900">
                  {formatCurrency(data.investments)}
                </span>
              </li>
              <li className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-slate-600">
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-300" aria-hidden="true" />
                  Deudas a favor
                </span>
                <span className="font-semibold text-emerald-600">
                  {formatCurrency(data.debtsInFavor)}
                </span>
              </li>
              <li className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-slate-600">
                  <span className="h-2.5 w-2.5 rounded-full bg-red-400" aria-hidden="true" />
                  Deudas en contra
                </span>
                <span className="font-semibold text-red-600">
                  {formatCurrency(data.debtsAgainst)}
                </span>
              </li>
              {Number(data.coupleShare) > 0 && (
                <li className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2 text-slate-600">
                    <span className="h-2.5 w-2.5 rounded-full bg-sky-400" aria-hidden="true" />
                    Pareja (50%)
                  </span>
                  <span className="font-semibold text-slate-900">
                    {formatCurrency(data.coupleShare)}
                  </span>
                </li>
              )}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}
