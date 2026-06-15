# Desarrollo local de `identity-verification-lib` en vivo (Mac + Windows)

Cómo tomar cambios de la lib `ib-mx-identity-verification-lib` directamente en este API
(`ib-identity-verification-journey`) sin publicar versiones, reflejándolos con un `tsc` +
reinicio, en lugar del ciclo viejo de `build:lib` + `cp -r`.

El flujo está **automatizado con scripts de Node** (`scripts/link-local-lib.js` y
`scripts/start-local-lib.js`) que funcionan igual en **Mac y Windows (Git Bash)**, sin
footguns de shell.

> Todos los comandos se corren **desde la raíz de este repo**, salvo que se indique lo
> contrario. La lib se asume clonada como **repo hermano**:
>
> ```
> <carpeta-padre>/
> ├── ib-identity-verification-journey/   ← este repo
> └── ib-mx-identity-verification-lib/     ← la lib (hermano)
> ```

El API consume la lib con el alias `identity-verification-lib`
(`"identity-verification-lib": "npm:@debug/ib-mx-identity-verification-lib@..."` en `package.json`).

---

## Versión de Node

Este proyecto es **NestJS 10** (`@nestjs/cli` v10), que usa el `inquirer` viejo (CJS).
**No requiere** Node ≥20.17 — funciona en **Node 18, 20.x**, etc. Cualquier Node moderno sirve.

> (A diferencia del journey de enrollment, que es NestJS 11 y sí exige Node ≥20.17. Aquí no.)

---

## Setup del repo (una sola vez, lo commitea quien lo configura)

Estas piezas van a archivos versionados; el resto del equipo solo hace `git pull` + `npm install`.

### 1. `scripts/link-local-lib.js`

```js
#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');

const apiDir = path.resolve(__dirname, '..');
// Ajusta si el folder interno de la lib cambia:
const libDist = path.resolve(apiDir, '../ib-mx-identity-verification-lib/dist/libs/ib-mx-identity-verification-lib');
const link = path.join(apiDir, 'node_modules', 'identity-verification-lib');

if (!fs.existsSync(libDist)) {
  console.error(`[ERROR] No existe el dist de la lib:\n        ${libDist}`);
  console.error('        Corre  npm run build:lib  en la lib primero.');
  process.exit(1);
}

try {
  const st = fs.lstatSync(link);
  if (st.isSymbolicLink() || st.isFile()) fs.unlinkSync(link);
  else fs.rmSync(link, { recursive: true, force: true });
  console.log('Quitado identity-verification-lib anterior.');
} catch (e) {
  if (e.code !== 'ENOENT') throw e;
}

// 'junction' -> junction en Windows (sin admin), symlink normal en Mac/Linux
fs.symlinkSync(libDist, link, 'junction');
console.log(`\n[OK] identity-verification-lib -> ${libDist}`);
```

### 2. `scripts/start-local-lib.js`

(Idéntico al de otros journeys: solo inyecta `--preserve-symlinks` y arranca el `start`.)

```js
#!/usr/bin/env node
'use strict';
const { spawn } = require('child_process');

const opts = (process.env.NODE_OPTIONS || '').split(' ').filter(Boolean);
if (!opts.includes('--preserve-symlinks')) opts.push('--preserve-symlinks');
process.env.NODE_OPTIONS = opts.join(' ');

// shell: true es OBLIGATORIO en Windows: desde Node 20.12+ (y 18.20.2+) spawn() de un
// .cmd/.bat sin shell lanza EINVAL (fix de seguridad CVE-2024-27980).
const child = spawn('npm', ['run', 'start'], {
  stdio: 'inherit',
  env: process.env,
  shell: true,
});
child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  else process.exit(code == null ? 0 : code);
});
```

### 3. `package.json` (scripts)

```json
"scripts": {
  "start": "nest start",
  "link:local-lib": "node scripts/link-local-lib.js",
  "start:local-lib": "node scripts/start-local-lib.js"
}
```
(No borres ni dupliques tu `start` actual; los otros lo usan por debajo.)

### 4. `tsconfig.json` → `preserveSymlinks`

