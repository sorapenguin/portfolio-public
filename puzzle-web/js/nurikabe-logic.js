export const NurikabeCell = { SEA: 0, ISLAND: 1, UNKNOWN: -1 };

export function verifyNurikabe(grid, clues, rows, cols) {
  if (!grid?.length || grid.length !== rows || (grid[0]?.length || 0) !== cols) return false;
  const clueMap = clueMapFor(clues);
  if (!allSeaConnected(grid, rows, cols)) return false;
  if (hasSeaSquare(grid, rows, cols)) return false;
  const seenIsland = new Set();

  for (const clue of clues || []) {
    if (grid[clue.row] && grid[clue.row][clue.col] === NurikabeCell.SEA) return false;
    const cells = collectIsland(grid, clue.row, clue.col, rows, cols);
    const clueCount = cells.filter(([r, c]) => clueMap.has(`${r},${c}`)).length;
    if (cells.length !== Number(clue.value) || clueCount !== 1) return false;
    cells.forEach(([r, c]) => seenIsland.add(`${r},${c}`));
  }

  for (let r = 0; r < rows; r += 1) {
    for (let c = 0; c < cols; c += 1) {
      if (grid[r][c] === NurikabeCell.UNKNOWN) return false;
      if (grid[r][c] === NurikabeCell.ISLAND && !seenIsland.has(`${r},${c}`)) return false;
    }
  }
  return true;
}

export function encodeNurikabeGrid(grid) {
  return JSON.stringify(grid);
}

export function decodeNurikabeGrid(json, rows, cols) {
  try {
    if (!json) throw new Error('empty');
    const parsed = typeof json === 'string' ? JSON.parse(json) : json;
    if (!Array.isArray(parsed) || parsed.length !== rows) throw new Error('row mismatch');
    return parsed.map((row) => {
      if (!Array.isArray(row) || row.length !== cols) throw new Error('col mismatch');
      return row.map((cell) => (cell === NurikabeCell.SEA || cell === NurikabeCell.ISLAND ? cell : NurikabeCell.UNKNOWN));
    });
  } catch(e) {
    return Array.from({ length: rows }, () => Array(cols).fill(NurikabeCell.UNKNOWN));
  }
}

export function islandSize(grid, startRow, startCol) {
  return collectIsland(grid, startRow, startCol, grid.length, grid[0]?.length || 0).length;
}

export function findNurikabeErrors(grid, clues, rows, cols) {
  const errors = new Set();
  if (hasSeaSquare(grid, rows, cols, errors)) return errors;
  for (const clue of clues || []) {
    const cells = collectIsland(grid, clue.row, clue.col, rows, cols);
    if (cells.length > Number(clue.value)) cells.forEach(([r, c]) => errors.add(`${r},${c}`));
  }
  return errors;
}

function clueMapFor(clues) {
  return new Map((clues || []).map((clue) => [`${clue.row},${clue.col}`, clue]));
}

function neighbors(r, c, rows, cols) {
  return [[r - 1, c], [r + 1, c], [r, c - 1], [r, c + 1]].filter(([nr, nc]) => nr >= 0 && nr < rows && nc >= 0 && nc < cols);
}

function collectIsland(grid, startRow, startCol, rows, cols) {
  if (startRow < 0 || startRow >= rows || startCol < 0 || startCol >= cols || grid[startRow][startCol] === NurikabeCell.SEA) return [];
  const queue = [[startRow, startCol]];
  const seen = new Set([`${startRow},${startCol}`]);
  for (let i = 0; i < queue.length; i += 1) {
    const [r, c] = queue[i];
    for (const [nr, nc] of neighbors(r, c, rows, cols)) {
      const key = `${nr},${nc}`;
      if (!seen.has(key) && grid[nr][nc] !== NurikabeCell.SEA) {
        seen.add(key);
        queue.push([nr, nc]);
      }
    }
  }
  return queue;
}

function allSeaConnected(grid, rows, cols) {
  const sea = [];
  for (let r = 0; r < rows; r += 1) {
    for (let c = 0; c < cols; c += 1) {
      if (grid[r][c] === NurikabeCell.SEA) sea.push([r, c]);
    }
  }
  if (!sea.length) return false;
  const queue = [sea[0]];
  const seen = new Set([`${sea[0][0]},${sea[0][1]}`]);
  for (let i = 0; i < queue.length; i += 1) {
    const [r, c] = queue[i];
    for (const [nr, nc] of neighbors(r, c, rows, cols)) {
      const key = `${nr},${nc}`;
      if (!seen.has(key) && grid[nr][nc] === NurikabeCell.SEA) {
        seen.add(key);
        queue.push([nr, nc]);
      }
    }
  }
  return seen.size === sea.length;
}

function hasSeaSquare(grid, rows, cols, errors = null) {
  let found = false;
  for (let r = 0; r < rows - 1; r += 1) {
    for (let c = 0; c < cols - 1; c += 1) {
      const isSquare = grid[r][c] === NurikabeCell.SEA &&
        grid[r + 1][c] === NurikabeCell.SEA &&
        grid[r][c + 1] === NurikabeCell.SEA &&
        grid[r + 1][c + 1] === NurikabeCell.SEA;
      if (isSquare) {
        found = true;
        errors?.add(`${r},${c}`);
        errors?.add(`${r + 1},${c}`);
        errors?.add(`${r},${c + 1}`);
        errors?.add(`${r + 1},${c + 1}`);
      }
    }
  }
  return found;
}

