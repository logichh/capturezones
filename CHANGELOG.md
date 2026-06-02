# CaptureZones - Change Log

## Version 1.1.4 - [2026-06-02]

### Added
- Added configurable town-wide money payout modes for zone rewards:
  - `OWNER_ACCOUNT` keeps the previous behavior and pays the controlling owner account.
  - `SPLIT_ONLINE` splits one configured reward across online eligible owner members.
  - `EACH_ONLINE` pays the full configured reward to each online eligible owner member.
  - `EACH_RESIDENT` pays the full configured reward to each Towny resident account, including offline residents when Towny can resolve the account.
- Added payout safety controls:
  - `rewards.money.max-recipients`
  - `rewards.money.batch-size`
- Added capturer-bound potion rewards:
  - Configured per zone under `rewards.potion-effects`.
  - Effects are granted to the player who initiated the successful capture.
  - Effects transfer away from the previous capturer when another owner captures the zone.
  - Effects are refreshed with short durations instead of unsafe permanent effects.
- Added Towny-owner conquest mode:
  - Ticket-based multi-zone matches.
  - Profile config under `conquest.profiles`.
  - Team tickets, ticket drain, capture ticket gain/loss, profile zones, and minimum team count.
  - Active conquest restricts managed zones to configured participating Towny owners.
- Added conquest admin commands:
  - `/cap admin conquest start [profile]`
  - `/cap admin conquest stop`
  - `/cap admin conquest status`
  - `/cap admin conquest assign [profile] <zone_id>`
  - `/cap admin conquest unassign [profile] <zone_id>`
  - `/cap admin conquest zones [profile]`
- Added `capturezones.admin.conquest` permission.
- Added Pl3xMap integration:
  - New `Pl3xMapProvider`.
  - Global config under `pl3xmap`.
  - Per-zone map styling under `pl3xmap` in `zone-template.yml`.
  - Zone area markers, center markers, colors, popups, and conquest ticket status.
- Added Pl3xMap as a soft dependency and Maven `provided` dependency.
- Added conquest ticket display to map info windows.

### Changed
- Updated plugin release metadata to `1.1.4`.
- Updated the Spigot compile API target to `1.21.11-R0.1-SNAPSHOT`.
- Updated Bukkit plugin metadata to `api-version: '1.21'` for modern 1.21.x server API behavior.
- Daily and hourly economy reward reporting now uses the actual paid total for statistics, Discord alerts, and broadcast messages when member payout modes are enabled.
- Towny owner adapter now exposes helper methods for online owner players, resident account names, and resident account deposits.
- Zone config validation now supports the new money payout and potion reward paths.
- Admin help and tab completion now include conquest commands.
- Default configs now include disabled-by-default sections for conquest and Pl3xMap, preserving existing behavior on upgrade.

### Configuration
- Added global config:
  - `conquest.enabled`
  - `conquest.default-profile`
  - `conquest.tick-interval-seconds`
  - `conquest.profiles.default.*`
  - `potion-rewards.refresh-interval-ticks`
  - `pl3xmap.*`
- Added per-zone config:
  - `rewards.money.payout-mode`
  - `rewards.money.max-recipients`
  - `rewards.money.batch-size`
  - `rewards.potion-effects.*`
  - `pl3xmap.*`
- Added language entries for conquest, potion reward grant/removal, new admin help, and map ticket labels.

### Performance & Stability
- Keeps new reward, potion, conquest, and Pl3xMap systems opt-in by default.
- Clamps risky admin-configurable values such as ticket amounts, payout recipient limits, payout batch size, and potion durations/amplifiers.
- Cleans up potion reward tracking on zone reset, delete, force capture, reload, and shutdown.
- Replaced removed Bukkit 1.21.11 API constants with current particle and potion effect names.
- Excluded JUnit from the shaded production jar through the `json-simple` dependency.

## Version 1.1.3 - [2026-02-28]

### Added
- New addon-facing Bukkit service API for external integrations:
  - `com.logichh.capturezones.api.CaptureZonesApi`
  - `com.logichh.capturezones.api.CaptureZonesActionResult`
