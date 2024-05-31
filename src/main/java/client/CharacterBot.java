package client;

import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
import client.processor.stat.AssignSPProcessor;
import constants.inventory.ItemConstants;
import constants.skills.*;
import net.PacketProcessor;
import net.server.channel.handlers.AbstractDealDamageHandler;
import org.w3c.dom.ranges.Range;
import server.ItemInformationProvider;
import server.StatEffect;
import server.life.Monster;
import server.life.MonsterDropEntry;
import server.maps.*;
import tools.PacketCreator;
import tools.Randomizer;

import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class CharacterBot {
    private enum Mode {
        WAITING, // no mode decided
        SOCIALIZING, // hang out in henesys
        GRINDING, // pick a map and kill monsters
        PQ, // do a pq
        BOSSING // fight a boss

    }

    private static Map<Job, int[][]> skillOrders = new HashMap<>();
    private static Map<Integer, Integer> skillDelayTimes = new HashMap<>(); // todo: put delay times

    private Character following = null;
    //private Foothold foothold;
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
    private int singleTargetAttack = -1, mobAttack = -1;  // skill id used for attacks, -1 is regular attack
    private List<Integer> buffSkills = new ArrayList<>(); // skill ids of the buff skills available to use
    private long currentModeStartTime;
    private boolean loggedOut = false;
    private long delay = 0; // delay after using an attack before another can be used

    public void setFollowing(Character following) {
        this.following = following;
    }

    public Character getPlayer() {
        return c.getPlayer();
    }

    public void login(String login, String password, int charID) throws SQLException {
        this.login = login;
        this.charID = charID;
        c = Client.createLoginClient(-1, "127.0.0.1", PacketProcessor.getLoginServerProcessor(), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPasswordPacket(login, password), (short) 1);
        c.handlePacket(PacketCreator.createServerListRequestPacket(), (short) 11);
        c.handlePacket(PacketCreator.createCharListRequestPacket(), (short) 5);
        c.handlePacket(PacketCreator.createCharSelectedPacket(charID), (short) 19);
        c = Client.createChannelClient(-1, "127.0.0.1", PacketProcessor.getChannelServerProcessor(0, 1), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPacket(charID), (short) 20);
        c.handlePacket(PacketCreator.createPartySearchUpdatePacket(), (short) 223);
        c.handlePacket(PacketCreator.createPlayerMapTransitionPacket(), (short) 207);
        //character will be floating at this point, so update position so send a packet to change their state and update their position so they are on the ground
        Foothold foothold = c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition());
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) c.getPlayer().getPosition().x, (short) foothold.getY1(), (byte) 4, (short) 100), (short) 41);
        level = c.getPlayer().getLevel();
        decideAttackSkills();
        putBuffSkills();
        chooseMode();
    }

    public void logout() {
        loggedOut = true;
        c.disconnect(false, false);
    }

    public boolean isFollower() {
        return following != null;
    }

    public void update() {
        if (loggedOut || level >= 70 || true) { // temporarily disabled bot updates for testing purposes
            return;
        }
        c.getPlayer().setMp(c.getPlayer().getMaxMp()); // todo: accurate potion usage, for now just refresh their mp each update
        if (c.getPlayer().getLevel() > level || c.getPlayer().getRemainingSp() > 0) {
            levelup();
            decideAttackSkills();
            putBuffSkills();
            return;
        }
        // todo: use mp pots
        for (int i : buffSkills) {
            if (c.getPlayer().getExpirationTime(i) - System.currentTimeMillis() < 10000 && c.getPlayer().getMp() > SkillFactory.getSkill(i).getEffect(c.getPlayer().getSkillLevel(i)).getMpCon()) {
                useBuff(i);
                return;
            }
        }
        int time = (int) (System.currentTimeMillis() - previousAction - delay); // amount of time for actions
        previousAction = System.currentTimeMillis();
        delay = 0;
        switch (currentMode) {
            case WAITING -> chooseMode();
            case GRINDING -> grind(time);
        }
    }

    public void followerUpdate() {
        if (loggedOut) {
            return;
        }
        c.getPlayer().setMp(c.getPlayer().getMaxMp()); // todo: accurate potion usage, for now just refresh their mp each update
        if (c.getPlayer().getLevel() > level || c.getPlayer().getRemainingSp() > 0) {
            levelup();
            decideAttackSkills();
            putBuffSkills();
            return;
        }
        if (c.getPlayer().getMapId() != following.getMapId()) {
            changeMap(following.getMap(), following.getMap().findClosestPortal(following.getPosition()));
            return;
        }
        // todo: use mp pots
        for (int i : buffSkills) {
            if (c.getPlayer().getExpirationTime(i) - System.currentTimeMillis() < 10000 && c.getPlayer().getMp() > SkillFactory.getSkill(i).getEffect(c.getPlayer().getSkillLevel(i)).getMpCon()) {
                useBuff(i);
                return;
            }
        }
        // todo: pqs
        System.out.println("current time: " + System.currentTimeMillis() + ", previous action: " + previousAction + ", delay: " + delay);
        int time = (int) (System.currentTimeMillis() - previousAction - delay); // amount of time for actions
        System.out.println("time: " + time);
        previousAction = System.currentTimeMillis();
        delay = 0;
        grind(time);
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
        if (hasTargetMonster) {
            facingLeft = targetMonster.getPosition().x < c.getPlayer().getPosition().x;
        }
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) (c.getPlayer().getPosition().x), (short) (c.getPlayer().getPosition().y), (byte) (facingLeft ? 5 : 4), (short) 10), (short) 41);
        return timeRemaining - 10;
    }

    private void pickupItem() {
        c.handlePacket(PacketCreator.createPickupItemPacket(targetItem.getObjectId()), (short) 202);
        hasTargetItem = false;
    }

    private void decideAttackSkills() {
        switch (c.getPlayer().getJob()) {
            case WARRIOR:
            case FIGHTER:
            case PAGE:
            case SPEARMAN:
            case CRUSADER:
                if (c.getPlayer().getSkillLevel(Warrior.POWER_STRIKE) > 0) {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                }
                if (c.getPlayer().getSkillLevel(Warrior.SLASH_BLAST) > 0) {
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case HERO:
                if (c.getPlayer().getSkillLevel(Hero.BRANDISH) > 0) {
                    singleTargetAttack = Hero.BRANDISH;
                    mobAttack = Hero.BRANDISH;
                } else {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case WHITEKNIGHT:
                singleTargetAttack = Warrior.POWER_STRIKE;
                if (c.getPlayer().getSkillLevel(WhiteKnight.CHARGE_BLOW) > 0) {
                    mobAttack = WhiteKnight.CHARGE_BLOW;
                } else {
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case PALADIN:
                if (c.getPlayer().getSkillLevel(Paladin.BLAST) > 0) {
                    singleTargetAttack = Paladin.BLAST;
                } else {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                }
                mobAttack = WhiteKnight.CHARGE_BLOW;
                break;
            case DRAGONKNIGHT:
                if (c.getPlayer().getSkillLevel(DragonKnight.SPEAR_CRUSHER) > 0) {
                    singleTargetAttack = DragonKnight.SPEAR_CRUSHER;
                } else {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                }
                if (c.getPlayer().getSkillLevel(DragonKnight.SPEAR_DRAGON_FURY) > 0) {
                    mobAttack = DragonKnight.SPEAR_DRAGON_FURY;
                } else {
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case DARKKNIGHT:
                singleTargetAttack = DragonKnight.SPEAR_CRUSHER;
                mobAttack = DragonKnight.SPEAR_DRAGON_FURY;
                break;
            case MAGICIAN:
                if (c.getPlayer().getSkillLevel(Magician.MAGIC_CLAW) > 0) {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                    mobAttack = Magician.MAGIC_CLAW;
                } else if (c.getPlayer().getSkillLevel(Magician.ENERGY_BOLT) > 0) {
                    singleTargetAttack = Magician.ENERGY_BOLT;
                    mobAttack = Magician.ENERGY_BOLT;
                }
                break;
            case FP_WIZARD:
                if (c.getPlayer().getSkillLevel(FPWizard.FIRE_ARROW) > 0) {
                    singleTargetAttack = FPWizard.FIRE_ARROW;
                    mobAttack = FPWizard.FIRE_ARROW;
                } else {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                    mobAttack = Magician.MAGIC_CLAW;
                }
                break;
            case FP_MAGE:
                if (c.getPlayer().getSkillLevel(FPMage.ELEMENT_AMPLIFICATION) > 0) {
                    singleTargetAttack = FPMage.ELEMENT_AMPLIFICATION;
                } else {
                    singleTargetAttack = FPWizard.FIRE_ARROW;
                }
                if (c.getPlayer().getSkillLevel(FPMage.EXPLOSION) > 0) {
                    mobAttack = FPMage.EXPLOSION;
                } else {
                    mobAttack = FPWizard.FIRE_ARROW;
                }
                break;
            case FP_ARCHMAGE:
                if (c.getPlayer().getSkillLevel(FPArchMage.PARALYZE) > 0) {
                    singleTargetAttack = FPArchMage.PARALYZE;
                } else {
                    singleTargetAttack = FPMage.ELEMENT_AMPLIFICATION;
                }
                if (c.getPlayer().getSkillLevel(FPArchMage.METEOR_SHOWER) > 0) {
                    mobAttack = FPArchMage.METEOR_SHOWER;
                } else {
                    mobAttack = FPMage.EXPLOSION;
                }
                break;
            case IL_WIZARD:
                if (c.getPlayer().getSkillLevel(ILWizard.COLD_BEAM) > 0) {
                    singleTargetAttack = ILWizard.COLD_BEAM;
                } else {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                }
                if (c.getPlayer().getSkillLevel(ILWizard.THUNDERBOLT) > 0) {
                    mobAttack = ILWizard.THUNDERBOLT;
                } else {
                    mobAttack = Magician.MAGIC_CLAW;
                }
                break;
            case IL_MAGE:
                if (c.getPlayer().getSkillLevel(ILMage.ELEMENT_COMPOSITION) > 0) {
                    singleTargetAttack = ILMage.ELEMENT_COMPOSITION;
                } else {
                    singleTargetAttack = ILWizard.COLD_BEAM;
                }
                if (c.getPlayer().getSkillLevel(ILMage.ICE_STRIKE) > 0) {
                    mobAttack = ILMage.ICE_STRIKE;
                } else {
                    mobAttack = ILWizard.THUNDERBOLT;
                }
                break;
            case IL_ARCHMAGE:
                if (c.getPlayer().getSkillLevel(ILArchMage.CHAIN_LIGHTNING) > 0) {
                    singleTargetAttack = ILArchMage.CHAIN_LIGHTNING;
                } else {
                    singleTargetAttack = ILMage.ELEMENT_COMPOSITION;
                }
                if (c.getPlayer().getSkillLevel(ILArchMage.BLIZZARD) > 0) {
                    mobAttack = ILArchMage.BLIZZARD;
                } else {
                    mobAttack = ILMage.ICE_STRIKE;
                }
                break;
            case CLERIC:
                if (c.getPlayer().getSkillLevel(Cleric.HOLY_ARROW) > 0) {
                    singleTargetAttack = Cleric.HOLY_ARROW;
                    mobAttack = Cleric.HOLY_ARROW;
                } else {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                    mobAttack = Magician.MAGIC_CLAW;
                }
                break;
            case PRIEST:
                if (c.getPlayer().getSkillLevel(Priest.SHINING_RAY) > 0) {
                    singleTargetAttack = Priest.SHINING_RAY;
                    mobAttack = Priest.SHINING_RAY;
                } else {
                    singleTargetAttack = Cleric.HOLY_ARROW;
                    mobAttack = Cleric.HOLY_ARROW;
                }
                break;
            case BISHOP:
                if (c.getPlayer().getSkillLevel(Bishop.ANGEL_RAY) > 0) {
                    singleTargetAttack = Bishop.ANGEL_RAY;
                } else {
                    singleTargetAttack = Priest.SHINING_RAY;
                }
                if (c.getPlayer().getSkillLevel(Bishop.GENESIS) > 0) {
                    singleTargetAttack = Bishop.GENESIS;
                } else {
                    singleTargetAttack = Priest.SHINING_RAY;
                }
                break;
            case BOWMAN:
                if (c.getPlayer().getSkillLevel(Archer.DOUBLE_SHOT) > 0) {
                    singleTargetAttack = Archer.DOUBLE_SHOT;
                    mobAttack = Archer.DOUBLE_SHOT;
                } else if (c.getPlayer().getSkillLevel(Archer.ARROW_BLOW) > 0) {
                    singleTargetAttack = Archer.ARROW_BLOW;
                    mobAttack = Archer.ARROW_BLOW;
                }
                break;
            case HUNTER:
                singleTargetAttack = Archer.DOUBLE_SHOT;
                if (c.getPlayer().getSkillLevel(Hunter.ARROW_BOMB) > 0) {
                    mobAttack = Hunter.ARROW_BOMB;
                } else {
                    mobAttack = Archer.DOUBLE_SHOT;
                }
                break;
            case RANGER:
                if (c.getPlayer().getSkillLevel(Ranger.STRAFE) > 0) {
                    singleTargetAttack = Ranger.STRAFE;
                } else {
                    singleTargetAttack = Archer.DOUBLE_SHOT;
                }
                if (c.getPlayer().getSkillLevel(Ranger.ARROW_RAIN) > 0) {
                    mobAttack = Ranger.ARROW_RAIN;
                } else {
                    mobAttack = Hunter.ARROW_BOMB;
                }
                break;
            case BOWMASTER:
                if (c.getPlayer().getSkillLevel(Bowmaster.HURRICANE) > 0) {
                    singleTargetAttack = Bowmaster.HURRICANE;
                } else {
                    singleTargetAttack = Ranger.STRAFE;
                }
                mobAttack = Ranger.ARROW_RAIN;
                break;
            case CROSSBOWMAN:
                singleTargetAttack = Archer.DOUBLE_SHOT;
                if (c.getPlayer().getSkillLevel(Crossbowman.IRON_ARROW) > 0) {
                    mobAttack = Crossbowman.IRON_ARROW;
                } else {
                    mobAttack = Archer.DOUBLE_SHOT;
                }
                break;
            case SNIPER:
                if (c.getPlayer().getSkillLevel(Sniper.STRAFE) > 0) {
                    singleTargetAttack = Sniper.STRAFE;
                } else {
                    singleTargetAttack = Archer.DOUBLE_SHOT;
                }
                if (c.getPlayer().getSkillLevel(Sniper.ARROW_ERUPTION) > 0) {
                    mobAttack = Sniper.ARROW_ERUPTION;
                } else {
                    mobAttack = Crossbowman.IRON_ARROW;
                }
                break;
            case MARKSMAN:
                singleTargetAttack = Sniper.STRAFE;
                if (c.getPlayer().getSkillLevel(Marksman.PIERCING_ARROW) > 0) {
                    mobAttack = Marksman.PIERCING_ARROW;
                } else {
                    mobAttack = Sniper.ARROW_ERUPTION;
                }
                break;
            case THIEF:
                if (c.getPlayer().getSkillLevel(Rogue.LUCKY_SEVEN) > 0) {
                    singleTargetAttack = Rogue.LUCKY_SEVEN;
                    mobAttack = Rogue.LUCKY_SEVEN;
                }
                break;
            case ASSASSIN:
                singleTargetAttack = Rogue.LUCKY_SEVEN;
                mobAttack = Rogue.LUCKY_SEVEN;
                break;
            case HERMIT:
                singleTargetAttack = Rogue.LUCKY_SEVEN;
                if (c.getPlayer().getSkillLevel(Hermit.AVENGER) > 0) {
                    mobAttack = Hermit.AVENGER;
                } else {
                    mobAttack = Rogue.LUCKY_SEVEN;
                }
                break;
            case NIGHTLORD:
                if (c.getPlayer().getSkillLevel(NightLord.TRIPLE_THROW) > 0) {
                    singleTargetAttack = NightLord.TRIPLE_THROW;
                } else {
                    singleTargetAttack = Rogue.LUCKY_SEVEN;
                }
                mobAttack = Hermit.AVENGER;
                break;
            case BANDIT:
                if (c.getPlayer().getSkillLevel(Bandit.SAVAGE_BLOW) > 0) {
                    singleTargetAttack = Bandit.SAVAGE_BLOW;
                    mobAttack = Bandit.SAVAGE_BLOW;
                } else {
                    singleTargetAttack = Rogue.DOUBLE_STAB;
                    mobAttack = Rogue.DOUBLE_STAB;
                }
                break;
            case CHIEFBANDIT:
                singleTargetAttack = Bandit.SAVAGE_BLOW;
                if (c.getPlayer().getSkillLevel(ChiefBandit.BAND_OF_THIEVES) > 0) {
                    mobAttack = ChiefBandit.BAND_OF_THIEVES;
                } else {
                    mobAttack = Bandit.SAVAGE_BLOW;
                }
                break;
            case SHADOWER:
                if (c.getPlayer().getSkillLevel(Shadower.BOOMERANG_STEP) > 0) {
                    singleTargetAttack = Shadower.BOOMERANG_STEP;
                } else {
                    singleTargetAttack = Bandit.SAVAGE_BLOW;
                }
                mobAttack = ChiefBandit.BAND_OF_THIEVES;
                break;
            case PIRATE:
                if (c.getPlayer().getSkillLevel(Pirate.SOMERSAULT_KICK) > 0) {
                    mobAttack = Pirate.SOMERSAULT_KICK;
                }
                if (c.getPlayer().getWeaponType().equals(WeaponType.GUN)) {
                    if (c.getPlayer().getSkillLevel(Pirate.DOUBLE_SHOT) > 0) {
                        singleTargetAttack = Pirate.DOUBLE_SHOT;
                    }
                } else {
                    if (c.getPlayer().getSkillLevel(Pirate.FLASH_FIST) > 0) {
                        singleTargetAttack = Pirate.FLASH_FIST;
                    }
                }
                break;
            case BRAWLER:
                if (c.getPlayer().getSkillLevel(Brawler.DOUBLE_UPPERCUT) > 0) {
                    singleTargetAttack = Brawler.DOUBLE_UPPERCUT;
                } else {
                    singleTargetAttack = Pirate.FLASH_FIST;
                }
                mobAttack = Pirate.SOMERSAULT_KICK;
                break;
            case MARAUDER:
                singleTargetAttack = Brawler.DOUBLE_UPPERCUT;
                mobAttack = Pirate.SOMERSAULT_KICK;
                break;
            case BUCCANEER:
                if (c.getPlayer().getSkillLevel(Buccaneer.BARRAGE) > 0) {
                    singleTargetAttack = Buccaneer.BARRAGE;
                } else {
                    singleTargetAttack = Brawler.DOUBLE_UPPERCUT;
                }
                if (c.getPlayer().getSkillLevel(Buccaneer.DRAGON_STRIKE) > 0) {
                    singleTargetAttack = Buccaneer.DRAGON_STRIKE;
                } else {
                    mobAttack = Pirate.SOMERSAULT_KICK;
                }
                break;
            case GUNSLINGER:
                if (c.getPlayer().getSkillLevel(Gunslinger.INVISIBLE_SHOT) > 0) {
                    singleTargetAttack = Gunslinger.INVISIBLE_SHOT;
                    mobAttack = Gunslinger.INVISIBLE_SHOT;
                } else {
                    singleTargetAttack = Pirate.DOUBLE_SHOT;
                    mobAttack = Pirate.DOUBLE_SHOT;
                }
                break;
            case OUTLAW:
                if (c.getPlayer().getSkillLevel(Outlaw.BURST_FIRE) > 0) {
                    singleTargetAttack = Pirate.DOUBLE_SHOT;
                } else {
                    singleTargetAttack = Gunslinger.INVISIBLE_SHOT;
                }
                mobAttack = Gunslinger.INVISIBLE_SHOT;
                break;
            case CORSAIR:
                if (c.getPlayer().getSkillLevel(Corsair.RAPID_FIRE) > 0) {
                    singleTargetAttack = Corsair.RAPID_FIRE;
                } else {
                    singleTargetAttack = Pirate.DOUBLE_SHOT;
                }
                mobAttack = Gunslinger.INVISIBLE_SHOT;
                break;
        }
    }

    private void putBuffSkills() {
        buffSkills = new ArrayList<>();
        switch (c.getPlayer().getJob()) {
            case WARRIOR:
                if (c.getPlayer().getSkillLevel(Warrior.IRON_BODY) > 0) {
                    buffSkills.add(Warrior.IRON_BODY);
                }
                break;
            case HERO:
                if (c.getPlayer().getSkillLevel(Hero.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Hero.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(Hero.STANCE) > 2) {
                    buffSkills.add(Hero.STANCE);
                }
                /*if (c.getPlayer().getSkillLevel(Hero.ENRAGE) > 3) {
                    buffSkills.add(Hero.ENRAGE);
                }*/ // todo: consumes orbs
            case CRUSADER:
                if (c.getPlayer().getSkillLevel(Crusader.COMBO) > 0) {
                    buffSkills.add(Crusader.COMBO);
                }
            case FIGHTER:
                if (c.getPlayer().getSkillLevel(Fighter.RAGE) > 0) {
                    buffSkills.add(Fighter.RAGE);
                }
                if (c.getPlayer().getSkillLevel(Fighter.POWER_GUARD) > 9) { // don't use if it will last less than 30 seconds
                    buffSkills.add(Fighter.POWER_GUARD);
                }
                if (c.getPlayer().getSkillLevel(Fighter.SWORD_BOOSTER) > 2) {
                    buffSkills.add(Fighter.SWORD_BOOSTER);
                }
                break;
            case PALADIN:
                if (c.getPlayer().getSkillLevel(Paladin.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Paladin.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(Paladin.STANCE) > 2) {
                    buffSkills.add(Paladin.STANCE);
                }
                if (c.getPlayer().getSkillLevel(Paladin.BW_HOLY_CHARGE) > 1) {
                    buffSkills.add(Paladin.BW_HOLY_CHARGE);
                }
            case WHITEKNIGHT:
                if (c.getPlayer().getSkillLevel(Paladin.BW_HOLY_CHARGE) < 2 && c.getPlayer().getSkillLevel(WhiteKnight.BW_FIRE_CHARGE) > 3) {
                    buffSkills.add(WhiteKnight.BW_FIRE_CHARGE);
                }
            case PAGE:
                if (c.getPlayer().getSkillLevel(Page.POWER_GUARD) > 9) {
                    buffSkills.add(Page.POWER_GUARD);
                }
                if (c.getPlayer().getSkillLevel(Page.BW_BOOSTER) > 2) {
                    buffSkills.add(Page.BW_BOOSTER);
                }
            case DARKKNIGHT:
                if (c.getPlayer().getSkillLevel(DarkKnight.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(DarkKnight.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(DarkKnight.STANCE) > 0) {
                    buffSkills.add(DarkKnight.STANCE);
                }
                if (c.getPlayer().getSkillLevel(DarkKnight.BEHOLDER) > 0) {
                    buffSkills.add(DarkKnight.BEHOLDER);
                }
            case DRAGONKNIGHT:
                /*if (c.getPlayer().getSkillLevel(DragonKnight.DRAGON_BLOOD) > 3) {
                    buffSkills.add(DragonKnight.DRAGON_BLOOD);
                }*/ // todo: this drains hp
            case SPEARMAN:
                if (c.getPlayer().getSkillLevel(Spearman.SPEAR_BOOSTER) > 2) {
                    buffSkills.add(Spearman.SPEAR_BOOSTER);
                }
                if (c.getPlayer().getSkillLevel(Spearman.HYPER_BODY) > 2) {
                    buffSkills.add(Spearman.HYPER_BODY);
                }
                break;
            case MAGICIAN:
                if (c.getPlayer().getSkillLevel(Magician.MAGIC_GUARD) > 0) {
                    buffSkills.add(Magician.MAGIC_GUARD);
                }
                if (c.getPlayer().getSkillLevel(Magician.MAGIC_ARMOR) > 0) {
                    buffSkills.add(Magician.MAGIC_ARMOR);
                }
                break;
            case FP_ARCHMAGE:
                if (c.getPlayer().getSkillLevel(FPArchMage.MAPLE_WARRIOR) > 2) {
                    buffSkills.add(FPArchMage.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(FPArchMage.MANA_REFLECTION) > 2) {
                    buffSkills.add(FPArchMage.MANA_REFLECTION);
                }
            case FP_MAGE:
                if (c.getPlayer().getSkillLevel(FPMage.SPELL_BOOSTER) > 2) {
                    buffSkills.add(FPMage.SPELL_BOOSTER);
                }
            case FP_WIZARD:
                if (c.getPlayer().getSkillLevel(FPWizard.MEDITATION) > 2) {
                    buffSkills.add(FPWizard.MEDITATION);
                }
                buffSkills.add(Magician.MAGIC_GUARD);
                break;
            case IL_ARCHMAGE:
                if (c.getPlayer().getSkillLevel(ILArchMage.MAPLE_WARRIOR) > 2) {
                    buffSkills.add(ILArchMage.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(ILArchMage.MANA_REFLECTION) > 2) {
                    buffSkills.add(ILArchMage.MANA_REFLECTION);
                }
            case IL_MAGE:
                if (c.getPlayer().getSkillLevel(ILMage.SPELL_BOOSTER) > 2) {
                    buffSkills.add(ILMage.SPELL_BOOSTER);
                }
            case IL_WIZARD:
                if (c.getPlayer().getSkillLevel(ILWizard.MEDITATION) > 2) {
                    buffSkills.add(ILWizard.MEDITATION);
                }
                buffSkills.add(Magician.MAGIC_GUARD);
                break;
            case BISHOP:
                if (c.getPlayer().getSkillLevel(Bishop.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Bishop.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(Bishop.MANA_REFLECTION) > 0) {
                    buffSkills.add(Bishop.MANA_REFLECTION);
                }
            case PRIEST:
                if (c.getPlayer().getSkillLevel(Priest.HOLY_SYMBOL) > 0) {
                    buffSkills.add(Priest.HOLY_SYMBOL);
                }
            case CLERIC:
                if (c.getPlayer().getSkillLevel(Cleric.BLESS) > 2) {
                    buffSkills.add(Cleric.BLESS);
                }
                if (c.getPlayer().getSkillLevel(Cleric.INVINCIBLE) > 1) {
                    buffSkills.add(Cleric.INVINCIBLE);
                }
                break;
            case BOWMAN:
                if (c.getPlayer().getSkillLevel(Archer.FOCUS) > 0) {
                    buffSkills.add(Archer.FOCUS);
                }
                break;
            case BOWMASTER:
                if (c.getPlayer().getSkillLevel(Bowmaster.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Bowmaster.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(Bowmaster.SHARP_EYES) > 2) {
                    buffSkills.add(Bowmaster.SHARP_EYES);
                }
                if (c.getPlayer().getSkillLevel(Bowmaster.CONCENTRATE) > 0) {
                    buffSkills.add(Bowmaster.CONCENTRATE);
                }
            case RANGER:
            case HUNTER:
                if (c.getPlayer().getSkillLevel(Hunter.BOW_BOOSTER) > 2) {
                    buffSkills.add(Hunter.BOW_BOOSTER);
                }
                if (c.getPlayer().getSkillLevel(Hunter.SOUL_ARROW) > 0) {
                    buffSkills.add(Hunter.SOUL_ARROW);
                }
                break;
            case MARKSMAN:
                if (c.getPlayer().getSkillLevel(Marksman.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Marksman.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(Marksman.SHARP_EYES) > 2) {
                    buffSkills.add(Marksman.SHARP_EYES);
                }
            case SNIPER:
            case CROSSBOWMAN:
                if (c.getPlayer().getSkillLevel(Crossbowman.CROSSBOW_BOOSTER) > 2) {
                    buffSkills.add(Crossbowman.CROSSBOW_BOOSTER);
                }
                if (c.getPlayer().getSkillLevel(Crossbowman.SOUL_ARROW) > 0) {
                    buffSkills.add(Crossbowman.SOUL_ARROW);
                }
                break;
            case NIGHTLORD:
                if (c.getPlayer().getSkillLevel(NightLord.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(NightLord.MAPLE_WARRIOR);
                }
            case HERMIT:
                if (c.getPlayer().getSkillLevel(Hermit.MESO_UP) > 1) {
                    buffSkills.add(Hermit.MESO_UP);
                }
                if (c.getPlayer().getSkillLevel(Hermit.SHADOW_PARTNER) > 0) {
                    buffSkills.add(Hermit.SHADOW_PARTNER);
                }
            case ASSASSIN:
                if (c.getPlayer().getSkillLevel(Assassin.HASTE) > 2) {
                    buffSkills.add(Assassin.HASTE);
                }
                if (c.getPlayer().getSkillLevel(Assassin.CLAW_BOOSTER) > 2) {
                    buffSkills.add(Assassin.CLAW_BOOSTER);
                }
                break;
            case SHADOWER:
                if (c.getPlayer().getSkillLevel(Shadower.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Shadower.MAPLE_WARRIOR);
                }
            case CHIEFBANDIT:
                if (c.getPlayer().getSkillLevel(ChiefBandit.MESO_GUARD) > 2) {
                    buffSkills.add(ChiefBandit.MESO_GUARD);
                }
            case BANDIT:
                if (c.getPlayer().getSkillLevel(Bandit.HASTE) > 2) {
                    buffSkills.add(Bandit.HASTE);
                }
                if (c.getPlayer().getSkillLevel(Bandit.DAGGER_BOOSTER) > 2) {
                    buffSkills.add(Bandit.DAGGER_BOOSTER);
                }
                break;
            case BUCCANEER:
                if (c.getPlayer().getSkillLevel(Buccaneer.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Buccaneer.MAPLE_WARRIOR);
                }
                if (c.getPlayer().getSkillLevel(Buccaneer.SPEED_INFUSION) > 0) {
                    buffSkills.add(Buccaneer.SPEED_INFUSION);
                }
            case MARAUDER:
            case BRAWLER:
                if (c.getPlayer().getSkillLevel(Brawler.KNUCKLER_BOOSTER) > 2) {
                    buffSkills.add(Brawler.KNUCKLER_BOOSTER);
                }
                break;
            case CORSAIR:
                if (c.getPlayer().getSkillLevel(Corsair.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Corsair.MAPLE_WARRIOR);
                }
            case OUTLAW:
            case GUNSLINGER:
                if (c.getPlayer().getSkillLevel(Gunslinger.GUN_BOOSTER) > 2) {
                    buffSkills.add(Gunslinger.GUN_BOOSTER);
                }
                break;
        }
    }

    private void useBuff(int skillId) {
        c.handlePacket(PacketCreator.createUseBuffPacket(skillId, c.getPlayer().getSkillLevel(skillId)), (short) 91);
    }

    private void attack(int time) {
        if (targetMonster.isBoss()) {
            doAttack(time, singleTargetAttack);
        } else {
            doAttack(time, mobAttack);
        }
    }

    private void doAttack(int time, int skillId) { // todo: combo orbs, arrow bomb, shadow partner, paladin charges, bucc stuff, are star att bonuses being use?
        if (skillId == -1) {
            doRegularAttack();
            delay = 500 - time; // todo: accurate delay
            return;
        }
        List<Monster> targets = new ArrayList<>();
        targets.add(targetMonster);
        StatEffect effect = SkillFactory.getSkill(skillId).getEffect(c.getPlayer().getSkillLevel(skillId));
        if (effect.getMobCount() > 1) {
            boolean added;
            for (int i = 0; i < effect.getMobCount() - 1; i++) {
                added = false;
                for (Monster m : c.getPlayer().getMap().getAllMonsters()) {
                    if (!targets.contains(m) && c.getPlayer().getPosition().distance(m.getPosition()) < 100) { // todo: accurate range
                        targets.add(m);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    break;
                }
            }
        }
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = skillId;
        attack.skilllevel = c.getPlayer().getSkillLevel(skillId);
        attack.numAttacked = targets.size();
        attack.numDamage = Math.max(effect.getAttackCount(), effect.getBulletCount());
        attack.numAttackedAndDamage = attack.numAttacked * 16 + attack.numDamage;
        attack.allDamage = new HashMap<>();
        attack.speed = c.getPlayer().getWeaponSpeed();
        attack.display = 0; // todo: if using any attacks that use diplay update this
        attack.position = c.getPlayer().getPosition();
        attack.stance = (!facingLeft || skillId == Bowmaster.HURRICANE || skillId == Corsair.RAPID_FIRE) ? 0 : -128;
        attack.direction = getDirection(skillId);
        attack.rangedirection = ((skillId == Bowmaster.HURRICANE || skillId == Corsair.RAPID_FIRE) && facingLeft) ? -128 : 0; // todo: figure out what this does, seems to increment with each skill usage
        List<Integer> damageNumbers;
        if (effect.getMatk() > 0) {
            attack.magic = true;
            for (Monster m : targets) {
                damageNumbers = new ArrayList<>();
                for (int i = 0; i < attack.numDamage; i++) {
                    damageNumbers.add(calcMagicDamage(skillId, m));
                }
                attack.allDamage.put(m.getObjectId(), damageNumbers);
            }
            c.handlePacket(PacketCreator.createMagicAttackPacket(attack), (short) 46);
        } else if (isRangedJob()) {
            rechargeProjectiles();
            attack.ranged = true;
            for (Monster m : targets) {
                damageNumbers = new ArrayList<>();
                for (int i = 0; i < attack.numDamage; i++) {
                    damageNumbers.add(calcRangedDamage(skillId, m));
                }
                attack.allDamage.put(m.getObjectId(), damageNumbers);
            }
            c.handlePacket(PacketCreator.createRangedAttackPacket(attack), (short) 45);
        } else {
            for (Monster m : targets) {
                damageNumbers = new ArrayList<>();
                for (int i = 0; i < attack.numDamage; i++) {
                    damageNumbers.add(calcCloseRangeDamage(skillId, m));
                }
                attack.allDamage.put(m.getObjectId(), damageNumbers);
            }
            c.handlePacket(PacketCreator.createCloseRangeAttackPacket(attack), (short) 44);
        }
        delay = 500 - time; // todo: accurate delay
    }

    private int calcMagicDamage(int skillId, Monster target) {
        int leveldelta = Math.max(0, target.getLevel() - c.getPlayer().getLevel());
        if (Randomizer.nextDouble() >= calculateMagicHitchance(leveldelta,
                c.getPlayer().getTotalInt(), c.getPlayer().getTotalLuk(), target.getStats().getEva())) {
            return 0;
        }
        int minDamage = c.getPlayer().calculateMinBaseMagicAttackDamage(skillId),
                maxDamage = c.getPlayer().calculateMaxBaseMagicAttackDamage(skillId);
        int monsterMagicDefense = target.getStats().getMDDamage();
        maxDamage = (int) (maxDamage - monsterMagicDefense * 0.5 * (1 + 0.01 * leveldelta));
        minDamage = (int) (minDamage - monsterMagicDefense * 0.6 * (1 + 0.01 * leveldelta));
        if (c.getPlayer().getJob() == Job.IL_ARCHMAGE || c.getPlayer().getJob() == Job.IL_MAGE) {
            int skillLvl = c.getPlayer().getSkillLevel(ILMage.ELEMENT_AMPLIFICATION);
            if (skillLvl > 0) {
                maxDamage = (int) (maxDamage * SkillFactory.getSkill(ILMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
                minDamage = (int) (minDamage * SkillFactory.getSkill(ILMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
            }
        } else if (c.getPlayer().getJob() == Job.FP_ARCHMAGE || c.getPlayer().getJob() == Job.FP_MAGE) {
            int skillLvl = c.getPlayer().getSkillLevel(FPMage.ELEMENT_AMPLIFICATION);
            if (skillLvl > 0) {
                maxDamage = (int) (maxDamage * SkillFactory.getSkill(FPMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
                minDamage = (int) (minDamage * SkillFactory.getSkill(FPMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
            }
        }
        return Math.max((Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage), 1);
    }

    private int calcRangedDamage(int skillId, Monster target) {
        int leveldelta = Math.max(0, target.getLevel() - c.getPlayer().getLevel());
        if (Randomizer.nextDouble() >= calculateHitchance(leveldelta,
                c.getPlayer().getAccuracy(), target.getStats().getEva())) {
            return 0;
        }
        int minDamage, maxDamage;
        if (skillId == Rogue.LUCKY_SEVEN || skillId == NightLord.TRIPLE_THROW) {
            maxDamage = (int) ((c.getPlayer().getTotalLuk() * 5) * Math.ceil(c.getPlayer().getTotalWatk() / 100.0));
            minDamage = (int) ((c.getPlayer().getTotalLuk() * 2.5) * Math.ceil(c.getPlayer().getTotalWatk() / 100.0));
        } else {
            minDamage = c.getPlayer().calculateMinBaseDamage(c.getPlayer().getTotalWatk());
            maxDamage = c.getPlayer().calculateMaxBaseDamage(c.getPlayer().getTotalWatk());
        }
        double multiplier = SkillFactory.getSkill(skillId).getEffect(c.getPlayer().getSkillLevel(skillId)).getDamage() / 100.0;
        int monsterPhysicalDefense = target.getStats().getPDDamage();
        if (Randomizer.nextDouble() < c.getPlayer().getCritRate()) {
            multiplier += c.getPlayer().getCritBonus();
        }
        minDamage = (int) (minDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.6);
        maxDamage = (int) (maxDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.5);
        return Math.max((int) ((Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage) * multiplier), 1);
    }

    private int calcCloseRangeDamage(int skillId, Monster target) {
        int leveldelta = Math.max(0, target.getLevel() - c.getPlayer().getLevel());
        if (Randomizer.nextDouble() >= calculateHitchance(leveldelta,
                c.getPlayer().getAccuracy(), target.getStats().getEva())) {
            return 0;
        }
        int minDamage = c.getPlayer().calculateMinBaseDamage(c.getPlayer().getTotalWatk()),
                maxDamage = c.getPlayer().calculateMaxBaseDamage(c.getPlayer().getTotalWatk());
        int monsterPhysicalDefense = target.getStats().getPDDamage();
        double multiplier = SkillFactory.getSkill(skillId).getEffect(c.getPlayer().getSkillLevel(skillId)).getDamage() / 100.0;
        if (Randomizer.nextDouble() < c.getPlayer().getCritRate()) {
            multiplier += c.getPlayer().getCritBonus();
        }
        minDamage = (int) (minDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.6);
        maxDamage = (int) (maxDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.5);
        return Math.max((int) ((Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage) * multiplier), 1);
    }

    private void doRegularAttack() {
        int monsterAvoid = targetMonster.getStats().getEva();
        float playerAccuracy = c.getPlayer().getAccuracy();
        int leveldelta = Math.max(0, targetMonster.getLevel() - c.getPlayer().getLevel());
        int damage;
        if (Randomizer.nextDouble() < calculateHitchance(leveldelta, playerAccuracy, monsterAvoid)) {
            damage = calcRegularAttackDamage(leveldelta);
            if (Randomizer.nextDouble() < c.getPlayer().getCritRate()) {
                damage = (int) (damage * c.getPlayer().getCritBonus());
            }
        } else {
            damage = 0;
        }
        c.handlePacket(PacketCreator.createRegularAttackPacket(targetMonster.getObjectId(), damage, facingLeft), (short) 44);
    }

    private int calcRegularAttackDamage(int leveldelta) {
        int maxDamage = c.getPlayer().calculateMaxBaseDamage(c.getPlayer().getTotalWatk());
        int minDamage = c.getPlayer().calculateMinBaseDamage(c.getPlayer().getTotalWatk());
        int monsterPhysicalDefense = targetMonster.getStats().getPDDamage();
        minDamage = Math.max(1, (int) (minDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.6));
        maxDamage = Math.max(1, (int) (maxDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.5));
        return Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage;
    }

    private static float calculateHitchance(int leveldelta, float playerAccuracy, int avoid) {
        float hitchance = playerAccuracy / (((1.84f + 0.07f * leveldelta) * avoid) + 1.0f);
        if (hitchance < 0.01f) {
            hitchance = 0.01f;
        }
        return hitchance;
    }

    private static float calculateMagicHitchance(int leveldelta, int player_int, int playerluk, int avoid) {
        float x =  (player_int / 10 + playerluk / 10) / (avoid + 1f) * (1 + 0.0415f * leveldelta);
        float hitchance = -2.5795f * x * x + 5.2343f * x - 1.6749f;
        if (hitchance < 0.01f) {
            hitchance = 0.01f;
        }
        return hitchance;
    }

    private void chooseMode() {
        currentModeStartTime = System.currentTimeMillis();
        if (c.getPlayer().getJob().equals(Job.BEGINNER)) {
            currentMode = Mode.GRINDING;
            pickMap();
            return;
        }
        // todo: pick mode randomly, maybe have a pq manager task that creates a party if enough bots are waiting for a pq
        currentMode = Mode.GRINDING;
        pickMap();
    }

    private void pickMap() {
        if (c.getPlayer().getJob().equals(Job.BEGINNER)) {
            changeMap(c.getChannelServer().getMapFactory().getMap(104000100 + Randomizer.nextInt(3) * 100));
        } else {
            changeMap(c.getChannelServer().getMapFactory().getMap(104000100 + Randomizer.nextInt(3) * 100)); // todo: pick map based on level
        }
    }

    private void changeMap(MapleMap target) {
        changeMap(target, target.getRandomPlayerSpawnpoint());
    }

    private void changeMap(MapleMap target, Portal targetPortal) {
        c.getPlayer().changeMap(target, targetPortal);
        Foothold foothold = c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition());
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) c.getPlayer().getPosition().x, (short) foothold.getY1(), (byte) 4, (short) 100), (short) 41);
        hasTargetItem = false;
        hasTargetMonster = false;
    }

    private boolean grind(int time) {
        boolean didAction = true;
        while (time > 0 && didAction) {
            didAction = false;
            if (!hasTargetItem || ((MapItem) targetItem).isPickedUp()) { // todo: pick closest item
                if (!c.getPlayer().getMap().getItems().isEmpty()) {
                    for (MapObject it : c.getPlayer().getMap().getItems()) {
                        if (!((MapItem) it).isPickedUp() && ((MapItem) it).canBePickedBy(c.getPlayer())) {
                            if (((MapItem) it).getItem() != null && !InventoryManipulator.checkSpace(c, ((MapItem) it).getItemId(), ((MapItem) it).getItem().getQuantity(), ((MapItem) it).getItem().getOwner())) {
                                continue;
                            }
                            targetItem = it;
                            hasTargetItem = true;
                            break;
                        }
                    }
                }
            }
            if (!hasTargetMonster || !targetMonster.isAlive()) {
                if (!c.getPlayer().getMap().getAllMonsters().isEmpty()) { // pick closest monster to the character
                    double minDistance = c.getPlayer().getPosition().distance(c.getPlayer().getMap().getAllMonsters().getFirst().getPosition()), nextDistance;
                    targetMonster = c.getPlayer().getMap().getAllMonsters().getFirst();
                    for (Monster m : c.getPlayer().getMap().getAllMonsters()) {
                        nextDistance = c.getPlayer().getPosition().distance(m.getPosition());
                        if (nextDistance < minDistance) {
                            minDistance = nextDistance;
                            targetMonster = m;
                        }
                    }
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
                if (c.getPlayer().getPosition().distance(targetMonster.getPosition().x - (isRangedJob() ? 300 : 50), targetMonster.getPosition().y) < (isRangedJob() ? 100 : 50)) { // todo: accurate range
                    attack(time);
                    time = 0;
                    didAction = true;
                } else {
                    time = moveBot((short) (targetMonster.getPosition().x - (isRangedJob() ? 300 : 50)), (short) targetMonster.getPosition().y, time);
                    didAction = true;
                }
            }
        }
        return didAction;
    }

    private void levelup() {
        assignSP(); // do this before doing job advance
        if (c.getPlayer().getJob().equals(Job.BEGINNER)) {
            if (c.getPlayer().getLevel() == 8 && Randomizer.nextInt(5) == 0) { // 1/5 chance to choose magician
                jobAdvance(Job.MAGICIAN);
            } else if (c.getPlayer().getLevel() == 10 && Randomizer.nextInt(500) != 0) {
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
            // todo: fourth job, probably have it give them max skill levels also
        }
        int remainingAP = c.getPlayer().getRemainingAp(), nextAP;
        // todo: even with max secondary stat, they may be unable to equip the highest level items if the item being replaced give the stat, need to address this eventually
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
                // todo: str daggers?
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
        this.level = c.getPlayer().getLevel();
        currentMode = Mode.WAITING; // pick new map? todo: only do this if you get into a new level range for maps probably
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
        assignSP();
    }

    private void assignSP() {
        int remainingSP = c.getPlayer().getRemainingSp(), i = 0;
        int[][] skillOrder = skillOrders.get(c.getPlayer().getJob());
        while (i < skillOrder.length && remainingSP > 0) {
            if (c.getPlayer().getSkillLevel(skillOrder[i][0]) < skillOrder[i][1]) {
                AssignSPProcessor.SPAssignAction(c, skillOrder[i][0]);
            }
            if (remainingSP == c.getPlayer().getRemainingSp()) {
                i++;
            }
            remainingSP = c.getPlayer().getRemainingSp();
        }
    }

    private void rechargeProjectiles() {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (Item torecharge : c.getPlayer().getInventory(InventoryType.USE).list()) {
            if (ItemConstants.isThrowingStar(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                c.getPlayer().forceUpdateItem(torecharge);
            } else if (ItemConstants.isArrow(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                c.getPlayer().forceUpdateItem(torecharge);
            } else if (ItemConstants.isBullet(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                c.getPlayer().forceUpdateItem(torecharge);
            } else if (ItemConstants.isConsumable(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                c.getPlayer().forceUpdateItem(torecharge);
            }
        }
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

    private boolean isRangedJob() {
        int jobId = c.getPlayer().getJob().getId();
        if (jobId == 500 && c.getPlayer().getWeaponType().equals(WeaponType.GUN) && !currentMode.equals(Mode.GRINDING)) {
            return true;
        }
        return jobId / 100 == 3 || (jobId / 100 == 4 && jobId % 100 / 10 != 2) || (jobId / 100 == 5 && jobId % 100 / 10 == 2);
    }

    public Character getFollowing() {
        return following;
    }

    private int getDirection(int skillId) {
        return switch (skillId) {
            case Warrior.POWER_STRIKE, Warrior.SLASH_BLAST, ChiefBandit.BAND_OF_THIEVES -> {
                int[] directions = new int[]{5, 6, 7, 16, 17};
                yield directions[Randomizer.nextInt(directions.length)];
            }
            case Hero.BRANDISH -> 63;
            case Paladin.BLAST -> 71;
            case DragonKnight.SPEAR_CRUSHER, DragonKnight.POLE_ARM_CRUSHER -> 54;
            case DragonKnight.SPEAR_DRAGON_FURY, DragonKnight.POLE_ARM_DRAGON_FURY -> Randomizer.nextInt(2) == 0 ? 13 : 14;
            case Magician.ENERGY_BOLT, Magician.MAGIC_CLAW, ILWizard.COLD_BEAM, ILWizard.THUNDERBOLT  -> Randomizer.nextInt(2) == 0 ? 28 : 29;
            case FPWizard.FIRE_ARROW, Ranger.INFERNO, Ranger.STRAFE, Ranger.ARROW_RAIN, Cleric.HOLY_ARROW, Bishop.ANGEL_RAY -> 22;
            case FPWizard.POISON_BREATH -> 7;
            case FPMage.ELEMENT_COMPOSITION, ILMage.ELEMENT_COMPOSITION -> 48;
            case FPMage.EXPLOSION -> 51;
            case FPMage.POISON_MIST -> 42;
            case FPArchMage.METEOR_SHOWER -> 66;
            case FPArchMage.PARALYZE -> 67;
            case ILMage.THUNDER_SPEAR -> 49;
            case ILMage.ICE_STRIKE, Priest.SHINING_RAY -> 50;
            case ILArchMage.CHAIN_LIGHTNING -> 75;
            case ILArchMage.BLIZZARD -> 68;
            case Bishop.GENESIS -> 69;
            case Archer.ARROW_BLOW, Archer.DOUBLE_SHOT -> c.getPlayer().getWeaponType().equals(WeaponType.BOW) ? (Randomizer.nextInt(2) == 0 ? 22 : 27) : 23;
            case Hunter.ARROW_BOMB -> Randomizer.nextInt(2) == 0 ? 22 : 27;
            case Crossbowman.IRON_ARROW, Sniper.ARROW_ERUPTION, Sniper.BLIZZARD, Sniper.STRAFE, Marksman.SNIPE -> 23;
            case Rogue.LUCKY_SEVEN, NightLord.TRIPLE_THROW -> {
                int[] directions = new int[]{24, 25, 26};
                yield directions[Randomizer.nextInt(directions.length)];
            }
            case Hermit.AVENGER -> 56;
            case Rogue.DOUBLE_STAB -> Randomizer.nextInt(2) == 0 ? 16 : 17;
            case Bandit.SAVAGE_BLOW -> 55;
            case Shadower.BOOMERANG_STEP -> 44;
            case Pirate.SOMERSAULT_KICK -> 78;
            case Pirate.DOUBLE_SHOT -> 87;
            case Pirate.FLASH_FIST -> 79;
            case Brawler.BACK_SPIN_BLOW -> 81;
            case Brawler.DOUBLE_UPPERCUT -> 84;
            case Brawler.CORKSCREW_BLOW -> 83;
            case Marauder.ENERGY_BLAST -> 80;
            case Marauder.SHOCKWAVE -> 99;
            case Buccaneer.DRAGON_STRIKE -> 85;
            case Buccaneer.BARRAGE -> 97;
            case Buccaneer.DEMOLITION -> -98;
            case Buccaneer.SNATCH -> -97;
            case Buccaneer.ENERGY_ORB -> 82;
            case Gunslinger.INVISIBLE_SHOT -> 93;
            case Gunslinger.RECOIL_SHOT -> 92;
            case Outlaw.FLAME_THROWER -> 95;
            case Outlaw.ICE_SPLITTER -> 96;
            case Corsair.AERIAL_STRIKE -> 89;
            case Corsair.BATTLESHIP_CANNON -> 109;
            case Corsair.BATTLESHIP_TORPEDO -> 110;
            default -> 0;
        };
    }

    public static void putSkillOrdersAndDelayTimes() {
        skillOrders.putIfAbsent(Job.BEGINNER, new int[][]{
                {Beginner.THREE_SNAILS, 3},
                {Beginner.RECOVERY, 3}
        });
        skillOrders.putIfAbsent(Job.WARRIOR, new int[][]{
                {Warrior.IMPROVED_HPREC, 5},
                {Warrior.IMPROVED_MAXHP, 10},
                {Warrior.POWER_STRIKE, 1},
                {Warrior.SLASH_BLAST, 20},
                {Warrior.ENDURE, 3},
                {Warrior.IRON_BODY, 1},
                {Warrior.POWER_STRIKE, 20},
                {Warrior.IMPROVED_HPREC, 16},
                {Warrior.IRON_BODY, 20},
                {Warrior.ENDURE, 8}
        });
        skillOrders.putIfAbsent(Job.FIGHTER, new int[][]{
                {Fighter.SWORD_MASTERY, 20},
                {Fighter.RAGE, 20},
                {Fighter.SWORD_BOOSTER, 20},
                {Fighter.FINAL_ATTACK_SWORD, 1},
                {Fighter.AXE_MASTERY, 5},
                {Fighter.AXE_BOOSTER, 1},
                {Fighter.FINAL_ATTACK_AXE, 1},
                {Fighter.POWER_GUARD, 30},
                {Fighter.FINAL_ATTACK_SWORD, 30},
                {Fighter.AXE_MASTERY, 20},
                {Fighter.AXE_BOOSTER, 20},
                {Fighter.FINAL_ATTACK_AXE, 30}
        });
        skillOrders.putIfAbsent(Job.PAGE, new int[][]{
                {Page.BW_MASTERY, 20},
                {Page.BW_BOOSTER, 20},
                {Page.THREATEN, 20},
                {Page.POWER_GUARD, 1},
                {Page.FINAL_ATTACK_BW, 1},
                {Page.SWORD_MASTERY, 5},
                {Page.SWORD_BOOSTER, 1},
                {Page.FINAL_ATTACK_SWORD, 1},
                {Page.POWER_GUARD, 20},
                {Page.FINAL_ATTACK_BW, 20},
                {Page.SWORD_MASTERY, 20},
                {Page.SWORD_BOOSTER, 20},
                {Page.FINAL_ATTACK_SWORD, 20}
        });
        skillOrders.putIfAbsent(Job.SPEARMAN, new int[][]{
                {Spearman.SPEAR_MASTERY, 20},
                {Spearman.SPEAR_BOOSTER, 20},
                {Spearman.IRON_WILL, 3},
                {Spearman.HYPER_BODY, 30},
                {Spearman.FINAL_ATTACK_SPEAR, 1},
                {Spearman.POLEARM_MASTERY, 5},
                {Spearman.POLEARM_BOOSTER, 1},
                {Spearman.FINAL_ATTACK_POLEARM, 1},
                {Spearman.IRON_WILL, 20},
                {Spearman.FINAL_ATTACK_SPEAR, 30},
                {Spearman.POLEARM_MASTERY, 20},
                {Spearman.POLEARM_BOOSTER, 20},
                {Spearman.FINAL_ATTACK_POLEARM, 30}
        });
        skillOrders.putIfAbsent(Job.CRUSADER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.WHITEKNIGHT, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.DRAGONKNIGHT, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.HERO, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.PALADIN, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.DARKKNIGHT, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.MAGICIAN, new int[][]{
                {Magician.ENERGY_BOLT, 1},
                {Magician.IMPROVED_MP_RECOVERY, 5},
                {Magician.IMPROVED_MAX_MP_INCREASE, 10},
                {Magician.MAGIC_CLAW, 20},
                {Magician.MAGIC_GUARD, 20},
                {Magician.MAGIC_ARMOR, 20},
                {Magician.IMPROVED_MP_RECOVERY, 16},
                {Magician.ENERGY_BOLT, 20}
        });
        skillOrders.putIfAbsent(Job.FP_WIZARD, new int[][]{
                {FPWizard.FIRE_ARROW, 30},
                {FPWizard.MP_EATER, 3},
                {FPWizard.MEDITATION, 20},
                {FPWizard.TELEPORT, 5},
                {FPWizard.SLOW, 1},
                {FPWizard.POISON_BREATH, 1},
                {FPWizard.MP_EATER, 20},
                {FPWizard.TELEPORT, 20},
                {FPWizard.SLOW, 20},
                {FPWizard.POISON_BREATH, 30}
        });
        skillOrders.putIfAbsent(Job.IL_WIZARD, new int[][]{
                {ILWizard.THUNDERBOLT, 30},
                {ILWizard.MP_EATER, 3},
                {ILWizard.MEDITATION, 20},
                {ILWizard.TELEPORT, 5},
                {ILWizard.SLOW, 1},
                {ILWizard.COLD_BEAM, 30},
                {ILWizard.MP_EATER, 20},
                {ILWizard.TELEPORT, 20},
                {ILWizard.SLOW, 20}
        });
        skillOrders.putIfAbsent(Job.CLERIC, new int[][]{
                {Cleric.HEAL, 5},
                {Cleric.INVINCIBLE, 5},
                {Cleric.BLESS, 20},
                {Cleric.HEAL, 30},
                {Cleric.MP_EATER, 1},
                {Cleric.TELEPORT, 1},
                {Cleric.HOLY_ARROW, 30},
                {Cleric.INVINCIBLE, 20},
                {Cleric.MP_EATER, 20},
                {Cleric.TELEPORT, 20},
        });
        skillOrders.putIfAbsent(Job.FP_MAGE, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.IL_MAGE, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.PRIEST, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.FP_ARCHMAGE, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.IL_ARCHMAGE, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.BISHOP, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.BOWMAN, new int[][]{
                {Archer.ARROW_BLOW, 1},
                {Archer.DOUBLE_SHOT, 20},
                {Archer.BLESSING_OF_AMAZON, 3},
                {Archer.EYE_OF_AMAZON, 8},
                {Archer.FOCUS, 1},
                {Archer.CRITICAL_SHOT, 20},
                {Archer.BLESSING_OF_AMAZON, 16},
                {Archer.FOCUS, 20},
                {Archer.ARROW_BLOW, 20}
        });
        skillOrders.putIfAbsent(Job.HUNTER, new int[][]{
                {Hunter.BOW_MASTERY, 20},
                {Hunter.ARROW_BOMB, 30},
                {Hunter.BOW_BOOSTER, 20},
                {Hunter.FINAL_ATTACK, 1},
                {Hunter.POWER_KNOCKBACK, 1},
                {Hunter.SOUL_ARROW, 20},
                {Hunter.FINAL_ATTACK, 30},
                {Hunter.POWER_KNOCKBACK, 20}
        });
        skillOrders.putIfAbsent(Job.CROSSBOWMAN, new int[][]{
                {Crossbowman.CROSSBOW_MASTERY, 20},
                {Crossbowman.IRON_ARROW, 30},
                {Crossbowman.CROSSBOW_BOOSTER, 20},
                {Crossbowman.FINAL_ATTACK, 1},
                {Crossbowman.POWER_KNOCKBACK, 1},
                {Crossbowman.SOUL_ARROW, 20},
                {Crossbowman.FINAL_ATTACK, 30},
                {Crossbowman.POWER_KNOCKBACK, 20}
        });
        skillOrders.putIfAbsent(Job.RANGER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.SNIPER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.BOWMASTER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.MARKSMAN, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.THIEF, new int[][]{
                {Rogue.LUCKY_SEVEN, 20}, // all thieves will use claws until lv 30 when some become bandits
                {Rogue.NIMBLE_BODY, 3},
                {Rogue.KEEN_EYES, 8},
                {Rogue.DISORDER, 3},
                {Rogue.DARK_SIGHT, 1},
                {Rogue.DOUBLE_STAB, 20},
                {Rogue.NIMBLE_BODY, 20},
                {Rogue.DARK_SIGHT, 20},
                {Rogue.DISORDER, 20}
        });
        skillOrders.putIfAbsent(Job.ASSASSIN, new int[][]{
                {Assassin.CLAW_MASTERY, 20},
                {Assassin.CRITICAL_THROW, 30},
                {Assassin.CLAW_BOOSTER, 20},
                {Assassin.ENDURE, 3},
                {Assassin.DRAIN, 1},
                {Assassin.HASTE, 20},
                {Assassin.ENDURE, 20},
                {Assassin.DRAIN, 30}
        });
        skillOrders.putIfAbsent(Job.BANDIT, new int[][]{
                {Bandit.DAGGER_MASTERY, 20},
                {Bandit.SAVAGE_BLOW, 30},
                {Bandit.DAGGER_BOOSTER, 20},
                {Bandit.ENDURE, 1},
                {Bandit.HASTE, 5},
                {Bandit.STEAL, 1},
                {Bandit.HASTE, 20},
                {Bandit.ENDURE, 20},
                {Bandit.STEAL, 30}
        });
        skillOrders.putIfAbsent(Job.HERMIT, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.CHIEFBANDIT, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.NIGHTLORD, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.SHADOWER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.PIRATE, new int[][]{
                {Pirate.DOUBLE_SHOT, 1},
                {Pirate.FLASH_FIST, 1},
                {Pirate.SOMERSAULT_KICK, 20},
                {Pirate.BULLET_TIME, 1},
                {Pirate.DASH, 1},
                {Pirate.DOUBLE_SHOT, 20},
                {Pirate.FLASH_FIST, 20},
                {Pirate.BULLET_TIME, 20},
                {Pirate.DASH, 10}
        });
        skillOrders.putIfAbsent(Job.BRAWLER, new int[][]{
                {Brawler.IMPROVE_MAX_HP, 10},
                {Brawler.KNUCKLER_MASTERY, 20},
                {Brawler.KNUCKLER_BOOSTER, 20},
                {Brawler.BACK_SPIN_BLOW, 1},
                {Brawler.CORKSCREW_BLOW, 1},
                {Brawler.MP_RECOVERY, 1},
                {Brawler.OAK_BARREL, 1},
                {Brawler.DOUBLE_UPPERCUT, 20},
                {Brawler.BACK_SPIN_BLOW, 20},
                {Brawler.CORKSCREW_BLOW, 20},
                {Brawler.MP_RECOVERY, 10},
                {Brawler.OAK_BARREL, 10}
        });
        skillOrders.putIfAbsent(Job.GUNSLINGER, new int[][]{
                {Gunslinger.GUN_MASTERY, 20},
                {Gunslinger.INVISIBLE_SHOT, 20},
                {Gunslinger.GUN_BOOSTER, 20},
                {Gunslinger.WINGS, 5},
                {Gunslinger.RECOIL_SHOT, 1},
                {Gunslinger.GRENADE, 1},
                {Gunslinger.BLANK_SHOT, 20},
                {Gunslinger.WINGS, 10},
                {Gunslinger.RECOIL_SHOT, 20},
                {Gunslinger.GRENADE, 20}
        });
        skillOrders.putIfAbsent(Job.MARAUDER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.OUTLAW, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.BUCCANEER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.CORSAIR, new int[][]{}); // todo

        // todo: delay times, decide what to do for different speeds
        // skillDelayTimes.putIfAbsent(-1, );
    }
}
