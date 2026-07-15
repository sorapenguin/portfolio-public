import { NurikabeCell } from './nurikabe-logic.js';

export class NurikabeGrid {
  constructor(canvas, { clues, rows, cols, onCellChange }) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.clues = clues || [];
    this.rows = rows;
    this.cols = cols;
    this.onCellChange = onCellChange;
    this.grid = Array.from({ length: rows }, () => Array(cols).fill(NurikabeCell.UNKNOWN));
    this.errorCells = new Set();
    this.cellSize = 32;
    this.dragIntent = null;
    this.dragVisited = new Set();
    this.bindEvents();
  }

  setGrid(grid) {
    this.grid = grid;
    this.resize();
  }

  resize() {
    const maxWidth = Math.min(this.canvas.parentElement?.clientWidth || 600, 600);
    this.cellSize = Math.max(24, Math.floor(maxWidth / this.cols));
    this.canvas.width = this.cellSize * this.cols;
    this.canvas.height = this.cellSize * this.rows;
    this.render();
  }

  render() {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    for (let r = 0; r < this.rows; r += 1) {
      for (let c = 0; c < this.cols; c += 1) this.drawCell(r, c);
    }
    ctx.strokeStyle = '#b8b8b8';
    ctx.lineWidth = 1;
    for (let c = 0; c <= this.cols; c += 1) line(ctx, c * this.cellSize, 0, c * this.cellSize, this.canvas.height);
    for (let r = 0; r <= this.rows; r += 1) line(ctx, 0, r * this.cellSize, this.canvas.width, r * this.cellSize);
  }

  drawCell(r, c) {
    const ctx = this.ctx;
    const x = c * this.cellSize;
    const y = r * this.cellSize;
    const state = this.grid[r][c];
    ctx.fillStyle = state === NurikabeCell.SEA ? '#222' : '#fff';
    ctx.fillRect(x, y, this.cellSize, this.cellSize);
    if (state === NurikabeCell.UNKNOWN) {
      ctx.fillStyle = '#f7f7f7';
      ctx.fillRect(x + 1, y + 1, this.cellSize - 2, this.cellSize - 2);
    }
    const clue = this.clues.find((item) => Number(item.row) === r && Number(item.col) === c);
    if (clue) {
      ctx.fillStyle = '#fff7d6';
      ctx.fillRect(x + 3, y + 3, this.cellSize - 6, this.cellSize - 6);
      ctx.fillStyle = '#222';
      ctx.font = `700 ${Math.max(14, Math.floor(this.cellSize * 0.46))}px system-ui`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(clue.value), x + this.cellSize / 2, y + this.cellSize / 2);
    }
    if (this.errorCells.has(`${r},${c}`)) {
      ctx.strokeStyle = '#d32f2f';
      ctx.lineWidth = 3;
      ctx.strokeRect(x + 2, y + 2, this.cellSize - 4, this.cellSize - 4);
    }
  }

  bindEvents() {
    window.addEventListener('resize', () => this.resize());
    this.canvas.addEventListener('contextmenu', (event) => event.preventDefault());

    this.canvas.addEventListener('mousedown', (event) => {
      this.startDrag(event, event.button === 2);
    });
    this.canvas.addEventListener('mousemove', (event) => {
      this.moveDrag(event);
    });
    window.addEventListener('mouseup', () => { this.dragIntent = null; this.dragVisited.clear(); });

    this.canvas.addEventListener('touchstart', (event) => {
      event.preventDefault();
      this.startDrag(event.touches[0], false);
    }, { passive: false });
    this.canvas.addEventListener('touchmove', (event) => {
      event.preventDefault();
      this.moveDrag(event.touches[0]);
    }, { passive: false });
    window.addEventListener('touchend', () => { this.dragIntent = null; this.dragVisited.clear(); });
  }

  startDrag(pointer, rightClick) {
    const cell = this.pointerToCell(pointer);
    if (!cell) return;
    const [r, c] = cell;
    const current = this.grid[r][c];
    // Determine what all drag-cells should become
    this.dragIntent = current === NurikabeCell.UNKNOWN
      ? (rightClick ? NurikabeCell.ISLAND : NurikabeCell.SEA)
      : NurikabeCell.UNKNOWN;
    this.dragVisited.clear();
    this.applyDrag(r, c);
  }

  moveDrag(pointer) {
    if (this.dragIntent === null) return;
    const cell = this.pointerToCell(pointer);
    if (!cell) return;
    this.applyDrag(cell[0], cell[1]);
  }

  applyDrag(r, c) {
    const key = r + ',' + c;
    if (this.dragVisited.has(key)) return;
    this.dragVisited.add(key);
    this.onCellChange(r, c, this.dragIntent);
  }

  pointerToCell(pointer) {
    const rect = this.canvas.getBoundingClientRect();
    const scaleX = this.canvas.width / rect.width;
    const scaleY = this.canvas.height / rect.height;
    const c = Math.floor(((pointer.clientX - rect.left) * scaleX) / this.cellSize);
    const r = Math.floor(((pointer.clientY - rect.top) * scaleY) / this.cellSize);
    return r >= 0 && r < this.rows && c >= 0 && c < this.cols ? [r, c] : null;
  }
}

function line(ctx, x1, y1, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}