- Core plugin now registers/unregisters the API service on enable/disable.
- API includes:
  - Snapshot endpoints (overview, zones, captures, KOTH, shops, statistics, configs, data files)
  - Safe action methods for admin flows (zone/capture/KOTH/shop/stats/config operations)
  - Capability discovery via `getCapabilities()`
- `repairSchema(...)` API responses now report `legacyTownOwnerRebindCount` after repair runs.

### Economy & Shops
- Added a dual-mode economy path for shops and money rewards:
  - Uses Towny accounts when Towny ownership is active.
  - Falls back to Vault-backed player economy support where available.
- Shops are no longer locked to Towny-only startup conditions when a valid Vault economy provider is installed.
- Added `Vault` support metadata so economy providers can be resolved cleanly in standalone deployments.

### Ownership & Migration
- Town-owned zones now store stable Towny UUID-backed owner IDs instead of name-derived IDs for newly normalized Towny owners.
- Towny-backed owner display names now refresh from live Towny data, so zone ownership labels follow town renames once the zone is using a stable owner ID.
- `/cap admin repair` and `/cap admin migrate` now also attempt to rebind legacy Towny owner references to stable IDs when the current town still resolves.
- Repair output now reports how many legacy Towny owner references were rebound.

### API & Stability
- Active capture snapshot rows now include:
  - `zoneName`
  - `initialPreparationTime`
  - `initialCaptureTime`
- Shop economy backend resolution now revalidates correctly across reloads and provider changes instead of holding a stale backend selection.

### Commands & UX
- `/cap list` output was cleaned up:
  - Shorter footer line
  - Consistent `Capture Zones` header text instead of old `Capture Points` wording
- The in-game update notification was redesigned into a cleaner compact format.

### Fixes
- Fixed Towny rename handling so UUID-backed town owners no longer keep showing stale cached names after a town rename.
- Fixed the in-game update notification link so it always points to the official Modrinth page:
  - `https://modrinth.com/plugin/capturezones`

## Version 1.1.2 - [2026-02-27]

### Commands & UX
- Consolidated admin command routing under `/cap admin ...` and updated help output to match.
- Added `/cap tp [zone_id]` teleport flow for active captures:
  - Single active zone: direct teleport.
  - Multiple active zones: choose a target zone.
- `/cap tp` is now fully gated by `settings.capture-teleport-enabled`:
  - Hidden from help/tab-complete when disabled.
  - Returns a disabled message if used while off.
- Added KOTH admin control and assignment flow:
  - `/cap admin koth <start|stop|status|assign|unassign|zones>`
  - Assigned-zone pool is stored in `koth.activation.zones`.
- `/cap admin koth start` now defaults to assigned KOTH zones only, and skips unassigned zones if explicitly passed.
- Removed legacy command surface that is no longer part of gameplay/admin flow:
  - `/cap settype`
  - `/cap test`
  - `/cap testall`
  - `/cap protectchunk`

### Configuration & Migration
- Added explicit concurrent capture controls:
  - `settings.capture-concurrency.allow-multiple-zones`
  - `settings.capture-concurrency.max-active-zones`
- Added bossbar visibility radius control:
  - `settings.bossbar-visibility-radius-chunks`
- Added global reinforcement spawn throttle:
  - `reinforcements.spawn-rate.global-max-per-tick`
- Enforced `settings.max-capture-points` at zone creation time so over-limit creations are blocked cleanly.
- Removed unused global config sections that are now handled per-zone or deprecated:
  - `anti-exploit`
  - `point_types`
  - `point-type-defaults`
  - `worldguard`
- Clarified and separated schema-repair modes:
  - `/cap admin migrate` = non-destructive migration for plugin-owned data/config files (add/fix missing or invalid schema paths).
  - `/cap admin repair` = same non-destructive behavior as migrate (alias for safe schema repair).

### KOTH
- Added optional KOTH subsystem with:
  - Scheduler (`koth.schedule.*`)
  - Zone selection modes (`koth.activation.selection-mode`)
  - Reward routing (`koth.rewards.*`)
- Added command-based KOTH zone assignment that writes back to config (`assign` / `unassign`).
- KOTH hold radius logic is square-based.
- Added temporary surface outline rendering during active KOTH (red concrete ring around hold radius) with full block restoration on end/stop.
- Dynmap/BlueMap info windows now switch to KOTH-specific details while a zone is active, then revert to normal zone view.
- Added optional KOTH-specific hologram line templates (`koth.hologram.*`).

