# Guía Windows: desarrollo local de `bcs-library` con cambios en vivo

Esta guía documenta el proceso para tomar cambios de la lib `ib-mx-client-validation-lib` directamente en el API `ib-client-validation-journey` sin tener que publicar versiones nuevas, y reflejarlos con un solo `tsc` + reinicio de proceso, en lugar del ciclo viejo de `build:lib` + `cp -r` + reinicio.

Aplica a:
- **API**: `C:\software\journey\ib-client-validation-journey`
- **Lib MX**: `C:\software\journey\ib-mx-client-validation-lib`

Está pensada para Windows + Git Bash + WebStorm. Una guía equivalente para Mac queda pendiente.

## Requisitos previos

- Ambos repos clonados como carpetas hermanas dentro de `C:\software\journey\`.
- Node 18+ instalado.
- Git Bash (incluido con Git for Windows).
- Acceso a cmd.exe estándar (Win + R → `cmd`).
- WebStorm (opcional, también funciona desde terminal).

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
| `Cannot find module 'bcs-library'` al arrancar | Junction roto | `ls -la node_modules/bcs-library` debe mostrar la flecha al dist correcto |

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

## Resumen de comandos para tener a la mano

**Setup completo (copia y pega):**

Git Bash:
```bash
cd C:\software\journey\ib-mx-client-validation-lib
npm install && npm run build:lib

cd C:\software\journey\ib-client-validation-journey
npm install && sh install-country-library.sh
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
