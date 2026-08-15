// Generates the fire-themed VFX textures used by the Nichirin Blade ability
// models (flame_blade + flame_orb). Pure Node, no dependencies.
//
// Usage: node tools/gen_vfx_textures.js
'use strict';

const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

const SIZE = 64;
const OUT_DIR = path.join(__dirname, '..', 'resourcepack', 'assets', 'ffacore', 'textures', 'vfx');

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

// --- Colour helpers ---------------------------------------------------------

function lerp(a, b, t) {
  return a + (b - a) * t;
}

function clamp01(t) {
  return t < 0 ? 0 : t > 1 ? 1 : t;
}

function smoothstep(t) {
  const x = clamp01(t);
  return x * x * (3 - 2 * x);
}

// Fire gradient: white-hot core -> yellow -> orange -> deep red.
function fire(t) {
  const stops = [
    [0.00, 255, 255, 252],
    [0.22, 255, 244, 170],
    [0.48, 255, 178, 74],
    [0.74, 238, 106, 34],
    [1.00, 178, 48, 20],
  ];
  for (let i = 0; i < stops.length - 1; i++) {
    const [t0, r0, g0, b0] = stops[i];
    const [t1, r1, g1, b1] = stops[i + 1];
    if (t <= t1) {
      const k = clamp01((t - t0) / (t1 - t0));
      return [
        Math.round(lerp(r0, r1, k)),
        Math.round(lerp(g0, g1, k)),
        Math.round(lerp(b0, b1, k)),
      ];
    }
  }
  return [178, 48, 20];
}

// --- flame_blade: a curved slash (annular arc) ------------------------------

function flameBlade(x, y) {
  const cx = SIZE / 2;
  const cy = SIZE / 2;
  const dx = x - cx;
  const dy = y - cy;
  const dist = Math.hypot(dx, dy);

  const rInner = 12;
  const rOuter = 30;
  const halfAngle = 0.92; // radians (~53 deg) each side of +X
  const angle = Math.atan2(dy, dx);

  // Annular band.
  if (dist < rInner - 2 || dist > rOuter + 2) {
    return [0, 0, 0, 0];
  }

  // Angle mask with soft edges.
  const angEdge = 1 - smoothstep((Math.abs(angle) - halfAngle + 0.18) / 0.36);

  // Radial fade at the inner/outer rims.
  const radialT = clamp01((dist - rInner) / (rOuter - rInner));
  const rimFade = smoothstep((dist - rInner + 2) / 4) * (1 - smoothstep((dist - rOuter + 2) / 4));

  // Colour: hot on the inner (cutting) edge, red on the outer edge.
  const [r, g, b] = fire(0.25 + radialT * 0.75);

  const alpha = Math.round(255 * angEdge * rimFade);
  return [r, g, b, alpha];
}

// --- flame_orb: radial solar glow -------------------------------------------

function flameOrb(x, y) {
  const cx = SIZE / 2;
  const cy = SIZE / 2;
  const dist = Math.hypot(x - cx, y - cy) / (SIZE / 2);
  if (dist >= 1) {
    return [0, 0, 0, 0];
  }
  const [r, g, b] = fire(dist);
  // Bright, soft falloff to transparency at the rim.
  const alpha = Math.round(255 * Math.pow(1 - dist, 1.4));
  return [r, g, b, alpha];
}

fs.mkdirSync(OUT_DIR, { recursive: true });
writePng(path.join(OUT_DIR, 'flame_blade.png'), flameBlade);
writePng(path.join(OUT_DIR, 'flame_orb.png'), flameOrb);
