import { useEffect, useState } from 'react'
import {
  listAssetClasses,
  listHoldings,
  createHolding,
  removeHolding,
  addBuy,
  setHoldingPrice,
  refreshPrices,
  listNfts,
  createNft,
  updateNft,
  removeNft,
} from '../api/investments'
import { formatCurrency, formatPercent, formatPrice, formatQuantity, todayISO } from '../lib/format'
import { allocation, summarize } from './investments/summary'
import LotHistory from './investments/LotHistory'
import { Field, FormError, SubmitButton } from '../components/form'
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, Modal, Notice, SelectField } from '../components/ui'

const SOURCE_LABELS = {
  CRYPTO: 'Cripto',
  METAL: 'Metal',
  FUND: 'Fondo',
  MANUAL: 'Manual',
}

// Only CRYPTO holdings get a live price from the refresh; the rest are manual.
function isManualSource(source) {
  return source !== 'CRYPTO'
}

function parseDecimal(raw) {
  return Number(String(raw).replace(',', '.'))
}

// --- New holding ---

function HoldingForm({ assetClasses, defaultAssetClassId, onClose, onSaved }) {
  const [form, setForm] = useState({
    assetClassId: defaultAssetClassId ? String(defaultAssetClassId) : String(assetClasses[0]?.id ?? ''),
    symbol: '',
    name: '',
    quantity: '',
    avgCost: '',
  })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function validate() {
    const errors = {}
    if (!form.assetClassId) errors.assetClassId = 'Elige una clase de activo'
    if (!form.symbol.trim()) errors.symbol = 'Introduce un símbolo'
    if (!form.name.trim()) errors.name = 'Introduce un nombre'
    const quantity = parseDecimal(form.quantity || '0')
    const avgCost = parseDecimal(form.avgCost || '0')
    if (Number.isNaN(quantity) || quantity < 0) errors.quantity = 'Cantidad no válida'
    if (Number.isNaN(avgCost) || avgCost < 0) errors.avgCost = 'Coste no válido'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSaving(true)
    try {
      await createHolding({
        assetClassId: Number(form.assetClassId),
        symbol: form.symbol.trim().toUpperCase(),
        name: form.name.trim(),
        quantity: parseDecimal(form.quantity || '0'),
        avgCost: parseDecimal(form.avgCost || '0'),
      })
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido crear la posición')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Nueva posición" onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <SelectField
          label="Clase de activo"
          name="assetClassId"
          value={form.assetClassId}
          onChange={handleChange}
          error={fieldErrors.assetClassId}
        >
          {assetClasses.map((ac) => (
            <option key={ac.id} value={ac.id}>
              {ac.name} ({SOURCE_LABELS[ac.pricingSource]})
            </option>
          ))}
        </SelectField>

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Símbolo"
            name="symbol"
            type="text"
            placeholder="BTC"
            value={form.symbol}
            onChange={handleChange}
            error={fieldErrors.symbol}
          />
          <Field
            label="Nombre"
            name="name"
            type="text"
            placeholder="Bitcoin"
            value={form.name}
            onChange={handleChange}
            error={fieldErrors.name}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Cantidad"
            name="quantity"
            type="number"
            inputMode="decimal"
            step="any"
            placeholder="0"
            value={form.quantity}
            onChange={handleChange}
            error={fieldErrors.quantity}
          />
          <Field
            label="Coste medio"
            name="avgCost"
            type="number"
            inputMode="decimal"
            step="any"
            placeholder="0"
            value={form.avgCost}
            onChange={handleChange}
            error={fieldErrors.avgCost}
          />
        </div>

        <SubmitButton loading={saving} loadingText="Guardando…">
          Crear posición
        </SubmitButton>
      </form>
    </Modal>
  )
}

// --- Add buy ---

function BuyForm({ holding, onClose, onSaved }) {
  const [form, setForm] = useState({ kind: 'BUY', quantity: '', unitPrice: '', date: todayISO() })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function validate() {
    const errors = {}
    const quantity = parseDecimal(form.quantity)
    const unitPrice = parseDecimal(form.unitPrice)
    if (!form.quantity.trim() || Number.isNaN(quantity) || quantity <= 0) {
      errors.quantity = 'Cantidad mayor que 0'
    }
    if (!form.unitPrice.trim() || Number.isNaN(unitPrice) || unitPrice < 0) {
      errors.unitPrice = 'Precio no válido'
    }
    if (!form.date) errors.date = 'Introduce una fecha'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setSaving(true)
    try {
      await addBuy(holding.id, {
        quantity: parseDecimal(form.quantity),
        unitPrice: parseDecimal(form.unitPrice),
        date: form.date,
        kind: form.kind,
      })
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido registrar la compra')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={`Añadir compra · ${holding.symbol}`} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <p className="text-sm text-slate-500">
          Se recalculará la cantidad y el coste medio de la posición. Una recompensa (staking,
          intereses) se registra a su precio de mercado del día.
        </p>

        <SelectField label="Tipo" name="kind" value={form.kind} onChange={handleChange}>
          <option value="BUY">Compra</option>
          <option value="REWARD">Recompensa</option>
        </SelectField>

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Cantidad"
            name="quantity"
            type="number"
            inputMode="decimal"
            step="any"
            placeholder="0"
            value={form.quantity}
            onChange={handleChange}
            error={fieldErrors.quantity}
          />
          <Field
            label="Precio unitario"
            name="unitPrice"
            type="number"
            inputMode="decimal"
            step="any"
            placeholder="0"
            value={form.unitPrice}
            onChange={handleChange}
            error={fieldErrors.unitPrice}
          />
        </div>

        <Field
          label="Fecha"
          name="date"
          type="date"
          value={form.date}
          onChange={handleChange}
          error={fieldErrors.date}
        />

        <SubmitButton loading={saving} loadingText="Guardando…">
          Registrar compra
        </SubmitButton>
      </form>
    </Modal>
  )
}

// --- Set manual price ---

function PriceForm({ holding, onClose, onSaved }) {
  const [value, setValue] = useState(holding.currentPrice != null ? String(holding.currentPrice) : '')
  const [fieldError, setFieldError] = useState(null)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const price = parseDecimal(value)
    if (!value.trim() || Number.isNaN(price) || price < 0) {
      setFieldError('Introduce un precio válido')
      return
    }
    setFieldError(null)
    setSaving(true)
    try {
      await setHoldingPrice(holding.id, { currentPrice: price })
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido fijar el precio')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={`Fijar precio · ${holding.symbol}`} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <Field
          label="Precio actual"
          name="currentPrice"
          type="number"
          inputMode="decimal"
          step="any"
          placeholder="0"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          error={fieldError}
        />
        <SubmitButton loading={saving} loadingText="Guardando…">
          Guardar precio
        </SubmitButton>
      </form>
    </Modal>
  )
}

// --- NFT form ---

function NftForm({ nft, onClose, onSaved }) {
  const isEdit = Boolean(nft)
  const [form, setForm] = useState(() =>
    nft
      ? {
          name: nft.name,
          collection: nft.collection ?? '',
          buyCryptoSymbol: nft.buyCryptoSymbol,
          buyCryptoAmount: String(nft.buyCryptoAmount),
          fiatValueAtPurchase: String(nft.fiatValueAtPurchase),
          ourCurrentValue: String(nft.ourCurrentValue),
          utility: nft.utility ?? '',
        }
      : {
          name: '',
          collection: '',
          buyCryptoSymbol: '',
          buyCryptoAmount: '',
          fiatValueAtPurchase: '',
          ourCurrentValue: '',
          utility: '',
        },
  )
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function validate() {
    const errors = {}
    if (!form.name.trim()) errors.name = 'Introduce un nombre'
    if (!form.buyCryptoSymbol.trim()) errors.buyCryptoSymbol = 'Introduce un símbolo'
    if (Number.isNaN(parseDecimal(form.buyCryptoAmount))) errors.buyCryptoAmount = 'Cantidad no válida'
    if (Number.isNaN(parseDecimal(form.fiatValueAtPurchase))) errors.fiatValueAtPurchase = 'Valor no válido'
    if (Number.isNaN(parseDecimal(form.ourCurrentValue))) errors.ourCurrentValue = 'Valor no válido'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = {
      name: form.name.trim(),
      collection: form.collection.trim() || undefined,
      buyCryptoSymbol: form.buyCryptoSymbol.trim().toUpperCase(),
      buyCryptoAmount: parseDecimal(form.buyCryptoAmount),
      fiatValueAtPurchase: parseDecimal(form.fiatValueAtPurchase),
      ourCurrentValue: parseDecimal(form.ourCurrentValue),
      utility: form.utility.trim() || undefined,
    }

    setSaving(true)
    try {
      if (isEdit) {
        await updateNft(nft.id, payload)
      } else {
        await createNft(payload)
      }
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido guardar el NFT')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={isEdit ? 'Editar NFT' : 'Nuevo NFT'} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Nombre"
            name="name"
            type="text"
            value={form.name}
            onChange={handleChange}
            error={fieldErrors.name}
          />
          <Field
            label="Colección"
            name="collection"
            type="text"
            value={form.collection}
            onChange={handleChange}
            error={fieldErrors.collection}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Cripto pagada"
            name="buyCryptoSymbol"
            type="text"
            placeholder="ETH"
            value={form.buyCryptoSymbol}
            onChange={handleChange}
            error={fieldErrors.buyCryptoSymbol}
          />
          <Field
            label="Cantidad cripto"
            name="buyCryptoAmount"
            type="number"
            inputMode="decimal"
            step="any"
            placeholder="0"
            value={form.buyCryptoAmount}
            onChange={handleChange}
            error={fieldErrors.buyCryptoAmount}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Valor fiat al comprar"
            name="fiatValueAtPurchase"
            type="number"
            inputMode="decimal"
            step="0.01"
            placeholder="0,00"
            value={form.fiatValueAtPurchase}
            onChange={handleChange}
            error={fieldErrors.fiatValueAtPurchase}
          />
          <Field
            label="Valor actual estimado"
            name="ourCurrentValue"
            type="number"
            inputMode="decimal"
            step="0.01"
            placeholder="0,00"
            value={form.ourCurrentValue}
            onChange={handleChange}
            error={fieldErrors.ourCurrentValue}
          />
        </div>

        <Field
          label="Utilidad"
          name="utility"
          type="text"
          placeholder="Acceso, staking…"
          value={form.utility}
          onChange={handleChange}
          error={fieldErrors.utility}
        />

        <SubmitButton loading={saving} loadingText="Guardando…">
          {isEdit ? 'Guardar cambios' : 'Crear NFT'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

// --- Summary, allocation and rows ---

const ALLOCATION_COLORS = ['bg-emerald-500', 'bg-sky-500', 'bg-amber-500', 'bg-violet-500', 'bg-rose-500', 'bg-slate-400']

function pnlClass(value) {
  return Number(value) >= 0 ? 'text-emerald-600' : 'text-red-600'
}

function signedCurrency(value) {
  return `${Number(value) > 0 ? '+' : ''}${formatCurrency(value)}`
}

function SummaryCard({ summary }) {
  const { value, invested, pnl, pnlPct, rewards, unpriced } = summary
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className="grid gap-4 sm:grid-cols-3">
        <div>
          <p className="text-sm text-slate-500">Valor actual</p>
          <p className="mt-1 text-3xl font-bold tracking-tight text-slate-900 tabular-nums">{formatCurrency(value)}</p>
        </div>
        <div>
          <p className="text-sm text-slate-500">Invertido</p>
          <p className="mt-1 text-xl font-semibold text-slate-900 tabular-nums">{formatCurrency(invested)}</p>
          {rewards > 0 && (
            <p className="text-xs text-slate-500">de ello {formatCurrency(rewards)} en recompensas</p>
          )}
        </div>
        <div>
          <p className="text-sm text-slate-500">Ganancia / pérdida</p>
          {pnlPct == null ? (
            <p className="mt-1 text-xl font-semibold text-slate-400">—</p>
          ) : (
            <p className={['mt-1 text-xl font-semibold tabular-nums', pnlClass(pnl)].join(' ')}>
              {signedCurrency(pnl)} <span className="text-base">({formatPercent(pnlPct.toFixed(2))})</span>
            </p>
          )}
        </div>
      </div>
      {unpriced > 0 && (
        <p className="mt-3 text-xs text-amber-700">
          {unpriced === 1
            ? '1 posición sin precio, no incluida en el valor'
            : `${unpriced} posiciones sin precio, no incluidas en el valor`}
        </p>
      )}
    </section>
  )
}

function AllocationBar({ items }) {
  if (items.length === 0) return null
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4" aria-label="Reparto">
      <div className="flex h-3 overflow-hidden rounded-full bg-slate-100">
        {items.map((item, i) => (
          <div
            key={item.key}
            className={ALLOCATION_COLORS[i % ALLOCATION_COLORS.length]}
            style={{ width: `${item.pct}%` }}
            title={`${item.label} ${formatPercent(item.pct.toFixed(1))}`}
          />
        ))}
      </div>
      <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
        {items.map((item, i) => (
          <li key={item.key} className="flex items-center gap-1.5">
            <span className={['h-2 w-2 rounded-full', ALLOCATION_COLORS[i % ALLOCATION_COLORS.length]].join(' ')} />
            {item.label} <span className="tabular-nums text-slate-400">{item.pct.toFixed(1).replace('.', ',')} %</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function HoldingRow({ holding, weightPct, onBuy, onPrice, onDelete }) {
  const [historyOpen, setHistoryOpen] = useState(false)
  const hasPrice = holding.marketValue != null
  const buttonClass =
    'rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700'
  return (
    <li>
      <div className="flex flex-wrap items-start gap-3 px-4 py-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-slate-900">
            {holding.symbol} <span className="font-normal text-slate-500">· {holding.name}</span>
          </p>
          <p className="text-xs text-slate-500 tabular-nums">
            <span title={String(holding.quantity)}>
              {formatQuantity(holding.quantity)} {holding.symbol}
            </span>
            {holding.currentPrice != null && <> · precio {formatPrice(holding.currentPrice)}</>}
            {' '}· coste medio {formatPrice(holding.avgCost)}
          </p>
          <p className="text-xs text-slate-500 tabular-nums">
            Invertido {formatCurrency(holding.cost)}
            {Number(holding.rewardsCost) > 0 && <> (recompensas {formatCurrency(holding.rewardsCost)})</>}
            {holding.pnlPct != null && (
              <>
                {' '}·{' '}
                <span className={['font-medium', pnlClass(holding.pnl)].join(' ')}>
                  {formatPercent(holding.pnlPct)} {signedCurrency(holding.pnl)}
                </span>
              </>
            )}
          </p>
        </div>

        <div className="text-right">
          <p className="text-sm font-semibold tabular-nums text-slate-900">
            {hasPrice ? formatCurrency(holding.marketValue) : 'Sin precio'}
          </p>
          {hasPrice && weightPct != null && (
            <p className="text-xs tabular-nums text-slate-400">{weightPct.toFixed(1).replace('.', ',')} %</p>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-0.5">
          <button type="button" onClick={() => onBuy(holding)} className={buttonClass}>
            Compra
          </button>
          <button
            type="button"
            onClick={() => setHistoryOpen((open) => !open)}
            aria-expanded={historyOpen}
            className={buttonClass}
          >
            Historial
          </button>
          {isManualSource(holding.pricingSource) && (
            <button type="button" onClick={() => onPrice(holding)} className={buttonClass}>
              Precio
            </button>
          )}
          <button
            type="button"
            onClick={() => onDelete(holding)}
            aria-label={`Eliminar ${holding.symbol}`}
            className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
              <path d="M3 6h18" />
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
              <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
            </svg>
          </button>
        </div>
      </div>
      {historyOpen && <LotHistory holdingId={holding.id} />}
    </li>
  )
}

// Most valuable first; unpriced positions at the end.
function byMarketValueDesc(a, b) {
  return (b.marketValue ?? -Infinity) - (a.marketValue ?? -Infinity)
}

function HoldingList({ holdings, totalValue, onBuy, onPrice, onDelete }) {
  return (
    <ul className="divide-y divide-slate-100">
      {[...holdings].sort(byMarketValueDesc).map((h) => (
        <HoldingRow
          key={h.id}
          holding={h}
          weightPct={totalValue > 0 && h.marketValue != null ? (Number(h.marketValue) / totalValue) * 100 : null}
          onBuy={onBuy}
          onPrice={onPrice}
          onDelete={onDelete}
        />
      ))}
    </ul>
  )
}

const relativeTime = new Intl.RelativeTimeFormat('es', { numeric: 'auto' })

function pricedAgo(holdings) {
  const latest = holdings
    .filter((h) => h.pricingSource === 'CRYPTO' && h.lastPricedAt)
    .reduce((max, h) => Math.max(max, Date.parse(h.lastPricedAt)), 0)
  if (!latest) return null
  const minutes = Math.round((latest - Date.now()) / 60000)
  if (Math.abs(minutes) < 60) return relativeTime.format(minutes, 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return relativeTime.format(hours, 'hour')
  return relativeTime.format(Math.round(hours / 24), 'day')
}

export default function InvestmentsPage() {
  // 'total', 'nfts' or an asset class id
  const [tab, setTab] = useState('total')

  const [assetClasses, setAssetClasses] = useState(null)
  const [holdings, setHoldings] = useState(null)
  const [nfts, setNfts] = useState(null)

  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const loading = loadedKey !== String(reloadKey)
  const [refreshing, setRefreshing] = useState(false)

  const [holdingFormOpen, setHoldingFormOpen] = useState(false)
  const [buying, setBuying] = useState(null)
  const [pricing, setPricing] = useState(null)
  const [holdingToDelete, setHoldingToDelete] = useState(null)
  const [deletingHolding, setDeletingHolding] = useState(false)

  const [nftFormOpen, setNftFormOpen] = useState(false)
  const [editingNft, setEditingNft] = useState(null)
  const [nftToDelete, setNftToDelete] = useState(null)
  const [deletingNft, setDeletingNft] = useState(false)

  useEffect(() => {
    let cancelled = false
    const key = String(reloadKey)
    Promise.all([listAssetClasses(), listHoldings(), listNfts()])
      .then(([classes, holds, nftList]) => {
        if (!cancelled) {
          setAssetClasses(classes)
          setHoldings(holds)
          setNfts(nftList)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar las inversiones')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  function reload() {
    setReloadKey((k) => k + 1)
  }

  async function handleRefreshPrices() {
    setRefreshing(true)
    setActionError(null)
    try {
      const updated = await refreshPrices()
      setHoldings(updated)
    } catch (err) {
      setActionError(err.message || 'No se han podido actualizar los precios')
    } finally {
      setRefreshing(false)
    }
  }

  async function handleDeleteHolding() {
    setDeletingHolding(true)
    setActionError(null)
    try {
      await removeHolding(holdingToDelete.id)
      reload()
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar la posición')
    } finally {
      setHoldingToDelete(null)
      setDeletingHolding(false)
    }
  }

  async function handleDeleteNft() {
    setDeletingNft(true)
    setActionError(null)
    try {
      await removeNft(nftToDelete.id)
      reload()
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar el NFT')
    } finally {
      setNftToDelete(null)
      setDeletingNft(false)
    }
  }

  const allHoldings = holdings ?? []
  const allNfts = nfts ?? []
  const selectedClass = typeof tab === 'number' ? (assetClasses ?? []).find((ac) => ac.id === tab) : null
  const tabHoldings = selectedClass ? allHoldings.filter((h) => h.assetClassId === selectedClass.id) : allHoldings
  const summary = tab === 'total' ? summarize(allHoldings, allNfts) : summarize(tabHoldings)
  const allocationItems =
    tab === 'total'
      ? allocation(
          [
            ...allHoldings,
            ...allNfts.map((nft) => ({ assetClassId: 'nfts', assetClassName: 'NFTs', marketValue: nft.ourCurrentValue })),
          ],
          (h) => h.assetClassId,
          (h) => h.assetClassName,
        )
      : allocation(tabHoldings, (h) => h.id, (h) => h.symbol)
  // Row weights compare holdings with holdings only: NFTs are valued by hand and
  // would dilute every position's share
  const holdingsValue = summarize(tabHoldings).value
  const lastPriced = pricedAgo(allHoldings)

  // Group holdings under their asset class for the Total tab
  const holdingsByClass = (assetClasses ?? [])
    .map((ac) => ({ assetClass: ac, items: allHoldings.filter((h) => h.assetClassId === ac.id) }))
    .filter(({ items }) => items.length > 0)

  const tabs = [
    { id: 'total', label: 'Total', empty: false },
    ...(assetClasses ?? []).map((ac) => ({
      id: ac.id,
      label: ac.name,
      empty: !allHoldings.some((h) => h.assetClassId === ac.id),
    })),
    { id: 'nfts', label: 'NFTs', empty: allNfts.length === 0 },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Inversiones</h1>
        {tab !== 'nfts' ? (
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleRefreshPrices}
              disabled={refreshing || !holdings}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700 disabled:opacity-60"
            >
              {refreshing ? 'Actualizando…' : 'Refrescar precios'}
            </button>
            <button
              type="button"
              onClick={() => setHoldingFormOpen(true)}
              disabled={!assetClasses || assetClasses.length === 0}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:opacity-60"
            >
              Nueva posición
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => {
              setEditingNft(null)
              setNftFormOpen(true)
            }}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
          >
            Nuevo NFT
          </button>
        )}
      </div>

      {lastPriced && <p className="-mt-2 text-xs text-slate-400">Precios {lastPriced}</p>}

      {/* Tabs */}
      <div role="tablist" className="flex gap-1 overflow-x-auto rounded-lg border border-slate-200 bg-white p-1 text-sm">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={[
              'shrink-0 rounded-md px-3 py-1.5 font-medium transition-colors',
              tab === t.id
                ? 'bg-emerald-600 text-white'
                : t.empty
                  ? 'text-slate-400 hover:text-slate-600'
                  : 'text-slate-600 hover:text-slate-900',
            ].join(' ')}
          >
            {t.label}
          </button>
        ))}
      </div>

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {loading && !assetClasses && <LoadingState label="Cargando inversiones…" />}

      {error && !loading && <ErrorState message={error} onRetry={reload} />}

      {/* Holdings tab */}
      {/* Total tab */}
      {!error && assetClasses && tab === 'total' && (
        <>
          <SummaryCard summary={summary} />
          <AllocationBar items={allocationItems} />

          {holdingsByClass.map(({ assetClass, items }) => (
            <section key={assetClass.id} className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
              <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2.5">
                <h2 className="text-sm font-semibold text-slate-700">{assetClass.name}</h2>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                  {SOURCE_LABELS[assetClass.pricingSource]}
                </span>
              </div>
              <HoldingList
                holdings={items}
                totalValue={holdingsValue}
                onBuy={setBuying}
                onPrice={setPricing}
                onDelete={setHoldingToDelete}
              />
            </section>
          ))}

          {assetClasses.length === 0 && (
            <EmptyState
              title="Aún no hay clases de activo"
              message="Las clases por defecto se crean al registrarte."
            />
          )}
        </>
      )}

      {/* Asset class tab */}
      {!error && selectedClass && (
        <>
          {tabHoldings.length === 0 ? (
            <EmptyState title={selectedClass.name} message="Sin posiciones en esta clase." />
          ) : (
            <>
              <SummaryCard summary={summary} />
              <AllocationBar items={allocationItems} />
              <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
                <HoldingList
                  holdings={tabHoldings}
                  totalValue={holdingsValue}
                  onBuy={setBuying}
                  onPrice={setPricing}
                  onDelete={setHoldingToDelete}
                />
              </section>
            </>
          )}
        </>
      )}

      {/* NFTs tab */}
      {!error && nfts && tab === 'nfts' && (
        <>
          {nfts.length === 0 ? (
            <EmptyState title="Aún no hay NFTs" message="Registra tu primer NFT con la cripto que pagaste." />
          ) : (
            <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
              {nfts.map((nft) => (
                <li key={nft.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-slate-900">
                      {nft.name}
                      {nft.collection ? <span className="font-normal text-slate-500"> · {nft.collection}</span> : null}
                    </p>
                    <p className="truncate text-xs text-slate-500">
                      Pagaste {formatQuantity(nft.buyCryptoAmount)} {nft.buyCryptoSymbol} ·{' '}
                      {formatCurrency(nft.fiatValueAtPurchase)}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-slate-500">Hoy esa cripto vale</p>
                    <p className="text-sm font-semibold tabular-nums text-slate-900">
                      {nft.currentPurchaseValue != null ? formatCurrency(nft.currentPurchaseValue) : 'Sin precio'}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-0.5">
                    <button
                      type="button"
                      onClick={() => {
                        setEditingNft(nft)
                        setNftFormOpen(true)
                      }}
                      aria-label={`Editar ${nft.name}`}
                      className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                        <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      onClick={() => setNftToDelete(nft)}
                      aria-label={`Eliminar ${nft.name}`}
                      className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                        <path d="M3 6h18" />
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                        <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                      </svg>
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {/* Modals */}
      {holdingFormOpen && assetClasses && (
        <HoldingForm
          assetClasses={assetClasses}
          defaultAssetClassId={selectedClass?.id}
          onClose={() => setHoldingFormOpen(false)}
          onSaved={() => {
            setHoldingFormOpen(false)
            reload()
          }}
        />
      )}

      {buying && (
        <BuyForm
          holding={buying}
          onClose={() => setBuying(null)}
          onSaved={() => {
            setBuying(null)
            reload()
          }}
        />
      )}

      {pricing && (
        <PriceForm
          holding={pricing}
          onClose={() => setPricing(null)}
          onSaved={() => {
            setPricing(null)
            reload()
          }}
        />
      )}

      {nftFormOpen && (
        <NftForm
          nft={editingNft}
          onClose={() => {
            setNftFormOpen(false)
            setEditingNft(null)
          }}
          onSaved={() => {
            setNftFormOpen(false)
            setEditingNft(null)
            reload()
          }}
        />
      )}

      {holdingToDelete && (
        <ConfirmDialog
          title="Eliminar posición"
          message={`¿Seguro que quieres eliminar «${holdingToDelete.symbol}»? Se borrarán también sus compras registradas.`}
          confirmLabel="Eliminar"
          loading={deletingHolding}
          onCancel={() => setHoldingToDelete(null)}
          onConfirm={handleDeleteHolding}
        />
      )}

      {nftToDelete && (
        <ConfirmDialog
          title="Eliminar NFT"
          message={`¿Seguro que quieres eliminar «${nftToDelete.name}»? Esta acción no se puede deshacer.`}
          confirmLabel="Eliminar"
          loading={deletingNft}
          onCancel={() => setNftToDelete(null)}
          onConfirm={handleDeleteNft}
        />
      )}
    </div>
  )
}