### Zone Gameplay & Reinforcements
- Added per-zone keep-inventory setting for active captures:
  - `capture.capture.keep-inventory-on-death`
  - Keeps inventory/XP and suppresses drops when enabled for that zone.
- Death cancellation messaging now supports localized non-player killer labels in `lang/en.json`:
  - Reinforcements
  - Environment
  - Unknown
- Added reinforcement spawn Y controls for better indoor/multi-level behavior:
  - `reinforcements.spawn-location.y-mode` (`SURFACE`, `ZONE_CENTER`, `ORIGIN_PLAYER`, `CLAMPED`)
  - `reinforcements.spawn-location.clamp-range`

### Fixes
- Fixed missing item-reward editor open message path by supporting both key styles:
  - `messages.zoneconfig-itemrewards-opened`
  - `messages.zoneconfig-itemrewards.opened`

### Performance & Stability
- Major backend optimization for servers with many active zones.
- Smarter zone lookup/player scanning to reduce unnecessary checks.
- Boundary particles and holograms now pause/short-circuit when nobody is close enough to see them.
- Reload/shutdown cleanup was hardened to prevent stale tasks, bossbars, markers, and holograms.
- Upgrade migration is safer: missing config keys are auto-added without overwriting existing server values.

### Zones & Capturing
- Added full cuboid zone support.
- Added a cuboid selector tool workflow for admins to pick corners more easily during zone creation.
- Added optional auto-capture when entering a zone (with anti-spam debounce).
- Capture ownership now supports players, towns, or nations.
- Added contested capture state when competing owner groups are inside the same zone.
- Added optional grace timer before capture is cancelled/reset after attackers leave.
- Added per-zone capture cooldowns and anti-instant-recapture options.
- Added optional same-town/same-nation recapture prevention rules.
- Force-capture now applies the same cooldown/lock behavior as normal successful captures.
- Beacon beam now reflects phase color (yellow in preparation, red in active capture).

### Rewards, Progress & Combat Flow
- Expanded reinforcement behavior options (wave scaling, per-wave caps, spawn tuning, targeting modes).
- Added configurable capture speed modifiers.
- Added modern item rewards (including custom items) with inventory-full handling options.
- Added permission-based economy reward modifiers.
- Added permission grant rewards (temporary or permanent).
- Expanded bossbar/actionbar controls for normal, contested, and grace states.

### Admin & Configuration
- Per-zone override behavior is now clearer and more reliable.
- Zone config tab completion now suggests a much broader set of settable paths.
- Added in-game item reward editor GUI (`/capturezones zoneconfig <zone> itemrewards`).
- New zone config files are cleaner: template banner text is no longer copied into every zone file.
- Legacy data folder migration is automatic (`plugins/TownyCapture` -> `plugins/CaptureZones`).

### Integrations
- Towny integration is now optional (standalone usage is supported).
- PlaceholderAPI coverage is much more complete for zone/global/player state.

### Fixes
- Fixed capture/preparation leave behavior that could continue in some cases.
- Improved moved-too-far cancel messaging to support player-name output.
- Fixed zone-template migration edge cases on existing servers.
- Dynmap and BlueMap marker/icon behavior is fixed.

## Version 1.1.1 - [2026-01-25]

### Added

#### 🔧 Zone Protection Controls
- Per-zone toggles to allow **ender pearls** and **chorus fruit** even when teleporting is blocked
- New zone config keys: `protection.allow-ender-pearls` and `protection.allow-chorus-fruit`

#### 🌐 Localization Coverage
- Discord webhook titles/descriptions/fields now fully translatable
- Statistics GUI menus and shop/shop-editor menus now fully translatable
- New translation keys added to `lang/en.json`

### Changed
- Shop quantity selection now uses slot mapping (translation-safe, no parsing display names)
- GUI title detection for shop/stats menus now uses translated titles
- Language bootstrap no longer attempts to copy a non-existent `lang/fr.json`

## Version 1.1.0 hotfix - [2026-01-17]

### Fixed

#### 🔧 Zone Protection System
Fixed ZoneProtectionListener to properly use per-zone configuration instead of global config.

