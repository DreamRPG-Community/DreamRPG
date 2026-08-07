# DreamRPG

DreamRPG is the first RPG foundation plugin for Paper 1.12.2 on JDK 21. It depends on Lib at
runtime and keeps SQLite outside the plugin archive. The fixed runtime manifest is embedded in
the plugin code, downloads `org.xerial:sqlite-jdbc:3.46.1.3` into `plugins/DreamRPG/libs/`, and
verifies its SHA-256 before use. No `libraries.yml` is generated or required.

## Public API

The plugin registers `DreamRpgApi` and `CoinService` through Bukkit's `ServicesManager`. The read-only
API exposes immutable career definitions, player profile loading, presentation values, the main
spawn, and spawn teleportation. `CoinService` is the write-capable domain boundary for gameplay
systems; Vault is only its compatibility adapter. Career mutation is intentionally internal so the
future transfer system can evolve inside this plugin without exposing a write surface prematurely.

## Current foundation

- `careers.yml` contains the gray `[无职业]` default career.
- Chat, TAB, and name tags use the career prefix.
- TAB entries reserve eight spaces after each player name.
- Sidebar title, lines, native placeholders, and update interval are configurable in `scoreboard.yml`.
  The current file must contain `normal` and `loading`; DreamRPG does not migrate an older
  top-level layout or a `config.yml.scoreboard` section, and invalid structure fails explicitly.
- `{coins}` is backed by DreamRPG's persistent coin ledger. DreamRPG registers that ledger as the
  Vault Economy provider when Vault is available, so QuickShop and other Vault consumers use the
  same balance. Vault bank accounts are intentionally unsupported.
- `database.mode` selects either SQLite or MySQL; the two connection sections are independent.
  The selected JDBC driver is downloaded by Lib into DreamRPG's private `libs/` directory.
- Player profiles and coin balances are stored through Lib's versioned SQL runner.
- Player inventory, armor, off-hand, selected hotbar slot, and a one-page 54-slot custom ender
  chest are stored in the same SQLite/MySQL storage mode with optimistic version saves.
- Right-clicking a real ender chest opens the gray `末影箱` menu and delegates the vanilla block
  action packet, observer counting, sounds, and quit/teleport cleanup to Lib. `/enderchest` and
  `/ec` open the personal menu with local sounds only and never fake a block packet.
- During asynchronous player loading, the loading scoreboard is shown and Lib freezes movement
  and interaction until both profile and storage data are ready. A failed load explicitly kicks
  the player instead of applying partial inventory data.
- Join and respawn behavior use the configured main-city spawn, with `/spawn` and `/dreamrpg spawn`.
- Administrators can use `/setspawn` to save their current location as the main-city spawn. X/Z are
  snapped to block centers and yaw/pitch to 45-degree steps, matching MythicThePit.
- `/dreamrpg reload` reloads configuration and careers but never reloads runtime dependency JARs.

The plugin does not yet implement levels, experience, combat, or transfer conditions.
