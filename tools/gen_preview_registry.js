#!/usr/bin/env node
/**
 * Scans the merged FFA-Core resource pack and generates
 * src/main/resources/preview-items.json - the catalog used by the
 * /ffa preview command to hand out every custom item in the pack.
 *
 * Two mechanisms feed the catalog:
 *   1. custom_model_data dispatches found in assets/minecraft/items/*.json
 *      (base material + cmd value; these are the guaranteed-rendering path).
 *   2. Item model definitions in assets/nexo/items/*.json that are not
 *      covered by a dispatch - previewed via the item_model component with
 *      a material guessed from the model path.
 *
 * Entries are de-duplicated by normalized model path, preferring the
 * custom_model_data variant.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const TOOLS = __dirname;
const PACK = path.join(TOOLS, '..', 'resourcepack');
const OUT = path.join(TOOLS, '..', 'src', 'main', 'resources', 'preview-items.json');

const ITEMS_DIR = path.join(PACK, 'assets', 'minecraft', 'items');
const NEXO_DIR = path.join(PACK, 'assets', 'nexo', 'items');

/** Resolves a model object to a plain model path, following conditions. */
function resolveModel(m) {
    if (!m) return null;
    if (typeof m === 'string') return m;
    if (m.type === 'model') return m.model || null;
    if (m.type === 'condition') {
        return resolveModel(m.on_false) || resolveModel(m.on_true) || resolveModel(m.fallback);
    }
    if (m.type === 'select') {
        return resolveModel(m.fallback) || null;
    }
    if (m.type === 'range_dispatch' || m.type === 'dispatch') {
        if (Array.isArray(m.entries) && m.entries.length > 0) {
            const flat = [];
            for (const e of m.entries) {
                if (e.model && typeof e.model === 'string') flat.push(e.model);
            }
            if (flat.length > 0) return flat[0];
            const first = resolveModel(m.entries[0].model);
            if (first) return first;
        }
        return resolveModel(m.fallback);
    }
    return null;
}

function normalizeModel(model) {
    if (!model) return null;
    let m = model.replace(/\.json$/, '');
    if (m.startsWith('minecraft:')) m = m.slice('minecraft:'.length);
    return m;
}

function vanillaModel(model) {
    if (!model) return true;
    const m = normalizeModel(model);
    return m.startsWith('item/') || m.startsWith('block/')
        || m.startsWith('minecraft/') || m === '';
}

function prettyName(model) {
    const norm = normalizeModel(model);
    const leaf = norm.includes(':') ? norm.split(':')[0] + '/' + norm.split(':')[1] : norm;
    const base = leaf.split('/').pop().replace(/[_-]+/g, ' ');
    return base.replace(/\b\w/g, (c) => c.toUpperCase());
}

function guessMaterial(model) {
    const m = model.toLowerCase();
    if (m.includes('crossbow')) return 'CROSSBOW';
    if (m.includes('bow')) return 'BOW';
    if (m.includes('shield')) return 'SHIELD';
    if (m.includes('pickaxe')) return 'NETHERITE_PICKAXE';
    if (m.includes('shovel')) return 'NETHERITE_SHOVEL';
    if (m.includes('axe')) return 'NETHERITE_AXE';
    if (m.includes('hoe')) return 'NETHERITE_HOE';
    if (m.includes('boots')) return 'NETHERITE_BOOTS';
    if (m.includes('leggings')) return 'NETHERITE_LEGGINGS';
    if (m.includes('helmet') || m.includes('_hat') || m.includes('halo')
            || m.includes('goggles') || m.includes('monocle') || m.includes('crown')
            || m.includes('headband') || m.includes('mask')) {
        return 'NETHERITE_HELMET';
    }
    if (m.includes('wing') || m.includes('backwear') || m.includes('backpack')
            || m.includes('_tank') || m.includes('chestplate') || m.includes('_body')) {
        return 'NETHERITE_CHESTPLATE';
    }
    if (m.includes('trident')) return 'TRIDENT';
    return 'NETHERITE_SWORD';
}

