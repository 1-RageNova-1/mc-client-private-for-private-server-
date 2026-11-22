try {
  require('dotenv').config({ path: process.env.ENV_FILE || 'config/example.env' });
} catch (e) {
  /* optional */
}
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');
const authRoutes = require('./routes/auth');

const app = express();
app.use(helmet());
app.use(cors({ origin: false }));
app.use(express.json({ limit: '32kb' }));
app.use(morgan('combined'));

app.get('/health', (_req, res) => res.json({ ok: true, service: 'maiclient-api', memoryDb: process.env.MEMORY_DB === '1' }));
app.use('/v1/auth', authRoutes);

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'internal' });
});

const port = Number(process.env.PORT || 8787);
app.listen(port, () => {
  console.log(`MaiClient API listening on :${port}`);
});
