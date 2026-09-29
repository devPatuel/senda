import { describe, it, expect } from 'vitest'
import { summarize, allocation } from './summary'

const btc = { assetClassId: 5, symbol: 'BTC', cost: 500, marketValue: 600, pnl: 100, rewardsCost: 0 }
const sol = { assetClassId: 5, symbol: 'SOL', cost: 250, marketValue: 200, pnl: -50, rewardsCost: 60 }
const eth = { assetClassId: 5, symbol: 'ETH', cost: 90, marketValue: null, pnl: null, rewardsCost: 0 }

describe('summarize', () => {
  it('sums priced positions for value and pnl, and every position for invested', () => {
    expect(summarize([btc, sol, eth])).toEqual({
      value: 800,
      invested: 840,
      pnl: 50,
      pnlPct: (50 / 750) * 100,
      rewards: 60,
      unpriced: 1,
    })
  })

  it('has no pnl percentage when nothing is priced', () => {
    expect(summarize([eth]).pnlPct).toBeNull()
  })

  it('counts NFTs by their estimated value against what was paid', () => {
    expect(summarize([], [{ ourCurrentValue: 50, fiatValueAtPurchase: 40 }])).toMatchObject({
      value: 50,
      invested: 40,
      pnl: 10,
      pnlPct: 25,
    })
  })

  it('is all zeros for an empty tab', () => {
    expect(summarize([])).toEqual({ value: 0, invested: 0, pnl: 0, pnlPct: null, rewards: 0, unpriced: 0 })
  })
})

describe('allocation', () => {
  const bySymbol = (h) => h.symbol

  it('weights priced items by market value, biggest first, skipping unpriced ones', () => {
    const result = allocation([sol, btc, eth], bySymbol, bySymbol)
    expect(result.map((a) => [a.key, a.pct])).toEqual([
      ['BTC', 75],
      ['SOL', 25],
    ])
  })

  it('groups items sharing a key', () => {
    const result = allocation([btc, sol], (h) => h.assetClassId, () => 'Cripto')
    expect(result).toEqual([{ key: 5, label: 'Cripto', value: 800, pct: 100 }])
  })

  it('is empty when nothing has value', () => {
    expect(allocation([eth, { ...btc, marketValue: 0 }], bySymbol, bySymbol)).toEqual([])
  })
})
