package org.patschgo.plugins.itemlwc;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;
import com.griefcraft.model.ProtectionTypes;
import com.griefcraft.scripting.Module;
import com.griefcraft.scripting.ModuleLoader;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemLwcPlugin extends JavaPlugin {
    private enum EconomyProvider {
        NONE,
        VAULT,
        ICONOMY,
        ESSENTIALS
    }

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final String CONFIG_INACTIVE_DAYS = "unlock.inactive-days";
    private static final String CONFIG_SCAN_INTERVAL_MINUTES = "unlock.scan-interval-minutes";
    private static final String CONFIG_PROMPT_TIMEOUT_SECONDS = "interaction.prompt-timeout-seconds";
    private static final String CONFIG_LANGUAGE_FILE = "language.file";
    private static final String CONFIG_TRIGGER_ITEM_ID = "interaction.trigger-item-id";
    private static final String CONFIG_UNLOCK_ITEM_ID = "interaction.unlock-item-id";
    private static final String CONFIG_TIME_CHECK_ITEM_ID = "interaction.time-check-item-id";
    private static final String CONFIG_ADMIN_TOOL_ITEM_ID = "interaction.admin-tool-item-id";
    private static final String CONFIG_REQUIRE_TOUCH_PERMISSION = "interaction.require-touch-permission";
    private static final String CONFIG_COST_PRIVATE = "interaction.cost.private";
    private static final String CONFIG_COST_PASSWORD = "interaction.cost.password";
    private static final String CONFIG_COST_PUBLIC = "interaction.cost.public";
    private static final String CONFIG_EXPIRE_PTYPE_PRIVATE = "unlock.expire.protection-types.private";
    private static final String CONFIG_EXPIRE_PTYPE_PASSWORD = "unlock.expire.protection-types.password";
    private static final String CONFIG_EXPIRE_PTYPE_PUBLIC = "unlock.expire.protection-types.public";
    private static final String CONFIG_EXPIRE_BTYPE_CHEST = "unlock.expire.block-types.chest";
    private static final String CONFIG_EXPIRE_BTYPE_TRAPPED_CHEST = "unlock.expire.block-types.trapped-chest";
    private static final String CONFIG_EXPIRE_BTYPE_FURNACE = "unlock.expire.block-types.furnace";
    private static final String CONFIG_EXPIRE_BTYPE_DISPENSER = "unlock.expire.block-types.dispenser";
    private static final String CONFIG_EXPIRE_BTYPE_DOOR = "unlock.expire.block-types.door";
    private static final String CONFIG_EXPIRE_BTYPE_OTHER = "unlock.expire.block-types.other";
    private static final String CONFIG_ACTIVITY_ROOT = "activity";
    private static final String LANGUAGE_FOLDER_NAME = "languages";
    private static final String DEFAULT_LANGUAGE_FILE_NAME = "messages-en.yml";
    private static final String[] BUNDLED_LANGUAGE_RESOURCES = new String[] {
            "languages/messages-en.yml",
            "languages/messages-de.yml",
            "languages/messages-es.yml",
            "languages/messages-fr.yml",
            "languages/messages-it.yml"
    };
    private static final int CLOCK_ITEM_ID = 347;
    private static final int IRON_INGOT_ITEM_ID = 265;
    private static final int STRING_ITEM_ID = 287;
    private static final String TOUCH_PERMISSION = "itemlwc.use";
    private static final String CREATE_PERMISSION = "itemlwc.create";
    private static final String ADMIN_PERMISSION = "itemlwc.admin";

    private final Map<String, PendingProtectionSession> pendingSessions = new HashMap<String, PendingProtectionSession>();
    private final Map<String, PendingProtectionSession> pendingUnlockSessions = new HashMap<String, PendingProtectionSession>();
    private final PlayerListener playerListener = new PlayerListener() {
        @Override
        public void onPlayerJoin(PlayerJoinEvent event) {
            ItemLwcPlugin.this.onPlayerJoin(event);
        }

        @Override
        public void onPlayerQuit(PlayerQuitEvent event) {
            ItemLwcPlugin.this.onPlayerQuit(event);
        }

        @Override
        public void onPlayerInteract(PlayerInteractEvent event) {
            ItemLwcPlugin.this.onPlayerInteract(event);
        }

        @Override
        public void onPlayerChat(PlayerChatEvent event) {
            ItemLwcPlugin.this.onPlayerChat(event);
        }
    };
    private Configuration configuration;
    private Configuration messagesConfiguration;
    private LWC lwc;
    private Object vaultEconomy;
    private EconomyProvider economyProvider = EconomyProvider.NONE;
    private boolean requireTouchPermission = false;
    private Material triggerMaterial = Material.LEVER;
    private Material unlockMaterial = Material.getMaterial(IRON_INGOT_ITEM_ID);
    private Material timeCheckMaterial = Material.getMaterial(CLOCK_ITEM_ID);
    private Material adminToolMaterial = Material.STRING;
    private int scanTaskId = -1;

    @Override
    public void onEnable() {
        if (!bindLwc()) {
            return;
        }

        ensureResourceExists("config.yml");
        ensureBundledLanguageFilesExist();
        configuration = getConfiguration();
        configuration.load();
        loadMessagesConfiguration();
        requireTouchPermission = configuration.getBoolean(CONFIG_REQUIRE_TOUCH_PERMISSION, false);
        triggerMaterial = resolveTriggerMaterial();
        unlockMaterial = resolveUnlockMaterial();
        timeCheckMaterial = resolveTimeCheckMaterial();
        adminToolMaterial = resolveAdminToolMaterial();
        bindEconomy();

        lwc.getModuleLoader().registerModule(this, createPasswordInterceptModule());

        getServer().getPluginManager().registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Normal, this);
        getServer().getPluginManager().registerEvent(Event.Type.PLAYER_QUIT, playerListener, Event.Priority.Normal, this);
        getServer().getPluginManager().registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Event.Priority.Lowest, this);
        getServer().getPluginManager().registerEvent(Event.Type.PLAYER_CHAT, playerListener, Event.Priority.Normal, this);
        scheduleExpiryTask();
        getServer().getLogger().info("ItemLWC enabled.");
    }

    @Override
    public void onDisable() {
        if (lwc != null) {
            lwc.getModuleLoader().removeModules(this);
        }
        for (String playerName : new ArrayList<String>(pendingUnlockSessions.keySet())) {
            clearPendingPasswordUnlock(playerName);
        }
        pendingUnlockSessions.clear();
        pendingSessions.clear();
        if (scanTaskId != -1) {
            getServer().getScheduler().cancelTask(scanTaskId);
            scanTaskId = -1;
        }
    }

    public void onPlayerJoin(PlayerJoinEvent event) {
        rememberActivity(event.getPlayer().getName(), System.currentTimeMillis());
    }

    public void onPlayerQuit(PlayerQuitEvent event) {
        rememberActivity(event.getPlayer().getName(), System.currentTimeMillis());
        pendingSessions.remove(normalizeKey(event.getPlayer().getName()));
        clearPendingPasswordUnlock(event.getPlayer());
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.hasItem() && event.getMaterial() == timeCheckMaterial) {
            if (showChestTimeLeft(player, block)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && isHoldingUnlockItem(player)) {
            if (handlePasswordUnlockPunch(player, block, event)) {
                return;
            }
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking() && event.hasItem() && event.getMaterial() == triggerMaterial) {
            handleInteractiveCreate(player, block, event);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            handleSneakRemove(player, block, event);
        }
    }

    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();

        PendingProtectionSession unlockSession = pendingUnlockSessions.get(normalizeKey(player.getName()));
        if (unlockSession != null) {
            handlePasswordUnlockChat(event, player, unlockSession);
            return;
        }

        PendingProtectionSession session = pendingSessions.get(normalizeKey(player.getName()));
        if (session == null) {
            return;
        }

        if (!canUseTouchFeatures(player)) {
            pendingSessions.remove(normalizeKey(player.getName()));
            player.sendMessage(lang("messages.no-create-permission", "&cYou do not have permission to create LWC protections."));
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            pendingSessions.remove(normalizeKey(player.getName()));
            player.sendMessage(lang("messages.protection-creation-cancelled", "&eProtection creation cancelled."));
            return;
        }

        if (isPromptExpired(session)) {
            pendingSessions.remove(normalizeKey(player.getName()));
            player.sendMessage(lang(
                    "messages.prompt-expired",
                    "&cThe protection prompt expired. Punch the block again while sneaking with {trigger}.",
                    replacements("trigger", formatTriggerItemName(true))
            ));
            return;
        }

        Block block = resolveBlock(session);
        if (block == null || !lwc.isProtectable(block)) {
            pendingSessions.remove(normalizeKey(player.getName()));
            player.sendMessage(lang("messages.block-no-longer-available", "&cThat block is no longer available for protection."));
            return;
        }

        if (lwc.findProtection(block) != null) {
            pendingSessions.remove(normalizeKey(player.getName()));
            player.sendMessage(lang("messages.block-already-protected", "&cThat block is already protected."));
            return;
        }

        if (session.getStep() == PendingProtectionSession.Step.CHOOSE_TYPE) {
            int selectedType = typeForMenuChoice(player, message);
            if (selectedType == ProtectionTypes.PRIVATE) {
                pendingSessions.remove(normalizeKey(player.getName()));
                createProtection(player, block, ProtectionTypes.PRIVATE, "");
                return;
            }

            if (selectedType == ProtectionTypes.PASSWORD) {
                session.setStep(PendingProtectionSession.Step.ENTER_PASSWORD);
                player.sendMessage(lang("messages.enter-password", "&eType the password for this protection in chat, or type cancel."));
                return;
            }

            if (selectedType == ProtectionTypes.PUBLIC) {
                pendingSessions.remove(normalizeKey(player.getName()));
                createProtection(player, block, ProtectionTypes.PUBLIC, "");
                return;
            }

            player.sendMessage(lang(
                    "messages.invalid-option",
                    "&cInvalid option. Type one of {options}, or cancel.",
                    replacements("options", availableMenuOptions(player))
            ));
            return;
        }

        if (message.length() == 0) {
            player.sendMessage(lang("messages.password-empty", "&cThe password cannot be empty. Type a password or cancel."));
            return;
        }

        pendingSessions.remove(normalizeKey(player.getName()));
        createProtection(player, block, ProtectionTypes.PASSWORD, message);
    }

    private boolean handlePasswordUnlockPunch(Player player, Block block, PlayerInteractEvent event) {
        if (!canUseTouchFeatures(player)) {
            return false;
        }

        Protection protection = lwc.findProtection(block);
        if (protection == null || protection.getType() != ProtectionTypes.PASSWORD) {
            Block companion = findDoubleDoorCompanion(block);
            if (companion != null) {
                protection = lwc.findProtection(companion);
                if (protection != null && protection.getType() == ProtectionTypes.PASSWORD) {
                    block = companion;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        if (lwc.canAccessProtection(player, protection)) {
            player.sendMessage(lang("messages.already-have-access",
                    "&8[&6ItemLWC&8] &7You already have access to that chest."));
            event.setCancelled(true);
            return true;
        }

        String playerKey = normalizeKey(player.getName());
        pendingUnlockSessions.put(playerKey, new PendingProtectionSession(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                System.currentTimeMillis()));
        event.setCancelled(true);
        player.sendMessage(lang("messages.password-chest-locked",
            "&4This {block} is locked with a magical spell",
            replacements("block", formatBlockName(block))));
        player.sendMessage(lang("messages.enter-unlock-password",
                "&8[&6ItemLWC&8] &ePlease type the password in chat, or type &6cancel&e to stop."));
        return true;
    }

    private void handlePasswordUnlockChat(PlayerChatEvent event, Player player, PendingProtectionSession session) {
        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            clearPendingPasswordUnlock(player);
            player.sendMessage(lang("messages.password-unlock-cancelled",
                    "&8[&6ItemLWC&8] &7Password unlock cancelled."));
            return;
        }

        if (isPromptExpired(session)) {
            clearPendingPasswordUnlock(player);
            player.sendMessage(lang("messages.password-unlock-expired",
                    "&8[&6ItemLWC&8] &cThat unlock prompt expired. Hit the block again with the unlock item to retry."));
            return;
        }

        if (message.length() == 0) {
            player.sendMessage(lang("messages.password-empty",
                    "&cThe password cannot be empty. Type a password or cancel."));
            return;
        }

        Block block = resolveBlock(session);
        Protection protection = block == null ? null : lwc.findProtection(block);
        if (protection == null || protection.getType() != ProtectionTypes.PASSWORD) {
            clearPendingPasswordUnlock(player);
            player.sendMessage(lang("messages.block-no-longer-available",
                    "&cThat block is no longer available for protection."));
            return;
        }

        String encrypted = lwc.encrypt(message);
        if (protection.getData() != null && protection.getData().equals(encrypted)) {
            clearPendingPasswordUnlock(player);
            lwc.getMemoryDatabase().registerPlayer(player.getName(), protection.getId());
            Block companionDoor = findDoubleDoorCompanion(block);
            if (companionDoor != null) {
                Protection companionProtection = lwc.findProtection(companionDoor);
                if (companionProtection != null && companionProtection.getType() == ProtectionTypes.PASSWORD) {
                    lwc.getMemoryDatabase().registerPlayer(player.getName(), companionProtection.getId());
                }
            }
            player.sendMessage(lang("messages.password-unlock-success",
                    "&8[&6ItemLWC&8] &aPassword accepted! You can now open that chest."));
            return;
        }

        player.sendMessage(lang("messages.password-unlock-wrong",
                "&8[&6ItemLWC&8] &cWrong password. Try again, or type &6cancel&c to stop."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"reset".equalsIgnoreCase(command.getName())) {
            return false;
        }

        if (args.length != 1 || !"lwc".equalsIgnoreCase(args[0])) {
            sender.sendMessage(lang("messages.reset-usage", "&cUsage: /reset lwc"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(lang("messages.reset-player-only", "&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        Block target = player.getTargetBlock(null, 5);
        if (target == null || target.getType() == Material.AIR) {
            player.sendMessage(lang("messages.reset-look-at-block", "&cLook at a protected block within 5 blocks, then run /reset lwc."));
            return true;
        }

        Protection protection = lwc.findProtection(target);
        if (protection == null || !isExpirable(protection)) {
            player.sendMessage(lang("messages.reset-no-protection", "&cThat block is not an LWC protection that can expire."));
            return true;
        }

        if (!protection.getOwner().equalsIgnoreCase(player.getName())
                && !player.isOp()
                && !lwc.hasPermission(player, "itemlwc.reset")) {
            player.sendMessage(lang("messages.reset-not-owner", "&cYou must own that chest or have itemlwc.reset permission."));
            return true;
        }

        long now = System.currentTimeMillis();
        rememberActivity(protection.getOwner(), now);
        player.sendMessage(lang(
                "messages.reset-success",
                "&aReset inactivity timer for this chest. Time left: {time}.",
                replacements("time", formatDuration(remainingMillisUntilUnlock(protection.getOwner(), now)))
        ));
        return true;
    }

    private boolean bindLwc() {
        Plugin plugin = getServer().getPluginManager().getPlugin("LWC");
        if (!(plugin instanceof LWCPlugin)) {
            getServer().getLogger().severe("ItemLWC requires LWC and could not bind to it.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        lwc = ((LWCPlugin) plugin).getLWC();
        return true;
    }

    private void bindEconomy() {
        vaultEconomy = null;
        economyProvider = EconomyProvider.NONE;

        Plugin vaultPlugin = getServer().getPluginManager().getPlugin("Vault");
        if (vaultPlugin != null && vaultPlugin.isEnabled()) {
            Class<?> economyClass = findClass("net.milkbowl.vault.economy.Economy");
            if (economyClass != null) {
                Object registration = invoke(getServer().getServicesManager(), "getRegistration", new Class<?>[] {Class.class}, economyClass);
                Object provider = invoke(registration, "getProvider");
                if (provider != null && invokeBoolean(provider, "isEnabled")) {
                    vaultEconomy = provider;
                    economyProvider = EconomyProvider.VAULT;
                    return;
                }
            }
        }

        Plugin iconomyPlugin = getServer().getPluginManager().getPlugin("iConomy");
        if (iconomyPlugin != null && iconomyPlugin.isEnabled() && findClass("com.iConomy.iConomy") != null) {
            economyProvider = EconomyProvider.ICONOMY;
            return;
        }

        Plugin essentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (essentialsPlugin != null && essentialsPlugin.isEnabled() && findClass("com.earth2me.essentials.api.Economy") != null) {
            economyProvider = EconomyProvider.ESSENTIALS;
            return;
        }

        if (hasAnyLeverCostsConfigured()) {
            getServer().getLogger().warning("ItemLWC has lever protection costs configured, but no supported economy provider is loaded. Lever protections will be free until Vault, iConomy, or Essentials is available.");
        }
    }

    private Material resolveTriggerMaterial() {
        return resolveConfiguredMaterial(CONFIG_TRIGGER_ITEM_ID, Material.LEVER.getId(), "trigger-item-id");
    }

    private Material resolveUnlockMaterial() {
        return resolveConfiguredMaterial(CONFIG_UNLOCK_ITEM_ID, IRON_INGOT_ITEM_ID, "unlock-item-id");
    }

    private Material resolveTimeCheckMaterial() {
        return resolveConfiguredMaterial(CONFIG_TIME_CHECK_ITEM_ID, CLOCK_ITEM_ID, "time-check-item-id");
    }

    private Material resolveAdminToolMaterial() {
        return resolveConfiguredMaterial(CONFIG_ADMIN_TOOL_ITEM_ID, STRING_ITEM_ID, "admin-tool-item-id");
    }

    private Material resolveConfiguredMaterial(String configPath, int defaultId, String label) {
        int configuredId = configuration.getInt(configPath, defaultId);
        Material configuredMaterial = Material.getMaterial(configuredId);
        if (configuredMaterial == null) {
            Material fallbackMaterial = Material.getMaterial(defaultId);
            int fallbackId = fallbackMaterial == null ? defaultId : fallbackMaterial.getId();
            getServer().getLogger().warning("ItemLWC " + label + " " + configuredId + " is invalid. Falling back to item id " + fallbackId + ".");
            return fallbackMaterial;
        }

        return configuredMaterial;
    }

    private void ensureResourceExists(String resourceName) {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File resourceFile = new File(dataFolder, resourceName);
        if (resourceFile.exists()) {
            return;
        }

        File parent = resourceFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (input == null) {
            return;
        }

        OutputStream output = null;
        try {
            output = new FileOutputStream(resourceFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (IOException ex) {
            getServer().getLogger().warning("Failed to create default " + resourceName + ": " + ex.getMessage());
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }

            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void ensureBundledLanguageFilesExist() {
        for (String resourceName : BUNDLED_LANGUAGE_RESOURCES) {
            ensureResourceExists(resourceName);
        }
    }

    private void loadMessagesConfiguration() {
        String configuredFile = configuration.getString(CONFIG_LANGUAGE_FILE, DEFAULT_LANGUAGE_FILE_NAME);
        if (configuredFile == null || configuredFile.trim().length() == 0) {
            configuredFile = DEFAULT_LANGUAGE_FILE_NAME;
        }

        configuredFile = configuredFile.trim();
        if (configuredFile.indexOf("/") >= 0 || configuredFile.indexOf("\\") >= 0) {
            configuredFile = DEFAULT_LANGUAGE_FILE_NAME;
        }

        ensureResourceExists(LANGUAGE_FOLDER_NAME + "/" + configuredFile);

        File messagesFile = new File(new File(getDataFolder(), LANGUAGE_FOLDER_NAME), configuredFile);
        if (!messagesFile.exists()) {
            ensureResourceExists(LANGUAGE_FOLDER_NAME + "/" + DEFAULT_LANGUAGE_FILE_NAME);
            messagesFile = new File(new File(getDataFolder(), LANGUAGE_FOLDER_NAME), DEFAULT_LANGUAGE_FILE_NAME);
        }

        messagesConfiguration = new Configuration(messagesFile);
        messagesConfiguration.load();
    }

    private void handleInteractiveCreate(Player player, Block block, PlayerInteractEvent event) {
        if (!canUseTouchFeatures(player)) {
            return;
        }

        if (!lwc.isProtectable(block)) {
            return;
        }

        if (!hasAnyCreatePermission(player)) {
            player.sendMessage(lang("messages.no-create-permission", "&cYou do not have permission to create LWC protections."));
            event.setCancelled(true);
            return;
        }

        if (lwc.findProtection(block) != null) {
            player.sendMessage(lang("messages.block-already-protected", "&cThat block is already protected."));
            event.setCancelled(true);
            return;
        }

        Block doorCompanion = findDoubleDoorCompanion(block);
        if (doorCompanion != null && lwc.findProtection(doorCompanion) != null) {
            player.sendMessage(lang("messages.block-already-protected", "&cThat block is already protected."));
            event.setCancelled(true);
            return;
        }

        pendingSessions.put(normalizeKey(player.getName()), new PendingProtectionSession(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), System.currentTimeMillis()));
        event.setCancelled(true);
        player.sendMessage(lang(
            "messages.menu-header",
            "&6LWC protection menu for {block}&6:",
            replacements("block", block.getType().toString().toLowerCase())
        ));
        if (hasCreatePermission(player, ProtectionTypes.PRIVATE)) {
            player.sendMessage(lang(
                "messages.menu-option-private",
                "&8[&e{option}&8] &fPrivate protection{cost}",
                replacements("cost", formatCostSuffix(ProtectionTypes.PRIVATE), "option", menuOptionNumber(player, ProtectionTypes.PRIVATE))
            ));
        }
        if (hasCreatePermission(player, ProtectionTypes.PASSWORD)) {
            player.sendMessage(lang(
                "messages.menu-option-password",
                "&8[&e{option}&8] &fPassword protection{cost}",
                replacements("cost", formatCostSuffix(ProtectionTypes.PASSWORD), "option", menuOptionNumber(player, ProtectionTypes.PASSWORD))
            ));
        }
        if (hasCreatePermission(player, ProtectionTypes.PUBLIC)) {
            player.sendMessage(lang(
                "messages.menu-option-public",
                "&8[&e{option}&8] &fPublic protection{cost}",
                replacements("cost", formatCostSuffix(ProtectionTypes.PUBLIC), "option", menuOptionNumber(player, ProtectionTypes.PUBLIC))
            ));
        }
        player.sendMessage(lang(
            "messages.menu-prompt",
            "&7Type one of &e{options}&7 in chat. Type &ecancel&7 to abort.",
            replacements("options", availableMenuOptions(player))
        ));
    }

    private void handleSneakRemove(Player player, Block block, PlayerInteractEvent event) {
        if (event.hasItem() && event.getMaterial() == adminToolMaterial
                && canAdminRemove(player)) {
            event.setCancelled(true);
            Protection protection = lwc.findProtection(block);
            if (protection == null) {
                return;
            }

            Block doorCompanion = findDoubleDoorCompanion(block);
            protection.remove();
            if (doorCompanion != null) {
                Protection companionProtection = lwc.findProtection(doorCompanion);
                if (companionProtection != null) {
                    companionProtection.remove();
                }
            }
            player.sendMessage(lang("messages.admin-removed-protection", "&aRemoved protection from that block."));
            return;
        }

        Protection protection = lwc.findProtection(block);
        if (protection == null) {
            return;
        }

        if (!canUseTouchFeatures(player)) {
            return;
        }

        if (!protection.getOwner().equalsIgnoreCase(player.getName())) {
            return;
        }

        event.setCancelled(true);
        Block doorCompanionRemove = findDoubleDoorCompanion(block);
        protection.remove();
        if (doorCompanionRemove != null) {
            Protection companionProtection = lwc.findProtection(doorCompanionRemove);
            if (companionProtection != null
                    && companionProtection.getOwner().equalsIgnoreCase(player.getName())) {
                companionProtection.remove();
            }
        }
        player.sendMessage(lang("messages.removed-protection", "&aRemoved your protection from that block."));
    }

    private void createProtection(Player player, Block block, int type, String passwordText) {
        double cost = getLeverProtectionCost(type);
        if (!hasCreatePermission(player, type)) {
            player.sendMessage(lang("messages.no-type-permission", "&cYou do not have permission for that protection type."));
            return;
        }

        if (!chargeLeverProtectionCost(player, type, cost)) {
            return;
        }

        Module.Result result = lwc.getModuleLoader().dispatchEvent(ModuleLoader.Event.REGISTER_PROTECTION, player, block);
        if (result == Module.Result.CANCEL) {
            refundLeverProtectionCost(player, cost);
            player.sendMessage(lang("messages.lwc-denied", "&cLWC denied protection creation at that block."));
            return;
        }

        String data = "";
        if (type == ProtectionTypes.PASSWORD) {
            data = lwc.encrypt(passwordText);
        }

        lwc.getPhysicalDatabase().registerProtection(block.getTypeId(), type, block.getWorld().getName(), player.getName(), data, block.getX(), block.getY(), block.getZ());

        Protection protection = lwc.getPhysicalDatabase().loadProtection(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (protection != null && type == ProtectionTypes.PASSWORD) {
            lwc.getMemoryDatabase().registerPlayer(player.getName(), protection.getId());
        }

        if (protection == null) {
            refundLeverProtectionCost(player, cost);
            player.sendMessage(lang("messages.lwc-create-failed", "&cLWC could not finish creating that protection."));
            return;
        }

        lwc.getModuleLoader().dispatchEvent(ModuleLoader.Event.POST_REGISTRATION, protection);

        Block doorCompanion = findDoubleDoorCompanion(block);
        if (doorCompanion != null && lwc.isProtectable(doorCompanion) && lwc.findProtection(doorCompanion) == null) {
            lwc.getPhysicalDatabase().registerProtection(doorCompanion.getTypeId(), type, doorCompanion.getWorld().getName(), player.getName(), data, doorCompanion.getX(), doorCompanion.getY(), doorCompanion.getZ());
            Protection companionProtection = lwc.getPhysicalDatabase().loadProtection(doorCompanion.getWorld().getName(), doorCompanion.getX(), doorCompanion.getY(), doorCompanion.getZ());
            if (companionProtection != null && type == ProtectionTypes.PASSWORD) {
                lwc.getMemoryDatabase().registerPlayer(player.getName(), companionProtection.getId());
            }
        }

        player.sendMessage(lang("messages.protection-created", "&aProtection created."));
        if (type == ProtectionTypes.PASSWORD) {
            player.sendMessage(lang("messages.password-protection-active", "&ePassword protection is active for that block."));
        }

        if (cost > 0.0D) {
            player.sendMessage(lang(
                    "messages.charged",
                    "&eCharged {cost} for lever-based protection.",
                    replacements("cost", formatCurrency(cost))
            ));
        }
    }

    private boolean hasAnyCreatePermission(Player player) {
        return hasCreatePermission(player, ProtectionTypes.PRIVATE)
                || hasCreatePermission(player, ProtectionTypes.PASSWORD)
                || hasCreatePermission(player, ProtectionTypes.PUBLIC);
    }

    private String availableMenuOptions(Player player) {
        List<Integer> options = availableProtectionTypes(player);

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < options.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(index + 1);
        }
        return builder.toString();
    }

    private List<Integer> availableProtectionTypes(Player player) {
        List<Integer> options = new ArrayList<Integer>();
        if (hasCreatePermission(player, ProtectionTypes.PRIVATE)) {
            options.add(Integer.valueOf(ProtectionTypes.PRIVATE));
        }
        if (hasCreatePermission(player, ProtectionTypes.PASSWORD)) {
            options.add(Integer.valueOf(ProtectionTypes.PASSWORD));
        }
        if (hasCreatePermission(player, ProtectionTypes.PUBLIC)) {
            options.add(Integer.valueOf(ProtectionTypes.PUBLIC));
        }
        return options;
    }

    private String menuOptionNumber(Player player, int type) {
        List<Integer> options = availableProtectionTypes(player);
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).intValue() == type) {
                return Integer.toString(index + 1);
            }
        }

        return "?";
    }

    private int typeForMenuChoice(Player player, String message) {
        int choice;
        try {
            choice = Integer.parseInt(message);
        } catch (NumberFormatException ignored) {
            return -1;
        }

        List<Integer> options = availableProtectionTypes(player);
        if (choice < 1 || choice > options.size()) {
            return -1;
        }

        return options.get(choice - 1).intValue();
    }

    private boolean hasCreatePermission(Player player, int type) {
        return player.isOp()
                || player.hasPermission(CREATE_PERMISSION)
                || player.hasPermission(CREATE_PERMISSION + "." + typeName(type));
    }

    private String typeName(int type) {
        if (type == ProtectionTypes.PUBLIC) {
            return "public";
        }

        if (type == ProtectionTypes.PASSWORD) {
            return "password";
        }

        return "private";
    }

    private double getLeverProtectionCost(int type) {
        if (type == ProtectionTypes.PUBLIC) {
            return Math.max(0.0D, configuration.getDouble(CONFIG_COST_PUBLIC, 0.0D));
        }

        if (type == ProtectionTypes.PASSWORD) {
            return Math.max(0.0D, configuration.getDouble(CONFIG_COST_PASSWORD, 0.0D));
        }

        return Math.max(0.0D, configuration.getDouble(CONFIG_COST_PRIVATE, 0.0D));
    }

    private boolean hasAnyLeverCostsConfigured() {
        return getLeverProtectionCost(ProtectionTypes.PRIVATE) > 0.0D
                || getLeverProtectionCost(ProtectionTypes.PASSWORD) > 0.0D
                || getLeverProtectionCost(ProtectionTypes.PUBLIC) > 0.0D;
    }

    private boolean chargeLeverProtectionCost(Player player, int type, double cost) {
        if (cost <= 0.0D) {
            return true;
        }

        if (economyProvider == EconomyProvider.NONE) {
            return true;
        }

        if (economyProvider == EconomyProvider.VAULT) {
            if (vaultEconomy == null) {
                return true;
            }

            if (!invokeBoolean(vaultEconomy, "has", new Class<?>[] {String.class, Double.TYPE}, player.getName(), Double.valueOf(cost))) {
                player.sendMessage(lang(
                        "messages.insufficient-funds",
                        "&cYou need {cost} to create a {type} protection with the lever method.",
                        replacements("cost", formatCurrency(cost), "type", typeName(type))
                ));
                return false;
            }

            Object response = invoke(vaultEconomy, "withdrawPlayer", new Class<?>[] {String.class, Double.TYPE}, player.getName(), Double.valueOf(cost));
            if (response == null || !invokeBoolean(response, "transactionSuccess")) {
                player.sendMessage(lang("messages.could-not-charge", "&cCould not charge your account for this protection."));
                return false;
            }

            return true;
        }

        if (economyProvider == EconomyProvider.ESSENTIALS) {
            try {
                if (!invokeStaticBoolean("com.earth2me.essentials.api.Economy", "playerExists", new Class<?>[] {String.class}, player.getName())) {
                    player.sendMessage(lang("messages.no-essentials-account", "&cNo Essentials economy account was found for you."));
                    return false;
                }

                if (!invokeStaticBoolean("com.earth2me.essentials.api.Economy", "hasEnough", new Class<?>[] {String.class, Double.TYPE}, player.getName(), Double.valueOf(cost))) {
                    player.sendMessage(lang(
                            "messages.insufficient-funds",
                            "&cYou need {cost} to create a {type} protection with the lever method.",
                            replacements("cost", formatCurrency(cost), "type", typeName(type))
                    ));
                    return false;
                }

                invokeStatic("com.earth2me.essentials.api.Economy", "subtract", new Class<?>[] {String.class, Double.TYPE}, player.getName(), Double.valueOf(cost));
                return true;
            } catch (Exception ex) {
                if (isExceptionType(ex, "com.earth2me.essentials.api.UserDoesNotExistException")) {
                    player.sendMessage(lang("messages.no-essentials-account", "&cNo Essentials economy account was found for you."));
                    return false;
                }

                if (isExceptionType(ex, "com.earth2me.essentials.api.NoLoanPermittedException")) {
                    player.sendMessage(lang(
                            "messages.insufficient-funds",
                            "&cYou need {cost} to create a {type} protection with the lever method.",
                            replacements("cost", formatCurrency(cost), "type", typeName(type))
                    ));
                    return false;
                }

                player.sendMessage(lang("messages.no-essentials-account", "&cNo Essentials economy account was found for you."));
                return false;
            }
        }

        Object account = invokeStatic("com.iConomy.iConomy", "getAccount", new Class<?>[] {String.class}, player.getName());
        if (account == null) {
            player.sendMessage(lang("messages.no-iconomy-account", "&cNo iConomy account was found for you."));
            return false;
        }

        Object holdings = invoke(account, "getHoldings");
        if (holdings == null || !invokeBoolean(holdings, "hasEnough", new Class<?>[] {Double.TYPE}, Double.valueOf(cost))) {
            player.sendMessage(lang(
                    "messages.insufficient-funds",
                    "&cYou need {cost} to create a {type} protection with the lever method.",
                    replacements("cost", formatCurrency(cost), "type", typeName(type))
            ));
            return false;
        }

        invoke(holdings, "subtract", new Class<?>[] {Double.TYPE}, Double.valueOf(cost));
        return true;
    }

    private void refundLeverProtectionCost(Player player, double cost) {
        if (cost <= 0.0D || economyProvider == EconomyProvider.NONE) {
            return;
        }

        if (economyProvider == EconomyProvider.VAULT) {
            if (vaultEconomy != null) {
                invoke(vaultEconomy, "depositPlayer", new Class<?>[] {String.class, Double.TYPE}, player.getName(), Double.valueOf(cost));
            }
            return;
        }

        if (economyProvider == EconomyProvider.ESSENTIALS) {
            try {
                invokeStatic("com.earth2me.essentials.api.Economy", "add", new Class<?>[] {String.class, Double.TYPE}, player.getName(), Double.valueOf(cost));
            } catch (Exception ignored) {
            }
            return;
        }

        Object account = invokeStatic("com.iConomy.iConomy", "getAccount", new Class<?>[] {String.class}, player.getName());
        if (account == null) {
            return;
        }

        Object holdings = invoke(account, "getHoldings");
        if (holdings != null) {
            invoke(holdings, "add", new Class<?>[] {Double.TYPE}, Double.valueOf(cost));
        }
    }

    private String formatCostSuffix(int type) {
        double cost = getLeverProtectionCost(type);
        if (cost <= 0.0D) {
            return "";
        }

        return " (" + formatCurrency(cost) + ")";
    }

    private String lang(String path, String fallback) {
        return lang(path, fallback, null);
    }

    private String lang(String path, String fallback, Map<String, String> replacements) {
        String resolved = fallback;
        if (messagesConfiguration != null) {
            String configured = messagesConfiguration.getString(path, fallback);
            if (configured != null) {
                resolved = configured;
            }
        }

        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return colorize(resolved);
    }

    private String colorize(String input) {
        if (input == null) {
            return "";
        }

        return input.replace('&', '\u00A7');
    }

    private Map<String, String> replacements(String... keyValues) {
        Map<String, String> result = new HashMap<String, String>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(keyValues[index], keyValues[index + 1]);
        }
        return result;
    }

    private String formatCurrency(double amount) {
        if (economyProvider == EconomyProvider.VAULT && vaultEconomy != null) {
            Object formatted = invoke(vaultEconomy, "format", new Class<?>[] {Double.TYPE}, Double.valueOf(amount));
            if (formatted instanceof String) {
                return (String) formatted;
            }
        }

        if (economyProvider == EconomyProvider.ICONOMY) {
            Object formatted = invokeStatic("com.iConomy.iConomy", "format", new Class<?>[] {Double.TYPE}, Double.valueOf(amount));
            if (formatted instanceof String) {
                return (String) formatted;
            }
        }

        if (economyProvider == EconomyProvider.ESSENTIALS) {
            Object formatted = invokeStatic("com.earth2me.essentials.api.Economy", "format", new Class<?>[] {Double.TYPE}, Double.valueOf(amount));
            if (formatted instanceof String) {
                return (String) formatted;
            }
        }

        if (amount == (long) amount) {
            return String.valueOf((long) amount);
        }

        return String.valueOf(amount);
    }

    private boolean showChestTimeLeft(Player player, Block block) {
        if (!canUseTouchFeatures(player)) {
            return false;
        }

        Protection protection = lwc.findProtection(block);
        if (!isExpirable(protection)) {
            Block companion = findDoubleDoorCompanion(block);
            if (companion != null) {
                protection = lwc.findProtection(companion);
            }
            if (!isExpirable(protection)) {
                return false;
            }
        }

        long now = System.currentTimeMillis();
        long remaining = remainingMillisUntilUnlock(protection.getOwner(), now);
        player.sendMessage(lang(
                "messages.time-left",
                "&aTime left until unlock: {time}.",
                replacements("time", formatDuration(remaining))
        ));
        return true;
    }

    private long remainingMillisUntilUnlock(String owner, long now) {
        int inactiveDays = configuration.getInt(CONFIG_INACTIVE_DAYS, 30);
        if (inactiveDays <= 0) {
            return 0L;
        }

        long threshold = inactiveDays * MILLIS_PER_DAY;
        long lastSeen = resolveLastSeen(owner);
        if (lastSeen < 0L) {
            return threshold;
        }

        long remaining = threshold - (now - lastSeen);
        return Math.max(0L, remaining);
    }

    private String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0m";
        }

        long totalMinutes = millis / (60L * 1000L);
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;

        StringBuilder builder = new StringBuilder();
        if (days > 0L) {
            builder.append(days).append("d ");
        }
        if (hours > 0L || days > 0L) {
            builder.append(hours).append("h ");
        }
        builder.append(minutes).append("m");
        return builder.toString().trim();
    }

    private String formatTriggerItemName(boolean includeArticle) {
        String materialName = triggerMaterial.toString().toLowerCase().replace('_', ' ');
        if (!includeArticle) {
            return materialName;
        }

        char first = materialName.length() == 0 ? 'a' : materialName.charAt(0);
        boolean vowel = first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u';
        return (vowel ? "an " : "a ") + materialName;
    }

    private boolean canUseTouchFeatures(Player player) {
        if (!requireTouchPermission) {
            return true;
        }

        return player.isOp() || player.hasPermission(TOUCH_PERMISSION);
    }

    private boolean canAdminRemove(Player player) {
        return player.isOp() || player.hasPermission(ADMIN_PERMISSION);
    }

    private boolean isDoorMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.toString();
        return name.equals("WOODEN_DOOR") || name.equals("WOOD_DOOR")
                || name.equals("IRON_DOOR_BLOCK") || name.equals("IRON_DOOR");
    }

    private Block getDoorBottomHalf(Block block) {
        if (block == null || !isDoorMaterial(block.getType())) {
            return null;
        }
        int data = block.getData();
        if ((data & 8) != 0) {
            Block below = block.getRelative(0, -1, 0);
            if (below != null && isDoorMaterial(below.getType())) {
                return below;
            }
            return null;
        }
        return block;
    }

    private Block findDoubleDoorCompanion(Block block) {
        Block bottom = getDoorBottomHalf(block);
        if (bottom == null) {
            return null;
        }
        Material doorType = bottom.getType();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            Block neighbor = bottom.getRelative(offset[0], 0, offset[1]);
            if (neighbor != null && neighbor.getType() == doorType) {
                int neighborData = neighbor.getData();
                if ((neighborData & 8) == 0) {
                    return neighbor;
                }
            }
        }
        return null;
    }

    private String formatBlockName(Block block) {
        if (block == null) {
            return "Chest";
        }

        String blockName = block.getType().toString().toLowerCase().replace('_', ' ');
        String[] words = blockName.split(" ");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < words.length; index++) {
            String word = words[index];
            if (word.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.length() == 0 ? "Chest" : builder.toString();
    }

    private boolean hasPendingUnlockPrompt(Player player, Protection protection) {
        if (player == null || protection == null) {
            return false;
        }

        PendingProtectionSession session = pendingUnlockSessions.get(normalizeKey(player.getName()));
        if (session == null) {
            return false;
        }

        Block block = protection.getBlock();
        if (block == null) {
            return false;
        }

        return block.getWorld().getName().equals(session.getWorldName())
                && block.getX() == session.getX()
                && block.getY() == session.getY()
                && block.getZ() == session.getZ();
    }

    private Module createPasswordInterceptModule() {
        return new Module() {
            public void load(LWC lwc) {
            }

            public Module.Result canAccessProtection(LWC lwc, Player player, Protection protection) {
                return Module.Result.DEFAULT;
            }

            public Module.Result canAdminProtection(LWC lwc, Player player, Protection protection) {
                return Module.Result.DEFAULT;
            }

            public Module.Result onDropItem(LWC lwc, Player player, Item item, ItemStack itemStack) {
                return Module.Result.DEFAULT;
            }

            public Module.Result onCommand(LWC lwc, CommandSender sender, String command, String[] args) {
                return Module.Result.DEFAULT;
            }

            public Module.Result onRedstone(LWC lwc, Protection protection, Block block, int current) {
                return Module.Result.DEFAULT;
            }

            public Module.Result onDestroyProtection(LWC lwc, Player player, Protection protection, Block block, boolean canAccess, boolean canAdmin) {
                return Module.Result.DEFAULT;
            }

            public Module.Result onProtectionInteract(LWC lwc, Player player, Protection protection, List<String> actions, boolean canAccess, boolean canAdmin) {
                if (protection.getType() != ProtectionTypes.PASSWORD) {
                    return Module.Result.DEFAULT;
                }
                if (canAccess) {
                    return Module.Result.DEFAULT;
                }
                if (hasPendingUnlockPrompt(player, protection)) {
                    return Module.Result.CANCEL;
                }
                player.sendMessage(lang("messages.password-chest-locked",
                        "&4This {block} is locked with a magical spell",
                        replacements("block", formatBlockName(protection.getBlock()))));
                return Module.Result.CANCEL;
            }

            public Module.Result onBlockInteract(LWC lwc, Player player, Block block, List<String> actions) {
                return Module.Result.DEFAULT;
            }

            public Module.Result onRegisterProtection(LWC lwc, Player player, Block block) {
                return Module.Result.DEFAULT;
            }

            public void onPostRegistration(LWC lwc, Protection protection) {
            }
        };
    }

    private boolean isHoldingUnlockItem(Player player) {
        if (unlockMaterial == null) {
            return false;
        }
        ItemStack hand = player.getItemInHand();
        return hand != null && hand.getType() == unlockMaterial;
    }

    private void clearPendingPasswordUnlock(Player player) {
        clearPendingPasswordUnlock(player.getName());
    }

    private void clearPendingPasswordUnlock(String playerName) {
        pendingUnlockSessions.remove(normalizeKey(playerName));
    }

    private void scheduleExpiryTask() {
        if (scanTaskId != -1) {
            getServer().getScheduler().cancelTask(scanTaskId);
        }

        long intervalTicks = Math.max(20L * 60L, configuration.getInt(CONFIG_SCAN_INTERVAL_MINUTES, 360) * 20L * 60L);
        scanTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                unlockExpiredProtections();
            }
        }, intervalTicks, intervalTicks);
    }

    private void unlockExpiredProtections() {
        int inactiveDays = configuration.getInt(CONFIG_INACTIVE_DAYS, 30);
        if (inactiveDays <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long inactiveThreshold = inactiveDays * MILLIS_PER_DAY;
        int unlocked = 0;

        List<Protection> protections = lwc.getPhysicalDatabase().loadProtections();
        for (Protection protection : protections) {
            if (!isExpirable(protection)) {
                continue;
            }

            String owner = protection.getOwner();
            if (owner == null || owner.length() == 0) {
                continue;
            }

            long lastSeen = resolveLastSeen(owner);
            if (lastSeen < 0L) {
                continue;
            }

            if ((now - lastSeen) < inactiveThreshold) {
                continue;
            }

            protection.remove();
            unlocked++;
        }

        if (unlocked > 0) {
            getServer().getLogger().info("Unlocked " + unlocked + " inactive LWC chest protections.");
        }
    }

    private boolean isExpirable(Protection protection) {
        if (protection == null) {
            return false;
        }

        if (!isProtectionTypeExpirable(protection.getType())) {
            return false;
        }

        Block block = protection.getBlock();
        return block != null && isBlockTypeExpirable(block.getType());
    }

    private boolean isProtectionTypeExpirable(int protectionType) {
        if (protectionType == ProtectionTypes.PRIVATE) {
            return configuration.getBoolean(CONFIG_EXPIRE_PTYPE_PRIVATE, true);
        }
        if (protectionType == ProtectionTypes.PASSWORD) {
            return configuration.getBoolean(CONFIG_EXPIRE_PTYPE_PASSWORD, true);
        }
        if (protectionType == ProtectionTypes.PUBLIC) {
            return configuration.getBoolean(CONFIG_EXPIRE_PTYPE_PUBLIC, false);
        }
        return false;
    }

    private boolean isBlockTypeExpirable(Material material) {
        if (material == null) {
            return false;
        }

        String materialName = material.toString();
        if (materialName.equals("CHEST")) {
            return configuration.getBoolean(CONFIG_EXPIRE_BTYPE_CHEST, true);
        }
        if (materialName.equals("TRAPPED_CHEST")) {
            return configuration.getBoolean(CONFIG_EXPIRE_BTYPE_TRAPPED_CHEST, false);
        }
        if (materialName.equals("FURNACE") || materialName.equals("BURNING_FURNACE")) {
            return configuration.getBoolean(CONFIG_EXPIRE_BTYPE_FURNACE, false);
        }
        if (materialName.equals("DISPENSER")) {
            return configuration.getBoolean(CONFIG_EXPIRE_BTYPE_DISPENSER, false);
        }
        if (materialName.equals("WOODEN_DOOR") || materialName.equals("IRON_DOOR_BLOCK")
                || materialName.equals("IRON_DOOR") || materialName.equals("WOOD_DOOR")) {
            return configuration.getBoolean(CONFIG_EXPIRE_BTYPE_DOOR, false);
        }
        return configuration.getBoolean(CONFIG_EXPIRE_BTYPE_OTHER, false);
    }

    private long resolveLastSeen(String owner) {
        Player onlinePlayer = getServer().getPlayer(owner);
        if (onlinePlayer != null) {
            rememberActivity(owner, System.currentTimeMillis());
            return System.currentTimeMillis();
        }

        long newest = -1L;
        List<World> worlds = getServer().getWorlds();
        for (World world : worlds) {
            File playerFile = new File(new File(world.getName(), "players"), owner + ".dat");
            if (playerFile.isFile()) {
                newest = Math.max(newest, playerFile.lastModified());
            }
        }

        String remembered = configuration.getString(CONFIG_ACTIVITY_ROOT + "." + normalizeKey(owner));
        if (remembered != null) {
            try {
                newest = Math.max(newest, Long.parseLong(remembered));
            } catch (NumberFormatException ignored) {
            }
        }

        return newest;
    }

    private void rememberActivity(String playerName, long timestamp) {
        if (configuration == null) {
            return;
        }

        configuration.setProperty(CONFIG_ACTIVITY_ROOT + "." + normalizeKey(playerName), String.valueOf(timestamp));
        configuration.save();
    }

    private boolean isPromptExpired(PendingProtectionSession session) {
        long timeoutMillis = configuration.getInt(CONFIG_PROMPT_TIMEOUT_SECONDS, 60) * 1000L;
        return timeoutMillis > 0L && (System.currentTimeMillis() - session.getCreatedAt()) > timeoutMillis;
    }

    private Block resolveBlock(PendingProtectionSession session) {
        World world = findWorld(session.getWorldName());
        if (world == null) {
            return null;
        }

        return world.getBlockAt(session.getX(), session.getY(), session.getZ());
    }

    private World findWorld(String worldName) {
        List<World> worlds = getServer().getWorlds();
        for (World world : worlds) {
            if (world.getName().equals(worldName)) {
                return world;
            }
        }

        return null;
    }

    private String normalizeKey(String value) {
        return value.toLowerCase();
    }

    private Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        Class<?> targetClass = findClass(className);
        if (targetClass == null) {
            return null;
        }

        try {
            Method method = targetClass.getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean invokeBoolean(Object target, String methodName) {
        return invokeBoolean(target, methodName, new Class<?>[0]);
    }

    private boolean invokeBoolean(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object result = invoke(target, methodName, parameterTypes, args);
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private boolean invokeStaticBoolean(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object result = invokeStatic(className, methodName, parameterTypes, args);
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private boolean isExceptionType(Exception ex, String className) {
        Throwable current = ex;
        while (current != null) {
            if (className.equals(current.getClass().getName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}