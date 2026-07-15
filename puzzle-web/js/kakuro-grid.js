export class KakuroGrid {
  constructor(canvas, { cells, rows, cols, onCellTap }) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.cells = cells;
    this.rows = rows;
    this.cols = cols;
    this.onCellTap = onCellTap;
    this.answers = Array.from({ length: rows }, () => Array(cols).fill(0));
    this.errors = new Set();
    this.selected = null;
    this.cellSize = 44;
    this.bindEvents();
    this.resize();
  }

  setAnswers(answers) { this.answers = answers; this.render(); }
  setErrors(errors) { this.errors = errors; this.render(); }
  setSelected(rc) { this.selected = rc; this.render(); }

  resize() {
    const parent = this.canvas.parentElement;
    const maxW = Math.min(parent ? parent.clientWidth : 400, 400);
    this.cellSize = Math.max(40, Math.floor(maxW / this.cols));
    this.canvas.width = this.cellSize * this.cols;
    this.canvas.height = this.cellSize * this.rows;
    this.render();
  }

  render() {
    const { ctx, rows, cols, cellSize: s } = this;
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) this.drawCell(r, c);
    }
    ctx.strokeStyle = '#999';
    ctx.lineWidth = 1;
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        if (this.cells[r * cols + c]?.t === 'a') {
          ctx.strokeRect(c * s + 0.5, r * s + 0.5, s - 1, s - 1);
        }
      }
    }
  }

  drawCell(r, c) {
    const { ctx } = this;
    const cell = this.cells[r * this.cols + c];
    const x = c * this.cellSize;
    const y = r * this.cellSize;
    const s = this.cellSize;

    if (!cell || cell.t === 'w') {
      ctx.fillStyle = '#3a3a3a';
      ctx.fillRect(x, y, s, s);
      return;
    }

    if (cell.t === 'c') {
      ctx.fillStyle = '#3a3a3a';
      ctx.fillRect(x, y, s, s);
      ctx.strokeStyle = '#999';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(x + 2, y + 2);
      ctx.lineTo(x + s - 2, y + s - 2);
      ctx.stroke();
      const fs = Math.max(10, Math.floor(s * 0.30));
      ctx.font = `600 ${fs}px system-ui`;
      ctx.fillStyle = '#ddd';
      if (cell.d) {
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(String(cell.d), x + s * 0.28, y + s * 0.75);
      }
      if (cell.r) {
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(String(cell.r), x + s * 0.72, y + s * 0.25);
      }
      return;
    }

    if (cell.t === 'a') {
      const isSel = this.selected && this.selected[0] === r && this.selected[1] === c;
      const isErr = this.errors.has(r + ',' + c);
      ctx.fillStyle = isSel ? '#e3f2fd' : '#fff';
      ctx.fillRect(x, y, s, s);
      const v = this.answers[r][c];
      if (v) {
        ctx.fillStyle = isErr ? '#c62828' : '#1a1a1a';
        ctx.font = `700 ${Math.floor(s * 0.52)}px system-ui`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(String(v), x + s / 2, y + s / 2);
      }
      if (isSel) {
        ctx.strokeStyle = '#1565c0';
        ctx.lineWidth = 3;
        ctx.strokeRect(x + 1.5, y + 1.5, s - 3, s - 3);
      } else if (isErr) {
        ctx.strokeStyle = '#c62828';
        ctx.lineWidth = 2;
        ctx.strokeRect(x + 1, y + 1, s - 2, s - 2);
      }
    }
  }

  bindEvents() {
    window.addEventListener('resize', () => this.resize());
    this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    this.canvas.addEventListener('click', (e) => {
      const cell = this.hitTest(e);
      if (cell) this.onCellTap(cell[0], cell[1]);
    });
    this.canvas.addEventListener('touchend', (e) => {
      e.preventDefault();
      const t = e.changedTouches[0];
      if (t) {
        const cell = this.hitTest(t);
        if (cell) this.onCellTap(cell[0], cell[1]);
      }
    }, { passive: false });
  }

  hitTest(pointer) {
    const rect = this.canvas.getBoundingClientRect();
    const sx = this.canvas.width / rect.width;
    const sy = this.canvas.height / rect.height;
    const c = Math.floor(((pointer.clientX - rect.left) * sx) / this.cellSize);
    const r = Math.floor(((pointer.clientY - rect.top) * sy) / this.cellSize);
    return r >= 0 && r < this.rows && c >= 0 && c < this.cols ? [r, c] : null;
  }
}
