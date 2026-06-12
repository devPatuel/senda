# ADR 0002 — Autenticación JWT stateless (vs sesiones de servidor)

**Fecha:** 2026-06-12 · **Estado:** aceptada

## Contexto

La API es consumida por una SPA (React) y debe ser multiusuario desde el día 1.
Las opciones eran sesiones de servidor (cookie + estado en backend) o tokens JWT
firmados sin estado.

## Decisión

JWT stateless: HS256 con jjwt 0.12.x, claim `sub` = userId, expiración 24 h, sin
refresh tokens en Fase 1. El secreto se lee del env `SENDA_JWT_SECRET` (propiedad
`senda.jwt.secret`); el default hardcodeado en `application.yml` es solo para desarrollo.
El frontend guarda el token en `localStorage` y lo adjunta como `Authorization: Bearer`.

## Consecuencias

- (+) El backend no guarda estado de sesión: simple, escalable y natural para una API REST + SPA.
- (+) El `user_id` viaja firmado en el token; toda consulta filtra por él, nunca por parámetros del cliente.
- (−) Un JWT no se puede revocar antes de expirar; mitigado con expiración corta (24 h).
- (−) `localStorage` es accesible desde JS (riesgo si hubiera XSS); asumido en Fase 1, revisable (cookie httpOnly) más adelante.
- (−) Sin refresh tokens, el usuario vuelve a hacer login cada 24 h (aceptable para uso personal).