**Issue:** The protection listener was reading `protection.*` settings from the main config.yml instead of zone-specific configs, causing per-zone protection settings to be ignored.

**Fixed:**
- All protection event handlers now check zone-specific configs via `ZoneConfigManager`
- Added `getZoneIdAtLocation()` helper method to identify which zone a location is in
- Protection settings now properly respect per-zone `protection.prevent-item-use`, `prevent-block-break`, `prevent-block-place`, `prevent-explosions`, `prevent-pvp`, `prevent-teleporting`, `prevent-homes`, and `prevent-claiming`
- Buffer zone size now reads from per-zone configs instead of global config

**Impact:** Players can now have different protection rules per zone. For example, one zone can allow item use (`prevent-item-use: false`) while another blocks it.

## Version 1.1.0 - [2026-01-16]

### Wiki

- Updated wiki

### Added

#### 🔔 Discord Webhook Integration
Real-time Discord notifications for all capture zone events with rich embeds and full customization!

- **13 Alert Types**: capture-started, capture-completed, capture-failed, capture-cancelled, rewards-distributed, first-capture-bonus, reinforcement-phases, player-death, zone-created, zone-deleted, weekly-reset, new-records, milestones
- **Rich Embeds**: Color-coded embeds with structured fields, titles, and footers
- **Individual Toggles**: Enable/disable each alert type independently
- **Privacy Controls**: Hide coordinates and/or reward amounts in messages
- **Role Mentions**: Optional Discord role pings for important events
- **Per-Zone Rate Limiting**: Prevents spam from same zone (configurable ms)
- **Async Execution**: Non-blocking HTTP requests won't lag server
- **Fallback Mode**: Plain text messages if embeds disabled
- **Configuration**: Complete control via `discord:` section in config.yml

**Alert Colors:**
- 🟡 Yellow - Capture started
- 🟢 Green - Capture completed
- 🔴 Red - Capture failed, reinforcements
- 🟠 Orange - Capture cancelled
- 🟡 Gold - Rewards distributed
- 🟤 Dark Orange - First capture bonus
- ⚫ Dark Gray - Player deaths
- 🔵 Cyan - Zone created
- ⚫ Gray - Zone deleted
- 🟣 Magenta - Weekly reset
- 🟡 Goldenrod - New records
- 🟣 Pink - Milestones

#### 📊 Modular Per-Zone Configurations
Each capture zone can now have its own dedicated config file!

- **Auto-Generation**: Zone configs automatically created as `{zone_id}_config.yml` when zones are created
- **Zone-Specific Settings**: Customize rewards, reinforcements, and mechanics per zone
- **Global Defaults**: `zone-template.yml` contains template for new zones
- **Automatic Migration**: Existing zones get configs generated on first load
- **Fallback System**: Uses zone-defaults if zone config is missing or invalid
- **Backup on Delete**: Zone deletion creates `{zone_id}_config.yml.backup`
- **ZoneConfigManager**: New manager class handles lifecycle and access

#### 📈 Comprehensive Statistics System
Track and visualize all capture zone activity!

- **Interactive GUI**: 54-slot menus with category-based navigation
- **6 Categories**: Captures, Combat, Control, Economy, Activity, Records
- **Player Stats**: Captures, kills, deaths, K/D ratio, mob kills, streaks
- **Town Stats**: Total captures, hold time, rewards earned, simultaneous zones
- **Zone Stats**: Captures, control time, deaths, fastest/longest captures
- **Server Records**: All-time bests, milestones, first capture tracking
- **Leaderboards**: Top 10 rankings in each category with pagination
- **Real-Time Tracking**: Capture events, combat, rewards, all logged automatically
- **JSON Persistence**: Auto-save every 5 minutes (configurable)
- **5-Minute Cooldown**: Prevents spam on `/cap stats` command (admins bypass)
- **Admin Tools**: Remove player stats or reset all statistics

#### 🗺️ BlueMap Integration
Full support for BlueMap as an alternative to Dynmap!

- Run Dynmap, BlueMap, both, or neither
- 100% optional - works without any map plugin installed
- Automatic marker synchronization across both maps
- Configurable styles and colors per map provider
- Independent enable/disable controls for each map plugin

