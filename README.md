# FFACore

[![Build](https://img.shields.io/github/actions/workflow/status/ro161012/FFACore/build.yml?branch=main&logo=github&label=build)](https://github.com/ro161012/FFACore/actions)
[![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-00A8A8)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)](https://adoptium.net)
[![License](https://img.shields.io/github/license/ro161012/FFACore?label=license)](LICENSE)

An all-in-one core for Free-For-All servers, combining three systems into a
single Paper plugin:

| Subsystem | What it does |
|---|---|
| **Arena regeneration** | Snapshot an arena once, restore it instantly after every match. |
| **Kill Token currency** | Players earn an ember-themed Kill Token for PvP kills, with pair anti-farming and killstreak multipliers. |
| **AFK zones** | Designate regions where idle players earn ocean-themed **AFK Shards**. |

Both currencies ship with a custom resource pack: unique item textures plus
gradient tooltip backgrounds.

## Installation

1. Download `FFACore-<version>.jar` from
   [Releases](https://github.com/ro161012/FFACore/releases).
2. Place it in your server's `plugins/` folder and restart.
3. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
   for scoreboard support and [WorldEdit](https://enginehub.org/worldedit/)
   for the fastest arena restore mode.
4. (Recommended) Distribute `FFACore-Resourcepack.zip` as your server
   resource pack so the custom currency textures and gradient tooltips render.

A default `plugins/FFACore/config.yml` is created on first run.

## Quick start

```text
/arena wand              # receive the selection wand
# left-click a block     # set position 1
# right-click a block    # set position 2
/arena create duel       # create an arena (snapshot saved automatically)
/arena setspawn duel     # set the spawn point
/arena regenerate duel   # restore the arena to its saved state

/afk wand                # receive the selection wand
/afk create lounge       # create an AFK zone from your selection
# players standing idle inside now earn AFK Shards
```

## Commands

### `/arena` (alias `/ar`)

| Command | Permission | Description |
|---|---|---|
| `/arena help` | `ffacore.arena.use` | Show the command list |
| `/arena wand` | `ffacore.arena.wand` | Receive the selection tool |
| `/arena create <name>` | `ffacore.arena.create` | Create an arena from your selection |
| `/arena list` | `ffacore.arena.use` | List all arenas |
| `/arena info <name>` | `ffacore.arena.use` | Show arena details |
| `/arena rename <old> <new>` | `ffacore.arena.create` | Rename an arena |
| `/arena setspawn <name>` | `ffacore.arena.create` | Set the arena spawn point |
| `/arena teleport <name>` | `ffacore.arena.teleport` | Teleport to the arena spawn |
| `/arena delspawn <name>` | `ffacore.arena.create` | Remove the arena spawn point |
| `/arena delete <name>` | `ffacore.arena.delete` | Delete an arena |
| `/arena resize <name>` | `ffacore.arena.create` | Resize an arena to a new selection |
| `/arena regenerate <name> [mode]` | `ffacore.arena.regenerate` | Regenerate an arena |
| `/arena cancel <name>` | `ffacore.arena.regenerate` | Cancel an in-progress regeneration |
| `/arena schedule <name> <time\|off>` | `ffacore.arena.schedule` | Schedule automatic regeneration |
| `/arena preview <name>` | `ffacore.arena.preview` | Show arena borders with particles |
| `/arena menu [name]` | `ffacore.arena.menu` | Open the GUI dashboard |
| `/arena settings <name> [key] [value]` | `ffacore.arena.settings` | View or change settings |
| `/arena subarena <parent> create\|delete\|list` | `ffacore.arena.subarena` | Manage sub-arenas |
| `/arena perf` | `ffacore.arena.perf` | View performance metrics |
| `/arena debug <name>` | `ffacore.arena.debug` | View arena diagnostics |
| `/arena migrate <arena\|all>` | `ffacore.arena.migrate` | Resave snapshots |
| `/arena reload` | `ffacore.arena.reload` | Reload configuration |

Regeneration modes: `STANDARD`, `PHASED`, `SELECTIVE`, `WAVE`, `WORLD_EDIT`.

### `/killtoken`

| Command | Permission | Description |
|---|---|---|
| `/killtoken set` | `ffacore.killtoken.set` | Use your held item as the Kill Token |
| `/killtoken give [player] [amount]` | `ffacore.killtoken.give` | Hand out tokens |
| `/killtoken giveblock [player] [amount]` | `ffacore.killtoken.give` | Hand out compressed blocks (64 tokens) |
| `/killtoken stats [player]` | — | View kills, deaths, KDR, streak |
| `/killtoken top [page]` | — | Kill leaderboard |
| `/killtoken test` | `ffacore.killtoken.test` | Preview killstreak systems |
| `/killtoken reload` | `ffacore.killtoken.reload` | Reload configuration |

### `/afk`

| Command | Permission | Description |
|---|---|---|
| `/afk create <name>` | `ffacore.afk.create` | Create a zone from your selection |
| `/afk delete <name>` | `ffacore.afk.delete` | Delete a zone |
| `/afk list` | `ffacore.afk.use` | List all zones |
| `/afk info [name]` | `ffacore.afk.use` | Zone details, or your own AFK status |
| `/afk wand` | `ffacore.afk.create` | Receive the selection wand |
| `/afk give [player] [amount]` | `ffacore.afk.give` | Hand out AFK Shards |
| `/afk reload` | `ffacore.afk.reload` | Reload configuration |

### `/ffa`

Shows a live overview of all three subsystems. `/ffa reload` (permission
`ffacore.admin`) reloads the whole configuration.

## Permissions

`ffacore.admin` is a catch-all that grants every management permission.
Each subsystem also exposes fine-grained permissions:

* Arena: `ffacore.arena.*` (all default to `op`)
* Kill Token: `ffacore.killtoken.*` (all default to `op`)
* AFK: `ffacore.afk.use` defaults to `true`; the rest default to `op`

See `plugin.yml` for the full list.

## PlaceholderAPI

| Placeholder | Returns |
|---|---|
| `%ffacore_total_arenas%` | Total arena count |
| `%ffacore_active_regens%` | Arenas currently regenerating |
| `%ffacore_queue_size%` | Arenas waiting to regenerate |
| `%ffacore_total_regenerations%` | Lifetime regeneration count |
| `%ffacore_total_blocks_restored%` | Lifetime blocks restored |
| `%ffacore_<arena>_status%` | `Ready`, `Locked`, or `Regenerating` |
| `%ffacore_<arena>_players%` | Player count inside the arena |
| `%killtoken_streak%` | Current PvP killstreak |
| `%killtoken_kills%` | Lifetime PvP kills |
| `%killtoken_deaths%` | Lifetime deaths |
| `%killtoken_kdr%` | Kill/death ratio |
| `%killtoken_tokens%` | Kill Tokens in inventory |
| `%afk_zone%` | Current AFK zone name |
| `%afk_idle_seconds%` | Seconds since last activity |
| `%afk_earned%` | Shards earned this session |
| `%afk_players%` | Players currently inside a zone |

## Configuration

Everything lives in `plugins/FFACore/config.yml`. Highlights:

```yaml
regeneration:
  default-mode: STANDARD      # STANDARD, PHASED, SELECTIVE, WAVE, WORLD_EDIT
  max-concurrent: 2
  tick-budget: 15             # ms of block placement per tick

killstreak:
  enabled: true
  max-token-multiplier: 5

afk:
  reward-interval-seconds: 30
  shards-per-interval: 1
  min-idle-seconds: 60
  max-shards-per-hour: 100
```

## Building

Requires JDK 21+ and the bundled Maven wrapper.

```bash
git clone https://github.com/ro161012/FFACore.git
cd FFACore
./mvnw clean package
```

This produces two artifacts in `target/`:

* `FFACore-<version>.jar` — the plugin
* `FFACore-Resourcepack.zip` — the resource pack

## Development

Every push and pull request runs two CI gates before packaging:

* **Checkstyle** — `config/checkstyle.xml` (catches unused/star imports,
  missing braces, switch fall-through, and naming violations).
* **Unit tests** — JUnit 5 + MockBukkit under `src/test/`.

Run them locally with:

```bash
./mvnw test                 # checkstyle + tests
./mvnw checkstyle:check     # checkstyle only
```

## License

MIT — see [LICENSE](LICENSE).
