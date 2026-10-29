require('dotenv').config();
const { Pool, types } = require('pg');

// En Render se usa DATABASE_URL (con SSL); en local, las variables PG* del .env
function configuracion() {
  if (process.env.DATABASE_URL) {
    return {
      connectionString: process.env.DATABASE_URL,
      ssl: process.env.PGSSL === 'false' ? false : { rejectUnauthorized: false },
    };
  }
  return {
    host: process.env.PGHOST || '127.0.0.1',
    port: Number(process.env.PGPORT || 5432),
    user: process.env.PGUSER || 'postgres',
    password: process.env.PGPASSWORD,
    database: process.env.PGDATABASE || 'medidor_conexion',
  };
}

// Los NUMERIC llegan como texto por defecto; la app los espera como números
types.setTypeParser(types.builtins.NUMERIC, parseFloat);

const pool = new Pool(configuracion());

module.exports = { pool, configuracion };