`NODE_OPTIONS=--preserve-symlinks` arregla el **runtime**, pero si la lib trae su propia copia de
algún paquete (`@nestjs/common`, etc.), tsc puede resolver por la carpeta real del link y dar
errores de tipos duplicados (`TS2345 ...`). El gemelo para tsc:

```json
{
  "compilerOptions": {
    "preserveSymlinks": true
  }
}
```

**¿Afecta producción? No.** Razones:
- Es un flag de **resolución en tiempo de compilación** (tsc). En prod corres `node dist/main.js`,
  que **no lee tsconfig** — la resolución en runtime la gobierna Node + `NODE_OPTIONS`.
- **No cambia el JS emitido**: solo afecta *qué archivo lee* el compilador, no el código de salida.
- En prod/CI instalas con `npm install`/`npm ci` (carpetas reales, **sin symlinks**) → resolución
  idéntica con o sin el flag. El junction solo existe en tu máquina local.

> Único caso donde importaría (no es el nuestro): build de prod con symlinks (**pnpm** o
> **workspaces**). Aquí usamos npm plano + `install-country-library.sh` (que **copia**), así que no aplica.

---

## Setup por desarrollador (al clonar o cambiar de feature)

```bash
# 1. Lib (repo hermano): instalar y compilar dist
cd ../ib-mx-identity-verification-lib
npm install
npm run build:lib
cd -    # de vuelta a este repo

# 2. API (este repo): instalar + deps transitivas + compilar
npm install
npm run build     # cadena: pre-build -> install-country-dependency -> sh install-country-library.sh
                  #         (puebla deps transitivas) + nest build + copyfiles
                  # alternativa más ligera, solo deps: npm run install-country-dependency

# 3. Crear el junction a la lib local (DEBE ir después del build)
npm run link:local-lib

# 4. Verificar que es junction (no carpeta real)
node -e "console.log(require('fs').lstatSync('node_modules/identity-verification-lib').isSymbolicLink() ? 'JUNCTION ✓' : 'carpeta real ✗')"
```

Interpretación del paso 4:
- **`JUNCTION ✓`** → apunta a tu lib local. Listo, sigue al workflow diario.
- **`carpeta real ✗`** → es la copia publicada, no el junction (típicamente porque algo volvió a
  correr `install-country-library.sh` después del link). **Solución:** vuelve a correr
  `npm run link:local-lib` y verifica de nuevo.

> El `npm run build` (vía `pre-build → install-country-dependency`) corre
> `sh install-country-library.sh`, que **sobrescribe** el junction con la versión publicada. Por eso
> `link:local-lib` **debe ir después**. Si vuelves a correr el build o el install-country, repite el
> `npm run link:local-lib`.

---

## Workflow diario

### Terminal A — watch de la lib (repo hermano)

```bash
cd ../ib-mx-identity-verification-lib
npx tsc -p libs/ib-mx-identity-verification-lib/tsconfig.lib.json --watch --watchFile priorityPollingInterval --watchDirectory dynamicPriorityPolling
```
Las flags de polling son necesarias en Windows/IDEs para que el watcher cache el guardado atómico.
Espera el `Found 0 errors. Watching for file changes.`

### Terminal B — arrancar el API

| Dónde arrancas | Cómo | Por qué |
|---|---|---|
| **WebStorm (recomendado para debug)** | tu config `start` (Run/Debug) | pon `NODE_OPTIONS=--preserve-symlinks` en **Environment**. Cadena de procesos corta → arranca más rápido y el debugger engancha directo. |
| **Terminal (Git Bash / Mac)** | `npm run start:local-lib` | inyecta `--preserve-symlinks` sin el campo Environment de WebStorm. |

> **No uses `start:local-lib` dentro de WebStorm**: agrega capas de proceso
> (wrapper → npm → shell → nest) que lo hacen más lento y complican el debugger. En WebStorm el
> `start` plano con el Environment ya trae todo.

### Configuración de WebStorm (una vez)

En tu run config tipo *npm script* apuntando a `start`:
- **Environment** → `NODE_OPTIONS=--preserve-symlinks`.

### Cuando cambias algo en la lib

