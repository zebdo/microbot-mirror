package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.timers.TimersPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.api.ItemID.BOOK_OF_THE_DEAD;

public class ThievingScript extends Script {

    public static String version = "1.6.1";
    ThievingConfig config;

    public boolean run(ThievingConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                List<Rs2Item> foods = Rs2Inventory.getInventoryFood();

                if (foods.isEmpty()) {
                    openCoinPouches(config);
                    dropItems(foods);
                    bank();
                    return;
                }
                if (Rs2Inventory.isFull()) {
                    dropItems(foods);
                }
                openCoinPouches(config);
                wearDodgyNecklace();
                Rs2Player.eatAt(config.hitpoints());
                if (config.shadowVeil() && Microbot.getVarbitValue(Varbits.SHADOW_VEIL) == 0) {
                        castShadowVeil();
                    }
                pickpocket();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleElves() {
        List<String> names = Arrays.asList(
                "Anaire", "Aranwe", "Aredhel", "Caranthir", "Celebrian", "Celegorm",
                "Cirdan", "Curufin", "Earwen", "Edrahil", "Elenwe", "Elladan", "Enel",
                "Erestor", "Enerdhil", "Enelye", "Feanor", "Findis", "Finduilas",
                "Fingolfin", "Fingon", "Galathil", "Gelmir", "Glorfindel", "Guilin",
                "Hendor", "Idril", "Imin", "Iminye", "Indis", "Ingwe", "Ingwion",
                "Lenwe", "Lindir", "Maeglin", "Mahtan", "Miriel", "Mithrellas",
                "Nellas", "Nerdanel", "Nimloth", "Oropher", "Orophin", "Saeros",
                "Salgant", "Tatie", "Thingol", "Turgon", "Vaire"
        );
        net.runelite.api.NPC npc = Rs2Npc.getNpcs()
                .filter(x -> names.stream()
                        .anyMatch(n -> n.equalsIgnoreCase(x.getName())))
                .findFirst()
                .orElse(null);
        if (npc != null) {
            if (Rs2Npc.pickpocket(npc)) {
                Microbot.status = "Pickpocketting " + npc.getName();
                sleep(300, 600);
            }
        }
    }

    private void openCoinPouches(ThievingConfig config) {
        if (Rs2Inventory.hasItemAmount("coin pouch", config.coinPouchTreshHold(), true)) {
            Rs2Inventory.interact("coin pouch", "open-all");
        }
    }

    private void wearDodgyNecklace() {
        if (!Rs2Equipment.isWearing("dodgy necklace")) {
            Rs2Inventory.wield("dodgy necklace");
        }
    }

    private void pickpocket() {
        if (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < config.hitpoints())
            return;
        if (config.THIEVING_NPC() != ThievingNpc.NONE) {
            sleepUntil(() -> TimersPlugin.t == null
                    || (!TimersPlugin.t.render())
                    || (TimersPlugin.t.render() && !Objects.equals(TimersPlugin.t.getName(), "PICKPOCKET_STUN")));
            System.out.println(TimersPlugin.t.getName());
            if (config.THIEVING_NPC() == ThievingNpc.ELVES) {
                handleElves();
            } else {
                Map<NPC, HighlightedNpc> highlightedNpcs =  net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs();
                if (highlightedNpcs.isEmpty()) {
                    if (Rs2Npc.pickpocket(config.THIEVING_NPC().getName())) {
                        sleep(50, 250);
                    } else {
                        Rs2Walker.walkTo(initialPlayerLocation);
                    }
                } else {
                    if (Rs2Npc.pickpocket(highlightedNpcs)) {
                        sleep(50, 250);
                    }
                }
            }
        }
    }

    private void bank() {
        Microbot.status = "Getting food from bank...";
        if (Rs2Bank.walkToBank()) {
            boolean isBankOpen = Rs2Bank.useBank();
            if (!isBankOpen) return;
            Rs2Bank.depositAll();
            Rs2Bank.withdrawX(true, config.food().getName(), config.foodAmount(), true);
            Rs2Bank.withdrawX(true, "dodgy necklace", config.dodgyNecklaceAmount());
            if (config.shadowVeil()) {
                Rs2Bank.withdrawAll(true,"Fire rune", true);
                sleep(75,200);
                Rs2Bank.withdrawAll(true,"Earth rune", true);
                sleep(75,200);
                Rs2Bank.withdrawAll(true,"Cosmic rune", true);
                sleep(75,200);
                if (config.equipBook()) {
                    Rs2Bank.withdrawAndEquip(BOOK_OF_THE_DEAD);
                } else {
                    Rs2Bank.withdrawItem(true,BOOK_OF_THE_DEAD);
                }
            }
            Rs2Bank.closeBank();
        }
    }

    private void dropItems(List<Rs2Item> food) {
        List<String> doNotDropItemList = Arrays.stream(config.DoNotDropItemList().split(",")).collect(Collectors.toList());

        List<String> foodNames = food.stream().map(x -> x.name).collect(Collectors.toList());

        doNotDropItemList.addAll(foodNames);

        doNotDropItemList.add(config.food().getName());
        doNotDropItemList.add("dodgy necklace");
        Rs2Inventory.dropAllExcept(config.keepItemsAboveValue(), doNotDropItemList);
    }

    private boolean castShadowVeil() {
        if (!Rs2Magic.isArceuus()) {
            return false;
        } else if (!(Rs2Player.getBoostedSkillLevel(Skill.MAGIC) >= MagicAction.SHADOW_VEIL.getLevel())) {
            return false;
        } else if (Microbot.getVarbitValue(Varbits.SHADOW_VEIL) == 1) {
            return false;
        } else if (Microbot.getVarbitValue(Varbits.SHADOW_VEIL_COOLDOWN) == 1) {
            return false;
        } else if (!Rs2Inventory.containsAll("Fire rune", "Cosmic rune", "Earth rune") &&
                !(Rs2Inventory.contains(BOOK_OF_THE_DEAD) || Rs2Equipment.hasEquipped(BOOK_OF_THE_DEAD))) {
            return false;
        }
        else {
            sleepUntil(() -> {
                Rs2Tab.switchToMagicTab();
                sleep(50, 150);
                return Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC;
            });
            Rs2Magic.cast(MagicAction.SHADOW_VEIL);
            Rs2Tab.switchToInventoryTab();
            return true;
        }
    }
}

