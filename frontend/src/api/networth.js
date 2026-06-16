// API module for /api/networth.
import { http } from './http'

/**
 * @typedef {Object} NetWorth
 * @property {number} liquid            Cash and bank balances (non-archived accounts).
 * @property {number} investments       Total investment value (holdings + NFTs).
 * @property {number} investmentsHoldings  Market value of priced holdings only.
 * @property {number} investmentsNfts      Sum of ourCurrentValue for all NFTs.
 * @property {number} debtsInFavor      Pending amounts owed to the user (THEY_OWE_ME).
 * @property {number} debtsAgainst      Pending amounts the user owes others (I_OWE).
 * @property {number} net               Net worth: liquid + investments + debtsInFavor - debtsAgainst.
 */

/**
 * Returns the authenticated user's net worth snapshot.
 * All amounts are in EUR with two decimal places.
 * @returns {Promise<NetWorth>}
 */
export function getNetWorth() {
  return http.get('/networth')
}
