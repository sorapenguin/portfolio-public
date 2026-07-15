export function buildAnswerGrid(rows, cols) {
  return Array.from({ length: rows }, () => Array(cols).fill(0));
}

function cellAt(cells, rows, cols, r, c) {
  if (r < 0 || r >= rows || c < 0 || c >= cols) return null;
  return cells[r * cols + c];
}

function collectHRun(cells, rows, cols, row, col) {
  let start = col;
  while (start > 0 && cellAt(cells, rows, cols, row, start - 1)?.t === 'a') start--;
  const run = [];
  for (let c = start; c < cols; c++) {
    if (cellAt(cells, rows, cols, row, c)?.t !== 'a') break;
    run.push([row, c]);
  }
  return run;
}

function collectVRun(cells, rows, cols, row, col) {
  let start = row;
  while (start > 0 && cellAt(cells, rows, cols, start - 1, col)?.t === 'a') start--;
  const run = [];
  for (let r = start; r < rows; r++) {
    if (cellAt(cells, rows, cols, r, col)?.t !== 'a') break;
    run.push([r, col]);
  }
  return run;
}

export function getImpossibleDigits(cells, rows, cols, row, col, answers) {
  const impossible = new Set();

  function checkRun(run, clueSum) {
    if (!clueSum) return;
    const others = run.filter(([r, c]) => r !== row || c !== col);
    const alreadySet = new Set(others.map(([r, c]) => answers[r][c]).filter(v => v));
    const sumAlready = others.reduce((s, [r, c]) => s + (answers[r][c] || 0), 0);
    const emptyOthers = others.filter(([r, c]) => !answers[r][c]).length;
    const remaining = clueSum - sumAlready;
    for (let d = 1; d <= 9; d++) {
      if (alreadySet.has(d)) { impossible.add(d); continue; }
      const need = remaining - d;
      if (need < emptyOthers || need > emptyOthers * 9) impossible.add(d);
    }
  }

  const hRun = collectHRun(cells, rows, cols, row, col);
  const vRun = collectVRun(cells, rows, cols, row, col);
  const hClueCell = cellAt(cells, rows, cols, hRun[0][0], hRun[0][1] - 1);
  const vClueCell = cellAt(cells, rows, cols, vRun[0][0] - 1, vRun[0][1]);
  checkRun(hRun, hClueCell && hClueCell.r ? hClueCell.r : 0);
  checkRun(vRun, vClueCell && vClueCell.d ? vClueCell.d : 0);
  return impossible;
}

export function getUsedDigits(cells, rows, cols, row, col, answers) {
  const used = new Set();
  const addFrom = (run) => run.forEach(([r, c]) => {
    if (r !== row || c !== col) {
      const v = answers[r][c];
      if (v) used.add(v);
    }
  });
  addFrom(collectHRun(cells, rows, cols, row, col));
  addFrom(collectVRun(cells, rows, cols, row, col));
  return used;
}

export function findErrors(cells, rows, cols, answers) {
  const errors = new Set();
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if (cellAt(cells, rows, cols, r, c)?.t !== 'a') continue;
      const v = answers[r][c];
      if (v && getUsedDigits(cells, rows, cols, r, c, answers).has(v)) {
        errors.add(r + ',' + c);
      }
    }
  }
  return errors;
}

function getAllRuns(cells, rows, cols) {
  const runs = [];
  for (let r = 0; r < rows; r++) {
    let run = null;
    for (let c = 0; c <= cols; c++) {
      if (cellAt(cells, rows, cols, r, c)?.t === 'a') {
        if (!run) {
          const cc = cellAt(cells, rows, cols, r, c - 1);
          run = { clue: cc && cc.r ? cc.r : 0, positions: [] };
        }
        run.positions.push([r, c]);
      } else {
        if (run) { runs.push(run); run = null; }
      }
    }
  }
  for (let c = 0; c < cols; c++) {
    let run = null;
    for (let r = 0; r <= rows; r++) {
      if (cellAt(cells, rows, cols, r, c)?.t === 'a') {
        if (!run) {
          const cc = cellAt(cells, rows, cols, r - 1, c);
          run = { clue: cc && cc.d ? cc.d : 0, positions: [] };
        }
        run.positions.push([r, c]);
      } else {
        if (run) { runs.push(run); run = null; }
      }
    }
  }
  return runs;
}

export function verifyKakuro(cells, rows, cols, answers) {
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if (cellAt(cells, rows, cols, r, c)?.t === 'a' && !answers[r][c]) return false;
    }
  }
  return getAllRuns(cells, rows, cols).every(({ clue, positions }) => {
    let sum = 0;
    const seen = new Set();
    for (const [r, c] of positions) {
      const v = answers[r][c];
      if (!v || seen.has(v)) return false;
      seen.add(v);
      sum += v;
    }
    return !clue || sum === clue;
  });
}
