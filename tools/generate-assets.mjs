// Generates the FFACore resource pack artwork with a minimal, dependency-free
// PNG encoder (zlib + CRC32). Run with: node tools/generate-assets.mjs
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', 'resourcepack', 'assets');

// ---------------------------------------------------------------------------
// Minimal PNG encoder (RGBA, 8-bit)
// ---------------------------------------------------------------------------

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

function crc32(bytes) {
  let crc = -1;
  for (let i = 0; i < bytes.length; i++) {
    crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ bytes[i]) & 0xff];
  }
  return (crc ^ -1) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function encodePng(width, height, rgba) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6; // RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }

  return Buffer.concat([
    signature,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------------------------------------------------------------------------
// Canvas helpers
// ---------------------------------------------------------------------------

function canvas(w, h) {
  return { w, h, data: Buffer.alloc(w * h * 4) };
}

function px(c, x, y, r, g, b, a = 255) {
  if (x < 0 || y < 0 || x >= c.w || y >= c.h) return;
  const i = (y * c.w + x) * 4;
  c.data[i] = r;
  c.data[i + 1] = g;
  c.data[i + 2] = b;
  c.data[i + 3] = a;
}

function blend(c, x, y, r, g, b, a) {
  if (x < 0 || y < 0 || x >= c.w || y >= c.h || a <= 0) return;
  const i = (y * c.w + x) * 4;
  const sa = a / 255;
  const da = c.data[i + 3] / 255;
  const outA = sa + da * (1 - sa);
  if (outA <= 0) return;
  c.data[i] = Math.round((r * sa + c.data[i] * da * (1 - sa)) / outA);
  c.data[i + 1] = Math.round((g * sa + c.data[i + 1] * da * (1 - sa)) / outA);
  c.data[i + 2] = Math.round((b * sa + c.data[i + 2] * da * (1 - sa)) / outA);
  c.data[i + 3] = Math.round(outA * 255);
}

function lerp(a, b, t) {
  return Math.round(a + (b - a) * t);
}

function rgb(c) {
  return [(c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff];
}

function save(rel, c) {
  const path = join(ROOT, rel);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, encodePng(c.w, c.h, c.data));
  console.log('wrote', rel);
}

// ---------------------------------------------------------------------------
// 1. Tooltip backgrounds and frames (nine-slice, 100x100)
// ---------------------------------------------------------------------------
//
// These four sprites are vendored 1:1 from the Altar SMP texture pack and
// ship as committed PNGs under assets/ffacore/textures/gui/sprites/tooltip/.
// They are intentionally NOT generated here so the art stays identical:
//   ocean_background.png  <- Altar "tidebreaker" (deep ocean teal)
//   ocean_frame.png       <- Altar "tidebreaker"
//   ember_background.png  <- Altar "red" (deep ember red)
//   ember_frame.png       <- Altar "red"

// ---------------------------------------------------------------------------
// 2. Per-theme tooltip backgrounds and frames (100x100 nine-slice)
// ---------------------------------------------------------------------------
//
// Each custom-item theme gets its own gradient tooltip: the background tints
// the whole panel and the frame draws a matching gradient border, so the
// tooltip colour matches the item title gradient in CustomItemManager.

const THEMES = {
  ember:  { from: 0xffe0a3, to: 0xe04646 },
  frost:  { from: 0xe6ffff, to: 0x429bff },
  earth:  { from: 0xffe2a4, to: 0x8b4f2a },
  gold:   { from: 0xfff3a1, to: 0xe58a00 },
  void:   { from: 0xf9c1ff, to: 0x6e2cad },
  purple: { from: 0xf0d1ff, to: 0x8b5cf6 },
};

function themeGradient(y, from, to) {
  const t = y / 99;
  return [lerp(from[0], to[0], t), lerp(from[1], to[1], t), lerp(from[2], to[2], t)];
}

function tooltipBackground(theme) {
  const { from, to } = THEMES[theme];
  const f = rgb(from);
  const t = rgb(to);
  const c = canvas(100, 100);
  // Tint the whole panel with a subtle vertical gradient.
  for (let y = 0; y < 100; y++) {
    const [r, g, b] = themeGradient(y, f, t);
    for (let x = 0; x < 100; x++) {
      px(c, x, y, r, g, b, 85);
    }
  }
  return c;
}

function tooltipFrame(theme) {
  const { from, to } = THEMES[theme];
  const f = rgb(from);
  const t = rgb(to);
  const c = canvas(100, 100);
  // 2px gradient border: left/right bars and top/bottom bars.
  for (let y = 7; y <= 92; y++) {
    const [r, g, b] = themeGradient(y, f, t);
    for (const x of [7, 8]) px(c, x, y, r, g, b, 255);
    for (const x of [91, 92]) px(c, x, y, r, g, b, 255);
  }
  for (let x = 8; x <= 91; x++) {
    const [r, g, b] = themeGradient(7, f, t);
    for (const y of [7, 8]) px(c, x, y, r, g, b, 255);
    const [r2, g2, b2] = themeGradient(92, f, t);
    for (const y of [91, 92]) px(c, x, y, r2, g2, b2, 255);
  }
  return c;
}

function saveMcmeta(rel) {
  const path = join(ROOT, rel);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify({
    gui: {
      scaling: {
        type: 'nine_slice',
        width: 100,
        height: 100,
        border: 10,
        stretch_inner: true,
      },
    },
  }, null, 2) + '\n');
  console.log('wrote', rel);
}

for (const theme of Object.keys(THEMES)) {
  save(`ffacore/textures/gui/sprites/tooltip/${theme}_background.png`,
    tooltipBackground(theme));
  save(`ffacore/textures/gui/sprites/tooltip/${theme}_frame.png`,
    tooltipFrame(theme));
  saveMcmeta(`ffacore/textures/gui/sprites/tooltip/${theme}_background.png.mcmeta`);
  saveMcmeta(`ffacore/textures/gui/sprites/tooltip/${theme}_frame.png.mcmeta`);
}

// ---------------------------------------------------------------------------
// 3. Kill Token (ember star) 16x16
// ---------------------------------------------------------------------------

function killToken() {
  const c = canvas(16, 16);
  const from = rgb(0xff5c5c);
  const to = rgb(0x8f0e0e);
  for (let y = 0; y < 16; y++) {
    for (let x = 0; x < 16; x++) {
      const dx = x - 7.5;
      const dy = y - 7.5;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const ang = Math.atan2(dy, dx);
      const spike = Math.abs(Math.cos(2 * ang));
      const radius = 7.6 * (0.38 + 0.62 * Math.pow(spike, 1.6));
      if (dist > radius) continue;
      const t = Math.min(1, dist / radius);
      px(c, x, y, lerp(from[0], to[0], t), lerp(from[1], to[1], t),
        lerp(from[2], to[2], t), 255);
      if (dist < 2.2) blend(c, x, y, 255, 255, 255, 90);
    }
  }
  return c;
}

save('ffacore/textures/item/kill_token.png', killToken());

// ---------------------------------------------------------------------------
// 4. AFK Shard (ocean crystal) 16x16
// ---------------------------------------------------------------------------

function afkShard() {
  const c = canvas(16, 16);
  const from = rgb(0x6fd6ff);
  const to = rgb(0x0a2a6b);
  for (let y = 0; y < 16; y++) {
    for (let x = 0; x < 16; x++) {
      const nx = Math.abs(x - 7.5) / 7.5;
      const ny = Math.abs(y - 7.5) / 7.5;
      if (nx + ny > 1.0) continue;
      const t = y / 15 + nx * 0.35;
      px(c, x, y, lerp(from[0], to[0], t), lerp(from[1], to[1], t),
        lerp(from[2], to[2], t), 255);
    }
  }
  for (let y = 2; y < 14; y++) {
    const x = Math.round(7.5 - 3 * (1 - Math.abs(y - 7.5) / 7.5));
    blend(c, x, y, 255, 255, 255, 60);
    blend(c, x + 1, y, 255, 255, 255, 30);
  }
  return c;
}

save('ffacore/textures/item/afk_shard.png', afkShard());

// ---------------------------------------------------------------------------
// 5. Pack icon 128x128
// ---------------------------------------------------------------------------

function packIcon() {
  const c = canvas(128, 128);
  const top = rgb(0x0a2a6b);
  const bottom = rgb(0x58c7f3);
  for (let y = 0; y < 128; y++) {
    const t = y / 127;
    const r = lerp(top[0], bottom[0], t);
    const g = lerp(top[1], bottom[1], t);
    const b = lerp(top[2], bottom[2], t);
    for (let x = 0; x < 128; x++) px(c, x, y, r, g, b, 255);
  }
  for (let y = 0; y < 128; y++) {
    for (let x = 0; x < 128; x++) {
      const nx = Math.abs(x - 64) / 64;
      const ny = Math.abs(y - 64) / 64;
      if (nx + ny <= 0.55) {
        blend(c, x, y, 0xeaf9ff, 0xeaf9ff, 0xeaf9ff, 40 + 160 * (1 - nx - ny));
      }
    }
  }
  for (let y = 16; y < 112; y++) {
    const x = Math.round(64 - 28 * (1 - Math.abs(y - 64) / 56));
    blend(c, x, y, 255, 255, 255, 80);
  }
  return c;
}

const packIconPath = join(ROOT, '..', 'pack.png');
writeFileSync(packIconPath, encodePng(128, 128, packIcon().data));
console.log('wrote pack.png');
