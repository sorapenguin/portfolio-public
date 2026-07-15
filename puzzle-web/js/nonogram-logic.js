export const CellState = { EMPTY: 0, FILLED: 1, MARKED: 2, AUTO_MARKED: 3 };

export function computeHint(line) {
  const hints = [];
  let count = 0;
  for (const value of line) {
    if (value === 1) {
      count += 1;
    } else if (count > 0) {
      hints.push(count);
      count = 0;
    }
  }
  if (count > 0) hints.push(count);
  return hints.length ? hints : [0];
}

export function verifySolution(grid, rowHints, colHints) {
  if (!rowHints?.length || !colHints?.length || !grid?.length) return false;
  for (let r = 0; r < grid.length; r += 1) {
    if (!sameHints(computeHint(grid[r].map((cell) => (cell === CellState.FILLED ? 1 : 0))), rowHints[r])) return false;
  }
  const cols = grid[0]?.length || 0;
  for (let c = 0; c < cols; c += 1) {
    const line = grid.map((row) => (row[c] === CellState.FILLED ? 1 : 0));
    if (!sameHints(computeHint(line), colHints[c])) return false;
  }
  return true;
}

export function autoMarkConfirmed(grid, rowHints, colHints, suppressedAutoMarks = new Set()) {
  let changed = false;
  for (let r = 0; r < grid.length; r += 1) {
    for (let c = 0; c < grid[r].length; c += 1) {
      if (grid[r][c] === CellState.AUTO_MARKED) {
        grid[r][c] = CellState.EMPTY;
        changed = true;
      }
    }
  }
  for (let r = 0; r < rowHints.length; r += 1) {
    const row = grid[r];
    if (!row) continue;
    if (row.filter((cell) => cell === CellState.FILLED).length === hintSum(rowHints[r])) {
      for (let c = 0; c < row.length; c += 1) {
        if (row[c] === CellState.EMPTY && !suppressedAutoMarks.has(`${r},${c}`)) {
          row[c] = CellState.AUTO_MARKED;
          changed = true;
        }
      }
    }
  }
  const cols = grid[0]?.length || 0;
  for (let c = 0; c < cols; c += 1) {
    if (!colHints[c]) continue;
    let filled = 0;
    for (let r = 0; r < grid.length; r += 1) {
      if (grid[r][c] === CellState.FILLED) filled += 1;
    }
    if (filled === hintSum(colHints[c])) {
      for (let r = 0; r < grid.length; r += 1) {
        if (grid[r][c] === CellState.EMPTY && !suppressedAutoMarks.has(`${r},${c}`)) {
          grid[r][c] = CellState.AUTO_MARKED;
          changed = true;
        }
      }
    }
  }
  return changed;
}

export function encodeGrid(grid) {
  return JSON.stringify(grid.map((row) => row.map((cell) => {
    if (cell === CellState.FILLED) return 1;
    if (cell === CellState.MARKED) return 2;
    return 0;
  })));
}

export function decodeGrid(json, rows, cols) {
  try {
    if (!json) throw new Error('empty');
    const parsed = typeof json === 'string' ? JSON.parse(json) : json;
    if (!Array.isArray(parsed) || parsed.length !== rows) throw new Error('row mismatch');
    return parsed.map((row) => {
      if (!Array.isArray(row) || row.length !== cols) throw new Error('col mismatch');
      return row.map((cell) => (cell === 1 ? CellState.FILLED : cell === 2 ? CellState.MARKED : CellState.EMPTY));
    });
  } catch(e) {
    return Array.from({ length: rows }, () => Array(cols).fill(CellState.EMPTY));
  }
}

export function solutionHash(solution) {
  const text = solution.map((row) => row.join(',')).join(';');
  let hash = 1125899906842597n;
  for (const ch of text) {
    hash = BigInt.asUintN(64, 31n * hash + BigInt(ch.codePointAt(0)));
  }
  return hash.toString(16);
}

function hintSum(hints) {
  return (hints || []).reduce((sum, value) => sum + Number(value || 0), 0);
}

function normalizeHints(hints) {
  const normalized = (hints || []).filter((value) => Number(value) > 0).map(Number);
  return normalized.length ? normalized : [0];
}

function sameHints(a, b) {
  const left = normalizeHints(a);
  const right = normalizeHints(b);
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

