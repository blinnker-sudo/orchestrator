# Guía Windows: desarrollo local de `bcs-library` con cambios en vivo

Esta guía documenta el proceso para tomar cambios de la lib `ib-mx-client-validation-lib` directamente en el API `ib-client-validation-journey` sin tener que publicar versiones nuevas, y reflejarlos con un solo `tsc` + reinicio de proceso, en lugar del ciclo viejo de `build:lib` + `cp -r` + reinicio.

Aplica a:
- **API**: `C:\software\journey\ib-client-validation-journey`
- **Lib MX**: `C:\software\journey\ib-mx-client-validation-lib`

Está pensada para Windows + Git Bash + WebStorm. Una guía equivalente para Mac queda pendiente.

## Requisitos previos

- Ambos repos clonados como carpetas hermanas dentro de `C:\software\journey\`.
- **Node 22+** instalado (a partir de la migración a NestJS 11; antes era Node 18+).
- Git Bash (incluido con Git for Windows).
- Acceso a cmd.exe estándar (Win + R → `cmd`).
- WebStorm (opcional, también funciona desde terminal).

Verifica tu versión de Node:
```bash
node --version
```

## Setup inicial (solo la primera vez, o cuando cambias de feature/branch)

### 1. Asegúrate que las dependencias de ambos repos estén instaladas

```bash
# Lib
cd C:\software\journey\ib-mx-client-validation-lib
npm install

