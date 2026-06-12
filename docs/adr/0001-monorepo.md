# ADR 0001 — Monorepo para backend, frontend y docs

**Fecha:** 2026-06-12 · **Estado:** aceptada

## Contexto

Senda es una app personal con backend (Spring Boot) y frontend (React) desarrollados
por una sola persona, que evolucionan juntos y se despliegan juntos en un NAS doméstico.
Separar en dos repos añadiría coordinación (versionado cruzado, PRs duplicadas, dos clones)
sin aportar nada a este tamaño de equipo.

## Decisión

Un único repositorio `senda/` con `/backend`, `/frontend`, `/docs` y los ficheros
`docker-compose*.yml` en la raíz. Cada parte mantiene su propio toolchain (Maven / npm)
y su propio Dockerfile.

## Consecuencias

- (+) Un cambio de API y su consumo en el front van en el mismo commit; la doc vive al lado del código.
- (+) `docker compose -f docker-compose.prod.yml up --build` construye todo desde la raíz.
- (−) El historial mezcla cambios de back y front; mitigado con prefijos en los mensajes de commit.
- (−) Si algún día se extrae un servicio, habrá que separar historial (asumible).
