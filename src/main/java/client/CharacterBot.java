package client;

import client.inventory.InventoryType;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import net.PacketProcessor;
import server.life.Monster;
import server.maps.*;
import tools.PacketCreator;
import tools.Randomizer;

import java.sql.SQLException;

public class CharacterBot {
    private enum Mode {
        WAITING, // no mode decided
        SOCIALIZING, // hang out in henesys
        GRINDING, // pick a map and kill monsters
        PQ, // do a pq
        BOSSING // fight a boss

    }

    private Character following = null;
    private Foothold foothold;
    private Client c;
    private Monster targetMonster;
    private MapObject targetItem;
    private boolean hasTargetMonster = false;
    private boolean hasTargetItem = false;
    private boolean facingLeft = false;
    private String login;
    private int charID;
    private Mode currentMode = Mode.WAITING;
    long previousAction = System.currentTimeMillis();
    private int level;

    public void setFollowing(Character following) {
        this.following = following;
    }

    public void login(String login, int charID) throws SQLException {
        this.login = login;
        this.charID = charID;
        c = Client.createLoginClient(-1, "127.0.0.1", PacketProcessor.getLoginServerProcessor(), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPasswordPacket(login), (short) 1);
        c.handlePacket(PacketCreator.createServerListRequestPacket(), (short) 11);
        c.handlePacket(PacketCreator.createCharListRequestPacket(), (short) 5);
        c.handlePacket(PacketCreator.createCharSelectedPacket(charID), (short) 19);
        c = Client.createChannelClient(-1, "127.0.0.1", PacketProcessor.getChannelServerProcessor(0, 1), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPacket(charID), (short) 20);
        c.handlePacket(PacketCreator.createPartySearchUpdatePacket(), (short) 223);
        c.handlePacket(PacketCreator.createPlayerMapTransitionPacket(), (short) 207);
        //character will be floating at this point, so update position so send a packet to change their state and update their position so they are on the ground
        foothold = c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition());
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) c.getPlayer().getPosition().x, (short) foothold.getY1(), (byte) 4, (short) 100), (short) 41);
        level = c.getPlayer().getLevel();
    }

    public boolean isFollower() {
        return following != null;
    }

    public void update() {
        if (true) {
            return; // disable bots for testing purposes
        }
        if (c.getPlayer().getLevel() > level) {
            levelup();
            return;
        }
        int time = (int) (System.currentTimeMillis() - previousAction); // amount of time for actions
        previousAction = System.currentTimeMillis();
        switch (currentMode) {
            case WAITING -> chooseMode();
            case GRINDING -> grind(time);
        }
    }

    private int moveBot(short targetX, short targetY, int time) {
        if (time == 0) {
            return 0;
        }
        int timeRemaining = time;
        short nextX, nextY;
        byte nextState;
        int xDiff = c.getPlayer().getPosition().x - targetX, yDiff = c.getPlayer().getPosition().y - targetY;
        System.out.println("xDiff: " + xDiff + ", yDiff: " + yDiff);
        if (yDiff < 0) {
            nextX = (short) (c.getPlayer().getPosition().x);
            if (-8 * yDiff < timeRemaining) {
                nextY = targetY;
                nextState = 16;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= -8 * yDiff;
            } else {
                nextY = (short) (c.getPlayer().getPosition().y + timeRemaining / 8.0);
                nextState = 16;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining = 0;
            }
        } else if (yDiff > 0) {
            nextX = (short) (c.getPlayer().getPosition().x);
            if (8 * yDiff < timeRemaining) {
                nextY = targetY;
                nextState = 16;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= 8 * yDiff;
            } else {
                nextY = (short) (c.getPlayer().getPosition().y - timeRemaining / 8.0);
                nextState = 16;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining = 0;
            }
        }
        if (timeRemaining == 0) {
            return 0;
        }
        if (xDiff < 0) {
            facingLeft = false;
            if (-8 * xDiff < timeRemaining) {
                nextX = targetX;
                nextY = (short) (c.getPlayer().getPosition().y);
                nextState = 2;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= -8 * xDiff;
            } else {
                nextX = (short) (c.getPlayer().getPosition().x + timeRemaining / 8.0);
                nextY = (short) (c.getPlayer().getPosition().y);
                nextState = 2;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining = 0;
            }
        } else if (xDiff > 0) {
            facingLeft = true;
            if (8 * xDiff < timeRemaining) {
                nextX = targetX;
                nextY = (short) (c.getPlayer().getPosition().y);
                nextState = 3;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= 8 * xDiff;
            } else {
                nextX = (short) (c.getPlayer().getPosition().x - timeRemaining / 8.0);
                nextY = (short) (c.getPlayer().getPosition().y);
                nextState = 3;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining = 0;
            }
        }
        if (c.getPlayer().getPosition().x == targetX && c.getPlayer().getPosition().y == targetY) {
            c.handlePacket(PacketCreator.createPlayerMovementPacket((short) (c.getPlayer().getPosition().x), (short) (c.getPlayer().getPosition().y), (byte) (facingLeft ? 5 : 4), (short) 10), (short) 41);
        }
        return timeRemaining - 10;
    }

    private void pickupItem() {
        c.handlePacket(PacketCreator.createPickupItemPacket(targetItem.getObjectId()), (short) 202);
    }

    private void attack() {
        int monsterAvoid = targetMonster.getStats().getEva();
        int playerAccuracy = 1000; // todo: calc player accuracy
        int leveldelta = Math.max(0, targetMonster.getLevel() - c.getPlayer().getLevel());
        if (Randomizer.nextDouble() < calculateHitchance(leveldelta, playerAccuracy, monsterAvoid)) {
            // todo: criticals
            c.handlePacket(PacketCreator.createRegularAttackPacket(targetMonster.getObjectId(), calcRegularAttackDamage(leveldelta), facingLeft), (short) 44);
        } else {
            //didn't hit, todo: send miss packet
        }
        //return time;
    }

    private int calcRegularAttackDamage(int leveldelta) {
        int maxDamage = c.getPlayer().calculateMaxBaseDamage(c.getPlayer().getTotalWatk());
        int minDamage = c.getPlayer().calculateMinBaseDamage(c.getPlayer().getTotalWatk());
        int monsterPhysicalDefense = targetMonster.getStats().getPDDamage();
        minDamage = Math.max(1, (int) (minDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.6));
        maxDamage = Math.max(1, (int) (maxDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.5));
        return Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage;
    }

    private float calculateHitchance(int leveldelta, int playerAccuracy, int avoid) {
        float faccuracy = (float) playerAccuracy;
        float hitchance = faccuracy / (((1.84f + 0.07f * leveldelta) * avoid) + 1.0f);
        if (hitchance < 0.01f) {
            hitchance = 0.01f;
        }
        return hitchance;
    }

    private void chooseMode() {
        if (c.getPlayer().getJob().equals(Job.BEGINNER)) {
            currentMode = Mode.GRINDING;
            pickMap();
        }
    }

    private void pickMap() {
        if (c.getPlayer().getJob().equals(Job.BEGINNER)) {
            MapleMap target = c.getChannelServer().getMapFactory().getMap(104000100 + Randomizer.nextInt(3) * 100);
            Portal targetPortal = target.getRandomPlayerSpawnpoint();
            c.getPlayer().changeMap(target, targetPortal);
        }
    }

    private boolean grind(int time) {
        boolean didAction = true;
        while (time > 0 && didAction) {
            didAction = false;
            hasTargetItem = false;
            if (!c.getPlayer().getMap().getItems().isEmpty()) { // todo: only try to pick up items if appropriate inventory is not full
                for (MapObject it : c.getPlayer().getMap().getItems()) {
                    if (!((MapItem) it).isPickedUp() && ((MapItem) it).canBePickedBy(c.getPlayer())) {
                        targetItem = it;
                        hasTargetItem = true;
                        break;
                    }
                }
            }
            if (!hasTargetMonster || !targetMonster.isAlive()) {
                if (!c.getPlayer().getMap().getAllMonsters().isEmpty()) {
                    targetMonster = c.getPlayer().getMap().getAllMonsters().get(Randomizer.nextInt(c.getPlayer().getMap().getAllMonsters().size()));
                    hasTargetMonster = true;
                } else {
                    hasTargetMonster = false;
                }
            }
            if (hasTargetItem) {
                if (!c.getPlayer().getPosition().equals(targetItem.getPosition())) {
                    time = moveBot((short) targetItem.getPosition().x, (short) targetItem.getPosition().y, time);
                    didAction = true;
                }
                if (c.getPlayer().getPosition().equals(targetItem.getPosition())) {
                    pickupItem();
                    didAction = true;
                }
            } else if (hasTargetMonster) {
                if (!c.getPlayer().getPosition().equals(targetMonster.getPosition())) {
                    time = moveBot((short) targetMonster.getPosition().x, (short) targetMonster.getPosition().y, time);
                    didAction = true;
                }
                if (c.getPlayer().getPosition().equals(targetMonster.getPosition()) && time > 500) {
                    attack();
                    time -= 500; // todo: make this accurate
                    didAction = true;
                }
            }
        }
        return didAction;
    }

    private void levelup() {
        this.level = c.getPlayer().getLevel();
        if (c.getPlayer().getJob().equals(Job.BEGINNER)) {
            if (level == 8 && Randomizer.nextInt(5) == 0) { // 1/5 chance to choose magician
                jobAdvance(Job.MAGICIAN);
            } else if (level == 10 && Randomizer.nextInt(500) != 0) {
                // randomly pick between the 4 non-magician explorer jobs
                // 1/500 chance to be a perma beginner, technically this makes mages slightly more common than the other 4, but it's negligible on this scale
                Job[] jobs = {Job.WARRIOR, Job.BOWMAN, Job.THIEF, Job.PIRATE};
                jobAdvance(jobs[Randomizer.nextInt(jobs.length)]);
            }
        } else if (c.getPlayer().getLevel() == 30) { // note: these assume the bot will not gain more than 1 level between updates
            // todo: second job
        } else if (c.getPlayer().getLevel() == 70) {
            // todo: third job
        } else if (c.getPlayer().getLevel() == 120) {
            // todo: fourth job
        }
        int remainingAP = c.getPlayer().getRemainingAp(), nextAP;
        if (c.getPlayer().getJob().equals(Job.BEGINNER)) {
            if (c.getPlayer().getTotalDex() < 60) {
                nextAP = Math.min(remainingAP, 60 - c.getPlayer().getTotalDex());
                remainingAP -= nextAP;
                c.getPlayer().assignDex(nextAP);
            }
            c.getPlayer().assignStr(remainingAP);
        } else if (c.getPlayer().getJob().getId() / 100 == 1) { // warrior
            if (c.getPlayer().getTotalDex() < 60 && c.getPlayer().getTotalDex() < c.getPlayer().getLevel() + 10) {
                nextAP = Math.min(Math.min(remainingAP, 60 - c.getPlayer().getTotalDex()), c.getPlayer().getLevel() + 10 - c.getPlayer().getTotalDex());
                remainingAP -= nextAP;
                c.getPlayer().assignDex(nextAP);
            }
            c.getPlayer().assignStr(remainingAP);
        } else if (c.getPlayer().getJob().getId() / 100 == 2) { // magician
            if (c.getPlayer().getTotalLuk() < 123 && c.getPlayer().getTotalLuk() < c.getPlayer().getLevel() + 3) {
                nextAP = Math.min(Math.min(remainingAP, 123 - c.getPlayer().getTotalLuk()), c.getPlayer().getLevel() + 3 - c.getPlayer().getTotalLuk());
                remainingAP -= nextAP;
                c.getPlayer().assignLuk(nextAP);
            }
            c.getPlayer().assignInt(remainingAP);
        } else if (c.getPlayer().getJob().getId() / 100 == 3) { // bowman
            if (c.getPlayer().getWeaponType().equals(WeaponType.BOW)) {
                if (c.getPlayer().getTotalStr() < 125 && c.getPlayer().getTotalStr() < c.getPlayer().getLevel() + 5) {
                    nextAP = Math.min(Math.min(remainingAP, 125 - c.getPlayer().getTotalStr()), c.getPlayer().getLevel() + 5 - c.getPlayer().getTotalStr());
                    remainingAP -= nextAP;
                    c.getPlayer().assignStr(nextAP);
                }
            } else { // crossbow
                if (c.getPlayer().getTotalStr() < 120 && c.getPlayer().getTotalStr() < c.getPlayer().getLevel()) {
                    nextAP = Math.min(Math.min(remainingAP, 120 - c.getPlayer().getTotalStr()), c.getPlayer().getLevel() - c.getPlayer().getTotalStr());
                    remainingAP -= nextAP;
                    c.getPlayer().assignStr(nextAP);
                }
            }
            c.getPlayer().assignDex(remainingAP);
        } else if (c.getPlayer().getJob().getId() / 100 == 4) { // thief
            if (c.getPlayer().getTotalDex() < 160 && c.getPlayer().getTotalDex() < c.getPlayer().getLevel() + 40) {
                nextAP = Math.min(Math.min(remainingAP, 160 - c.getPlayer().getTotalDex()), c.getPlayer().getLevel() + 40 - c.getPlayer().getTotalDex());
                remainingAP -= nextAP;
                c.getPlayer().assignDex(nextAP);
            }
            /*if (weaponType.equals(WeaponType.DAGGER_THIEVES)) {
                // todo: str daggers
            }*/
            c.getPlayer().assignLuk(remainingAP);
        } else if (c.getPlayer().getJob().getId() / 100 == 5) { // pirate
            if (c.getPlayer().getWeaponType().equals(WeaponType.GUN)) {
                if (c.getPlayer().getTotalStr() < 120 && c.getPlayer().getTotalStr() < c.getPlayer().getLevel()) {
                    nextAP = Math.min(Math.min(remainingAP, 120 - c.getPlayer().getTotalStr()), c.getPlayer().getLevel() - c.getPlayer().getTotalStr());
                    remainingAP -= nextAP;
                    c.getPlayer().assignStr(nextAP);
                }
                c.getPlayer().assignDex(remainingAP);
            } else { // knuckle
                if (c.getPlayer().getTotalDex() < 120 && c.getPlayer().getTotalDex() < c.getPlayer().getLevel()) {
                    nextAP = Math.min(Math.min(remainingAP, 120 - c.getPlayer().getTotalDex()), c.getPlayer().getLevel() - c.getPlayer().getTotalDex());
                    remainingAP -= nextAP;
                    c.getPlayer().assignDex(nextAP);
                }
                c.getPlayer().assignStr(remainingAP);
            }
        }
        assignSP();
    }

    private void jobAdvance(Job newJob) {
        boolean firstJob = c.getPlayer().getJob().equals(Job.BEGINNER);
        c.getPlayer().changeJob(newJob);
        if (firstJob) {
            c.getPlayer().resetStats();
            switch (newJob) {
                case WARRIOR -> gainAndEquip(1302077);
                case MAGICIAN -> gainAndEquip(1372043);
                case BOWMAN -> {
                    gainAndEquip(1452051);
                    gainItem(2060000, (short) 1000); // arrows
                }
                case THIEF -> {
                    gainAndEquip(1472061);
                    gainItem(2070015, (short) 500); // stars
                }
                case PIRATE -> {
                    if (Randomizer.nextInt(2) == 0) {
                        gainAndEquip(1492000);
                        gainItem(2330000, (short) 1000);
                    } else {
                        gainAndEquip(1482000);
                    }
                }
            }
        }
    }

    private void assignSP() {
        // todo
    }

    private void gainItem(int itemId, short quantity) {
        InventoryType inventoryType = ItemConstants.getInventoryType(itemId);
        while (!InventoryManipulator.checkSpace(c, itemId, quantity, "")) {
            // make sure not to give a quantity that can't fit in their inventory or this will get stuck
            short lowestValueItemPos = getLowestValueItemPos(inventoryType);
            InventoryManipulator.drop(c, inventoryType, lowestValueItemPos, InventoryManipulator.getQuantity(c, inventoryType, lowestValueItemPos));
        }
        InventoryManipulator.addById(c, itemId, quantity);
    }

    private void gainAndEquip(int itemId) {
        if (!ItemConstants.getInventoryType(itemId).equals(InventoryType.EQUIP)) {
            return; // check that it actually is an equip
        }
        if (!InventoryManipulator.checkSpace(c, itemId, 1, "")) {
            InventoryManipulator.drop(c, InventoryType.EQUIP, getLowestValueItemPos(InventoryType.EQUIP), (short) 1);
        }
        InventoryManipulator.addById(c, itemId, (short) 1);
        InventoryManipulator.equip(c, InventoryManipulator.getPosition(c, itemId), (short) -11);
    }

    private short getLowestValueItemPos(InventoryType inventoryType) {
        // todo
        return 1;
    }
}
