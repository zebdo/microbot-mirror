package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.TileObject;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.cannon.CannonPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class Rs2Cannon {

    public static boolean repair() {
        TileObject brokenCannon = Rs2GameObject.findObjectById(14916);

        if (brokenCannon == null) return false;

        Microbot.status = "Repairing Cannon";

        Rs2GameObject.interact(brokenCannon, "Repair");
        return true;
    }

    public static boolean refill() {
        return refill(Random.random(10, 15));
    }

    public static boolean refill(int cannonRefillAmount) {
        if (!Rs2Inventory.hasItemAmount("cannonball", 15, true)) {
            System.out.println("Not enough cannonballs!");
            return false;
        }

        int cannonBallsLeft = Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getVarpValue(VarPlayer.CANNON_AMMO));

        if (cannonBallsLeft > cannonRefillAmount) return false;

        Microbot.status = "Refilling Cannon";

        TileObject cannon = Rs2GameObject.findObjectById(6);
        if (cannon == null) return false;

        WorldArea cannonLocation = new WorldArea(cannon.getWorldLocation().getX() - 1, cannon.getWorldLocation().getY() - 1, 3, 3, cannon.getWorldLocation().getPlane());
        if (!cannonLocation.toWorldPoint().equals(CannonPlugin.getCannonPosition().toWorldPoint())) return false;

        Rs2GameObject.interact(cannon, "Fire");
        Rs2Player.waitForWalking();
        sleep(1200);
        Rs2GameObject.interact(cannon, "Fire");
        sleepUntil(() -> Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getVarpValue(VarPlayer.CANNON_AMMO)) > Random.random(10, 15));
        return true;
    }

}
