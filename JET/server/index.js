import express from 'express';
import { appendFileSync, existsSync, mkdirSync, readFileSync } from 'fs';
import { dirname, join } from 'path';
import { randomUUID } from 'crypto';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const dataDir = join(__dirname, '..', 'data');
const reportsFile = join(dataDir, 'reports.jsonl');

if (!existsSync(dataDir)) mkdirSync(dataDir, { recursive: true });

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use((_req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  next();
});

app.post('/api/report', (req, res) => {
  const report = {
    id: randomUUID(),
    timestamp: new Date().toISOString(),
    ...req.body,
  };
  appendFileSync(reportsFile, JSON.stringify(report) + '\n', 'utf8');
  console.log(`[report] ${report.problemId ?? '?'} step${report.stepIndex ?? '?'} @ ${report.timestamp}`);
  res.json({ ok: true, id: report.id });
});

app.get('/api/reports', (_req, res) => {
  if (!existsSync(reportsFile)) return res.json([]);
  const raw = readFileSync(reportsFile, 'utf8').trim();
  const reports = raw
    .split('\n')
    .filter(Boolean)
    .map(line => { try { return JSON.parse(line); } catch { return null; } })
    .filter(Boolean)
    .reverse();
  res.json(reports);
});

app.options('/api/report', (_req, res) => res.sendStatus(204));

app.listen(3998, '0.0.0.0', () => {
  console.log('JET report server → http://0.0.0.0:3998');
});
