# Guía: desarrollo local de `enrollment-library` en vivo (Mac + Windows)

Esta guía documenta cómo tomar cambios de la lib `ib-mx-enrollment-lib` directamente en el API `ib-enrollment-journey` sin publicar versiones, reflejándolos con un `tsc` + reinicio, en lugar del ciclo viejo de `build:lib` + `cp -r`.

A diferencia de la versión anterior (que usaba `mklink` manual desde cmd.exe y `setx`), este flujo está **automatizado con scripts de Node** que funcionan igual en **Mac y Windows (Git Bash)**, sin footguns de shell.

Aplica a:
- **API**: `ib-enrollment-journey`
- **Lib MX**: `ib-mx-enrollment-lib` (consumida en el API con el alias `enrollment-library`)

Ambos repos como carpetas hermanas:
```
.../journey/
├── ib-enrollment-journey/
└── ib-mx-enrollment-lib/
```
(en Windows típicamente bajo `C:\software\journey\`)

---

## ⚠️ Requisito crítico: versión de Node

El proyecto usa **NestJS 11**, y `@nestjs/cli` v11 hace `require()` de un módulo **ESM** (`@inquirer/prompts`). `require()` de ESM **solo existe desde Node 20.17+**. Con Node más viejo (p.ej. 20.11 o 18) `nest start` truena con:

```
Error [ERR_REQUIRE_ESM]: require() of ES Module ...@inquirer/prompts...
```

**Por eso necesitas Node ≥ 20.17 en local** (recomendado: **20.19.x LTS**).

> Esto NO cambia prod: `@nestjs/cli` es tooling de dev. El JS compilado se sigue desplegando en Node 18, y NestJS 11 corre en Node 18 en runtime. Solo tu máquina necesita el Node nuevo para correr el CLI.

### Instalar Node ≥ 20.17 sin permisos de administrador (Windows)

`nvm-windows` requiere admin (crea symlinks en `C:\Program Files`). Si no tienes admin, usa el **zip portable**:

1. Baja `node-v20.19.0-win-x64.zip` de https://nodejs.org/dist/v20.19.0/
2. Extrae en tu carpeta de usuario, p.ej. `C:\Users\<usuario>\node20\node-v20.19.0-win-x64\`
3. En Git Bash, agrégalo **al frente** del PATH en `~/.bashrc` (gana sobre el Node del sistema):
   ```bash
   echo 'export PATH="/c/Users/<usuario>/node20/node-v20.19.0-win-x64:$PATH"' >> ~/.bashrc
   source ~/.bashrc
   node -v   # debe decir v20.19.0
   ```
   > El PATH del **sistema** (donde está `C:\Program Files\nodejs`) va antes que el del usuario, por eso editar variables de usuario en el panel de Windows NO basta para cmd. En Git Bash el `~/.bashrc` sí gana porque corre después.
4. En **WebStorm**: *Run → Edit Configurations → Node interpreter → `...` → Add* → apunta a
   `C:\Users\<usuario>\node20\node-v20.19.0-win-x64\node.exe`

(En Mac, usa nvm/fnm/Homebrew para tener Node ≥ 20.17.)

---

## Setup del repo (una sola vez, lo commitea quien lo configura)

Estos cambios van a `package.json` / archivos versionados; el resto del equipo solo hace `git pull` + `npm install`.

### 1. Script para crear el junction/symlink — `scripts/link-local-lib.js`

```js
#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');

const apiDir = path.resolve(__dirname, '..');
// Ajusta si el folder interno de la lib cambia:
const libDist = path.resolve(apiDir, '../ib-mx-enrollment-lib/dist/libs/ib-mx-enrollment-lib');
const link = path.join(apiDir, 'node_modules', 'enrollment-library');

if (!fs.existsSync(libDist)) {
  console.error(`[ERROR] No existe el dist de la lib:\n        ${libDist}`);
  console.error('        Corre  npm run build:lib  en la lib primero.');
  process.exit(1);
}

try {
  const st = fs.lstatSync(link);
  if (st.isSymbolicLink() || st.isFile()) fs.unlinkSync(link);
  else fs.rmSync(link, { recursive: true, force: true });
  console.log('Quitado enrollment-library anterior.');
} catch (e) {
  if (e.code !== 'ENOENT') throw e;
}

// 'junction' -> junction en Windows (sin admin), symlink normal en Mac/Linux
fs.symlinkSync(libDist, link, 'junction');
console.log(`\n[OK] enrollment-library -> ${libDist}`);
```

### 2. Script para arrancar con `--preserve-symlinks` — `scripts/start-local-lib.js`

```js
#!/usr/bin/env node
'use strict';
const { spawn } = require('child_process');

const opts = (process.env.NODE_OPTIONS || '').split(' ').filter(Boolean);
if (!opts.includes('--preserve-symlinks')) opts.push('--preserve-symlinks');
process.env.NODE_OPTIONS = opts.join(' ');

// shell: true es OBLIGATORIO en Windows: desde Node 20.12+ spawn() de un
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

### 3. npm scripts en `package.json` del API

```json
"scripts": {
  "start": "nest start",
  "link:local-lib": "node scripts/link-local-lib.js",
  "start:local-lib": "node scripts/start-local-lib.js"
}
```
(No borres ni dupliques tu `start` actual; los otros lo usan por debajo.)

### 4. `tsconfig.json` del API → `preserveSymlinks`

`NODE_OPTIONS=--preserve-symlinks` arregla el **runtime**, pero el error de compilación
`TS2345 ... Property 'MULTI_STATUS' is missing` (deps duplicadas de `@nestjs/common`) es de
**tsc**, y `NODE_OPTIONS` no afecta a tsc. Hay que poner el gemelo:

```json
{
  "compilerOptions": {
    "preserveSymlinks": true
  }
}
```
Es seguro para prod: sin symlinks presentes es un no-op.

---

## Setup por desarrollador (al clonar o cambiar de feature)

```bash
# 1. Lib: instalar y compilar dist
cd .../journey/ib-mx-enrollment-lib
npm install
npm run build:lib

# 2. API: instalar (trae los scripts) + deps transitivas + compilar
cd .../journey/ib-enrollment-journey
npm install
npm run build     # corre install-country-library.sh (deps transitivas) + nest build
                  # alternativa más ligera si solo quieres las deps: sh install-country-library.sh

# 3. Crear el junction a la lib local (DEBE ir después del build)
npm run link:local-lib

# 4. Verificar que es junction (no carpeta real)
node -e "console.log(require('fs').lstatSync('node_modules/enrollment-library').isSymbolicLink() ? 'JUNCTION ✓' : 'carpeta real ✗')"
```

> `sh install-country-library.sh` (o `npm run build`, que lo llama) **sobrescribe** el junction con la versión publicada. Si lo corres, vuelve a hacer `npm run link:local-lib`.

---

## Workflow diario

### Terminal A — watch de la lib

```bash
cd .../journey/ib-mx-enrollment-lib
npx tsc -p libs/ib-mx-enrollment-lib/tsconfig.lib.json --watch --watchFile priorityPollingInterval --watchDirectory dynamicPriorityPolling
```
Las flags de polling son necesarias en Windows/IDEs para que el watcher cache el guardado atómico. Espera el `Found 0 errors. Watching for file changes.`

### Terminal B — arrancar el API

| Dónde arrancas | Cómo | Por qué |
|---|---|---|
| **WebStorm (recomendado para debug)** | tu config `start` (Run/Debug) | ya tiene el interpreter 20.19 + `NODE_OPTIONS=--preserve-symlinks` en **Environment**. Cadena de procesos corta → arranca más rápido y el debugger engancha directo. |
| **Terminal (Git Bash / Mac)** | `npm run start:local-lib` | inyecta `--preserve-symlinks` sin el campo Environment de WebStorm. (Node ≥20.17 viene de tu `.bashrc`/nvm.) |

> **No uses `start:local-lib` dentro de WebStorm**: agrega capas de proceso (wrapper → npm → shell → nest) que lo hacen más lento y complican el debugger. En WebStorm el `start` plano ya trae todo.

### Configuración de WebStorm (una vez)

En tu run config tipo *npm script* apuntando a `start`:
- **Node interpreter** → el `node.exe` de la 20.19 (paso de requisitos).
- **Environment** → `NODE_OPTIONS=--preserve-symlinks`.

### Cuando cambias algo en la lib

1. Editas un `.ts` en `libs/ib-mx-enrollment-lib/src/...` y guardas.
2. Terminal A muestra `File change detected ... Found 0 errors.`
3. tsc actualizó el `dist/.../*.js`; como el junction apunta al `dist/`, ya está disponible.
4. **Reinicia el API** (Node cachea módulos al arrancar; no hay hot-reload del módulo sin reinicio).
5. Pruebas el cambio.

---

## Volver a modo prod (lib publicada)

```bash
cd .../journey/ib-enrollment-journey
sh install-country-library.sh    # reescribe enrollment-library con la versión del registry
```
Para volver a dev: `npm run build:lib` (en la lib, si hace falta) + `npm run link:local-lib`.

---

## Troubleshooting (todo lo que nos pasó)

| Síntoma | Causa | Solución |
|---|---|---|
| `Error [ERR_REQUIRE_ESM] ... @inquirer/prompts` al arrancar | Node < 20.17 con `@nestjs/cli` v11 | Sube tu Node local a ≥20.17 (20.19 LTS). Ver "Requisito crítico". |
| `Error: spawn EINVAL` en `start-local-lib.js` | Node 20.12+ no deja `spawn()` de `.cmd` sin shell | El script ya usa `shell: true`. Si lo copiaste viejo, agrégalo. |
| `node -v` en terminal sigue dando la versión vieja | El Node del sistema gana en el PATH | Prepende el Node nuevo en `~/.bashrc` y reabre Git Bash. |
| `TS2345 ... Property 'MULTI_STATUS' is missing` | `@nestjs/common` duplicado: tsc resuelve por la carpeta real del link | `"preserveSymlinks": true` en el `tsconfig.json` del API. |
| `Cannot find module 'uuid'` (o similar) al arrancar | Falta `--preserve-symlinks` en runtime | WebStorm: Environment `NODE_OPTIONS=--preserve-symlinks`. Terminal: usa `start:local-lib`. |
| `Cannot find module 'enrollment-library'` | Junction roto / no creado | `npm run link:local-lib` y verifica con el `node -e ...isSymbolicLink()`. |
| `ls -la node_modules/enrollment-library` muestra **contenido**, no la flecha | Normal: `ls` sigue el link y lista el destino | Usa `ls -lad node_modules/enrollment-library` o el `node -e ...isSymbolicLink()`. |
| `enrollment-library` aparece como `drwxr-xr-x` (carpeta real) | Es la copia publicada, no el junction (lo sobrescribió `install-country-library.sh`) | `npm run link:local-lib`. |
| Doble `//` al final de la ruta en `ls` | Cosmético: `ls --classify` agrega `/` al destino-directorio | Ignóralo; el junction está bien (mira el `[OK]` del script). |
| `rm -rf node_modules/enrollment-library` borró el dist de la lib | `rm -rf` de Git Bash recursa dentro del junction | **Nunca** uses `rm -rf` sobre el junction. Usa `node -e "require('fs').unlinkSync('node_modules/enrollment-library')"`. |
| `rm -rf node_modules` tarda muchísimo | Normal en Windows + Git Bash | Cancela y usa cmd `rd /s /q node_modules`, o deja que `npm ci`/`npm install` lo limpie. |

---

## Por qué cada pieza

| Pieza | Para qué |
|---|---|
| Node ≥ 20.17 local | `@nestjs/cli` v11 hace `require()` de ESM, soportado solo desde 20.17 |
| `install-country-library.sh` | Poblar `node_modules/` del API con las deps transitivas que la lib usa pero no declara |
| `link:local-lib` (junction) | Conectar `node_modules/enrollment-library` directo al `dist/` de la lib, sin copiar |
| `NODE_OPTIONS=--preserve-symlinks` | Que Node resuelva las deps del código de la lib por el `node_modules` del API (donde están las transitivas) |
| `preserveSymlinks: true` en tsconfig | El gemelo de lo anterior para **tsc** (si no, deps duplicadas → TS2345) |
| `start-local-lib.js` con `shell:true` | Inyectar `--preserve-symlinks` desde terminal, cross-platform y sin EINVAL |

---

## Nota sobre el alias y por qué junction (no yalc)

El API consume la lib con un **alias de npm** (`"enrollment-library": "npm:ib-mx-enrollment-lib@..."`).
yalc trabaja con el **nombre real** del paquete (`ib-mx-enrollment-lib`), así que con yalc igual harías
falta un junction para el alias → tendrías la complejidad de yalc **más** el junction. Por eso aquí el
**junction puro gana**: el link lo nombras tú directamente `enrollment-library` y el alias se resuelve solo.