#### 🎭 MythicMobs Integration
Full support for MythicMobs custom mobs as reinforcements!

- 100% optional - vanilla mobs works without MythicMobs installed
- Configure custom mob types in <zone-id>.config.yml
- Set mob levels for difficulty scaling
- Seamless integration with existing reinforcement system

#### 🛒 Zone Shop System
Per-zone player shops with full economy integration and dynamic pricing!

- **100% Optional**: Global toggle in config.yml (`shops.enabled: false` by default)
- **Per-Zone Shops**: Each zone can have its own independent shop
- **GUI-Based Admin Editor**: No complex commands - configure shops visually with GUIs
- **Buy & Sell**: Support for both purchase and sale of items
- **Quantity Selectors**: Visual selection (1, 2, 3, 5, 10, 16, 32, 64 items)
- **5 Configuration Dimensions** per shop:
  - **Access Mode**: ALWAYS / CONTROLLED_ONLY / OWNER_ONLY (who can access)
  - **Stock System**: INFINITE / LIMITED / FINITE (stock behavior)
  - **Layout Mode**: SINGLE_PAGE / CATEGORIES / PAGINATED (organization)
  - **Pricing Mode**: FIXED / DYNAMIC (price behavior)
  - **Restock Schedule**: HOURLY / DAILY / WEEKLY / MANUAL (when stock replenishes)
- **Dynamic Pricing**: Multi-factor algorithm (stock level + demand + time decay)
- **Stock Management**: Configure max stock per item, auto-restock on schedule
- **Category Organization**: Group items for easier browsing
- **Economy Integration**: Uses Towny account system (same as rewards)
- **Transaction Tracking**: Per-shop statistics (total buys, sells, revenue)
- **Visual Feedback**: Green panes for buy, red panes for sell
- **Persistence**: Each shop saved as `{zone_id}_shop.yml`

**Shop Features:**
- Player-facing shop GUI with category navigation
- Admin editor with visual settings panels
- Per-item configuration (buyable/sellable, prices, stock limits, categories)
- Real-time price display with total cost calculation
- Stock availability indicators
- Manual restock capability for admins

**Dynamic Pricing Algorithm:**
- Factor 1: Stock level (low stock = higher prices)
- Factor 2: Transaction frequency (high demand = higher prices)
- Factor 3: Time-based decay (gradual return to base prices)
- Configurable sensitivity and min/max multipliers (default: 0.5x to 2.0x)

### Commands Added
- `/cap zoneconfig <zone_id> set <path> <value>` - Set zone-specific config value
- `/cap zoneconfig <zone_id> reset [path]` - Reset zone config to defaults
- `/cap zoneconfig <zone_id> reload` - Reload zone config from disk
- `/cap stats` - Open interactive statistics GUI
- `/cap stats remove <player>` - Remove a player's statistics (admin)
- `/cap stats reset CONFIRM` - Reset all server statistics (admin)
- `/cap shop` - Open nearest zone shop (player)
- `/cap shop edit <zone_id>` - Open shop editor GUI (admin)
- `/cap shop reload <zone_id>` - Reload shop configuration (admin)
- `/cap shop restock <zone_id>` - Manually restock shop (admin)
- `/cap shop enable <zone_id>` - Enable shop for zone (admin)
- `/cap shop disable <zone_id>` - Disable shop for zone (admin)

### Permissions Added
- `capturepoints.admin.zoneconfig` - Access zone config commands
- `capturepoints.admin.zoneconfig.set` - Set zone config values
- `capturepoints.admin.zoneconfig.reset` - Reset zone configs
- `capturepoints.admin.zoneconfig.reload` - Reload zone configs
- `capturepoints.admin.stats` - Manage statistics (remove/reset)
- `capturepoints.admin.stats.nocooldown` - Bypass stats command cooldown
- `capturepoints.shop.use` - Access and use zone shops (default: true)
- `capturepoints.admin.shop` - Manage zone shops (edit, enable, disable, reload)
- `capturepoints.admin.shop.restock` - Manually restock zone shops

