package client;

import net.PacketProcessor;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapItem;
import server.maps.MapObject;
import tools.PacketCreator;
import tools.Randomizer;

import java.sql.SQLException;

public class CharacterBot {

    private Character following = null;
    private Foothold foothold;
    private int xpos, ypos;
    private int leftovertime;
    private Client c;
    private Monster targetMonster;
    private MapObject targetItem;
    private boolean hasTargetMonster = false;
    private boolean hasTargetItem = false;
    private boolean facingLeft = false;

    public void setFollowing(Character following) {
        this.following = following;
    }

    public void login() throws SQLException {
        c = Client.createLoginClient(-1, "127.0.0.1", PacketProcessor.getLoginServerProcessor(), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPasswordPacket(), (short) 1);
        c.handlePacket(PacketCreator.createServerListRequestPacket(), (short) 11);
        c.handlePacket(PacketCreator.createCharListRequestPacket(), (short) 5);
        c.handlePacket(PacketCreator.createCharSelectedPacket(5), (short) 19);
        c = Client.createChannelClient(-1, "127.0.0.1", PacketProcessor.getChannelServerProcessor(0, 1), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPacket(5), (short) 20);
        c.handlePacket(PacketCreator.createPartySearchUpdatePacket(), (short) 223);
        c.handlePacket(PacketCreator.createPlayerMapTransitionPacket(), (short) 207);
        //character will be floating at this point, so update position so send a packet to change their state and update their position so they are on the ground
        foothold = c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition());
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) c.getPlayer().getPosition().x, (short) foothold.getY1(), (byte) 4, (short) 100), (short) 41);
    }

    public boolean isFollower() {
        return following != null;
    }

    public void update() {
        int time = 500 + leftovertime; // amount of time for actions
        hasTargetItem = false;
        if (!c.getPlayer().getMap().getItems().isEmpty()) {
            for (MapObject it : c.getPlayer().getMap().getItems()) {
                if (((MapItem) it).canBePickedBy(c.getPlayer())) {
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
            }
            if (c.getPlayer().getPosition().equals(targetItem.getPosition())) {
                pickupItem();
            }
        } else if (hasTargetMonster) {
            if (!c.getPlayer().getPosition().equals(targetMonster.getPosition())) {
                time = moveBot((short) targetMonster.getPosition().x, (short) targetMonster.getPosition().y, time);
            }
            if (c.getPlayer().getPosition().equals(targetMonster.getPosition()) && time > 400) {
                attack();
            }
        }

        leftovertime = Math.min(100, time);
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
        return timeRemaining;
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
        return Randomizer.nextInt(maxDamage - minDamage) + minDamage;
    }

    private float calculateHitchance(int leveldelta, int playerAccuracy, int avoid) {
        float faccuracy = (float) playerAccuracy;
        float hitchance = faccuracy / (((1.84f + 0.07f * leveldelta) * avoid) + 1.0f);
        if (hitchance < 0.01f) {
            hitchance = 0.01f;
        }
        return hitchance;
    }
}
