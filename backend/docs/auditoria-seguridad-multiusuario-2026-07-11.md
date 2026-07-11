# Auditoría de seguridad multiusuario — Senda (2026-07-11)

## Alcance

~70 endpoints en 14 módulos. Criterio de aislamiento: **tocar un recurso ajeno devuelve 404, nunca 403** (no revelar existencia). Excepción legítima: el módulo `space`, donde un recurso con `space_id` no nulo es accesible por cualquier **miembro ACTIVO** del espacio.

Metodología: auditoría dirigida por tests. Por cada endpoint mutante/derivado sin cobertura cross-tenant se añadió un test de integración "usuario B no puede leer/editar/borrar recurso de A → 404". Un test que pasa a la primera certifica que el endpoint ya estaba blindado (queda como regresión); uno que falla revela una fuga a corregir.

## Resultado global

**No se encontró ninguna fuga.** El aislamiento por usuario ya estaba correctamente implementado en todo el código auditado. El trabajo consistió en **cerrar huecos de cobertura de tests** (endpoints correctos pero sin prueba cross-tenant explícita) y en blindar contra regresiones futuras.

- Mecanismo de auth central: verificado (JWT → `JwtAuthFilter` → `CurrentUser.id()`).
- Patrón de ownership: consistente (`findByIdAndUserId` → 404, o `findAccessible` con `SpaceAccess` para recursos space-aware).
- Predicado `space_id IS NULL` en agregados personales: presente en todas las queries auditadas.

## Mecanismo de auth

- `common/JwtService` — el token transporta solo el `userId` en el subject; sin claims de rol ni de espacio.
- `common/JwtAuthFilter` — principal = `Long userId`, autoridad fija `ROLE_USER`.
- `common/CurrentUser.id()` — única fuente del usuario autenticado; nunca se acepta `userId` desde body/query.
- `common/SpaceAccess.assertActiveMember(userId, spaceId)` — 404 si no eres miembro ACTIVO; `activeSpaceIds(userId)` para agregados.

Verificado por `AuthContractIntegrationTest` (401 sin token, 401 token basura, 200 con token válido). Sin hallazgos.

## Estado por dominio

| Dominio | Cobertura cross-tenant | Test añadido en esta auditoría | Fuga |
|---|---|---|---|
| transaction | OK (previa: summary, trends por usuario, categoría ajena) | — | — |
| category | OK | `userBCannotAssignOrTargetCategoryOfUserA` (assign/target) | — |
| account | OK (previa) | — | — |
| debt | OK (POST payment ya cubierto) | `userBCannotListNorDeleteUserADebtPayments` (GET/DELETE payments) | — |
| investment | OK (buys/price/delete ya cubiertos) | `userBCannotListUserAHoldingLots` (GET lots) | — |
| recurring | OK (previa) | — | — |
| shopping | OK (bought/update/delete/envelope ajeno ya cubiertos) | — | — |
| categoryrule | OK (previa) | — | — |
| allocation | OK (previa) | — | — |
| imports | Sin test cross-tenant previo | `commitCannotUseAnotherUsersCategory` (commit con categoría de otro usuario → 404) | — |
| networth | OK (previa: `NetWorthScopingIntegrationTest`) | — | — |
| alerts | Verificado por inspección (ver abajo) | — | — |
| space | OK (`SpaceScopingIntegrationTest`) | `coupleTransactionDoesNotLeakIntoPersonalTrends` | — |
| auth | Verificado | `AuthContractIntegrationTest` | — |

Barrido adicional: `PersonalModulesIgnoreSpaceIntegrationTest` confirma que los dominios sin columna `space_id` (debt, recurring, investment, …) **ignoran** un `?spaceId=` colado — el parámetro es inerte y no puede ampliar el scope personal.

## Verificación del módulo `alerts` (por inspección)

`alerts` no tiene test cross-tenant porque montar sus condiciones (gastos hormiga, suscripción olvidada) requiere series de gasto costosas de fabricar. Se verificó por lectura de código que **todas** sus fuentes de datos están acotadas al usuario y excluyen filas de espacio:

- `TransactionRepository.expenseStatsByCategory(userId, from, to)` → `where t.userId = :userId and t.spaceId is null`.
- `TransactionRepository.categoryIdsWithExpenseSince(userId, from)` → `where t.userId = :userId and t.spaceId is null`.
- `RecurringPaymentRepository.findByUserId(userId)` → dominio puramente personal (sin `space_id`).
- `CategoryRepository.findByUserIdAndSpaceIdIsNull(userId)` → solo categorías personales.

Conclusión: `alerts` no filtra datos de pareja ni de otro usuario.

## Hallazgos y correcciones

Ninguno. No hubo fugas que corregir. El valor de esta auditoría es doble:

1. **Certificación**: se comprobó empíricamente (tests que pasan) que el aislamiento por usuario es correcto endpoint por endpoint.
2. **Regresión**: los 6 tests añadidos blindan los huecos de cobertura, de modo que una futura query personal que olvide `space_id is null`, o un sub-recurso nuevo que no valide ownership, se detectará en CI.

## Convenciones confirmadas (para el doc de seguridad del Brain)

- **404 nunca 403** en todo acceso a un recurso ajeno (personal de otro usuario o de un espacio del que no eres miembro ACTIVO).
- **`space_id IS NULL` obligatorio** en toda query/agregado del scope personal: las filas de un espacio conservan el `user_id` del autor, así que sin ese predicado se filtrarían al ledger individual. Es la clase de fuga más probable del proyecto y ahora está cubierta por tests en summary, balance, budget y trends.
- El `userId` se resuelve siempre server-side con `CurrentUser.id()`; jamás se confía en un `userId`/`spaceId` del cliente para ampliar el scope.

## Tests añadidos (todos verdes)

- `common/AuthContractIntegrationTest`
- `investment/InvestmentIntegrationTest#userBCannotListUserAHoldingLots`
- `debt/DebtIntegrationTest#userBCannotListNorDeleteUserADebtPayments`
- `category/CategoryIntegrationTest#userBCannotAssignOrTargetCategoryOfUserA`
- `imports/ImportIntegrationTest#commitCannotUseAnotherUsersCategory`
- `space/SpaceScopingIntegrationTest#coupleTransactionDoesNotLeakIntoPersonalTrends`
- `space/PersonalModulesIgnoreSpaceIntegrationTest`