# API
cd C:\software\journey\ib-client-validation-journey
npm install
```

> ⚠️ **Si el `npm install` del API falla con error de peer dependency** (típicamente `@nestjs/axios ^4.0.1` después de la migración a NestJS 11), usa:
> ```bash
> npm install --legacy-peer-deps
> ```
> Para no tener que escribirlo cada vez, agrega o crea un `.npmrc` en la raíz del API con:
> ```
> legacy-peer-deps=true
> ```

### 2. Compila la lib una vez para generar `dist/`

```bash
cd C:\software\journey\ib-mx-client-validation-lib
npm run build:lib
```

Tarda ~30 segundos (corre openapi-generator, update-version, tsc, copyfiles). Después de esto debe existir:

```
C:\software\journey\ib-mx-client-validation-lib\dist\libs\ib-mx-customer-validation-bcs-lib\
├── index.js
├── package.json
├── journey/
└── (otros)
```

> ⚠️ **Verifica que `index.js` esté en la raíz del dist, NO bajo `src/`.** Si después de `build:lib` ves la estructura con `dist/libs/.../src/index.js` (todo bajo `src/`), es un bug conocido relacionado con la versión de TypeScript. Ve a la sección [Issues conocidos → Dist con estructura `src/`](#dist-con-estructura-src-extra-cannot-find-module-bcs-library) más abajo para el fix antes de continuar.

### 3. Instala la versión publicada en el API (para tener TODAS las deps transitivas)

Esto es **crítico**. La razón: el `package.json` que se publica en la lib no declara dependencias propias, así que un `npm install file:` no instala nada. Pero la versión publicada en el registry sí "trae" deps como `uuid`, `ib-country-event-tracing-lib`, etc., porque están bundleadas o porque el publish las incluye. Las necesitamos en `node_modules` del API.

```bash
cd C:\software\journey\ib-client-validation-journey
sh install-country-library.sh
```

Verifica desde Git Bash:

```bash
ls node_modules/uuid                          # debe existir
ls node_modules/ib-country-event-tracing-lib  # debe existir
```

### 4. Reemplaza `node_modules\bcs-library` con un junction al `dist/` de la lib

**Importante**: este paso **debe hacerse desde cmd.exe**, no desde Git Bash. Git Bash hace traducción rara de los argumentos de `mklink` y termina creando la junction con un espacio al final del nombre o con la ruta destino mal resuelta.

Abre cmd.exe (Win + R → `cmd` → Enter) y corre, una línea a la vez:

```cmd
cd /d C:\software\journey\ib-client-validation-journey
rmdir /s /q node_modules\bcs-library
mklink /J node_modules\bcs-library C:\software\journey\ib-mx-client-validation-lib\dist\libs\ib-mx-customer-validation-bcs-lib
```

Salida esperada:

```
Vínculo de unión creado para node_modules\bcs-library <<===>> C:\software\journey\ib-mx-client-validation-lib\dist\libs\ib-mx-customer-validation-bcs-lib
```

**Usa siempre ruta absoluta**. Las rutas relativas con `..\..\` se resuelven respecto al cwd actual (no respecto al folder donde queda el link), y eso confunde a Windows.

> 💡 **Alternativa**: usa el script `link-lib-dev.cmd` (si lo tienes en el repo) que automatiza estos 3 pasos con validaciones:
> ```cmd
> link-lib-dev.cmd ^
>   C:\software\journey\ib-client-validation-journey ^
>   C:\software\journey\ib-mx-client-validation-lib\dist\libs\ib-mx-customer-validation-bcs-lib ^
>   bcs-library
> ```

### 5. Verifica que el junction funciona

Desde Git Bash:

```bash
cd C:\software\journey\ib-client-validation-journey
ls -la node_modules/bcs-library
```

Debes ver:

```
lrwxrwxrwx ... bcs-library -> /c/software/journey/ib-mx-client-validation-lib/dist/libs/ib-mx-customer-validation-bcs-lib
```

Y:

```bash
cat node_modules/bcs-library/journey/step-factory.js | head -20
```

Debe mostrar el contenido del archivo compilado de tu lib (NO el de la versión publicada).

### 6. Configura `NODE_OPTIONS=--preserve-symlinks`

Sin este flag, Node resuelve el symlink a su ruta real (el dist de la lib) y busca las dependencias transitivas en el `node_modules` del repo de la lib, donde no están todas. Con el flag, Node trata el symlink como si fuera el folder real, y la búsqueda de dependencias pasa por el `node_modules` del API, donde sí están todas (gracias al paso 3).

#### Opción A: permanente para todo el sistema (recomendado)

Desde **cmd.exe**:

```cmd
setx NODE_OPTIONS "--preserve-symlinks"
```

Cierra y vuelve a abrir cmd / Git Bash / WebStorm para que tome efecto.

> ⚠️ `setx` solo afecta procesos **nuevos**. Si WebStorm o alguna terminal quedó abierta antes del `setx`, esa instancia NO va a tener la variable. Hay que cerrar todo completo (revisar Task Manager si hace falta) y reabrir.

Verifica:

```bash
echo $NODE_OPTIONS    # Git Bash
```
o
```cmd
echo %NODE_OPTIONS%   # cmd
```

Debe imprimir `--preserve-symlinks`.

#### Opción B: solo para esta sesión de Git Bash

```bash
export NODE_OPTIONS=--preserve-symlinks
```

#### Opción C: solo en WebStorm

**Run → Edit Configurations** → tu config de arranque del API:
- Si es tipo **npm script**: en "Environment", agrega `NODE_OPTIONS=--preserve-symlinks`.
- Si es tipo **Node.js**: en "Node parameters", pon `--preserve-symlinks`.

> Consideración: `--preserve-symlinks` afecta a todos los procesos Node que arranques mientras esté activo. Es seguro para la mayoría de proyectos, pero si alguno te falla raro después de activarlo, quítalo (`setx NODE_OPTIONS ""` y reabre la terminal).

## Workflow diario

### Una sola vez al arrancar la jornada

#### Terminal A — `tsc --watch` en la lib

```bash
cd C:\software\journey\ib-mx-client-validation-lib
npx tsc -p libs/ib-mx-customer-validation-bcs-lib/tsconfig.lib.json --watch --watchFile priorityPollingInterval --watchDirectory dynamicPriorityPolling
```

Las flags `--watchFile priorityPollingInterval --watchDirectory dynamicPriorityPolling` activan el polling, necesario en Windows + Git Bash + VSCode/WebStorm para que el watcher detecte los cambios (sin polling, el guardado atómico de los IDEs se le escapa).

Espera a que imprima:

```
Found 0 errors. Watching for file changes.
```

> 💡 El watch SOLO es necesario mientras desarrollas activamente la lib. El junction y el API funcionan sin el watch corriendo — el watch solo automatiza el "recompilar `dist/` cuando guardas un `.ts`".

#### Terminal B — API (o WebStorm)

Si arrancas desde Git Bash:

```bash
cd C:\software\journey\ib-client-validation-journey
npm run start
```

Si arrancas desde WebStorm: solo dale Run a tu config (que ya tiene `NODE_OPTIONS=--preserve-symlinks` por la Opción C de arriba).

### Cuando haces un cambio en la lib

1. Editas un `.ts` en `libs/ib-mx-customer-validation-bcs-lib/src/...` y guardas.
2. En la Terminal A (la del watch) deberías ver:
   ```
   File change detected. Starting incremental compilation...
   Found 0 errors. Watching for file changes.
   ```
3. tsc actualizó el `dist/.../<tu-archivo>.js` correspondiente. Como `node_modules\bcs-library` del API es junction al `dist/`, los cambios ya están "ahí" para el API.
4. **Reinicia el API** (Ctrl+C en la terminal del API + volver a `npm run start`, o el botón Restart de WebStorm). Esto es necesario porque Node cachea los módulos en memoria al arrancar; cualquier cambio en disco no se aplica al proceso ya corriendo.
5. Prueba tu cambio (curl al endpoint, log en la consola, lo que sea).

### Verificación rápida si algo no se refleja

| Síntoma | Posible causa | Verifica |
|---|---|---|
| Watch no detecta nada | Falta polling | El comando incluye `--watchFile priorityPollingInterval` |
| Watch detecta pero archivo `.js` no se actualiza | Estás revisando el archivo equivocado | Si modificaste `src/journey/foo.ts`, mira `dist/.../journey/foo.js`, NO `index.js` |
| Archivo se actualiza, API no toma el cambio | API no se reinició | Mata el proceso y vuelve a arrancarlo |
| `Cannot find module 'X'` al arrancar | Falta `--preserve-symlinks` | `echo $NODE_OPTIONS` debe decir `--preserve-symlinks` |
| `Cannot find module 'bcs-library'` al arrancar | Junction roto o `dist/` con estructura mala | `ls -la node_modules/bcs-library` debe mostrar la flecha al dist correcto Y `ls dist/libs/.../` debe tener `index.js` en la raíz |
| `npm install` falla con peer deps | Migración NestJS 11 + axios 4 | Usa `--legacy-peer-deps` o agrega `.npmrc` |

## Cómo limpiar y empezar de cero

Si algo se rompe y prefieres empezar fresco, sigue este orden estricto desde **cmd.exe** (no necesitas permisos de administrador):

### 1. Cierra TODO lo que use Node

- WebStorm cerrado completo. Verifica desde Task Manager (Ctrl+Shift+Esc) que no quede `webstorm.exe`, `webstorm64.exe`, ni `java.exe` colgado del IDE. Si quedan, click derecho → "End task".
- Mata cualquier `node` corriendo. Desde cmd:
  ```cmd
  taskkill /F /IM node.exe
  ```
  Si te dice "Acceso denegado", abre Task Manager y termina los procesos `node.exe` manualmente desde ahí (no requiere admin para matar procesos que iniciaste tú).
- Cierra la terminal del `tsc --watch` de la lib.

### 2. Borra el junction (no el dist real)

Desde cmd, parado en la raíz del API:

```cmd
cd /d C:\software\journey\ib-client-validation-journey

