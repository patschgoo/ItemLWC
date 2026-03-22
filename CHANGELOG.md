# Changelog

This repository does not include git history. The entries below are reconstructed from the files available in this workspace on March 18, 2026 and from the changes applied in this codespace session.

## 1.0.0-SNAPSHOT - Custom ItemLWC create permissions

## 1.0.0-SNAPSHOT - Admin removal tool

- Added `itemlwc.admin` permission with default `op`.
- Added `interaction.admin-tool-item-id` to configure the admin removal item.
- Players with `itemlwc.admin` or op can now remove any protection by sneak + right clicking with the configured admin tool.

- Replaced the LWC create permission dependency with ItemLWC-owned permission nodes.
- Added `itemlwc.create`, `itemlwc.create.private`, `itemlwc.create.password`, and `itemlwc.create.public`.
- Updated the chat creation flow to use the new ItemLWC permission nodes for menu visibility and creation checks.

## 1.0.0-SNAPSHOT - Permission-aware protection menu

- Updated the chat creation menu to show only the protection types the player is allowed to create.
- Blocked hidden protection types from being selected by typing their menu number.
- Updated the English chat prompts so the visible menu choices are reflected in the prompt text.

## 1.0.0-SNAPSHOT - Renamed touch permission node

- Renamed the touch interaction permission from `touch.lwc` to `itemlwc.use`.
- Updated runtime permission checks, `plugin.yml`, and README documentation to use the new node.

## 1.0.0-SNAPSHOT - Configurable touch permission requirement

- Reverted touch access behavior back to the simpler `v0.002` style permission model.
- Removed the Vault group-detection logic for `itemlwc.use`.
- Added `interaction.require-touch-permission` to `config.yml`.
- When `interaction.require-touch-permission` is `false`, everyone can use touch interactions.
- When `interaction.require-touch-permission` is `true`, only ops or players with `itemlwc.use` can use touch interactions.

## 1.0.0-SNAPSHOT - touch.lwc default allow with group-aware restriction

- Kept `itemlwc.use` metadata in `plugin.yml` non-default so Bukkit does not implicitly grant it to everyone.
- Updated touch-permission runtime behavior so ItemLWC allows touch interactions for everyone only when no server group is assigned `itemlwc.use`.
- Added Vault permission-group detection: when `itemlwc.use` is granted to one or more groups, ItemLWC restricts touch interactions to players who actually have that permission node (plus op overrides).

## 1.0.0-SNAPSHOT - Configurable time-check item

- Added `interaction.time-check-item-id` to `config.yml` for the chest time-left check interaction item.
- Set the default to clock (item id `347`).
- Updated interaction handling to use the configured material instead of a hardcoded clock id.

## 1.0.0-SNAPSHOT - Versioned build artifacts

- Updated `build.sh` to create incrementing versioned jars in `build/` using the format `itemlwc_vX.XXX.jar` (for example `itemlwc_v0.001.jar`).
- Added persistent version tracking in `.itemlwc_build_version`.
- Kept `build/ItemLWC.jar` as the latest convenience artifact while preserving prior versioned builds.

## 1.0.0-SNAPSHOT - languages-only message loading

- Removed root `messages.yml` creation from plugin startup.
- Removed legacy `messages.yml` fallback from message loading.
- Message loading now uses only `plugins/ItemLWC/languages/<language.file>` with `messages-en.yml` as the default fallback.

## 1.0.0-SNAPSHOT - touch.lwc enforcement fix

- Removed the legacy permissions-plugin auto-allow fallback that let players use touch interactions without `itemlwc.use`.
- Enforced touch access with direct Bukkit permission checks (`player.hasPermission("itemlwc.use")` or op).
- Added a safety check to stop pending chat-based protection sessions if the player no longer has touch permission.

## 1.0.0-SNAPSHOT - touch.lwc default update

- Changed `itemlwc.use` permission default from `true` to `false` in `plugin.yml`.
- Updated README permission documentation to match the new default.

## 1.0.0-SNAPSHOT - Full restore to touch.lwc-era state

