// Generates the white crescent "gleam" texture used by the Moonbow, Half
// Moon launched crescents. Pure Node, no dependencies.
//
// Usage: node tools/gen_white_crescent.js
'use strict';

const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

const SIZE = 64;
const OUT_DIR = path.join(__dirname, '..', 'resourcepack', 'assets', 'ffacore',
    'textures', 'item');

// --- Minimal PNG writer -----------------------------------------------------

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}

function writePng(file, pixelFn) {
  const raw = Buffer.alloc((SIZE * 4 + 1) * SIZE);
  for (let y = 0; y < SIZE; y++) {
    raw[y * (SIZE * 4 + 1)] = 0; // filter: none
    for (let x = 0; x < SIZE; x++) {
      const [r, g, b, a] = pixelFn(x, y);
      const o = y * (SIZE * 4 + 1) + 1 + x * 4;
      raw[o] = r;
      raw[o + 1] = g;
      raw[o + 2] = b;
      raw[o + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(SIZE, 0);
  ihdr.writeUInt32BE(SIZE, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // colour type RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const out = Buffer.concat([
    sig,
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
  fs.writeFileSync(file, out);
  console.log('wrote', file);
}

function clamp01(t) {
  return t < 0 ? 0 : t > 1 ? 1 : t;
}

function smoothstep(t) {
  const x = clamp01(t);
  return x * x * (3 - 2 * x);
}

// A crescent moon: the sliver between a large outer circle and a smaller
// inner circle nudged to one side. Rendered white with soft feathered edges
// so it reads as a bright gleam.
function whiteCrescent(x, y) {
  const cx = SIZE / 2;
  const cy = SIZE / 2;
  const dx = x - cx;
  const dy = y - cy;

  const outerR = 27;
  const innerCx = cx + 11; // bite taken out of the right side
  const innerR = 23;
  const dOuter = Math.hypot(dx, dy);
  const dInner = Math.hypot(x - innerCx, y - cy);

  if (dOuter > outerR || dInner < innerR) {
    return [0, 0, 0, 0];
  }

  // Feather the two edges of the sliver.
  const outerEdge = 1 - smoothstep((dOuter - outerR + 2.5) / 2.5);
  const innerEdge = smoothstep((dInner - innerR) / 2.5);
  const alpha = Math.round(255 * Math.min(outerEdge, innerEdge));
  return [255, 255, 255, alpha];
}

fs.mkdirSync(OUT_DIR, { recursive: true });
writePng(path.join(OUT_DIR, 'white_crescent.png'), whiteCrescent);
