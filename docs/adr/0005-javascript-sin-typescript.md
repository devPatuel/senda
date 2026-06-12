# ADR 0005 — JavaScript sin TypeScript en el frontend

**Fecha:** 2026-06-12 · **Estado:** aceptada (revisable en fases futuras)

## Contexto

La spec de Fase 0/1 fija React + Vite + Tailwind pero no pide TypeScript. Jordi viene
de Java (DAM) y este proyecto es a la vez de uso real y de aprendizaje: el objetivo de
Fase 1 es asentar React, hooks, router y el consumo de la API, no añadir un sistema de
tipos nuevo a la curva.

## Decisión

Frontend en **JavaScript** (ESLint como red de seguridad), sin TypeScript en Fase 1.

## Consecuencias

- (+) Curva de aprendizaje centrada en React y el ecosistema, menos fricción de configuración.
- (−) Sin tipos, los errores de forma de datos se detectan en ejecución; mitigado con tests (Vitest) y un cliente HTTP central.
- (~) Revisable: si el proyecto crece (más módulos, más DTOs), migrar a TypeScript es viable de forma incremental (`allowJs`).
