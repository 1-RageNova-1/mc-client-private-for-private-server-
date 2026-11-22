const { Pool } = require('pg');
let pool = null;
function getPool() {
  if (!pool) {
    if (!process.env.DATABASE_URL) {
      throw new Error('DATABASE_URL required when MEMORY_DB is not enabled');
    }
    pool = new Pool({ connectionString: process.env.DATABASE_URL, max: 10 });
  }
  return pool;
}
module.exports = new Proxy({}, {
  get(_t, prop) {
    return getPool()[prop].bind(getPool());
  }
});
