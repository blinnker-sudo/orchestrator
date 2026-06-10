#!/usr/bin/env node
/* ============================================================
 *  start-local-lib.js
 *  Arranca el API inyectando NODE_OPTIONS=--preserve-symlinks,
 *  sin depender de cross-env (que ensuciaba el arbol de deps).
 *  Cross-platform: Node esta en todos lados.
 *  Uso:  npm run start:local-lib
 * ============================================================ */
'use strict';
const { spawn } = require('child_process');

// Agregar --preserve-symlinks a lo que ya traiga NODE_OPTIONS, sin duplicar.
const opts = (process.env.NODE_OPTIONS || '').split(' ').filter(Boolean);
if (!opts.includes('--preserve-symlinks')) opts.push('--preserve-symlinks');
process.env.NODE_OPTIONS = opts.join(' ');

// shell: true es OBLIGATORIO en Windows: desde Node 20.12+ spawn() de un
// .cmd/.bat sin shell lanza EINVAL (fix de seguridad CVE-2024-27980).
// Con shell: true tambien basta 'npm' (el shell resuelve el .cmd).
const child = spawn('npm', ['run', 'start'], {
  stdio: 'inherit',
  env: process.env,
  shell: true,
});

child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  else process.exit(code == null ? 0 : code);
});
