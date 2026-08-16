# Changelog

All notable changes to **FFACore** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release entries are generated automatically by `tools/release.sh` from the
commits between tags.

## [Unreleased]

## [1.21.44] - 2026-08-15
- Remove the Catastrophe moon core and recolor Moonbow purple
- Auto-deploy jar and resource pack to local test server on release/commit


## [1.21.43] - 2026-08-15
- Fix Catastrophe crash: pass required Float data to dragon_breath particles


## [1.21.42] - 2026-08-15
- Use vanilla lava texture and fix the Catastrophe crescent vortex crash


## [1.21.41] - 2026-08-15
- Rebuild Catastrophe as a crescent-blade vortex and reduce its range
- Add animated lava block model for the Nichirin lava bursts


## [1.21.40] - 2026-08-15
- Generate full changelogs and maintain CHANGELOG.md on release
- Use orange dust and glowing lava blocks for Nichirin; rebuild Catastrophe as purple rings


## [1.21.39] - 2026-08-15
- Fix Catastrophe: apply damage immediately and enlarge the expanding ring

## [1.21.38] - 2026-08-15
- Make lava projectiles vanish on impact and never place lava sources

## [1.21.37] - 2026-08-15
- Shoot real lava blocks for Nichirin abilities instead of invisible displays

## [1.21.36] - 2026-08-15
- Make Dancing Flash lava visible, aim Moonbow crescents, enrich ability VFX

## [1.21.35] - 2026-08-15
- Recolor Nichirin tooltip frame edge from white to fire orange

## [1.21.34] - 2026-08-15
- Shrink Dancing Flash default radius to 4 and scale its lava burst

## [1.21.33] - 2026-08-15
- Fix the landing shockwave blocks rendering dark/overlapping

## [1.21.32] - 2026-08-15
- Rename Kokoshibos Sword to Kokushibo and brighten its purple palette

## [1.21.31] - 2026-08-15
- Revert Nichirin to glass+lava, simplify Catastrophe, rework Moonbow to shot-on-click

## [1.21.30] - 2026-08-15
- Slow and stabilize the Catastrophe crescent vortex

## [1.21.29] - 2026-08-15
- Fix Nichirin flame VFX missing-texture checkerboard

## [1.21.28] - 2026-08-15
- Clean up the Catastrophe vortex into two uniform counter-rotating rings

## [1.21.27] - 2026-08-15
- Merge the AltarSMP pack into the FFACore resource pack

## [1.21.26] - 2026-08-15
- Shoot lava forward on Dancing Flash and sear targets through Fire Resistance

## [1.21.25] - 2026-08-15
- Add an earthquake landing shockwave after Clear Blue Sky's boost

## [1.21.24] - 2026-08-15
- Brighten the Kokushibo tooltip text to a vivid purple theme

## [1.21.23] - 2026-08-15
- Make Moonbow's boss bar bright purple to match Catastrophe

## [1.21.22] - 2026-08-15
- Give Nichirin abilities custom flame resource-pack models for its VFX

## [1.21.21] - 2026-08-15
- Make Catastrophe crescents spin flat around the vertical axis instead of tumbling vertically

## [1.21.20] - 2026-08-15
- Shoot real lava blocks out of Clear Blue Sky instead of ground fire

## [1.21.19] - 2026-08-15
- Enlarge Catastrophe to 20 blocks, make Moonbow crescents soar down with a white trail

## [1.21.18] - 2026-08-15
- Make Dancing Flash bigger and scale its VFX to the radius

## [1.21.17] - 2026-08-15
- Make Clear Blue Sky a 15-block radius by default

## [1.21.16] - 2026-08-15
- Strengthen Clear Blue Sky launch so it works in the air

## [1.21.15] - 2026-08-15
- Rework Kokushibo abilities to match the Fourteenth and Sixteenth Forms

## [1.21.14] - 2026-08-15
- Rework Clear Blue Sky, rename Enbu to Dancing Flash, drop custom sounds