REM Verifica primero que sea junction (debe decir <JUNCTION>)
dir node_modules | findstr bcs-library

REM Bórralo
rmdir /s /q node_modules\bcs-library
```

> ⚠️ Usa `rmdir`, **no** `del` ni borrar desde el Explorador de archivos. Esos a veces siguen el junction y te borran el `dist/` real de la lib.

### 3. (Opcional) Limpia `node_modules` del API completo

```cmd
rmdir /s /q node_modules
```

> ⚠️ **NO borres `package-lock.json`.** Si lo borras, npm va a resolver el árbol de deps desde cero contra el registry y muy probablemente te tope con conflictos de peer deps (especialmente después de la migración a NestJS 11). Si por accidente lo borraste, recupéralo desde Git: `git checkout package-lock.json` y luego `npm ci`.

### 4. Verifica que el `dist` de la lib siga intacto

```cmd
dir C:\software\journey\ib-mx-client-validation-lib\dist\libs\ib-mx-customer-validation-bcs-lib
```

Debe existir y tener archivos. Si por accidente se borró, recompila con `npm run build:lib`.

### 5. Re-ejecuta los pasos 1-6 del Setup inicial

En este orden estricto:
1. `npm install` en la lib y en el API
2. `npm run build:lib` en la lib (verifica estructura del dist)
3. `sh install-country-library.sh` en el API
4. `mklink` desde cmd
5. Verifica junction
6. `NODE_OPTIONS` (si no estaba seteado)

## Volver al modo prod

Cuando quieras probar contra la versión publicada (por ejemplo antes de mergear, para validar que tu cambio también funciona con el tarball oficial):

```bash
cd C:\software\journey\ib-client-validation-journey
sh install-country-library.sh
```

Este comando reescribe `node_modules\bcs-library` con la versión publicada del registry (sustituye el junction por una copia normal). Para volver a modo dev, repite los pasos 3-5 del setup inicial.

## Por qué cada pieza es necesaria

| Paso | Para qué sirve |
|---|---|
| 1. `npm install` en lib y API | Tener las deps directas listas |
| 2. `npm run build:lib` inicial | Generar el `dist/` con `package.json` correcto y archivos copiados |
| 3. `sh install-country-library.sh` | Poblar `node_modules/` del API con TODAS las deps transitivas (uuid, etc) que la lib usa pero no declara explícitamente |
| 4. `mklink /J` | Conectar el `node_modules/bcs-library` del API directo al `dist/` local de la lib, sin copiar archivos |
| 5. Verificación | Confirmar que el junction está bien y apunta al dist correcto |
| 6. `NODE_OPTIONS=--preserve-symlinks` | Decirle a Node que cuando resuelva `require()` desde código de la lib, walk hacia arriba por el `node_modules` del API (donde están las deps transitivas), no por el de la lib (donde faltan) |

## Cuándo SÍ hay que correr `build:lib` completo

El watch con `tsc` solo recompila TypeScript. Si modificas alguna de estas cosas, necesitas el build completo:

- Contratos OpenAPI (regenera modelos): `npm run openapi-generator:local` en la lib.
- Versión en `package.json`: `npm run update-version`.
- Algo de `copy-files.sh` o assets no-TS.

En esos casos: `npm run build:lib` en la lib y luego reinicias el API. El junction sigue siendo válido porque apunta al folder, no a archivos individuales.

## Issues conocidos en Windows + Git Bash que ya documentamos

### `mklink` desde Git Bash crea el link con espacio al final
Síntoma: `ls -la` te muestra `bcs-library ` (con espacio) en lugar de `bcs-library`.
Solución: corre `mklink` siempre desde cmd.exe.

### Watch de tsc no detecta cambios
Síntoma: editas el archivo, guardas, y la terminal del watch nunca dice "File change detected".
Solución: usa las flags de polling: `--watchFile priorityPollingInterval --watchDirectory dynamicPriorityPolling`.

### `cat` no encuentra el archivo a través del symlink
Síntoma: `cat node_modules/bcs-library/journey/step-factory.js` dice "No such file or directory" pero `ls -la node_modules/bcs-library` muestra el symlink.
Solución: revisa la ruta destino del symlink con `ls -la`. Si apunta a un path equivocado (típicamente porque usaste rutas relativas con `..\..\`), borra y rehaz el junction con ruta absoluta desde cmd.

### API arranca pero da `Cannot find module 'uuid'` (o cualquier otra dep)
Síntoma: Node no encuentra dependencias transitivas que sí existen en `node_modules` del API.
Solución: activa `NODE_OPTIONS=--preserve-symlinks`.

### `npm install` del API falla con `unable to resolve dependency tree` / `peer @nestjs/axios@^4.0.1`
Síntoma: después de la migración a NestJS 11 en la lib, el API (que sigue en NestJS 10) no puede resolver las peer deps.
Solución temporal: `npm install --legacy-peer-deps`, o agregar `legacy-peer-deps=true` al `.npmrc` del API.
Solución real: migrar el API también a NestJS 11.

### Dist con estructura `src/` extra (`Cannot find module 'bcs-library'`)

**Síntoma**: después de `build:lib`, el dist queda así:
```
dist/libs/ib-mx-customer-validation-bcs-lib/
├── package.json   ← dice "main": "index.js"
├── src/           ← pero el index.js real está acá adentro
│   ├── index.js
│   ├── journey/
│   └── ...
```

Al levantar el API: `Cannot find module 'bcs-library'`, porque Node lee el `main: "index.js"` y busca en la raíz del dist, donde no está.

**Causa raíz**: alguna versión nueva de TypeScript (dentro del rango `^5.1.3` que tiene la lib) infiere `rootDir` distinto cuando no está declarado explícitamente. Esto típicamente aparece después de un `npm install` que regenera el `package-lock.json` con una versión más nueva de TS.

**Fix permanente**: editar `libs/ib-mx-customer-validation-bcs-lib/tsconfig.lib.json` y agregar `"rootDir": "./src"`:

```json
{
  "extends": "../../tsconfig.json",
  "compilerOptions": {
    "declarationMap": true,
    "declaration": true,
    "outDir": "../../dist/libs/ib-mx-customer-validation-bcs-lib",
    "rootDir": "./src"
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "test", "**/*spec.ts"]
}
```

Luego:
```bash
cd C:\software\journey\ib-mx-client-validation-lib
rm -rf dist
npm run build:lib
```

Verifica que ahora `index.js` esté en la raíz del dist:
```bash
ls dist/libs/ib-mx-customer-validation-bcs-lib/
```

Debe mostrar `index.js`, `package.json`, `journey/`, etc. — sin subcarpeta `src/`.

> 💡 Este fix idealmente debería ir en un PR a la lib (es una línea), porque le pasa a cualquiera que clone y compile después de la migración a NestJS 11.

**Workaround alternativo** (si no puedes tocar el tsconfig): editar manualmente el `package.json` del dist y cambiar `"main": "index.js"` por `"main": "src/index.js"`. Pero ese cambio se sobrescribe en cada `build:lib`, así que es solución temporal.

## Resumen de comandos para tener a la mano

**Setup completo (copia y pega):**

Git Bash:
```bash
cd C:\software\journey\ib-mx-client-validation-lib
npm install && npm run build:lib

