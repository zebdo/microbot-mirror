package net.runelite.client.plugins.microbot;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Microbot",
        description = "Microbot",
        tags = {"main", "microbot", "parent"},
        alwaysOn = true,
        hidden = true
)
@Slf4j
public class MicrobotPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    Notifier notifier;
    @Inject
    WorldService worldService;
    @Inject
    ProfileManager profileManager;
    @Inject
    ItemManager itemManager;
    @Inject
    NPCManager npcManager;
    @Inject
    ConfigManager configManager;
    @Inject
    ChatMessageManager chatMessageManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    private MicrobotOverlay microbotOverlay;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SpriteManager spriteManager;
    @Inject
    private WorldMapOverlay worldMapOverlay;
    @Inject
    private InfoBoxManager infoBoxManager;
    @Inject
    private WorldMapPointManager worldMapPointManager;
    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        Microbot.setWorldService(worldService);
        Microbot.setProfileManager(profileManager);
        Microbot.setItemManager(itemManager);
        Microbot.setNpcManager(npcManager);
        Microbot.setMouse(new VirtualMouse());
        Microbot.setSpriteManager(spriteManager);
        Microbot.setPluginManager(pluginManager);
        Microbot.setWorldMapOverlay(worldMapOverlay);
        Microbot.setInfoBoxManager(infoBoxManager);
        Microbot.setWorldMapPointManager(worldMapPointManager);
        Microbot.setChatMessageManager(chatMessageManager);
        if (overlayManager != null) {
            overlayManager.add(microbotOverlay);
        }

        new InputSelector(clientToolbar);

        //TODO: Rs2NpcManager.loadJson();
    }

    protected void shutDown() {
        overlayManager.remove(microbotOverlay);
    }


    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        Microbot.setIsGainingExp(true);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {

        if (event.getContainerId() == InventoryID.BANK.getId()) {
            Rs2Bank.storeBankItemsInMemory(event);
        } else if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            Rs2Inventory.storeInventoryItemsInMemory(event);
        } else if (event.getContainerId() == InventoryID.EQUIPMENT.getId()) {
            Rs2Equipment.storeEquipmentItemsInMemory(event);
        } else {
            Rs2Shop.storeShopItemsInMemory(event, event.getContainerId());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.HOPPING || gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.CONNECTION_LOST) {
            if (Rs2Bank.bankItems != null)
                Rs2Bank.bankItems.clear();
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        Rs2Player.handlePotionTimers(event);
    }

    @Subscribe(priority = 999)
    private void onMenuEntryAdded(MenuEntryAdded event) {
        if (Microbot.targetMenu != null && event.getType() != Microbot.targetMenu.getType().getId()) {
            this.client.setMenuEntries(new MenuEntry[]{});
        }

        if (Microbot.targetMenu != null) {
            MenuEntry entry =
                    this.client.createMenuEntry(-1)
                            .setOption(Microbot.targetMenu.getOption())
                            .setTarget(Microbot.targetMenu.getTarget())
                            .setIdentifier(Microbot.targetMenu.getIdentifier())
                            .setType(Microbot.targetMenu.getType())
                            .setParam0(Microbot.targetMenu.getParam0())
                            .setParam1(Microbot.targetMenu.getParam1())
                            .setForceLeftClick(true);

            if (Microbot.targetMenu.getItemId() > 0) {
                try {
                    Rs2Reflection.setItemId(entry, Microbot.targetMenu.getItemId());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    System.out.println(e.getMessage());
                }
            }
            this.client.setMenuEntries(new MenuEntry[]{entry});
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        Microbot.targetMenu = null;
        System.out.println(event.getMenuEntry());
    }

    @SneakyThrows
    private void startPlugin(Plugin p) {
        SwingUtilities.invokeAndWait(() ->
        {
            try {
                pluginManager.setPluginEnabled(p, true);
                pluginManager.startPlugin(p);
            } catch (PluginInstantiationException e) {
                System.out.printf("Failed to start plugin: %s%n", p.getName());
            }
        });
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event)
    {
        final GraphicsObject graphicsObject = event.getGraphicsObject();
        int SCURRIUS_FALLING_ROCKS = 2644;
        if (graphicsObject.getId() == SCURRIUS_FALLING_ROCKS) {
            Rs2Tile.init();
            int ticks = 8;
            Rs2Tile.addDangerousGraphicsObjectTile(graphicsObject, 600 * ticks);
        }
    }
}
