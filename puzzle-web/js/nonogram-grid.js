import { CellState } from './nonogram-logic.js';

export class NonogramGrid {
  constructor(canvas, { rowHints, colHints, onCellChange, onGestureStart, onGestureEnd }) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.rowHints = rowHints;
    this.colHints = colHints;
    this.onCellChange = onCellChange;
    this.onGestureStart = onGestureStart;
    this.onGestureEnd = onGestureEnd;
    this.grid = [];
    this.fillMode = true;
    this.errorRows = new Set();
    this.errorCols = new Set();
    this.completedRows = new Set();
    this.completedCols = new Set();
    this.hintedCells = new Set();
    this.dragIntent = null;
    this.dragged = new Set();
    this.lastCell = null;
    this.dragStartCell = null;
    this.dragAxis = null;
    this.cellSize = 28;
    this.hintSlots = 5;
    this.bindEvents();
  }

  setGrid(grid) {
    this.grid = grid;
    this.resize();
  }

  resize() {
    const rows = this.grid.length;
    const cols = this.grid[0]?.length || 0;
    if (!rows || !cols) return;
    const maxWidth = Math.min(this.canvas.parentElement?.clientWidth || 600, 600);
    const hintRatio = 0.64;
    this.cellSize = Math.max(18, Math.floor(maxWidth / (cols + this.hintSlots * hintRatio)));
    this.hintSize = Math.max(13, Math.floor(this.cellSize * hintRatio));
    this.gridLeft = this.hintSize * this.hintSlots;
    this.gridTop = this.hintSize * this.hintSlots;
    this.canvas.width = this.gridLeft + this.cellSize * cols;
    this.canvas.height = this.gridTop + this.cellSize * rows;
    this.render();
  }

  render() {
    const rows = this.grid.length;
    const cols = this.grid[0]?.length || 0;
    if (!rows || !cols) return;
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

    for (let r = 0; r < rows; r += 1) {
      if (this.errorRows.has(r) || this.completedRows.has(r)) {
        ctx.fillStyle = this.errorRows.has(r) ? '#fde8e8' : '#e9f7ee';
        ctx.fillRect(0, this.gridTop + r * this.cellSize, this.canvas.width, this.cellSize);
      }
    }
    for (let c = 0; c < cols; c += 1) {
      if (this.errorCols.has(c) || this.completedCols.has(c)) {
        ctx.fillStyle = this.errorCols.has(c) ? 'rgba(253,232,232,.65)' : 'rgba(233,247,238,.65)';
        ctx.fillRect(this.gridLeft + c * this.cellSize, 0, this.cellSize, this.canvas.height);
      }
    }

    for (let r = 0; r < rows; r += 1) {
      for (let c = 0; c < cols; c += 1) {
        this.drawCell(r, c, this.grid[r][c]);
      }
    }
    this.drawGridLines(rows, cols);
    this.drawHints(rows, cols);
  }

  drawCell(r, c, state) {
    const ctx = this.ctx;
    const x = this.gridLeft + c * this.cellSize;
    const y = this.gridTop + r * this.cellSize;
    const pad = Math.max(2, this.cellSize * 0.08);
    if (state === CellState.FILLED) {
      ctx.fillStyle = '#333';
      ctx.fillRect(x + pad, y + pad, this.cellSize - pad * 2, this.cellSize - pad * 2);
    } else if (state === CellState.MARKED || state === CellState.AUTO_MARKED) {
      ctx.fillStyle = state === CellState.MARKED ? '#f3f4f6' : '#fafafa';
      ctx.fillRect(x + 1, y + 1, this.cellSize - 2, this.cellSize - 2);
      ctx.strokeStyle = state === CellState.MARKED ? '#777' : '#bbb';
      ctx.lineWidth = state === CellState.MARKED ? 2 : 1;
      ctx.beginPath();
      ctx.moveTo(x + pad * 2, y + pad * 2);
      ctx.lineTo(x + this.cellSize - pad * 2, y + this.cellSize - pad * 2);
      ctx.moveTo(x + this.cellSize - pad * 2, y + pad * 2);
      ctx.lineTo(x + pad * 2, y + this.cellSize - pad * 2);
      ctx.stroke();
    }
    if (this.hintedCells.has(`${r},${c}`)) {
      ctx.strokeStyle = '#2f80ed';
      ctx.lineWidth = 3;
      ctx.strokeRect(x + 2, y + 2, this.cellSize - 4, this.cellSize - 4);
    }
  }

  drawGridLines(rows, cols) {
    const ctx = this.ctx;
    for (let c = 0; c <= cols; c += 1) {
      ctx.strokeStyle = c % 5 === 0 ? '#333' : '#cfcfcf';
      ctx.lineWidth = c % 5 === 0 ? 1.5 : 1;
      const x = this.gridLeft + c * this.cellSize;
      line(ctx, x, this.gridTop, x, this.gridTop + rows * this.cellSize);
    }
    for (let r = 0; r <= rows; r += 1) {
      ctx.strokeStyle = r % 5 === 0 ? '#333' : '#cfcfcf';
      ctx.lineWidth = r % 5 === 0 ? 1.5 : 1;
      const y = this.gridTop + r * this.cellSize;
      line(ctx, this.gridLeft, y, this.gridLeft + cols * this.cellSize, y);
    }
  }

  drawHints(rows, cols) {
    const ctx = this.ctx;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = `${Math.max(11, Math.floor(this.cellSize * 0.42))}px system-ui`;
    for (let r = 0; r < rows; r += 1) {
      const hints = (this.rowHints[r] || [0]).slice(-this.hintSlots);
      const offset = this.hintSlots - hints.length;
      ctx.fillStyle = this.errorRows.has(r) ? '#c62828' : this.completedRows.has(r) ? '#2e7d32' : '#222';
      hints.forEach((hint, i) => ctx.fillText(String(hint), (offset + i + 0.5) * this.hintSize, this.gridTop + (r + 0.5) * this.cellSize));
    }
    for (let c = 0; c < cols; c += 1) {
      const hints = (this.colHints[c] || [0]).slice(-this.hintSlots);
      const offset = this.hintSlots - hints.length;
      ctx.fillStyle = this.errorCols.has(c) ? '#c62828' : this.completedCols.has(c) ? '#2e7d32' : '#222';
      hints.forEach((hint, i) => ctx.fillText(String(hint), this.gridLeft + (c + 0.5) * this.cellSize, (offset + i + 0.5) * this.hintSize));
    }
  }

  bindEvents() {
    window.addEventListener('resize', () => this.resize());
    this.canvas.addEventListener('contextmenu', (event) => event.preventDefault());
    this.canvas.addEventListener('mousedown', (event) => this.startGesture(event, event.button === 2));
    this.canvas.addEventListener('mousemove', (event) => this.moveGesture(event));
    window.addEventListener('mouseup', () => this.endGesture());
    this.canvas.addEventListener('touchstart', (event) => this.startGesture(event.touches[0], false, event), { passive: false });
    this.canvas.addEventListener('touchmove', (event) => this.moveGesture(event.touches[0], event), { passive: false });
    window.addEventListener('touchend', () => this.endGesture());
  }

  startGesture(pointer, forceMark, event = null) {
    event?.preventDefault();
    const cell = this.pointerToCell(pointer);
    if (!cell) return;
    this.onGestureStart?.();
    this.dragged.clear();
    this.dragStartCell = cell;
    this.dragAxis = null;
    const [r, c] = cell;
    const current = this.grid[r][c];
    const markMode = forceMark || !this.fillMode;
    this.dragIntent = markMode
      ? (current === CellState.MARKED || current === CellState.AUTO_MARKED ? CellState.EMPTY : CellState.MARKED)
      : (current === CellState.FILLED ? CellState.EMPTY : CellState.FILLED);
    this.applyCell(r, c);
  }

  moveGesture(pointer, event = null) {
    if (this.dragIntent === null) return;
    event?.preventDefault();
    const cell = this.pointerToCell(pointer);
    if (!cell) return;
    let [r, c] = cell;
    if (this.dragAxis === null && this.dragStartCell) {
      const dr = Math.abs(r - this.dragStartCell[0]);
      const dc = Math.abs(c - this.dragStartCell[1]);
      if (dr > dc) this.dragAxis = 'vertical';
      else if (dc > dr) this.dragAxis = 'horizontal';
    }
    if (this.dragAxis === 'horizontal') r = this.dragStartCell[0];
    if (this.dragAxis === 'vertical') c = this.dragStartCell[1];
    this.applyCell(r, c);
  }

  endGesture() {
    if (this.dragIntent !== null) this.onGestureEnd?.();
    this.dragIntent = null;
    this.dragged.clear();
    this.dragStartCell = null;
    this.dragAxis = null;
  }

  applyCell(r, c) {
    const key = `${r},${c}`;
    if (this.dragged.has(key)) return;
    this.dragged.add(key);
    const current = this.grid[r][c];
    let next = current;
    if (this.dragIntent === CellState.FILLED) next = current === CellState.EMPTY ? CellState.FILLED : current;
    if (this.dragIntent === CellState.MARKED) next = current === CellState.EMPTY ? CellState.MARKED : current;
    if (this.dragIntent === CellState.EMPTY) next = CellState.EMPTY;
    if (next !== current) this.onCellChange?.(r, c, next);
  }

  pointerToCell(pointer) {
    const rect = this.canvas.getBoundingClientRect();
    const scaleX = this.canvas.width / rect.width;
    const scaleY = this.canvas.height / rect.height;
    const x = (pointer.clientX - rect.left) * scaleX;
    const y = (pointer.clientY - rect.top) * scaleY;
    const c = Math.floor((x - this.gridLeft) / this.cellSize);
    const r = Math.floor((y - this.gridTop) / this.cellSize);
    return r >= 0 && r < this.grid.length && c >= 0 && c < (this.grid[0]?.length || 0) ? [r, c] : null;
  }
}

function line(ctx, x1, y1, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}

