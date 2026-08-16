// Generates the animated molten-lava block texture used by the Nichirin
// ability lava bursts. Pure Node, no dependencies.
//
// Writes a 16x64 image (4 stacked 16x16 frames, scrolling vertically) plus a
// .mcmeta so the lava visibly flows. The shroomlight block model in the pack
// points at this texture, so BlockDisplay entities of shroomlight render as
// flowing lava.
//
// Usage: node tools/gen_lava_texture.js
'use strict';

const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

const FRAME = 16;
const FRAMES = 4;
const WIDTH = FRAME;
const HEIGHT = FRAME * FRAMES;
const OUT_DIR = path.join(__dirname, '..', 'resourcepack', 'assets', 'ffacore',
    'textures', 'block');

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

function writePng(file, width, height, pixelFn) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter: none
    for (let x = 0; x < width; x++) {
      const [r, g, b, a] = pixelFn(x, y);
      const o = y * (width * 4 + 1) + 1 + x * 4;
      raw[o] = r;
      raw[o + 1] = g;
      raw[o + 2] = b;
      raw[o + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
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

// --- Deterministic noise -----------------------------------------------------

function hash(x, y, seed) {
  let n = Math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453;
  return n - Math.floor(n);
}

// A molten lava tile: a mottled dark-red base with bright orange veins and
// white-hot cracks. Each frame shifts the pattern up 4 px so the lava reads
// as flowing, and the shift wraps so the 4-frame loop is seamless.
function lavaPixel(x, y) {
  const frame = Math.floor(y / FRAME);
  const ly = y % FRAME;
  const yy = (ly + frame * 4) % FRAME; // scroll upward, wrap every 4 frames

  const base = hash(x, yy, 1);
  const vein = hash(x + yy * 2, x - yy * 3, 5);
  const crack = hash(x * 3 + 1, yy * 3 + 2, 9);

  if (crack > 0.90) {
    return [255, 246, 205, 255]; // white-hot
  }
  if (vein > 0.62) {
    const t = (vein - 0.62) / 0.28;
    // bright orange vein, hottest at its core
    return [255, Math.round(96 + 30 * t), Math.round(0 + 40 * t), 255];
  }
  // mottled dark base from deep red to burnt orange
  const t = base;
  return [Math.round(112 + 44 * t), Math.round(20 + 28 * t), 0, 255];
}

fs.mkdirSync(OUT_DIR, { recursive: true });
writePng(path.join(OUT_DIR, 'lava_orb.png'), WIDTH, HEIGHT, lavaPixel);

const mcmeta = {
  animation: {
    frametime: 3,
  },
};
const mcmetaPath = path.join(OUT_DIR, 'lava_orb.png.mcmeta');
fs.writeFileSync(mcmetaPath, JSON.stringify(mcmeta, null, 2) + '\n');
console.log('wrote', mcmetaPath);
