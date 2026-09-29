// Pure aggregations for the Investments screen. Per-position figures (cost, pnl,
// rewardsCost) come computed from the backend; this only adds them up per tab.

/**
 * @param {Array<{cost: number, marketValue: number|null, pnl: number|null, rewardsCost?: number}>} holdings
 * @param {Array<{ourCurrentValue: number, fiatValueAtPurchase: number}>} [nfts]
 * @returns {{value: number, invested: number, pnl: number, pnlPct: number|null, rewards: number, unpriced: number}}
 */
export function summarize(holdings, nfts = []) {
  let value = 0
  let invested = 0
  let pnl = 0
  let pricedCost = 0
  let rewards = 0
  let unpriced = 0

  for (const h of holdings) {
    const cost = Number(h.cost)
    invested += cost
    rewards += Number(h.rewardsCost ?? 0)
    if (h.marketValue == null) {
      unpriced += 1
    } else {
      value += Number(h.marketValue)
      pnl += Number(h.pnl)
      pricedCost += cost
    }
  }
  for (const nft of nfts) {
    const paid = Number(nft.fiatValueAtPurchase)
    const worth = Number(nft.ourCurrentValue)
    invested += paid
    value += worth
    pnl += worth - paid
    pricedCost += paid
  }

  // The percentage only compares positions that have a price, so unpriced ones
  // do not drag it down as if they were worth 0.
  const pnlPct = pricedCost > 0 ? (pnl / pricedCost) * 100 : null
  return { value, invested, pnl, pnlPct, rewards, unpriced }
}

/**
 * Share of the total market value per group, biggest first. Items without a
 * positive value are left out; an empty result means there is nothing to draw.
 * @template T
 * @param {T[]} items objects with a `marketValue`
 * @param {(item: T) => string|number} keyFn
 * @param {(item: T) => string} labelFn
 * @returns {Array<{key: string|number, label: string, value: number, pct: number}>}
 */
export function allocation(items, keyFn, labelFn) {
  const groups = new Map()
  for (const item of items) {
    const value = Number(item.marketValue ?? 0)
    if (!(value > 0)) continue
    const key = keyFn(item)
    const group = groups.get(key) ?? { key, label: labelFn(item), value: 0 }
    group.value += value
    groups.set(key, group)
  }
  const total = [...groups.values()].reduce((sum, g) => sum + g.value, 0)
  if (total === 0) return []
  return [...groups.values()]
    .map((g) => ({ ...g, pct: (g.value / total) * 100 }))
    .sort((a, b) => b.value - a.value)
}
