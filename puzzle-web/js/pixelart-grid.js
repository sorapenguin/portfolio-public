export function PixelArtGrid(canvas, opts) {
  this.canvas = canvas;
  this.ctx = canvas.getContext('2d');
  this.pixels = opts.pixels || [];
  this.palette = opts.palette || [];
  this.displayMap = opts.displayMap || {};
  this.onPaint = opts.onPaint || function() {};
  this.painted = [];
  this.rows = this.pixels.length;
  this.cols = (this.pixels[0] && this.pixels[0].length) || 0;
  this.cellSize = 40;
  this._drag = false;
  this._dragKey = null;
  this._setup();
}

PixelArtGrid.prototype._setup = function() {
  var self = this;
  this._resize();
  window.addEventListener('resize', function() { self._resize(); });

  this.canvas.addEventListener('mousedown', function(e) {
    e.preventDefault();
    self._drag = true;
    self._dragKey = null;
    self._handleMouse(e);
  });
  this.canvas.addEventListener('mousemove', function(e) {
    if (self._drag) self._handleMouse(e);
  });
  window.addEventListener('mouseup', function() {
    self._drag = false;
    self._dragKey = null;
  });

  this.canvas.addEventListener('touchstart', function(e) {
    e.preventDefault();
    self._drag = true;
    self._dragKey = null;
    self._handleTouch(e);
  }, { passive: false });
  this.canvas.addEventListener('touchmove', function(e) {
    e.preventDefault();
    if (self._drag) self._handleTouch(e);
  }, { passive: false });
  window.addEventListener('touchend', function() {
    self._drag = false;
    self._dragKey = null;
  });
};

PixelArtGrid.prototype._resize = function() {
  var parent = this.canvas.parentElement;
  var avail = parent ? parent.clientWidth - 20 : 460;
  var maxW = Math.min(avail, 480);
  if (this.rows > 0 && this.cols > 0) {
    this.cellSize = Math.max(28, Math.floor(maxW / Math.max(this.rows, this.cols)));
  }
  this.canvas.width = this.cellSize * this.cols;
  this.canvas.height = this.cellSize * this.rows;
  this.render();
};

PixelArtGrid.prototype._toCell = function(clientX, clientY) {
  var rect = this.canvas.getBoundingClientRect();
  var sx = this.canvas.width / rect.width;
  var sy = this.canvas.height / rect.height;
  var col = Math.floor(((clientX - rect.left) * sx) / this.cellSize);
  var row = Math.floor(((clientY - rect.top) * sy) / this.cellSize);
  if (row < 0 || row >= this.rows || col < 0 || col >= this.cols) return null;
  if (!this.pixels[row] || this.pixels[row][col] === 0) return null;
  return [row, col];
};

PixelArtGrid.prototype._handleMouse = function(e) {
  var cell = this._toCell(e.clientX, e.clientY);
  if (!cell) return;
  var key = cell[0] + ',' + cell[1];
  if (this._dragKey === key) return;
  this._dragKey = key;
  this.onPaint(cell[0], cell[1]);
};

PixelArtGrid.prototype._handleTouch = function(e) {
  if (!e.touches.length) return;
  var t = e.touches[0];
  var cell = this._toCell(t.clientX, t.clientY);
  if (!cell) return;
  var key = cell[0] + ',' + cell[1];
  if (this._dragKey === key) return;
  this._dragKey = key;
  this.onPaint(cell[0], cell[1]);
};

PixelArtGrid.prototype.setPainted = function(painted) {
  this.painted = painted;
  this.render();
};

PixelArtGrid.prototype.render = function() {
  var ctx = this.ctx;
  var cs = this.cellSize;
  ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
  ctx.font = 'bold ' + Math.max(10, Math.floor(cs * 0.44)) + 'px system-ui';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';

  for (var r = 0; r < this.rows; r++) {
    for (var c = 0; c < this.cols; c++) {
      var x = c * cs;
      var y = r * cs;
      var target = (this.pixels[r] && this.pixels[r][c]) || 0;

      if (target === 0) {
        ctx.fillStyle = '#CCCCCC';
        ctx.fillRect(x, y, cs, cs);
      } else {
        var row = this.painted[r];
        var pv = (row && row[c] !== undefined) ? row[c] : -1;
        var dn = String(this.displayMap[target] !== undefined ? this.displayMap[target] : target);
        if (pv >= 0) {
          ctx.fillStyle = this.palette[pv] || '#888888';
          ctx.fillRect(x, y, cs, cs);
        } else {
          ctx.fillStyle = '#F8F8F8';
          ctx.fillRect(x, y, cs, cs);
          ctx.fillStyle = '#444444';
          ctx.fillText(dn, x + cs / 2, y + cs / 2);
        }
        ctx.strokeStyle = 'rgba(0,0,0,0.18)';
        ctx.lineWidth = 1;
        ctx.strokeRect(x + 0.5, y + 0.5, cs - 1, cs - 1);
      }
    }
  }
};