1. Editas un `.ts` en `../ib-mx-identity-verification-lib/libs/.../src/...` y guardas.
2. Terminal A muestra `File change detected ... Found 0 errors.`
3. tsc actualizó el `dist/.../*.js`; como el junction apunta al `dist/`, ya está disponible.
4. **Reinicia el API** (Node cachea módulos al arrancar; no hay hot-reload del módulo sin reinicio).
5. Pruebas el cambio.

---

## Volver a modo prod (lib publicada)

```bash
sh install-country-library.sh    # reescribe identity-verification-lib con la versión del registry
                                  # (equivale a: npm run install-country-dependency)
```
Para volver a dev: `npm run build:lib` (en la lib, si hace falta) + `npm run link:local-lib`.

---

## Troubleshooting

| Síntoma | Causa | Solución |
|---|---|---|
| `Error: spawn EINVAL` en `start-local-lib.js` | Node 20.12+ / 18.20.2+ no deja `spawn()` de `.cmd` sin shell | El script usa `shell: true`. Si lo copiaste viejo, agrégalo. |
| `TS2345 ... Property '...' is missing` (tipos duplicados) | La lib trae su propia copia de un paquete; tsc resuelve por la carpeta real del link | `"preserveSymlinks": true` en `tsconfig.json`. |
| `Cannot find module 'uuid'` (o similar) al arrancar | Falta `--preserve-symlinks` en runtime | WebStorm: Environment `NODE_OPTIONS=--preserve-symlinks`. Terminal: usa `start:local-lib`. |
| `Cannot find module 'identity-verification-lib'` | Junction roto / no creado | `npm run link:local-lib` y verifica con el `node -e ...isSymbolicLink()`. |
| `ls -la node_modules/identity-verification-lib` muestra **contenido**, no la flecha | Normal: `ls` sigue el link y lista el destino | Usa `ls -lad node_modules/identity-verification-lib` o el `node -e ...isSymbolicLink()`. |
| `identity-verification-lib` aparece como `drwxr-xr-x` (carpeta real) | Es la copia publicada, no el junction (lo sobrescribió `install-country-library.sh`) | `npm run link:local-lib`. |
| Doble `//` al final de la ruta en `ls` | Cosmético: `ls --classify` agrega `/` al destino-directorio | Ignóralo; el junction está bien (mira el `[OK]` del script). |
| `rm -rf node_modules/identity-verification-lib` borró el dist de la lib | `rm -rf` de Git Bash recursa dentro del junction | **Nunca** uses `rm -rf` sobre el junction. Usa `node -e "require('fs').unlinkSync('node_modules/identity-verification-lib')"`. |
| `rm -rf node_modules` tarda muchísimo | Normal en Windows + Git Bash | Cancela y usa cmd `rd /s /q node_modules`, o deja que `npm ci`/`npm install` lo limpie. |

---

## Por qué cada pieza

| Pieza | Para qué |
|---|---|
| `install-country-library.sh` (vía `npm run build`/`install-country-dependency`) | Poblar `node_modules/` del API con las deps transitivas que la lib usa pero no declara |
| `link:local-lib` (junction) | Conectar `node_modules/identity-verification-lib` directo al `dist/` de la lib, sin copiar |
| `NODE_OPTIONS=--preserve-symlinks` | Que Node resuelva las deps del código de la lib por el `node_modules` del API (donde están las transitivas) |
| `preserveSymlinks: true` en tsconfig | El gemelo de lo anterior para **tsc** (evita tipos duplicados) |
| `start-local-lib.js` con `shell:true` | Inyectar `--preserve-symlinks` desde terminal, cross-platform y sin EINVAL |

---

## Por qué junction y no yalc

El API consume la lib con un **alias de npm**
(`"identity-verification-lib": "npm:@debug/ib-mx-identity-verification-lib@..."`).
yalc trabaja con el **nombre real** del paquete (`@debug/ib-mx-identity-verification-lib`), así que
con yalc igual haría falta un junction para el alias → tendrías la complejidad de yalc **más** el
junction. Por eso el **junction puro gana**: el link lo nombras tú directamente
`identity-verification-lib` y el alias se resuelve solo.