- Restored a consistent touch.lwc-era snapshot across code, `plugin.yml`, `config.yml`, bundled language files, and external `language_pack/` files.
- Restored bundled language files in `src/main/resources/languages/` (`messages-en.yml`, `messages-de.yml`, `messages-es.yml`, `messages-fr.yml`, `messages-it.yml`).
- Fixed `plugin.yml` indentation to spaces and aligned permissions to the restored snapshot.

## 1.0.0-SNAPSHOT - Restored external language pack files

- Recreated the external `language_pack/` example files so the workspace once again contains `messages-cs.yml`, `messages-id.yml`, `messages-nl.yml`, `messages-pl.yml`, `messages-pt-br.yml`, `messages-ru.yml`, `messages-tr.yml`, and `messages-uk.yml`.

## 1.0.0-SNAPSHOT - Customizable language file

- Added a dedicated `messages.yml` language file at `src/main/resources/messages.yml`.
- Added runtime loading for `messages.yml` from the plugin data folder.
- Moved player-facing chat text from hardcoded Java strings to language keys.
- Added placeholder support for dynamic values such as `{trigger}`, `{block}`, `{type}`, and `{cost}`.
- Added `&` color-code support for configured language messages.

## 1.0.0-SNAPSHOT - Chest timer reset and clock readout

- Added a new `/reset lwc` command to reset the inactivity timer for a targeted protected chest.
- Added ownership/permission checks for timer reset (`itemlwc.reset` for non-owners).
- Declared the `itemlwc.reset` permission in `plugin.yml` with `op` default.
- Added clock interaction: punching a locked chest while holding a clock shows remaining unlock time in green chat text.
- Added new language keys in `messages.yml` for reset command feedback and time-left readout.

## 1.0.0-SNAPSHOT - Initial plugin creation

- Created a Bukkit plugin under the Project Poseidon plugin namespace.
- Implemented inactivity-based unlocking for LWC chest protections.
- Added a chat-based LWC protection creation workflow with pending protection sessions.
- Added configurable protection costs for private, password, and public protections.
- Added configurable trigger item support for the interaction flow.
- Added economy integration paths for Vault, iConomy, and Essentials.
- Added activity tracking, prompt timeout handling, and automatic expiry scanning.
- Published the plugin under the original project identity `LWCActivity`.

### Original project identity

- Plugin name: `LWCActivity`
- Main class: `org.patschgo.plugins.lwcactivity.LwcActivityPlugin`
- Helper class package: `org.patschgo.plugins.lwcactivity`
- Maven artifactId: `lwc-activity`
- Maven project name: `LWC Activity`
- Build artifact: `build/LWCActivity.jar`

## 1.0.0-SNAPSHOT - Rename to ItemLWC

- Renamed the plugin project identity from `LWCActivity` to `ItemLWC`.
- Updated the plugin manifest name to `ItemLWC`.
- Renamed the plugin entry point to `org.patschgo.plugins.itemlwc.ItemLwcPlugin`.
- Moved the Java sources from `org.patschgo.plugins.lwcactivity` to `org.patschgo.plugins.itemlwc`.
- Renamed the main plugin class from `LwcActivityPlugin` to `ItemLwcPlugin`.
- Moved `PendingProtectionSession` into the new `itemlwc` package.
- Updated the Maven artifactId from `lwc-activity` to `itemlwc`.
- Updated the Maven project name from `LWC Activity` to `ItemLWC`.
- Updated the build artifact name from `build/LWCActivity.jar` to `build/ItemLWC.jar`.
- Updated runtime log messages to use the `ItemLWC` project name.

### Files changed for the rename

- `pom.xml`
- `build.sh`
- `src/main/resources/plugin.yml`
- `src/main/java/org/patschgo/plugins/itemlwc/ItemLwcPlugin.java`
- `src/main/java/org/patschgo/plugins/itemlwc/PendingProtectionSession.java`

### Limitations

- Because there is no commit history in this workspace, this changelog cannot enumerate undocumented historical edits beyond the state visible in the current source tree.