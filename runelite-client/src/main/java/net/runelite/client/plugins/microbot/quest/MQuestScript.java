package net.runelite.client.plugins.microbot.quest;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.cluescrolls.clues.emote.Emote;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.questhelper.requirements.Requirement;
import net.runelite.client.plugins.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.questhelper.steps.*;
import net.runelite.client.plugins.questhelper.steps.widget.WidgetHighlight;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MQuestScript extends Script {
    public static double version = 0.2;


    public static List<ItemRequirement> itemRequirements = new ArrayList<>();

    public static List<ItemRequirement> itemsMissing = new ArrayList<>();
    public static List<ItemRequirement> grandExchangeItems = new ArrayList<>();

    boolean unreachableTarget = false;
    int unreachableTargetCheckDist = 1;

    private MQuestConfig config;
    private static ArrayList<NPC> npcsHandled = new ArrayList<>();
    private static ArrayList<TileObject> objectsHandeled = new ArrayList<>();

    QuestStep dialogueStartedStep = null;

    public boolean run(MQuestConfig config) {
        this.config = config;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (getQuestHelperPlugin().getSelectedQuest() == null) return;

                if (Rs2Player.isAnimating())
                    Rs2Player.waitForAnimation();

                QuestStep questStep = getQuestHelperPlugin().getSelectedQuest().getCurrentStep().getActiveStep();

                if (Rs2Dialogue.isInDialogue() && dialogueStartedStep == null)
                    dialogueStartedStep = questStep;

                if (questStep != null && Rs2Widget.isWidgetVisible(WidgetInfo.DIALOG_OPTION_OPTIONS)){
                    var dialogOptions = Rs2Widget.getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS);
                    var dialogChoices = dialogOptions.getDynamicChildren();

                    for (var choice : questStep.getChoices().getChoices()){
                        if (choice.getExpectedPreviousLine() != null)
                            continue; // TODO

                        if (choice.getExcludedStrings() != null && choice.getExcludedStrings().stream().anyMatch(Rs2Widget::hasWidget))
                            continue;

                        for (var dialogChoice : dialogChoices){
                            if (dialogChoice.getText().endsWith(choice.getChoice())){
                                Rs2Keyboard.keyPress(dialogChoice.getOnKeyListener()[7].toString().charAt(0));
                                return;
                            }
                        }
                    }
                }

                if (questStep != null && !questStep.getWidgetsToHighlight().isEmpty()){
                    var widgetHighlight = questStep.getWidgetsToHighlight().stream()
                            .filter(x -> x instanceof WidgetHighlight)
                            .map(x -> (WidgetHighlight)x)
                            .filter(x -> Rs2Widget.isWidgetVisible(x.getGroupId(), x.getChildId()))
                            .findFirst().orElse(null);

                    if (widgetHighlight != null){
                        var widget = Rs2Widget.getWidget(widgetHighlight.getGroupId(), widgetHighlight.getChildId());
                        if (widget != null){
                            if (widgetHighlight.getChildChildId() != -1){
                                var childWidget = widget.getChildren()[widgetHighlight.getChildChildId()];
                                if (childWidget != null) {
                                    Rs2Widget.clickWidget(childWidget.getId());
                                    return;
                                }
                            } else {
                                Rs2Widget.clickWidget(widget.getId());
                                return;
                            }
                        }
                    }
                }

                if (getQuestHelperPlugin().getSelectedQuest() != null && !Microbot.getClientThread().runOnClientThread(() -> getQuestHelperPlugin().getSelectedQuest().isCompleted())) {
                    Widget widget = Rs2Widget.findWidget("Start ");
                    if (Rs2Widget.isWidgetVisible(WidgetInfo.DIALOG_OPTION_OPTIONS) && getQuestHelperPlugin().getSelectedQuest().getQuest().getId() != Quest.COOKS_ASSISTANT.getId() || (widget != null &&
                            Microbot.getClientThread().runOnClientThread(() -> widget.getParent().getId()) != 10616888)) {
                        Rs2Keyboard.keyPress('1');
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        return;
                    }

                    if (Rs2Dialogue.isInDialogue() && dialogueStartedStep == questStep) {
                        // Stop walker if in dialogue
                        Rs2Walker.setTarget(null);
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        return;
                    } else {
                        dialogueStartedStep = null;
                    }

                    boolean isInCutscene = Microbot.getVarbitValue(4606) > 0;
                    if (isInCutscene) {
                        if (ShortestPathPlugin.getMarker() != null)
                            ShortestPathPlugin.exit();
                        return;
                    }

                    if (questStep instanceof DetailedQuestStep && handleRequirements((DetailedQuestStep) questStep)){
                        sleep(500, 1000);
                        return;
                    }

                    /**
                     * This portion is needed when using item on another item in your inventory.
                     * If we do not prioritize this, the script will think we are missing items
                     */
                    if (questStep instanceof DetailedQuestStep && !(questStep instanceof NpcStep || questStep instanceof ObjectStep || questStep instanceof DigStep)) {
                        boolean result = applyDetailedQuestStep((DetailedQuestStep) getQuestHelperPlugin().getSelectedQuest().getCurrentStep().getActiveStep());
                        if (result) {
                            sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue(), 500);
                            sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isMoving() && !Rs2Player.isAnimating());
                            return;
                        }
                    }

                    if (getQuestHelperPlugin().getSelectedQuest().getCurrentStep() instanceof ConditionalStep) {
                        QuestStep conditionalStep = getQuestHelperPlugin().getSelectedQuest().getCurrentStep().getActiveStep();
                        applyStep(conditionalStep);
                    } else if (getQuestHelperPlugin().getSelectedQuest().getCurrentStep() instanceof NpcStep) {
                        applyNpcStep((NpcStep) getQuestHelperPlugin().getSelectedQuest().getCurrentStep());
                    } else if (getQuestHelperPlugin().getSelectedQuest().getCurrentStep() instanceof ObjectStep){
                        applyObjectStep((ObjectStep) getQuestHelperPlugin().getSelectedQuest().getCurrentStep());
                    } else if (getQuestHelperPlugin().getSelectedQuest().getCurrentStep() instanceof DigStep){
                        applyDigStep((DigStep) getQuestHelperPlugin().getSelectedQuest().getCurrentStep());
                    } else if (getQuestHelperPlugin().getSelectedQuest().getCurrentStep() instanceof PuzzleStep){
                        applyPuzzleStep((PuzzleStep) getQuestHelperPlugin().getSelectedQuest().getCurrentStep());
                    }

                    sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue(), 500);
                    sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isMoving() && !Rs2Player.isAnimating());
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }, 0, Random.random(400, 1000), TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean handleRequirements(DetailedQuestStep questStep) {
        var requirements = questStep.getRequirements();

        for (var requirement : requirements){
            if (requirement instanceof ItemRequirement){
                var itemRequirement = (ItemRequirement) requirement;

                if (itemRequirement.isEquip() && Rs2Inventory.contains(itemRequirement.getAllIds().toArray(new Integer[0]))
                    && itemRequirement.getAllIds().stream().noneMatch(Rs2Equipment::isWearing)){
                    Rs2Inventory.wear(itemRequirement.getAllIds().stream().filter(Rs2Inventory::contains).findFirst().orElse(-1));
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        reset();
    }

    public static void reset() {
        itemsMissing = new ArrayList<>();
        itemRequirements = new ArrayList<>();
        grandExchangeItems = new ArrayList<>();
    }

    public boolean applyStep(QuestStep step) {
        if (step == null) return false;

        if (step instanceof ObjectStep) {
            return applyObjectStep((ObjectStep) step);
        } else if (step instanceof NpcStep) {
            return applyNpcStep((NpcStep) step);
        } else if (step instanceof WidgetStep){
            return applyWidgetStep((WidgetStep) step);
        } else if (step instanceof DigStep){
            return applyDigStep((DigStep) step);
        } else if (step instanceof PuzzleStep){
            return applyPuzzleStep((PuzzleStep) step);
        } else if (step instanceof DetailedQuestStep) {
            return applyDetailedQuestStep((DetailedQuestStep) step);
        }
        return true;
    }

    public boolean applyNpcStep(NpcStep step) {
        var npcs = step.getNpcs();
        var npc = npcs.stream().findFirst().orElse(null);

        if (step.isAllowMultipleHighlights()){
            if (npcs.stream().anyMatch(x -> !npcsHandled.contains(x)))
                npc = npcs.stream().filter(x -> !npcsHandled.contains(x)).findFirst().orElse(null);
            else
                npc = npcs.stream().min(Comparator.comparing(x -> Rs2Player.getWorldLocation().distanceTo(x.getWorldLocation()))).orElse(null);
        }

        // Workaround for instances
        if (npc != null && Rs2Camera.isTileOnScreen(npc.getLocalLocation()) && (Microbot.getClient().isInInstancedRegion() || Rs2Npc.canWalkTo(npc, 10))) {
            // Stop pathing
            Rs2Walker.setTarget(null);

            if (step.getText().stream().anyMatch(x -> x.toLowerCase().contains("kill"))) {
                if (!Rs2Combat.inCombat())
                    Rs2Npc.interact(step.npcID, "Attack");

                return true;
            }

            if (step instanceof NpcEmoteStep){
                var emoteStep = (NpcEmoteStep)step;

                for (Widget emoteWidget : Rs2Widget.getWidget(WidgetInfo.EMOTE_CONTAINER).getDynamicChildren())
                {
                    if (emoteWidget.getSpriteId() == emoteStep.getEmote().getSpriteId())
                    {
                        var id = emoteWidget.getOriginalX() / 42 + ((emoteWidget.getOriginalY() - 6) / 49) * 4;

                        Microbot.doInvoke(new NewMenuEntry("Perform", emoteWidget.getText(), 1, MenuAction.CC_OP, id, ComponentID.EMOTES_EMOTE_CONTAINER, false), new Rectangle(0, 0, 1, 1));
                        Rs2Player.waitForAnimation();

                        if (Rs2Dialogue.isInDialogue())
                            return false;
                    }
                }
            }

            var itemId = step.getIconItemID();
            if (itemId != -1){
                Rs2Inventory.use(itemId);
                Rs2Npc.interact(npc);
            } else
                Rs2Npc.interact(npc, chooseCorrectNPCOption(step, npc));

            if (step.isAllowMultipleHighlights()){
                npcsHandled.add(npc);
                // Might open up a dialog
                sleepUntil(Rs2Dialogue::isInDialogue);
            }
        } else if (npc != null && !Rs2Camera.isTileOnScreen(npc.getLocalLocation())) {
            Rs2Walker.walkTo(npc.getWorldLocation(), 2);
        } else if (npc != null && (!Rs2Npc.hasLineOfSight(npc) || !Rs2Npc.canWalkTo(npc, 10))) {
            Rs2Walker.walkTo(npc.getWorldLocation(), 2);
        } else {
            if (step.getWorldPoint().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) > 3) {
                Rs2Walker.walkTo(step.getWorldPoint(), 2);
                return false;
            }
        }
        return true;
    }


    public boolean applyObjectStep(ObjectStep step) {
        var object = step.getObjects().stream().findFirst().orElse(null);
        var itemId = step.getIconItemID();

        if (step.getObjects().size() > 1){
            if (step.getObjects().stream().anyMatch(x -> !objectsHandeled.contains(x)))
                object = step.getObjects().stream().filter(x -> !objectsHandeled.contains(x)).findFirst().orElse(null);
            else
                object = step.getObjects().stream().min(Comparator.comparing(x -> Rs2Player.getWorldLocation().distanceTo(x.getWorldLocation()))).orElse(null);
        }

        if (object != null && unreachableTarget){
            var tileObjects = Rs2GameObject.getTileObjects().stream().filter(x -> x instanceof WallObject).collect(Collectors.toList());

            for (var tile : Rs2Tile.getWalkableTilesAroundTile(object.getWorldLocation(), unreachableTargetCheckDist)){
                if (tileObjects.stream().noneMatch(x -> x.getWorldLocation().equals(tile))){
                    if (!Rs2Walker.walkTo(tile) && ShortestPathPlugin.getPathfinder() == null)
                        return false;

                    sleepUntil(() -> ShortestPathPlugin.getPathfinder() == null || ShortestPathPlugin.getPathfinder().isDone());
                    if (ShortestPathPlugin.getPathfinder() == null || ShortestPathPlugin.getPathfinder().isDone()){
                        unreachableTarget = false;
                        unreachableTargetCheckDist = 1;
                    }
                    return false;
                }
            }

            unreachableTargetCheckDist++;
            return false;
        }

        if (step.getWorldPoint() != null && Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo2D(step.getWorldPoint()) > 1
                && !Rs2GameObject.canWalkTo(object, 10)) {
            WorldPoint targetTile = null;
            WorldPoint stepLocation = object == null ? step.getWorldPoint() : object.getWorldLocation();
            int radius = 0;
            while (targetTile == null) {
                radius++;
                TileObject finalObject = object;
                targetTile = Rs2Tile.getWalkableTilesAroundTile(stepLocation, radius)
                        .stream().filter(x -> Rs2GameObject.hasLineOfSight(x, finalObject))
                        .sorted(Comparator.comparing(x -> x.distanceTo(Rs2Player.getWorldLocation()))).findFirst().orElse(null);

                if (radius > 10 && targetTile == null)
                    targetTile = stepLocation;
            }

            Rs2Walker.walkTo(targetTile, 1);

            if (ShortestPathPlugin.getPathfinder() != null){
                var path = ShortestPathPlugin.getPathfinder().getPath();
                if (path.get(path.size() - 1).distanceTo(step.getWorldPoint()) <= 1)
                    return false;
            } else
                return false;
        }

        if (Rs2GameObject.hasLineOfSight(object) || object != null && (Rs2Camera.isTileOnScreen(object) || object.getCanvasLocation() != null)){
            // Stop pathing
            Rs2Walker.setTarget(null);

            if (itemId == -1)
                Rs2GameObject.interact(object, chooseCorrectObjectOption(step, object));
            else{
                Rs2Inventory.use(itemId);
                Rs2GameObject.interact(object);
            }

            sleepUntil(() -> Rs2Player.isWalking() || Rs2Player.isAnimating());
            sleep(100);
            sleepUntil(() -> !Rs2Player.isWalking() && !Rs2Player.isAnimating());
            objectsHandeled.add(object);
        }

        return true;
    }

    private boolean applyDigStep(DigStep step){
        if (!Rs2Walker.walkTo(step.getWorldPoint()))
            return false;
        else if (!Rs2Player.getWorldLocation().equals(step.getWorldPoint()))
            Rs2Walker.walkFastCanvas(step.getWorldPoint());
        else {
            Rs2Inventory.interact(ItemID.SPADE, "Dig");
            return true;
        }

        return false;
    }

    private boolean applyPuzzleStep(PuzzleStep step){
        if (!step.getHighlightedButtons().isEmpty()){
            var widgetDetails = step.getHighlightedButtons().stream().filter(x -> Rs2Widget.isWidgetVisible(x.groupID, x.childID)).findFirst().orElse(null);
            if (widgetDetails != null){
                Rs2Widget.clickWidget(widgetDetails.groupID, widgetDetails.childID);
                return true;
            }
        }

        return false;
    }

    private String chooseCorrectObjectOption(QuestStep step, TileObject object){
        ObjectComposition objComp = Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getObjectDefinition(object.getId()));

        if (objComp == null)
            return "";

        String[] actions;
        if (objComp.getImpostorIds() != null) {
            actions = objComp.getImpostor().getActions();
        } else {
            actions = objComp.getActions();
        }

        for (var action : actions){
            if (action != null && step.getText().stream().anyMatch(x -> x.toLowerCase().contains(action.toLowerCase())))
                return action;
        }

        return "";
    }

    private String chooseCorrectNPCOption(QuestStep step, NPC npc){
        var npcComp = Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getNpcDefinition(npc.getId()));

        if (npcComp == null)
            return "Talk-to";

        for (var action : npcComp.getActions()){
            if (action != null && step.getText().stream().anyMatch(x -> x.toLowerCase().contains(action.toLowerCase())))
                return action;
        }

        return "Talk-to";
    }

    private String chooseCorrectItemOption(QuestStep step, int itemId){
        for (var action : Rs2Inventory.get(itemId).getInventoryActions()){
            if (action != null && step.getText().stream().anyMatch(x -> x.toLowerCase().contains(action.toLowerCase())))
                return action;
        }

        return "use";
    }

    private boolean applyDetailedQuestStep(DetailedQuestStep conditionalStep) {
        if (conditionalStep instanceof NpcStep) return false;

        boolean usingItems = false;
        for (Requirement requirement : conditionalStep.getRequirements()) {
            if (requirement instanceof ItemRequirement) {
                ItemRequirement itemRequirement = (ItemRequirement) requirement;

                if (itemRequirement.shouldHighlightInInventory(Microbot.getClient())
                    && Rs2Inventory.contains(itemRequirement.getAllIds().toArray(new Integer[0]))) {
                    var itemId = itemRequirement.getAllIds().stream().filter(Rs2Inventory::contains).findFirst().orElse(-1);
                    Rs2Inventory.interact(itemId, chooseCorrectItemOption(conditionalStep, itemId));
                    sleep(100, 200);
                    usingItems = true;
                    continue;
                }

                if (!Rs2Inventory.contains(itemRequirement.getAllIds().toArray(new Integer[0])) && conditionalStep.getWorldPoint() != null) {
                    if (Rs2Walker.canReach(conditionalStep.getWorldPoint()) &&
                            (conditionalStep.getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) < 2)
                            || conditionalStep.getWorldPoint().toWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Microbot.getClient().getLocalPlayer().getWorldLocation().toWorldArea())
                            && Rs2Camera.isTileOnScreen(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), conditionalStep.getWorldPoint()))) {
                        Rs2GroundItem.loot(itemRequirement.getId());
                    } else {
                        Rs2Walker.walkTo(conditionalStep.getWorldPoint(), 2);
                    }
                    return true;
                } else if (!Rs2Inventory.contains(itemRequirement.getAllIds().toArray(new Integer[0]))){
                    Rs2GroundItem.loot(itemRequirement.getId());
                    return true;
                }
            }
        }

        if (!usingItems && conditionalStep.getWorldPoint() != null && !Rs2Walker.walkTo(conditionalStep.getWorldPoint()))
            return true;

        return usingItems;
    }

    private boolean applyWidgetStep(WidgetStep step) {
        var widgetDetails = step.getWidgetDetails().get(0);
        var widget = Microbot.getClient().getWidget(widgetDetails.groupID, widgetDetails.childID);

        if (widgetDetails.childChildID != -1){
            var tmpWidget = widget.getChild(widgetDetails.childChildID);

            if (tmpWidget != null)
                widget = tmpWidget;
        }

        return Rs2Widget.clickWidget(widget.getId());
    }

    protected QuestHelperPlugin getQuestHelperPlugin() {
        return (QuestHelperPlugin)Microbot.getPluginManager().getPlugins().stream().filter(x -> x instanceof QuestHelperPlugin).findFirst().orElse(null);
    }

    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getMessage().equalsIgnoreCase("I can't reach that!"))
            unreachableTarget = true;
    }
}