### Configuration Added
```yaml
# NEW: zone-defaults section in config.yml
zone-defaults:
  rewards:
    hourly-mode: STATIC
    hourly-dynamic:
      min: 50
      max: 100
  reinforcements:
    enabled: true
    mobs-per-wave: 1
    max-mobs-per-point: 50

statistics:
  enabled: true
  auto-save-interval: 300  # Save stats every 5 minutes
  command-cooldown: 300    # 5-minute cooldown on /cap stats

# NEW: Shop system configuration
shops:
  enabled: false  # Global toggle - 100% optional
  defaults:
    access-mode: "ALWAYS"        # ALWAYS / CONTROLLED_ONLY / OWNER_ONLY
    stock-system: "INFINITE"     # INFINITE / LIMITED / FINITE
    layout-mode: "SINGLE_PAGE"   # SINGLE_PAGE / CATEGORIES / PAGINATED
    pricing-mode: "FIXED"        # FIXED / DYNAMIC
    restock-schedule: "HOURLY"   # HOURLY / DAILY / WEEKLY / MANUAL
  dynamic-pricing:
    sensitivity: 0.1              # Price variation sensitivity
    min-multiplier: 0.5           # Minimum price (50% of base)
    max-multiplier: 2.0           # Maximum price (200% of base)
```

### Technical Implementation
- `ZoneConfigManager.java` - Manages per-zone config files with fallback to zone-defaults
- Refactored reward calculation to use zone-specific settings (`calculateHourlyReward`)
- Refactored reinforcement spawning to use zone configs (`ReinforcementListener`)
- Zone creation auto-generates config from zone-defaults template
- Zone deletion backs up config as `.backup` file
- Added `getZoneConfigManager()` accessor to `TownyCapture`
- Updated `CaptureCommands` with zone config admin command routing
- Enhanced `CaptureCommandTabCompleter` with zone config tab completion
- `StatisticsData.java` - Comprehensive data models (PlayerStats, TownStats, ZoneStats, ServerRecords)
- `StatisticsManager.java` - Core tracking, persistence, and query engine
- `StatisticsGUI.java` - Interactive menu system with category navigation
- `StatisticsGUIListener.java` - Inventory click and close event handling
- `StatisticsTrackingListener.java` - Combat tracking within capture zones
- Integrated tracking hooks in capture lifecycle (start, complete, fail)
- Integrated reward distribution tracking (hourly and one-time)
- On-demand calculation with HashMap-based storage for efficiency
- `ShopItemConfig.java` - Item configuration with stock management, pricing state, and YAML persistence
- `ShopData.java` - Shop data container with 5 configuration enums and complete YAML serialization
- `DynamicPricing.java` - Multi-factor pricing engine with stock, demand, and time decay algorithms
- `ShopManager.java` - Core shop logic with transaction processing, access control, Towny economy integration, auto-restock tasks
- `ShopGUI.java` - Player shop browsing interface with quantity selectors and visual feedback (318 lines)
- `ShopEditorGUI.java` - Admin configuration GUI with visual settings panels and item placement (413 lines)
- `ShopListener.java` - GUI click event handling for both player and admin interfaces (305 lines)
- Integrated shop initialization and cleanup in `TownyCapture` onEnable/onDisable
- Shop data persisted as `{zone_id}_shop.yml` in `plugins/TownyCapture/shops/` directory
- Periodic tasks: auto-restock every 5 minutes, dynamic pricing updates every hour

### Localization
Added messages to `lang/en.json`:
- Discord webhook notification messages and labels
- Zone config command usage and error messages
- Statistics GUI labels and navigation text
- BlueMap integration messages
- MythicMobs integration messages
- Error messages for disabled features and cooldowns
- Success messages for admin operations
- Shop GUI titles and navigation labels (10 messages)
- Shop transaction messages (buy/sell success, 2 messages)
- Shop management messages (restocked, saved, enabled, disabled, reloaded, 5 messages)
- Shop error messages (11 messages for access, stock, funds, etc.)
- Shop command usage messages (5 messages)

## Version 1.0.8 - [2025-12-02]

### Fixed
- **Critical**: Fixed post-deletion capture completion spam. Deleting a zone during an active capture session no longer triggers "no town was found" warnings or completion callbacks.
- Added defensive guards in `completeCapture()` to prevent execution if the capture zone has been removed from the registry.
- Zone deletion and capture stopping now properly cancel all scheduled tasks (preparation/capture timers) and remove boss bars to ensure clean state.

