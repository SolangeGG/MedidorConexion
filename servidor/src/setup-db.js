// Crea la base de datos (si no existe) y la tabla de mediciones. Se puede correr las veces que haga falta.
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');
const { pool, configuracion } = require('./db');

async function crearBaseSiFalta() {
  if (process.env.DATABASE_URL) return; // en Render la base ya viene creada
  const cfg = configuracion();
  const admin = new Client({ ...cfg, database: 'postgres' });
  await admin.connect();
  const { rowCount } = await admin.query('SELECT 1 FROM pg_database WHERE datname = $1', [cfg.database]);
  if (rowCount === 0) {
    await admin.query(`CREATE DATABASE "${cfg.database}"`);
    console.log(`Base de datos "${cfg.database}" creada`);
  } else {
    console.log(`Base de datos "${cfg.database}" ya existía`);
  }
  await admin.end();
}

async function main() {
  await crearBaseSiFalta();
  const sql = fs.readFileSync(path.join(__dirname, '..', 'schema.sql'), 'utf8');
  await pool.query(sql);
  console.log('Tabla "mediciones" lista');
  await pool.end();
}

main().catch((e) => {
  console.error('Error:', e.message);
  process.exit(1);
});
