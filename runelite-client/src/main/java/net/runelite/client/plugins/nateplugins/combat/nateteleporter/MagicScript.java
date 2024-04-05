package net.runelite.client.plugins.nateplugins.combat.nateteleporter;

import net.runelite.api.Actor;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2Cannon;
import net.runelite.client.plugins.microbot.util.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.nateplugins.combat.nateteleporter.enums.SPELLS;

import java.util.concurrent.TimeUnit;


public class MagicScript extends Script {

    public static double version = 1.7;


    WorldPoint initialPlayerLocation;
    public boolean run(MagicConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            if (!Microbot.isLoggedIn()) return;

            if (initialPlayerLocation == null)
                initialPlayerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

            try {
                if (!config.highAlchemy() && (Microbot.isMoving() || Microbot.isAnimating() || Microbot.pauseAllScripts)) return;

                if (config.highAlchemy()) {

                    Rs2Item item = Rs2Inventory.get(config.highAlchemyItem());
                    if (item == null) {
                        Microbot.showMessage("Item: " + config.highAlchemyItem() + " not found in your inventory.");
                        return;
                    }
                    Rs2Magic.highAlch(item);
                    sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC);
                }

                Rs2Player.eatAt(50);

                if (config.cannon()) {
                    if (Rs2Cannon.repair()) return;
                    if (Rs2Cannon.refill(5)) return;
                    if (Microbot.getClient().getLocalPlayer().getWorldLocation().equals(initialPlayerLocation)) return;
                    Microbot.getWalker().walkFastCanvas(initialPlayerLocation);
                }

                if (config.MODE() == SPELLS.NONE)
                    return;

                Widget widget = Microbot.getClient().getWidget(config.MODE().getSpell().getSpell().getWidgetId());

                if (config.MODE().getSpell().getLevel() > Microbot.getClient().getBoostedSkillLevel(Skill.MAGIC)) {
                    Microbot.showMessage("Level not high enough!");
                    shutdown();
                } else if (widget != null && widget.getSpriteId() != config.MODE().getSpell().getSpell().getSprite()) {
                    Microbot.showMessage("Missing required items!");
                    shutdown();
                } else {
                    Rs2Magic.cast(config.MODE().getSpell().getSpell());
                    sleep(100, 300);
                    if (!config.MODE().toString().contains("teleport")) {
                        net.runelite.api.NPC npc = (net.runelite.api.NPC) Microbot.getClient().getLocalPlayer().getInteracting();
                        if (npc != null && npc.getName().equals(config.NPC()))
                            Rs2Npc.interact(npc, "");
                        else
                            Rs2Npc.interact(config.NPC(), "");
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        initialPlayerLocation = null;
        super.shutdown();
    }
}
