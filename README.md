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
/ffa arena wand              # receive the selection wand
# left-click a block         # set position 1
# right-click a block        # set position 2
/ffa arena create duel       # create an arena (snapshot saved automatically)
/ffa arena setspawn duel     # set the spawn point
/ffa arena regenerate duel   # restore the arena to its saved state

/ffa afk wand                # receive the selection wand
/ffa afk create lounge       # create an AFK zone from your selection
# players standing idle inside now earn AFK Shards
```

## Commands

### `/ffa arena`

| Command | Permission | Description |
|---|---|---|
| `/ffa arena help` | `ffacore.arena.use` | Show the command list |
| `/ffa arena wand` | `ffacore.arena.wand` | Receive the selection tool |
| `/ffa arena create <name>` | `ffacore.arena.create` | Create an arena from your selection |
| `/ffa arena list` | `ffacore.arena.use` | List all arenas |
| `/ffa arena info <name>` | `ffacore.arena.use` | Show arena details |
| `/ffa arena rename <old> <new>` | `ffacore.arena.create` | Rename an arena |
| `/ffa arena setspawn <name>` | `ffacore.arena.create` | Set the arena spawn point |
| `/ffa arena teleport <name>` | `ffacore.arena.teleport` | Teleport to the arena spawn |
| `/ffa arena delspawn <name>` | `ffacore.arena.create` | Remove the arena spawn point |
| `/ffa arena delete <name>` | `ffacore.arena.delete` | Delete an arena |
| `/ffa arena resize <name>` | `ffacore.arena.create` | Resize an arena to a new selection |
| `/ffa arena regenerate <name> [mode]` | `ffacore.arena.regenerate` | Regenerate an arena |
| `/ffa arena cancel <name>` | `ffacore.arena.regenerate` | Cancel an in-progress regeneration |
| `/ffa arena schedule <name> <time\|off>` | `ffacore.arena.schedule` | Schedule automatic regeneration |
| `/ffa arena preview <name>` | `ffacore.arena.preview` | Show arena borders with particles |
| `/ffa arena menu [name]` | `ffacore.arena.menu` | Open the GUI dashboard |
| `/ffa arena settings <name> [key] [value]` | `ffacore.arena.settings` | View or change settings |
| `/ffa arena subarena <parent> create\|delete\|list` | `ffacore.arena.subarena` | Manage sub-arenas |
| `/ffa arena perf` | `ffacore.arena.perf` | View performance metrics |
| `/ffa arena debug <name>` | `ffacore.arena.debug` | View arena diagnostics |
| `/ffa arena migrate <arena\|all>` | `ffacore.arena.migrate` | Resave snapshots |
| `/ffa arena reload` | `ffacore.arena.reload` | Reload configuration |

Regeneration modes: `STANDARD`, `PHASED`, `SELECTIVE`, `WAVE`, `WORLD_EDIT`.

### `/ffa killtoken`

| Command | Permission | Description |
|---|---|---|
| `/ffa killtoken set` | `ffacore.killtoken.set` | Use your held item as the Kill Token |
| `/ffa killtoken give [player] [amount]` | `ffacore.killtoken.give` | Hand out tokens |
| `/ffa killtoken giveblock [player] [amount]` | `ffacore.killtoken.give` | Hand out compressed blocks (64 tokens) |
| `/ffa killtoken stats [player]` | — | View kills, deaths, KDR, streak |
| `/ffa killtoken top [page]` | — | Kill leaderboard |
| `/ffa killtoken test` | `ffacore.killtoken.test` | Preview killstreak systems |
| `/ffa killtoken reload` | `ffacore.killtoken.reload` | Reload configuration |

### `/ffa afk`

| Command | Permission | Description |
|---|---|---|
| `/ffa afk create <name>` | `ffacore.afk.create` | Create a zone from your selection |
| `/ffa afk delete <name>` | `ffacore.afk.delete` | Delete a zone |
| `/ffa afk list` | `ffacore.afk.use` | List all zones |
| `/ffa afk info [name]` | `ffacore.afk.use` | Zone details, or your own AFK status |
| `/ffa afk wand` | `ffacore.afk.create` | Receive the selection wand |
| `/ffa afk give [player] [amount]` | `ffacore.afk.give` | Hand out AFK Shards |
| `/ffa afk reload` | `ffacore.afk.reload` | Reload configuration |

### `/ffa`

Every FFACore command lives under `/ffa`:

| Command | Permission | Description |
|---|---|---|
| `/ffa arena ...` | `ffacore.arena.*` | Arena regeneration management |
| `/ffa killtoken ...` | `ffacore.killtoken.*` | The Kill Token currency |
| `/ffa afk ...` | `ffacore.afk.*` | AFK zones and AFK Shards |
| `/ffa config` | `ffacore.config` | Open the in-game config menu |
| `/ffa reload` | `ffacore.admin` | Reload `config.yml` from disk |

Without arguments it shows a live overview of all three subsystems. These
are the only commands the plugin registers - there are no standalone
`/arena`, `/killtoken` or `/afk` commands.

#### In-game config menu (`/ffa config`)

Opens a native Paper dialog (1.21.6+ client) with a section menu:
General, Regeneration, Kill Token, AFK Zones, and Storage & Performance.
Each section opens its own screen where you toggle booleans, drag sliders,
pick enum options or type values, then hit **Save & Apply** — the change is
written to `config.yml` and pushed to every subsystem immediately, no
restart or reload required. **Back** returns to the section list.

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
