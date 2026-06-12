# ADR 0006 — Spring Boot 3.5.15 (no 4.x)

**Fecha:** 2026-06-12 · **Estado:** aceptada (revisable)

## Contexto

start.spring.io ya ofrece Spring Boot 4.1 por defecto, pero la spec aprobada de Senda
fija Spring Boot 3. La rama 3.5.x es la última de la generación 3, madura y con mucho
más ecosistema probado encima (guías, respuestas, librerías verificadas), lo que importa
en un proyecto que también es de aprendizaje.

## Decisión

Spring Boot **3.5.15** con Java 21. No adoptar 4.x en Fase 0/1.

## Consecuencias

- (+) Se respeta la spec; documentación y ecosistema más maduros, menos sorpresas con dependencias (jjwt, Testcontainers, Flyway).
- (−) En algún momento tocará migrar a 4.x; al ser un codebase pequeño y por capas, el coste será contenido.
- (~) Vigilar el fin de soporte OSS de la rama 3.5 para planificar la migración.