## [1.21.13] - 2026-08-15
- Make Clear Blue Sky a full 360-degree circle

## [1.21.12] - 2026-08-15
- Reuse Altar SMP slash sounds for Nichirin abilities

## [1.21.11] - 2026-08-15
- Rebuild Nichirin VFX as canonical Hinokami Kagura forms

## [1.21.10] - 2026-08-15
- Match Clear Blue Sky boss bar to the lava theme

## [1.21.9] - 2026-08-14
- Use a real emissive core shader for ability VFX

## [1.21.8] - 2026-08-14
- Keep Enbu VFX cubes uniform (no stretched blocks)

## [1.21.7] - 2026-08-14
- Differentiate Nichirin ability VFX with lava block entities

## [1.21.6] - 2026-08-14
- Make ability animation speed/duration configurable

## [1.21.5] - 2026-08-14
- Rebuild ability VFX as bounded block-entity geometry

## [1.21.4] - 2026-08-14
- Rebuild Kokushibo ability VFX as clean particle effects

## [1.21.3] - 2026-08-14
- Rebuild Nichirin ability VFX as flowing particle waves

## [1.21.2] - 2026-08-14
- Switch Nichirin ability VFX from glass to glowing emissive blocks

## [1.21.1] - 2026-08-14
- Style ability cooldown boss bars with full technique names

## [1.21.0] - 2026-08-14
- Rework Upper Moon One passive and upgrade Nichirin ability VFX

## [1.20.6] - 2026-08-14
- Add /ffa customweapons resetcooldown to clear ability cooldowns

## [1.20.5] - 2026-08-14
- Brighten the Kokoshibos Sword tooltip frame to electric purple

## [1.20.4] - 2026-08-14
- Cast swap-key abilities from either hand; guard WorldEdit hook load

## [1.20.3] - 2026-08-14
- Drop right-click trigger; abilities follow the swap-hands keybind

## [1.20.2] - 2026-08-14
- Hide the enchantment glint on the Nichirin Blade

## [1.20.1] - 2026-08-14
- Restore the Kokoshibos Sword bright purple theme

## [1.20.0] - 2026-08-14
- Fix weapon ability keybinds with model-data detection and right-click trigger

## [1.19.0] - 2026-08-14
- Replace Crescent Throw with Catastrophe, Tenman Crescent Moon vortex

## [1.18.0] - 2026-08-14
- Drop stray [Offhand] tag and dramatize ability VFX

## [1.17.0] - 2026-08-14
- Nest custom weapon configs under a Custom Weapons submenu

## [1.16.0] - 2026-08-14
- Soften Kokoshibo theme, lock offhand placement, add cooldown boss bars

## [1.15.0] - 2026-08-14
- Add themed Custom Weapons section to /ffa config

## [1.14.0] - 2026-08-14
- Consolidate weapon giving into /ffa customweapons

## [1.13.0] - 2026-08-14
- Add the Kokoshibos Sword Upper Moon One weapon

## [1.12.0] - 2026-08-14
- Add the Nichirin Blade Demon Slayer weapon with abilities

## [1.11.0] - 2026-08-14
- Remove standalone commands; /ffa is the only command

## [1.10.0] - 2026-08-14
- Fix config sections failing to open with "key must be a valid input name"

## [1.9.0] - 2026-08-14
- Remove the manual reload button; config auto-applies on every save

## [1.8.0] - 2026-08-14
- Restore working section submenus in the config menu

## [1.7.0] - 2026-08-14
- Theme the config menu white instead of cyan

## [1.6.0] - 2026-08-14
- Consolidate every command under /ffa

## [1.5.0] - 2026-08-14
- Rework config menu into a single dialog and harden input building

## [1.4.0] - 2026-08-14
- Use animated Altar cutlass/bloodlust tooltips for currencies
- Add in-game dialog config menu with realtime apply
- Add reusable manual release script

## [1.2.0] - 2026-08-14
- Force italic on ember gradient names to match Kill Token