cd C:\software\journey\ib-client-validation-journey
npm install && sh install-country-library.sh
# Si npm install falla por peer deps:
#   npm install --legacy-peer-deps && sh install-country-library.sh
```

cmd.exe:
```cmd
cd /d C:\software\journey\ib-client-validation-journey
rmdir /s /q node_modules\bcs-library
mklink /J node_modules\bcs-library C:\software\journey\ib-mx-client-validation-lib\dist\libs\ib-mx-customer-validation-bcs-lib
setx NODE_OPTIONS "--preserve-symlinks"
```

(Cierra y reabre todas las terminales después del `setx`.)

**Workflow diario (Git Bash, dos terminales):**

Terminal A (lib):
```bash
cd C:\software\journey\ib-mx-client-validation-lib
npx tsc -p libs/ib-mx-customer-validation-bcs-lib/tsconfig.lib.json --watch --watchFile priorityPollingInterval --watchDirectory dynamicPriorityPolling
```

Terminal B (API):
```bash
cd C:\software\journey\ib-client-validation-journey
npm run start
```

Después: editas lib → ves "File change detected" → matas API con Ctrl+C → arrancas API otra vez → verificas el cambio.

## Changelog de la guía

- **Versión inicial**: setup con junction + `tsc --watch` + `NODE_OPTIONS=--preserve-symlinks`.
- **Update post-migración NestJS 11 / Node 22**:
   - Subido requisito a Node 22+.
   - Documentado workaround `--legacy-peer-deps` para conflicto de `@nestjs/axios 4.0.1`.
   - Documentado bug de estructura `dist/src/` por cambio implícito de versión de TypeScript, con fix vía `rootDir` explícito.
   - Agregada sección de "Cómo limpiar y empezar de cero" con la advertencia de NO borrar `package-lock.json`.
   - Aclaración de que `setx` solo afecta procesos nuevos.
