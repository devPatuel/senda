# Apple Shortcut: captura rápida de gastos

Permite registrar un gasto en Senda desde el iPhone en segundos, sin abrir la app.
No se distribuye un archivo `.shortcut`: aquí está la configuración exacta para montarlo a mano.

## 1. Requisito previo: generar un token personal

1. Entra en Senda → **Tokens** (`/tokens`).
2. Pulsa **Generar**, ponle un nombre (p. ej. «iPhone de Jordi»).
3. Copia el valor `senda_pat_...` **en ese momento**: no se vuelve a mostrar.
4. Lo pegarás una sola vez dentro del atajo (paso 3 de abajo).

> El token equivale a tu contraseña. Si pierdes el iPhone, revócalo desde `/tokens`.

## 2. Pasos del atajo (app Atajos de iOS)

1. **Pedir entrada** → tipo **Número** → pregunta: *«¿Importe?»*. (Guarda como *Importe*.)
2. **Pedir entrada** → tipo **Texto** → pregunta: *«¿Descripción?»*. (Guarda como *Descripción*.)
3. **Obtener contenido de la URL** con esta configuración:
   - **URL**: `https://senda-pc.<tailnet>.ts.net/api/transactions/quick`
     (con Tailscale; en local sería `http://localhost:8080/api/transactions/quick`).
   - **Método**: `POST`
   - **Cabeceras**:
     - `Authorization`: `Bearer senda_pat_...` (tu token del paso 1)
     - `Content-Type`: `application/json`
   - **Cuerpo de la solicitud**: `JSON`
     ```json
     {
       "amount": [Importe],
       "description": "[Descripción]"
     }
     ```
     (Sustituye `[Importe]` y `[Descripción]` por las variables mágicas de los pasos 1 y 2.)
4. (Opcional) **Mostrar resultado** con el contenido de la URL, para confirmar.

## 3. Cómo se asigna la categoría

El endpoint `POST /api/transactions/quick`:

- Tipo siempre **EXPENSE** (gasto), fecha **hoy**, ámbito **personal**.
- Si **no** envías `categoryId`, la categoría se resuelve por tus **reglas de categoría**
  (`/reglas`) contra la descripción. Ej.: una regla «mercadona → Supermercado» hará que
  *«Compra en Mercadona»* se clasifique sola.
- Si **ninguna** regla coincide, la API responde **400**. Dos salidas:
  - Crea una regla en `/reglas` para esa descripción, o
  - Añade `"categoryId": <id>` al cuerpo JSON del atajo.

## 4. Respuestas posibles

| Código | Significado | Acción |
|---|---|---|
| 201 | Gasto creado | OK |
| 400 | No se pudo resolver categoría (sin regla ni `categoryId`) | Crear regla o pasar `categoryId` |
| 401 | Token inválido o revocado | Generar uno nuevo en `/tokens` |
| 404 | La `categoryId` enviada no es tuya | Usar una categoría propia |

## 5. Ejemplo de petición (para depurar con curl)

```bash
curl -X POST https://senda-pc.<tailnet>.ts.net/api/transactions/quick \
  -H "Authorization: Bearer senda_pat_XXXXXXXX" \
  -H "Content-Type: application/json" \
  -d '{"amount": 12.50, "description": "Mercadona"}'
```
