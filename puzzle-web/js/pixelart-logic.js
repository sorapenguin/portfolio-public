// default16 palette: index 0 = transparent/background, 1-15 = paintable colors
export var PALETTE = [
  '#CCCCCC',
  '#FFFFFF',
  '#C0C0C0',
  '#606060',
  '#000000',
  '#DC3232',
  '#8C1414',
  '#F08C1E',
  '#F0DC32',
  '#32C850',
  '#147828',
  '#32D2DC',
  '#3C64DC',
  '#14288C',
  '#C83CC8',
  '#A05A28'
];

export var DEMO_PUZZLE = {
  id: 0,
  title: 'Acid Drop',
  width: 8,
  height: 8,
  pixels: [
    [0, 0, 0,  0,  0,  0, 0, 0],
    [0, 3, 9,  9,  9,  9, 3, 0],
    [0, 3, 5,  9,  9,  5, 3, 0],
    [0, 0, 9, 10, 10,  9, 0, 0],
    [0, 0, 9, 10, 10,  9, 0, 0],
    [0, 3, 5,  9,  9,  5, 3, 0],
    [0, 0, 0,  9,  9,  0, 0, 0],
    [0, 0, 0,  0,  0,  0, 0, 0]
  ],
  palette: [
    '#CCCCCC', '#FFFFFF', '#C0C0C0', '#606060', '#000000',
    '#DC3232', '#8C1414', '#F08C1E', '#F0DC32',
    '#32C850', '#147828', '#32D2DC', '#3C64DC',
    '#14288C', '#C83CC8', '#A05A28'
  ]
};

export function getUsedIndices(pixels) {
  var seen = {};
  var result = [];
  for (var r = 0; r < pixels.length; r++) {
    for (var c = 0; c < pixels[r].length; c++) {
      var v = pixels[r][c];
      if (v > 0 && !seen[v]) {
        seen[v] = true;
        result.push(v);
      }
    }
  }
  result.sort(function(a, b) { return a - b; });
  return result;
}

export function makePainted(rows, cols) {
  var out = [];
  for (var r = 0; r < rows; r++) {
    var row = [];
    for (var c = 0; c < cols; c++) row.push(-1);
    out.push(row);
  }
  return out;
}

export function checkClear(pixels, painted) {
  for (var r = 0; r < pixels.length; r++) {
    for (var c = 0; c < pixels[r].length; c++) {
      var target = pixels[r][c];
      if (target > 0 && painted[r][c] !== target) return false;
    }
  }
  return true;
}

export function encodePainted(painted) {
  return JSON.stringify(painted);
}

export function decodePainted(json, rows, cols) {
  if (!json) return makePainted(rows, cols);
  var parsed;
  try {
    parsed = JSON.parse(json);
  } catch(e) {
    console.warn('pixelart-logic: decodePainted parse error');
    return makePainted(rows, cols);
  }
  if (!Array.isArray(parsed) || parsed.length !== rows) return makePainted(rows, cols);
  var result = [];
  for (var r = 0; r < rows; r++) {
    var row = parsed[r];
    if (!Array.isArray(row) || row.length !== cols) {
      result.push(Array(cols).fill(-1));
      continue;
    }
    var newRow = [];
    for (var c = 0; c < cols; c++) {
      var v = row[c];
      newRow.push((typeof v === 'number' && v >= -1 && v <= 15) ? v : -1);
    }
    result.push(newRow);
  }
  return result;
}

export function buildDisplayMap(usedIndices) {
  var map = {};
  for (var i = 0; i < usedIndices.length; i++) {
    map[usedIndices[i]] = i + 1;
  }
  return map;
}

export function hexLuminance(hex) {
  var s = (hex || '').replace('#', '');
  if (s.length < 6) return 0.5;
  var r = parseInt(s.slice(0, 2), 16) / 255;
  var g = parseInt(s.slice(2, 4), 16) / 255;
  var b = parseInt(s.slice(4, 6), 16) / 255;
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
