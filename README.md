# ItemLWC

A Bukkit plugin for [Project Poseidon](https://github.com/retromcorg/Project-Poseidon) that adds item-based LWC protection management and automatic inactivity-based unlocking.

---

## Features

- **Chat-based protection creation** — Sneak + left-click a block with the trigger item to open an interactive protection menu (Private, Password, Public).
- **Password unlock flow** — Left-click a password-protected block with the unlock item, then type the password in chat.
- **Inactivity-based expiry** — Protections are automatically removed after a configurable number of inactive days. Fully configurable per protection type and block type.
- **Chest timer readout** — Left-click a protected block with a clock to see the remaining time until expiry.
- **Timer reset command** — `/reset lwc` resets the inactivity timer on the targeted block.
- **Admin removal** — Ops or players with `itemlwc.admin` can remove any protection by sneak + right-clicking with the admin tool.
- **Double door support** — Protecting, removing, unlocking, or checking time on one side of a double door automatically applies to both sides.
- **Economy integration** — Optional protection costs via Vault, iConomy, or Essentials.
- **Multi-language support** — All player-facing messages are loaded from configurable YAML language files with color codes and placeholder support.

---

## Requirements

- Project Poseidon (or compatible Bukkit server)
- [LWC](https://github.com/Hidendra/LWC) (required)
- Vault, iConomy, or Essentials (optional — for economy features)

---

## Installation

1. Place `ItemLWC.jar` into the server's `plugins/` folder.
2. Start the server. The plugin creates its default config and language files under `plugins/ItemLWC/`.
3. Edit `plugins/ItemLWC/config.yml` to adjust settings.
4. Reload or restart the server.

---

## Configuration

```yaml
unlock:
  inactive-days: 30            # Days of owner inactivity before a protection expires (0 = disabled)
  scan-interval-minutes: 360   # How often the expiry scan runs (in minutes)
  expire:
    protection-types:          # Which protection types can expire
      private: true
      password: true
      public: false
    block-types:               # Which block types can expire
      chest: true
      trapped-chest: false
      furnace: false
      dispenser: false
      door: false
      other: false             # Catch-all for any other LWC-lockable block

language:
  file: messages-en.yml        # Language file from plugins/ItemLWC/languages/

interaction:
  require-touch-permission: false   # When true, only ops/players with itemlwc.use can interact
  trigger-item-id: 69               # Protection creation trigger (default: lever)
  unlock-item-id: 265               # Password unlock trigger (default: iron ingot)
  time-check-item-id: 347           # Check time left (default: clock)
  admin-tool-item-id: 287           # Admin removal tool (default: string)
  prompt-timeout-seconds: 60        # Seconds before a prompt expires
  cost:
    private: 0.0
    password: 0.0
    public: 0.0
```

A protection expires only when **both** its protection type and block type are set to `true`.

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `itemlwc.use` | `false` | Use touch interactions (only enforced when `require-touch-permission` is `true`) |
| `itemlwc.create` | `false` | Create all protection types via the chat menu |
| `itemlwc.create.private` | `false` | Create private protections |
| `itemlwc.create.password` | `false` | Create password protections |
| `itemlwc.create.public` | `false` | Create public protections |
| `itemlwc.reset` | `op` | Reset inactivity timer on other players' blocks |
| `itemlwc.admin` | `op` | Remove any protection with the admin tool |

`itemlwc.create` grants access to all three types. The type-specific nodes grant access individually.

---

## Commands

| Command | Description |
|---|---|
| `/reset lwc` | Reset the inactivity timer on the block you are looking at |

---

## Interactions

| Action | How |
|---|---|
| Create protection | Sneak + left-click a protectable block with the trigger item |
| Remove own protection | Sneak + right-click your own protected block |
| Admin remove protection | Sneak + right-click any protected block with the admin tool |
| Check time left | Left-click a protected block with the time-check item |
| Unlock password block | Left-click a password-protected block with the unlock item |

---

## Language Files

**Bundled** (shipped inside the jar):
English, German, Spanish, French, Italian.

**Language pack** (in `language_pack/`):
Czech, Dutch, Indonesian, Polish, Portuguese (BR), Russian, Turkish, Ukrainian.

To use a different language, copy the file into `plugins/ItemLWC/languages/` and set `language.file` in `config.yml`.

---

## Building

```
./build.sh
```

Output:
- `build/ItemLWC.jar` — latest build
- `build/itemlwc_vX.XXX.jar` — versioned archive

---

## Version History

### v0.030
- Time-left display now works on either side of a double door (shows only once).
- Password unlock with the unlock item now unlocks both sides of a double door.
- Password unlock punch now works when clicking either door of a double door pair.
- **Fixed in this version:** In v0.029 the time-left check and password unlock did not recognize the companion door — clicking the "wrong" side showed nothing or failed to start the unlock flow.

### v0.029
- Added double door support for protection creation and removal.
- Protecting one side of a double door automatically protects the companion door.
- Removing a protection on one door also removes it from the companion.
- Creation flow blocks if either side of a double door is already protected.
- New helpers: `isDoorMaterial`, `getDoorBottomHalf`, `findDoubleDoorCompanion`.
- **Known issue:** Time-left and password unlock only worked on the specific door block that held the protection, not the companion. Fixed in v0.030.

### v0.028
- Made inactivity expiry fully configurable per protection type and per block type.
- New config section `unlock.expire.protection-types` (private, password, public).
- New config section `unlock.expire.block-types` (chest, trapped-chest, furnace, dispenser, door, other).
- `/reset lwc` now works on any expirable block, not just chests.
- Locale key `reset-look-at-chest` renamed to `reset-look-at-block` with generic wording in all 13 locale files.
- **Fixed in this version:** Before v0.028, inactivity expiry only applied to `Material.CHEST` with non-PUBLIC protection types. Other LWC-lockable blocks (trapped chests, furnaces, dispensers, doors) could never expire.

### v0.027
- Block names in chat messages are now title-cased with all words capitalized (e.g. "Trapped Chest" instead of "Trapped chest").
- **Fixed in this version:** In v0.026, only the first word was capitalized ("Trapped chest"). Now all words are title-cased.

### v0.026
- Block name in `password-chest-locked` message is now capitalized (first letter uppercase).
- **Fixed in this version:** In v0.025 and earlier, the `{block}` placeholder showed the raw lowercase material name (e.g. "trapped chest").

### v0.025
- Added `hasPendingUnlockPrompt()` check to prevent duplicate lock messages from the LWC intercept module.
- **Fixed in this version:** In v0.023–v0.024, `password-chest-locked` was shown twice — once from the unlock punch handler and once from the LWC intercept module.

### v0.024
- Protection menu now uses dynamic numbering based on the player's permissions.
- If a player only has permission for password and public, those are numbered 1 and 2 instead of 2 and 3.
- Added `{option}` placeholder to all 13 locale files for menu option numbers.
- New helpers: `typeForMenuChoice`, `menuOptionNumber`, `availableProtectionTypes`.
- **Fixed in this version:** Before v0.024, menu option numbers were hardcoded (1 = private, 2 = password, 3 = public) regardless of which types were visible, so hidden types left gaps in the numbering.

### v0.023
- `password-chest-locked` message is now shown before `enter-unlock-password` in the password unlock flow.
- Added `formatBlockName` helper for displaying block names in chat messages.
- **Known issue:** `password-chest-locked` appeared twice — the unlock handler sent it, and the LWC intercept module also sent it. Fixed in v0.025.

### v0.022
- Aligned all bundled locale files (EN, DE, ES, FR, IT) and all language pack files (TR, PL, RU, NL, UK, ID, PT-BR, CS) to the current English schema.
- All 13 locale files now have identical key sets.
- **Fixed in this version:** Before v0.022, some locale files were missing keys or had outdated key names, causing fallback to hardcoded English defaults.

### v0.021
- First build of the current development session.
- Added admin removal tool: sneak + right-click any protected block with the configured admin tool (default: string, item 287).
- Added `itemlwc.admin` permission (default: op).
- Added `interaction.admin-tool-item-id` config option.
- Added custom create permissions: `itemlwc.create`, `itemlwc.create.private`, `itemlwc.create.password`, `itemlwc.create.public`.
- Protection menu only shows types the player has permission to create.
- Hidden types cannot be selected by typing their number.
- **Known issue:** Menu option numbers were hardcoded 1/2/3 regardless of which types were visible. Fixed in v0.024.
- **Known issue:** Locale files across languages were not aligned to the same key set. Fixed in v0.022.

### v0.020
- Added `interaction.unlock-item-id` config option (default: iron ingot, item 265).
- Added password unlock flow: left-click a password-protected block with the unlock item to start a chat-based password prompt.
- Added `PendingProtectionSession` support for password unlock sessions.
- Added locale keys: `enter-unlock-password`, `password-unlock-cancelled`, `password-unlock-expired`, `password-unlock-success`, `password-unlock-wrong`, `already-have-access`, `password-chest-locked`.

### v0.019
- Permission-aware protection menu: only shows types the player has permission for.
- Updated English chat prompts to reflect visible menu choices.

### v0.018
- Renamed the touch interaction permission from `touch.lwc` to `itemlwc.use`.
- Updated runtime permission checks, `plugin.yml`, and README.

### v0.017
- First versioned build preserved on disk.
- Added `interaction.require-touch-permission` config option.
- Reverted touch access behavior back to a simple permission model.
- Removed the Vault group-detection logic for `itemlwc.use`.
- When `require-touch-permission` is `false` (default), everyone can interact.
- When `require-touch-permission` is `true`, only ops or `itemlwc.use` holders can interact.
- **Fixed in this version:** The group-aware restriction from v0.016 was overly complex and unreliable. Replaced with a simple boolean toggle.

---

### Pre-v0.017 (archived)

Builds v0.001–v0.016 are archived at `build/archive/jars/`. The changes below are reconstructed from the changelog. Exact version-to-change mapping is approximate.

#### v0.016
- Vault permission-group detection: when `itemlwc.use` is granted to one or more groups, only those players (plus ops) can use touch interactions.
- **Known issue:** Group detection logic was complex and unreliable. Replaced in v0.017 with a simple config toggle.

#### v0.015
- Changed `itemlwc.use` permission default from `true` to `false` in `plugin.yml`.

#### v0.014
- Removed legacy permissions-plugin auto-allow fallback.
- Enforced touch access with direct Bukkit permission checks.
- Added safety check to stop pending sessions if the player no longer has touch permission.

#### v0.013
- Removed root `messages.yml` creation from plugin startup.
- Removed legacy `messages.yml` fallback from message loading.
- Message loading now uses only `plugins/ItemLWC/languages/<file>`.
- **Fixed in this version:** Before v0.013, the plugin still created and fell back to a root `messages.yml`, which conflicted with the languages folder introduced in v0.011.

#### v0.012
- Added `interaction.time-check-item-id` to config (default: clock, item 347).
- Updated interaction handling to use the configured material instead of hardcoded clock id.

#### v0.011
- Restored bundled language files (EN, DE, ES, FR, IT) in `src/main/resources/languages/`.
- Fixed `plugin.yml` indentation.
- Aligned permissions to a consistent snapshot.
- **Fixed in this version:** Language files and `plugin.yml` formatting had drifted out of sync. This version restored everything to a clean baseline.

#### v0.010
- Recreated external `language_pack/` example files (CS, ID, NL, PL, PT-BR, RU, TR, UK).

#### v0.009
- Added `messages.yml` language file with all player-facing chat messages.
- Moved hardcoded Java strings to language keys.
- Added placeholder support (`{trigger}`, `{block}`, `{type}`, `{cost}`).
- Added `&` color-code support.

#### v0.008
- Introduced versioned build artifacts (`build.sh` now creates `itemlwc_vX.XXX.jar`).
- Added persistent version tracking in `.itemlwc_build_version`.
- Kept `build/ItemLWC.jar` as the latest convenience artifact.

#### v0.007
- Added `/reset lwc` command to reset inactivity timer for a targeted protected chest.
- Added ownership/permission checks (`itemlwc.reset` for non-owners).
- Declared `itemlwc.reset` permission in `plugin.yml` (default: op).
- Added clock interaction: punching a locked chest while holding a clock shows remaining unlock time.

#### v0.006
- Added `config.yml` with configurable protection costs and trigger item.
- Added economy integration for Vault, iConomy, and Essentials.

#### v0.005
- Added chat-based LWC protection creation workflow with pending sessions.
- Added configurable trigger item support.
- Added prompt timeout handling.

#### v0.004
- Implemented inactivity-based unlocking for LWC chest protections.
- Added activity tracking and automatic periodic expiry scanning.

#### v0.003
- Renamed plugin from LWCActivity to ItemLWC.
- Moved Java sources to `org.patschgo.plugins.itemlwc`.
- Renamed main class to `ItemLwcPlugin`.
- Updated `pom.xml`, `build.sh`, `plugin.yml`.

#### v0.002
- Renamed plugin entry point and updated build artifact from `LWCActivity.jar` to `ItemLWC.jar`.

#### v0.001
- Initial plugin creation under the LWCActivity project identity.
- Basic Bukkit plugin scaffold with LWC dependency.
