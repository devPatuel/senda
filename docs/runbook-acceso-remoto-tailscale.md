# Runbook: acceso remoto a Senda con Tailscale

Senda encendida 24/7 en un PC viejo en casa, accesible desde fuera para dos personas
(Jordi + pareja) mediante Tailscale (VPN privada WireGuard), **sin publicarla en internet**.

Decisión de diseño: **Tailscale** frente a Cloudflare Tunnel o port-forwarding. Motivo:
no se expone Senda a internet abierto, cifrado extremo a extremo, sin abrir puertos ni
dominio, mantenimiento mínimo para 2 usuarios.

---

## 1. Requisitos del PC

- Docker + Docker Compose instalados.
- **Docker al arranque del sistema**:
  - Linux: `sudo systemctl enable --now docker`.
  - macOS/Windows: activar «iniciar Docker al arrancar» en Docker Desktop.
- Con `restart: unless-stopped` (ya en `docker-compose.prod.yml`), los contenedores
  vuelven solos tras reiniciar el PC.

## 2. Levantar la pila de producción

```bash
cd /ruta/a/senda
export SENDA_DB_PASSWORD='...'   # desde el gestor de contraseñas
export SENDA_JWT_SECRET='...'    # desde el gestor de contraseñas
# Todas las URL desde las que se abrirá la app, con su puerto y separadas por comas
export SENDA_CORS_ORIGINS='http://localhost:8088,http://senda-pc.<tailnet>.ts.net:8088'
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps   # los 3 servicios healthy/running
```

- **`SENDA_CORS_ORIGINS` no es opcional en la práctica.** El navegador envía la cabecera
  `Origin` en cada escritura y el backend rechaza con `403` los orígenes que no estén en
  la lista. Síntoma de que falta uno: la app carga y muestra datos, pero no deja
  guardar nada. Si se activa el HTTPS de `tailscale serve` (sección 5), añadir también
  `https://senda-pc.<tailnet>.ts.net`.
- **Primera cuenta.** El registro viene cerrado. Para crear las cuentas, levantar una
  vez con `export SENDA_REGISTRATION_ENABLED=true`, registrarse desde la app y volver a
  levantar sin la variable (`unset SENDA_REGISTRATION_ENABLED` y el mismo `up -d`).

- El volumen `senda_pgdata_prod` conserva la contraseña con la que se inicializó;
  cambiar `SENDA_DB_PASSWORD` después NO la cambia.
- La app queda servida por nginx en el puerto **8088** del PC.

## 3. Instalar Tailscale

1. En el PC: instalar Tailscale y `sudo tailscale up` (login con la cuenta del tailnet).
   Verificar: `tailscale status` → el PC aparece con su IP `100.x` y su nombre MagicDNS
   (p. ej. `senda-pc`).
2. Activar **MagicDNS** en la consola de Tailscale (admin panel → DNS → MagicDNS: on).
3. En los dos iPhones: instalar la app Tailscale, iniciar sesión en el mismo tailnet y
   activar la VPN.

## 4. Acceder a Senda desde fuera

- URL: `http://senda-pc.<tailnet>.ts.net:8088`
- **No hay que reconfigurar el frontend**: nginx sirve el SPA y proxya `/api` en el mismo
  origen con URL relativa (ver `frontend/Dockerfile`, `ARG VITE_API_URL=/api`). Cualquier
  dispositivo del tailnet resuelve el nombre MagicDNS.
- Verificar salud. El endpoint de salud del backend no se publica fuera de Docker, así
  que se comprueba en dos pasos:
  ```bash
  docker compose -f docker-compose.prod.yml ps          # backend: (healthy)
  curl -s -o /dev/null -w '%{http_code}\n' http://senda-pc.<tailnet>.ts.net:8088/   # 200
  ```

## 5. Seguridad del transporte (TLS)

- El tráfico entre dispositivos del tailnet va cifrado por WireGuard extremo a extremo.
  El aviso de «HTTP en claro en la LAN» del compose queda **mitigado mientras se acceda por
  Tailscale** (no por la IP LAN directa).
- **Bonus HTTPS con certificado (opcional):** activar HTTPS de MagicDNS en la consola y
  exponer con:
  ```bash
  sudo tailscale serve --bg --https=443 http://localhost:8088
  ```
  La app queda en `https://senda-pc.<tailnet>.ts.net` con certificado válido, sin puerto.
  Verificar: `tailscale serve status`.
  Con `tailscale serve` delante, nginx ve todas las peticiones llegar desde el propio
  PC, así que el límite de intentos de login (10 por minuto) vuelve a ser uno solo para
  todos los dispositivos. Con dos usuarios es asumible; conviene saberlo si alguien se
  queda sin poder entrar durante un minuto.

## 6. Backup y restore

- Backup manual: `./scripts/backup-db.sh` → crea `~/Documents/Senda/backups/senda-<fecha>.sql.gz` (fuera del repo: el volcado lleva datos reales).
  Guardar los `.sql.gz` fuera del PC (Proton Drive u otro disco).
- Backup periódico (Linux, diario a las 03:00) con cron:
  ```
  0 3 * * *  cd /ruta/a/senda && ./scripts/backup-db.sh /ruta/backups >> /var/log/senda-backup.log 2>&1
  ```
- Restore (destructivo, pide confirmación). Va en una sola transacción: si algo falla,
  la base queda como estaba.
  ```bash
  ./scripts/restore-db.sh ~/Documents/Senda/backups/senda-<fecha>.sql.gz
  docker compose -f docker-compose.prod.yml restart backend
  ```
- Los dos scripts aceptan `--local` como primer argumento para trabajar con la base de
  desarrollo (`docker-compose.yml`, la que arranca `start.command`) en vez de la de
  producción.
- Los volcados se generan con `--clean --if-exists`: borran y recrean cada objeto, así
  que se restauran tanto sobre una base vacía como sobre una que ya tiene datos. Un
  volcado anterior al 30/09/2026 no lleva esas órdenes y solo se restaura sobre una
  base vacía.
- **Prueba de restore**: hacer backup → restore → `docker compose ... ps` debe mostrar
  el backend `healthy`. Último ciclo probado: **30/09/2026**, con un volcado de la base
  local restaurado dos veces seguidas en un Postgres 16 desechable (vacío y con datos),
  mismos recuentos de filas que el original. _Pendiente de repetir en el PC de prod._

## 7. Operación diaria / troubleshooting

- Estado: `docker compose -f docker-compose.prod.yml ps`
- Logs: `docker compose -f docker-compose.prod.yml logs -f backend`
- Actualizar a una versión nueva:
  `git pull && docker compose -f docker-compose.prod.yml up -d --build`
- Si un móvil no accede: comprobar VPN activa (`tailscale status` en el móvil), MagicDNS on,
  y que el PC está «online» en la consola.
- Revocar acceso de un dispositivo perdido: quitarlo desde el admin panel de Tailscale.
  (Para revocar solo la captura por atajo, revocar su token en `/tokens`.)
