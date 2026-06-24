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
import { formatCurrency, todayISO } from '../lib/format'
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

// Quantities/prices can carry up to 8 decimals: format without forcing currency.
function formatQuantity(value) {
  return new Intl.NumberFormat('es-ES', { maximumFractionDigits: 8 }).format(Number(value))
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
  const [form, setForm] = useState({ quantity: '', unitPrice: '', date: todayISO() })
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
          Se recalculará la cantidad y el coste medio de la posición.
        </p>

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

// --- Holding row ---

function HoldingRow({ holding, onBuy, onPrice, onDelete }) {
  const hasPnl = holding.pnl != null
  const pnlPositive = hasPnl && Number(holding.pnl) >= 0
  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-slate-900">
          {holding.symbol} <span className="font-normal text-slate-500">· {holding.name}</span>
        </p>
        <p className="truncate text-xs text-slate-500">
          {formatQuantity(holding.quantity)} · coste medio {formatCurrency(holding.avgCost)}
        </p>
      </div>

      <div className="text-right">
        <p className="text-sm font-semibold tabular-nums text-slate-900">
          {holding.marketValue != null ? formatCurrency(holding.marketValue) : 'Sin valorar'}
        </p>
        {hasPnl ? (
          <p
            className={[
              'text-xs font-medium tabular-nums',
              pnlPositive ? 'text-emerald-600' : 'text-red-600',
            ].join(' ')}
          >
            {pnlPositive ? '+' : ''}
            {formatCurrency(holding.pnl)}
          </p>
        ) : (
          <p className="text-xs text-slate-400">
            {holding.currentPrice != null ? formatCurrency(holding.currentPrice) : 'Sin precio'}
          </p>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-0.5">
        <button
          type="button"
          onClick={() => onBuy(holding)}
          className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
        >
          Compra
        </button>
        {isManualSource(holding.pricingSource) && (
          <button
            type="button"
            onClick={() => onPrice(holding)}
            className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
          >
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
    </li>
  )
}

export default function InvestmentsPage() {
  const [tab, setTab] = useState('holdings')

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

  const totalMarketValue = (holdings ?? [])
    .filter((h) => h.marketValue != null)
    .reduce((sum, h) => sum + Number(h.marketValue), 0)

  // Group holdings under their asset class for display
  const holdingsByClass = (assetClasses ?? []).map((ac) => ({
    assetClass: ac,
    items: (holdings ?? []).filter((h) => h.assetClassId === ac.id),
  }))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Inversiones</h1>
        {tab === 'holdings' ? (
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

      {/* Tabs */}
      <div className="flex gap-1 rounded-lg border border-slate-200 bg-white p-1 text-sm">
        <button
          type="button"
          onClick={() => setTab('holdings')}
          className={[
            'flex-1 rounded-md px-3 py-1.5 font-medium transition-colors',
            tab === 'holdings' ? 'bg-emerald-600 text-white' : 'text-slate-600 hover:text-slate-900',
          ].join(' ')}
        >
          Posiciones
        </button>
        <button
          type="button"
          onClick={() => setTab('nfts')}
          className={[
            'flex-1 rounded-md px-3 py-1.5 font-medium transition-colors',
            tab === 'nfts' ? 'bg-emerald-600 text-white' : 'text-slate-600 hover:text-slate-900',
          ].join(' ')}
        >
          NFTs
        </button>
      </div>

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {loading && !assetClasses && <LoadingState label="Cargando inversiones…" />}

      {error && !loading && <ErrorState message={error} onRetry={reload} />}

      {/* Holdings tab */}
      {!error && assetClasses && tab === 'holdings' && (
        <>
          <section className="rounded-2xl border border-slate-200 bg-white p-6 text-center">
            <p className="text-sm text-slate-500">Valor de mercado</p>
            <p className="mt-1 text-4xl font-bold tracking-tight text-emerald-600">
              {formatCurrency(totalMarketValue)}
            </p>
          </section>

          {holdingsByClass.map(({ assetClass, items }) => (
            <section key={assetClass.id} className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
              <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2.5">
                <h2 className="text-sm font-semibold text-slate-700">{assetClass.name}</h2>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                  {SOURCE_LABELS[assetClass.pricingSource]}
                </span>
              </div>
              {items.length === 0 ? (
                <p className="px-4 py-4 text-sm text-slate-400">Sin posiciones en esta clase.</p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {items.map((h) => (
                    <HoldingRow
                      key={h.id}
                      holding={h}
                      onBuy={setBuying}
                      onPrice={setPricing}
                      onDelete={setHoldingToDelete}
                    />
                  ))}
                </ul>
              )}
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
