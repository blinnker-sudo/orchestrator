#!/usr/bin/env node
/* ============================================================
 *  link-local-lib.js
 *  Enlaza node_modules/enrollment-library -> dist de la lib local.
 *  Cross-platform sin quirks de shell: usa fs.symlinkSync con tipo
 *  'junction' (en Windows crea junction sin admin; en Mac/Linux
 *  hace un symlink normal).
 *  Uso:  npm run link:local-lib
 * ============================================================ */
'use strict';
const fs = require('fs');
const path = require('path');

const apiDir = path.resolve(__dirname, '..');

// Dist de la lib (repo hermano). Ajusta si el folder interno cambia.
const libDist = path.resolve(
  apiDir,
  '../ib-mx-enrollment-lib/dist/libs/ib-mx-enrollment-lib'
);

const link = path.join(apiDir, 'node_modules', 'enrollment-library');

if (!fs.existsSync(libDist)) {
  console.error(`[ERROR] No existe el dist de la lib:\n        ${libDist}`);
  console.error('        Corre  npm run build:lib  en la lib primero.');
  process.exit(1);
}

// Quitar lo que haya (symlink/junction viejo, o copia publicada) de forma segura.
// unlink para symlink/junction (NO recursa al target); rm -r para carpeta real.
try {
  const st = fs.lstatSync(link);
  if (st.isSymbolicLink() || st.isFile()) {
    fs.unlinkSync(link);
  } else {
    fs.rmSync(link, { recursive: true, force: true });
  }
  console.log('Quitado enrollment-library anterior.');
} catch (e) {
  if (e.code !== 'ENOENT') throw e; // no existia: ok
}

// Crear el link. 'junction' -> junction en Windows, symlink normal en Mac/Linux.
fs.symlinkSync(libDist, link, 'junction');

console.log(`\n[OK] enrollment-library -> ${libDist}`);
