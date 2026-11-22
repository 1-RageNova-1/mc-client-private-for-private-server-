const requiredProd = ['JWT_SECRET', 'DATABASE_URL'];
const memory = process.env.MEMORY_DB === '1' || process.env.MEMORY_DB === 'true';
let ok = true;
if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 16) {
  console.error('JWT_SECRET missing or too short (min 16)');
  ok = false;
}
if (!memory && !process.env.DATABASE_URL) {
  console.error('DATABASE_URL required when MEMORY_DB is not enabled');
  ok = false;
}
if (process.env.JWT_SECRET && /CHANGE_ME/.test(process.env.JWT_SECRET)) {
  console.error('JWT_SECRET still placeholder');
  ok = false;
}
console.log(ok ? 'env_ok' : 'env_bad');
process.exit(ok ? 0 : 1);