### Added
- **Startup Banner**: Added ASCII art banner displaying "TOWNY CAPTURE" on server startup with version and author information.

## Version 1.0.7 - [2025]

### Changed
- Towns can no longer start a capture on zones they already control (configurable via `capture-conditions.prevent-self-capture`).
- Added per-zone capture cooldown (default 5 minutes) between any successful captures, configurable under `capture-conditions.capture-cooldown`.
- Successful captures now stamp the last capturing town and timestamp for accurate cooldown enforcement without blocking admin resets/cancellations.

## Version 1.0.6 - [2025]

### Added (Boundary visualization, weekly resets are modular)
- Modular boundary visualization modes (ON / REDUCED / OFF) with safe task cleanup.
- Reinforcement optimization: per-tick spawn limiter, queued spawns around the capturing player, smarter targeting, and cleanup on cancel.
- Dynmap infowindow now fully localizable and template-configurable (HTML-friendly placeholders, item payout placeholder).
- Weekly reset system (configurable day/time) that neutralizes points and flags a configurable first-capture bonus.
- Hourly reward mode selector (STATIC vs DYNAMIC range) alongside existing daily/hourly scheduling.

### Fixed/Changed
- /capturepoint deletezone now requires an explicit CONFIRM and actually removes zones from saved data.
- Capture point saving no longer skips empty datasets, preventing deleted zones from respawning.
- Boundary auto-visualization respects config modes and cancels tasks when disabled.

## Version 1.0.2 - [2025]

### ✨ New Features

#### 💰 Configurable Reward System
A flexible reward distribution system that allows server owners to choose between daily or hourly reward payouts, with full customization options.

**Reward Types:**
- **Daily Mode**: Traditional system - towns receive full daily reward once per Towny day
- **Hourly Mode**: Proportional rewards - towns receive `(daily_reward / 24)` every hour they control a zone

**Configuration:**
```yaml
rewards:
  reward-type: "hourly"  # "daily" or "hourly"
  hourly-interval: 3600  # Interval in seconds (default: 1 hour)
```

**How It Works:**
- **Daily Mode**: Same as before - full reward at Towny new day
- **Hourly Mode**: 
  - Every hour, controlling towns get proportional reward
  - Example: 1000 daily reward = 41.67 hourly reward (1000/24)
  - Holding for 6 hours = 6 × 41.67 = 250 total reward
  - Holding for 24 hours = same total as daily mode (1000)

**Benefits:**
- More frequent feedback for players
- Rewards scale with actual holding time
- Server owners can choose based on their economy
- Fully backward compatible

**Messages:**
- Daily: "Town has received 1000 for controlling Point!"
- Hourly: "Town has received 41.67 hourly reward for controlling Point!"

## Version 1.0.1 - [2025]

### 🐛 Bug Fixes
- Fixed compilation and runtime errors including NullPointerException on capture completion, boundary cleanup issues, and zone deletion during active captures

### ✨ New Features

#### 🔊 Sound System
Play immersive sound effects during capture events to enhance gameplay and alert players to important moments.

**Sound Events:**
- `capture-started`: Deep wither ambient sound when preparation begins
- `capture-phase-started`: Ominous wither spawn when capture phase starts  
- `capture-complete`: Victory level-up sound when capture succeeds
- `capture-failed`: Dramatic ender dragon death for failed captures
- `capture-progress`: Subtle note block pling during active capture
- `point-reset`: Experience orb pickup sound when admin resets a point

**Configuration:**
```yaml
sounds:
  enabled: true  # Set to false to disable all sounds
  capture-started:
    sound: "entity.wither.ambient"
    volume: 1.0
    pitch: 1.2
```

**Usage:**
- Sounds play automatically during capture events
- Only players without disabled notifications will hear sounds
- Each sound can be customized in config.yml with different volume and pitch

#### 👹 Mob Reinforcement System
A dynamic defensive mob system that makes captures progressively more challenging as time passes.