function categoryOf(model) {
    const m = model.toLowerCase();
    if (m.includes('bowl')) return 'Decor';
    if (m.includes('crossbow') || m.includes('/bow') || m.includes('bow_')
            || m.includes('_bow')) return 'Bows';
    if (m.includes('sword') || m.includes('_blade') || m.includes('scythe')
            || m.includes('trident') || m.includes('staff') || m.includes('spear')
            || m.includes('mace') || m.includes('hammer') || m.includes('kanabo')
            || m.includes('anchor') || m.includes('cleaver') || m.includes('sickle')
            || m.includes('katana') || m.includes('warpick') || m.includes('saber')
            || m.includes('scepter') || m.includes('wand') || m.includes('greatsword')) {
        return 'Weapons';
    }
    if (m.includes('pickaxe') || m.includes('shovel') || m.includes('_axe')
            || m.includes('_hoe') || m.includes('_tool')) {
        return 'Tools';
    }
    if (m.includes('wing') || m.includes('halo') || m.includes('cosmetic')
            || m.includes('_hat') || m.includes('backpack') || m.includes('helmet')
            || m.includes('goggles') || m.includes('floatie') || m.includes('surfboard')
            || m.includes('umbrella') || m.includes('snorkel') || m.includes('crown')
            || m.includes('mask') || m.includes('scuba') || m.includes('_bag')) {
        return 'Cosmetics';
    }
    if (m.includes('sign') || m.includes('furniture') || m.includes('_chair')
            || m.includes('_table') || m.includes('bench') || m.includes('lantern')
            || m.includes('_pot') || m.includes('shop') || m.includes('street')
            || m.includes('garden') || m.includes('outdoor') || m.includes('pond')
            || m.includes('phone') || m.includes('gnome') || m.includes('raft')
            || m.includes('trampolin') || m.includes('wheelbarrow') || m.includes('_pool')) {
        return 'Decor';
    }
    if (m.includes('icon') || m.includes('_gui') || m.includes('menu')) {
        return 'UI';
    }
    return 'Misc';
}

function walkDir(dir) {
    const out = [];
    if (!fs.existsSync(dir)) return out;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            out.push(...walkDir(full));
        } else if (entry.name.endsWith('.json')) {
            out.push(full);
        }
    }
    return out;
}

function readJson(file) {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function main() {
    const byModel = new Map(); // normalized model path -> entry
    const order = [];

    // Pass 1: custom_model_data dispatches.
    for (const file of walkDir(ITEMS_DIR)) {
        const rel = path.relative(ITEMS_DIR, file).split(path.sep).join('/');
        const material = rel.replace(/\.json$/, '').toUpperCase();
        let data;
        try {
            data = readJson(file);
        } catch (e) {
            console.warn(`  skip unreadable ${rel}: ${e.message}`);
            continue;
        }
        const modelObj = data.model;
        if (!modelObj || modelObj.type !== 'range_dispatch') continue;
        const entries = Array.isArray(modelObj.entries) ? modelObj.entries : [];
        for (const entry of entries) {
            const model = resolveModel(entry.model);
            if (!model || vanillaModel(model)) continue;
            const norm = normalizeModel(model);
            if (byModel.has(norm)) continue;
            const e = {
                name: prettyName(model),
                material,
                cmd: Math.round(entry.threshold),
                model: norm,
                category: categoryOf(model),
            };
            byModel.set(norm, e);
            order.push(e);
        }
    }

    // Pass 2: nexo item definitions not covered above.
    for (const file of walkDir(NEXO_DIR)) {
        const rel = path.relative(NEXO_DIR, file).split(path.sep).join('/');
        let data;
        try {
            data = readJson(file);
        } catch (e) {
            console.warn(`  skip unreadable ${rel}: ${e.message}`);
            continue;
        }
        const model = resolveModel(data.model || data);
        if (!model || vanillaModel(model)) continue;
        const norm = normalizeModel(model);
        if (byModel.has(norm)) continue;
        const defId = rel.replace(/\.json$/, '');
        const e = {
            name: prettyName(norm),
            material: guessMaterial(norm),
            itemModel: 'nexo:' + defId,
            model: norm,
            category: categoryOf(norm),
        };
        byModel.set(norm, e);
        order.push(e);
    }

    if (order.length === 0) {
        console.error('No preview items found - aborting.');
        process.exit(1);
    }

    order.sort((a, b) => a.category.localeCompare(b.category)
            || a.name.localeCompare(b.name));

    const out = {
        generated: new Date().toISOString().slice(0, 10),
        items: order,
    };
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(OUT, JSON.stringify(out, null, 2) + '\n', 'utf8');
    console.log(`Wrote ${order.length} preview items to ${path.relative(TOOLS, OUT)}`);
    const counts = {};
    for (const e of order) counts[e.category] = (counts[e.category] || 0) + 1;
    console.log('Categories:', JSON.stringify(counts));
}

main();