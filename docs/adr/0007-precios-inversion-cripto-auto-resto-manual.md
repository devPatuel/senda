# ADR 0007 — Precios de inversión: cripto automática, metales y fondos manuales

**Fecha:** 2026-06-16 · **Estado:** aceptada

## Contexto

El módulo de inversiones necesita valorar las posiciones (`holdings`) a precio de
mercado para calcular P&L y patrimonio neto. Cada clase de activo tiene un origen de
precio distinto y la disponibilidad de datos gratuitos y fiables varía mucho:

- **Cripto**: CoinGecko ofrece una API pública gratuita, sin API key, con precios en EUR.
- **Metales** (oro/plata): no se encontró ninguna API pública fiable, sin API key, que
  devuelva el spot en EUR.
- **Fondos** (por ISIN): no hay API gratuita fiable que devuelva el NAV por ISIN.

Además, la capa gratuita de CoinGecko tiene un *rate limit* estricto: un refresco de
muchos holdings (o valorar varios NFTs) podría agotarlo si cada llamada pega contra la API.

## Decisión

El origen de precio se modela con el enum `PricingSource` (`CRYPTO`, `METAL`, `FUND`,
`MANUAL`):

- **`CRYPTO` se valora automáticamente** contra CoinGecko
  (`GET /simple/price?ids={id}&vs_currencies=eur`) vía `RestClient` con *timeouts* de
  conexión y lectura de **5 s**. Cualquier fallo (red, timeout, rate limit, body
  inválido) se registra y **degrada en silencio** a "sin precio" (`Optional.empty()`),
  nunca propaga el error. CoinGecko indexa por *coin id*, no por *ticker*, así que se
  mantiene un **mapa estático `symbol → id`** (`BTC → bitcoin`, `ETH → ethereum`, …);
  un símbolo no mapeado cae a su forma en minúsculas como id (funciona para algunas
  monedas, devuelve vacío inofensivamente en el resto).
- **`METAL`, `FUND` y `MANUAL` son manuales**: el usuario teclea el precio con
  `PUT /api/investments/holdings/{id}/price`, que sella `last_priced_at`. No se
  auto-valoran.

Para no agotar el rate limit, `PricingService` mantiene una **caché en memoria**
(`ConcurrentHashMap`) con TTL diferenciado: **15 min para aciertos** y **2 min para
fallos** (*negative caching*), para que un símbolo basura o un proveedor caído no
disparen una llamada saliente en cada refresco.

## Consecuencias

- (+) La cripto se actualiza sola; lo que no tiene fuente fiable y gratuita no finge
  tenerla: el usuario controla el precio y sabe que es suyo.
- (+) Sin API key que custodiar ni rotar; sin coste.
- (+) La caché y la degradación silenciosa hacen que el módulo nunca rompa por un
  proveedor externo caído ni agote su cuota.
- (−) Los precios de metales y fondos quedan tan al día como el usuario los mantenga;
  un holding sin precio vale 0 a efectos de patrimonio (ver [ADR 0010](0010-calculo-patrimonio-neto.md)).
- (−) El mapa `symbol → id` es estático: añadir una moneda nueva exige tocar código.
- (−) Caché en memoria por instancia: no se comparte entre réplicas (irrelevante con
  un único backend en el NAS).

## Actualización 2026-09-29 — petición única y proveedor de respaldo

- **Una sola petición a CoinGecko** por refresco (`/simple/price?ids=a,b,c`) en lugar de
  una por posición: seis posiciones seguidas bastaban para el 429 del plan gratuito. La
  caché sigue siendo por símbolo y solo se piden los que no están frescos.
- **Coinbase como respaldo.** CoinGecko bloquea por IP algunas conexiones domésticas (403
  de CloudFront en `/simple/price`, aunque `/ping` responda). Se añade
  `CoinbasePriceProvider` (`GET /v2/exchange-rates?currency=EUR`, pública y sin clave): una
  petición trae todas las monedas en EUR como "unidades por 1 EUR", así que el precio es
  `1 / tasa` (escala 8, `HALF_UP`).
- `FallbackCryptoPriceProvider` pregunta a CoinGecko por todo y a Coinbase **solo por lo
  que falte**; si CoinGecko responde completo, Coinbase no se llama. Es el único bean
  `CryptoPriceProvider` (lo crea `PricingConfig`), de modo que `PricingService` y su caché
  no cambian y los tests lo sustituyen con `@MockitoBean` sin ambigüedad.
- (−) Las dos fuentes pueden diferir unas décimas; no se guarda qué proveedor dio cada
  precio.
