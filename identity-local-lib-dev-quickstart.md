# Quickstart — lib local en vivo (`identity-verification-lib`)

> Versión corta. Para el detalle, el "por qué" y troubleshooting → [guía completa](local-lib-dev.md).
> Repos como hermanos: `ib-identity-verification-journey/` y `ib-mx-identity-verification-lib/`.
> Comandos desde la raíz del API. (NestJS 10 → cualquier Node moderno sirve.)

## Setup (una vez, al clonar o cambiar de feature)

```bash
# Lib (repo hermano): compilar dist
cd ../ib-mx-identity-verification-lib && npm install && npm run build:lib && cd -

# API: instalar + deps transitivas + junction a la lib local
npm install
npm run build              # corre install-country-library.sh (deps transitivas)
npm run link:local-lib     # crea el junction (DEBE ir después del build)

# Verificar
node -e "console.log(require('fs').lstatSync('node_modules/identity-verification-lib').isSymbolicLink() ? 'JUNCTION ✓' : 'carpeta real ✗')"
```
`carpeta real ✗` → repite `npm run link:local-lib`.

## Día a día

**Terminal A — watch de la lib:**
```bash
cd ../ib-mx-identity-verification-lib
npx tsc -p libs/ib-mx-identity-verification-lib/tsconfig.lib.json --watch --watchFile priorityPollingInterval --watchDirectory dynamicPriorityPolling
```

**Terminal B — arrancar el API:**
- WebStorm (debug): config `start` con `NODE_OPTIONS=--preserve-symlinks` en *Environment*.
- Terminal: `npm run start:local-lib`

**Al cambiar la lib:** editas `.ts` → Terminal A dice `Found 0 errors` → **reinicias el API** → pruebas.

## Volver a modo prod
```bash
sh install-country-library.sh    # restaura la versión publicada
```

## Si algo falla (rápido)
| Síntoma | Fix |
|---|---|
| `Cannot find module 'identity-verification-lib'` | `npm run link:local-lib` |
| `carpeta real ✗` / no toma cambios | `npm run link:local-lib` + reinicia API |
| `Cannot find module 'uuid'` u otra dep | falta `--preserve-symlinks` (Environment / `start:local-lib`) |
| `TS2345 ... is missing` | `"preserveSymlinks": true` en `tsconfig.json` |
| `spawn EINVAL` | el `start-local-lib.js` necesita `shell: true` |