**How It Works:**
- Defensive mobs spawn to protect captured zones from being taken
- Mobs spawn at specific timer marks (:00 and :30 - like 14:30, 14:00, 13:30, etc.)
- Each phase spawns more mobs than the last (Phase 1: 2 mobs, Phase 2: 3 mobs, Phase 11+: 12 mobs max)
- No spawns in the final minute (gives capturing players a break)
- 26 different hostile mob types can spawn including zombies, skeletons, creepers, blazes, witches, evokers, and more

**Smart Targeting:**
- Mobs ONLY attack players from the capturing town
- Defenders from other towns can walk by safely
- Mobs pathfind directly to the nearest capturing player

**Daylight Protection:**
- All mobs are equipped with helmets to prevent sunlight burning
- Can spawn during daytime without instantly dying
- Uses random helmets: leather, iron, golden, or chainmail

**Kill Rewards:**
Every reinforcement mob you kill reduces the capture timer:
- Reduces timer by **1-3 seconds** (random)
- Shows message: "**-2 seconds! Capture timer reduced!**"
- Killing multiple mobs = stacking time reductions
- Successfully fighting defenders can significantly speed up your capture

**Phase System:**
- Phases spawn based on the actual capture timer position, not elapsed real-time
- If you kill lots of mobs and timer jumps from 14:45 to 14:12, the system immediately spawns the 14:30 phase
- Every phase always spawns regardless of how much time was skipped by kill rewards
- Example timeline:
  - Start capture: 15:00 remaining, Phase 1 spawns (2 mobs)
  - 30 seconds later: 14:30 remaining, Phase 2 spawns (3 mobs)
  - You kill 5 mobs: Timer drops from 14:25 to 14:15, Phase 2 spawns immediately
  - 30 seconds later: 14:00 remaining, Phase 3 spawns (4 mobs)
  - Final minute: No more mobs spawn, just survive to complete!

**Configuration:**
```yaml
reinforcements:
  enabled: true  # Disable entire reinforcement system
  mobs-per-wave: 1  # Base mobs per phase (+ phase number)
  wave-interval: 30  # Spawns at :00 and :30 marks
  max-mobs-per-point: 50  # Maximum accumulated mobs
  mob-types:  # 26 different types available
    - "ZOMBIE"
    - "CREEPER"
    - "BLAZE"
    # ... etc
  hat-items:
    - "LEATHER_HELMET"
    - "GOLDEN_HELMET"
    # ... etc
```

**Tips:**
- Bring friends! Killing mobs together speeds up captures
- Stay in the zone while fighting - leaving can cancel capture
- Each mob kill is worth 1-3 seconds - every kill matters
- Later phases get intense - up to 12 different mob types at once
- Last minute is mob-free - if you survive that long, you're almost done

#### 🎨 Visual Improvements
**Updated Color Scheme:**
- Unclaimed zones now use dark red fill (`#8B0000`) instead of bright red
- Zone borders use dark gray (`#404040`) instead of dark red
- More subtle and less obtrusive appearance on the map
- Captured zones maintain their town's color for visual ownership

#### 🔇 Notification Toggle System
A unified toggle to control ALL plugin notifications (boss bars, sounds, messages) in one command.

**Command:**
```
/capturepoint notifications
/cap notifications
/capturepoint silence  # Alias
```

**What Gets Toggled:**
- ✅ Boss bars (capture progress display)
- ✅ Sound effects (all capture-related sounds)
- ✅ Capture messages (phase started, completed, etc.)

**Usage:**
1. Start with notifications ON by default
2. Run `/cap notifications` to disable all TownyCapture notifications
3. Run `/cap notifications` again to re-enable them
4. Toggle persists across captures until server restart

**Example:**
```
Player runs: /capturepoint notifications
Chat: "All TownyCapture notifications disabled."

(Tries to capture a zone - sees nothing, hears nothing, gets no boss bar)

Player runs: /capturepoint notifications  
Chat: "All TownyCapture notifications enabled."

(Now sees boss bars, hears sounds, gets messages)
```

## Summary

This update introduces **Mob Reinforcement System** - a dynamic defense system that spawns hostile mobs to protect zones, rewarding skilled players with timer reductions for kills. The **Sound System** provides immersive audio feedback for all capture events, while the **Notification Toggle** gives players complete control over UI elements. Visual improvements make the map cleaner with darker color schemes. All critical bugs have been fixed for a more stable experience.
