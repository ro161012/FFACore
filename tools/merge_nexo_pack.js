#!/usr/bin/env node
/**
 * Merges the external Nexo resource pack (the "asdasdads" folder) into
 * FFA-Core's own resourcepack directory so the shipped FFACore-Resourcepack
 * zip contains everything: FFACore/Altar assets + all Nexo items.
 *
 * Conflicts are merged per file kind:
 *   - assets/minecraft/items/*.json  -> range_dispatch entries are combined
 *     (thresholds de-duplicated, sorted) so both packs' custom-model-data
 *     ranges keep working on the same base item.
 *   - assets/minecraft/font/default.json -> provider lists are concatenated.
 *   - lang / sounds JSON -> key maps are merged (FFACore wins collisions).
 *   - pack.mcmeta -> FFACore description, min/max format raised, and the
 *     Nexo overlay entries + overlay directories are carried over.
 *
 * Junk files (desktop.ini, Nexo watermark files) are not copied.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const TOOLS = __dirname;
const DEST = path.join(TOOLS, '..', 'resourcepack');
const SRC = path.join(TOOLS, '..', '..', 'asdasdads');

const JUNK = new Set(['desktop.ini', '.index']);
const HEX_WATERMARK = /^[0-9a-f]{16}$/;

// Files that exist in both packs and need a real merge.
const MERGE_ITEMS = new Set([
    'assets/minecraft/items/bow.json',
    'assets/minecraft/items/crossbow.json',
    'assets/minecraft/items/mace.json',
    'assets/minecraft/items/netherite_axe.json',
    'assets/minecraft/items/netherite_pickaxe.json',
    'assets/minecraft/items/netherite_spear.json',
    'assets/minecraft/items/netherite_sword.json',
    'assets/minecraft/items/paper.json',
]);
const MERGE_JSON_MAPS = new Set([
    'assets/minecraft/lang/en_us.json',
    'assets/minecraft/sounds.json',
]);
const MERGE_FONT = 'assets/minecraft/font/default.json';

function walk(dir) {
    const out = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            out.push(...walk(full));
        } else {
            out.push(full);
        }
    }
    return out;
}

function readJson(file) {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function writeJson(file, obj) {
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n', 'utf8');
}

/** Merges two custom_model_data range_dispatch objects into one. */
function mergeRangeDispatch(ours, theirs) {
    const byThreshold = new Map();
    for (const entry of [...(ours.model.entries || []), ...(theirs.model.entries || [])]) {
        byThreshold.set(entry.threshold, entry);
    }
    const entries = [...byThreshold.values()].sort((a, b) => a.threshold - b.threshold);
    const merged = {
        model: {
            type: ours.model.type || 'range_dispatch',
            property: ours.model.property || 'custom_model_data',
            entries,
        },
    };
    if (merged.model.property === 'custom_model_data') {
        merged.model.fallback = ours.model.fallback || theirs.model.fallback || {
            type: 'model', model: 'minecraft:item/custom_model_data',
        };
    }
    // Preserve extras like oversized_in_gui from either side.
    for (const extra of ['oversized_in_gui']) {
        if (theirs[extra] !== undefined && ours[extra] === undefined) {
            merged[extra] = theirs[extra];
        }
    }
    return merged;
}

function copyFile(srcFile, rel) {
    const destFile = path.join(DEST, rel);
    fs.mkdirSync(path.dirname(destFile), { recursive: true });
    fs.copyFileSync(srcFile, destFile);
    console.log(`  copy  ${rel}`);
}

function main() {
    if (!fs.existsSync(SRC)) {
        console.error(`Source pack not found: ${SRC}`);
        process.exit(1);
    }
    fs.mkdirSync(DEST, { recursive: true });

    let copied = 0;
    let merged = 0;
    for (const srcFile of walk(SRC)) {
        const rel = path.relative(SRC, srcFile).split(path.sep).join('/');
        const base = path.basename(rel);
        if (JUNK.has(base) || HEX_WATERMARK.test(base)) {
            continue;
        }
        const destFile = path.join(DEST, rel);

        if (MERGE_ITEMS.has(rel) && fs.existsSync(destFile)) {
            const mergedObj = mergeRangeDispatch(readJson(destFile), readJson(srcFile));
            writeJson(destFile, mergedObj);
            console.log(`  merge ${rel} (${mergedObj.model.entries.length} entries)`);
            merged++;
        } else if (rel === MERGE_FONT && fs.existsSync(destFile)) {
            const ours = readJson(destFile);
            const theirs = readJson(srcFile);
            ours.providers = [...(ours.providers || []), ...(theirs.providers || [])];
            writeJson(destFile, ours);
            console.log(`  merge ${rel} (${ours.providers.length} providers)`);
            merged++;
        } else if (MERGE_JSON_MAPS.has(rel) && fs.existsSync(destFile)) {
            const ours = readJson(destFile);
            const theirs = readJson(srcFile);
            for (const [key, value] of Object.entries(theirs)) {
                if (!(key in ours)) {
                    ours[key] = value;
                }
            }
            writeJson(destFile, ours);
            console.log(`  merge ${rel} (${Object.keys(ours).length} keys)`);
            merged++;
        } else if (rel === 'pack.mcmeta') {
            // Handled after the loop; the destination pack.mcmeta is rebuilt.
            continue;
        } else {
            copyFile(srcFile, rel);
            copied++;
        }
    }

    // Rebuild pack.mcmeta with FFACore's description + Nexo's overlays.
    const destMcmeta = path.join(DEST, 'pack.mcmeta');
    const ours = readJson(destMcmeta);
    const theirs = readJson(path.join(SRC, 'pack.mcmeta'));
    if (theirs.overlays) {
        ours.overlays = theirs.overlays;
    }
    ours.pack = {
        description: ours.pack.description + " \u00a78\u00b7\u00a7e Nexo items",
        min_format: Math.min(ours.pack.min_format || 75, theirs.pack.min_format || 75),
        max_format: Math.max(ours.pack.max_format || 199, theirs.pack.max_format || 199),
    };
    if (theirs.sodium) {
        ours.sodium = theirs.sodium;
    }
    writeJson(destMcmeta, ours);
    console.log(`  merge pack.mcmeta (formats ${ours.pack.min_format}-${ours.pack.max_format})`);

    // Copy the versioned overlay directories (nexo_1_21_1 ... nexo_1_21_11).
    for (const dir of fs.readdirSync(SRC, { withFileTypes: true })) {
        if (dir.isDirectory() && dir.name.startsWith('nexo_')) {
            for (const f of walk(path.join(SRC, dir.name))) {
                const rel = path.relative(SRC, f).split(path.sep).join('/');
                copyFile(f, rel);
                copied++;
            }
        }
    }

    console.log(`\nDone: ${copied} files copied, ${merged} files merged.`);
}

main();