package client;

import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
import client.processor.stat.AssignSPProcessor;
import constants.inventory.ItemConstants;
import constants.skills.*;
import net.PacketProcessor;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.world.Party;
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

import static client.PQConstants.*;
import static java.util.concurrent.TimeUnit.MINUTES;

public class CharacterBot {
    public enum Mode {
        WAITING, // no mode decided
        SOCIALIZING, // hang out in henesys
        GRINDING, // pick a map and kill monsters
        MANAGE_INVENTORY, // check if any equips are better than what they're using and sell lowest value items to make space
        PQ, // do a pq
        LEAVE_PARTY, // leave party after pq
        BOSSING // fight a boss

    }

    private static final int[] lv1to5Maps = {
            104000100, 104000200, 104000300, // lith maps
            101040000 // perion street corner
    },
    lv6to10Maps = {
            104040000, // hhg1
            100010000, // hill east of henesys
            101010000, // field north of ellinia
            102010000, // west street corner of perion
            102050000, // sunset sky
            100030000 // the forest east of henesys
    },
    lv11to15Maps = {
            104040001, // hhg2
            100050000, // field south of ellinia
            103010000, // kerning city construction site
            102040000 // construction site north of kerning city
    },
    lv16to20Maps = {
            103000101, // bubblings
            104040002, // hhg3
            101030402 //east rocky mountain 3
    },
    lv21to25Maps = {
            104000400, // mano
            105050000, // ant tunnel 1
            101030403, // east rocky mountain 4
            101030101, // excavation site 1
            101030102, // excavation site 2
            101030103 // excavation site 3
    },
    lv26to30Maps = {
            105040000, // swampy land in a deep forest
            101030406 // east rocky mountain 6
    },
    lv31to35Maps = {
            105070100, // cave of evil eye 1
            105040100, // hunting ground in the deep forest 1
            107000000, // the swamp of despair 1
            107000001, // swamp of the jr necki
            101030404, // east rocky mountain 5
            106000101, // the burnt land 1
            220010500 // terrace hall
    },
    lv36to40Maps = {
            105040301, // sleepy dungeon 1
            105040302, // sleepy dungeon 2
            105040303 // sleepy dungeon 3
    },
    lv41to45Maps = {
            106000120 //the burnt land 3
    },
    lv46to50Maps = {
            101030105, //tomb 1
            103000104 // line 1 area 3
    },
    lv51to55Maps = {
            106010102 // the entrance of golem's temple
    },
    lv56to60Maps = {
            106010103, // golem's temple 1
            106010104, // golem's temple 2
            106010105, // golem's temple 3
            106010106, // golem's temple 4
            105040306 // the forest of golem
    },
    lv61to65Maps = {
            107000300 // dangerous croko 1
    },
    lv66to70Maps = {
            610030011 // tornado corridor
    },
    lv71to75Maps = {
            101030108, // tomb 4
            101030109 // tomb 5
    };

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
    private int delay = 0; // delay after using an attack before another can be used
    private boolean doneWithPQTask = false; // used to track progress in some PQ stages

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
        Foothold foothold = getPlayer().getMap().getFootholds().findBelow(getPlayer().getPosition());
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) getPlayer().getPosition().x, (short) foothold.getY1(), (byte) 4, (short) 100), (short) 41);
        level = getPlayer().getLevel();
        decideAttackSkills();
        putBuffSkills();
        chooseMode();
    }

    public void setLoggedOut() {
        loggedOut = true;
    }

    public void logout() {
        loggedOut = true;
        c.disconnect(false, false);
    }

    public boolean isLoggedIn() {
        return !loggedOut;
    }

    public boolean isFollower() {
        return following != null;
    }

    public void setMode(Mode newMode) {
        currentMode = newMode;
    }

    public Mode getMode() {
        return currentMode;
    }

    public void update() {
        if (loggedOut || level >= 69) {
            return;
        }
        if (!currentMode.equals(Mode.PQ) && System.currentTimeMillis() - currentModeStartTime > MINUTES.toMillis(30)) {
            chooseMode();
            return;
        }
        getPlayer().setHp(getPlayer().getMaxHp());
        getPlayer().setMp(getPlayer().getMaxMp()); // todo: accurate potion usage, for now just refresh their hp/mp each update
        if (getPlayer().getLevel() > level || getPlayer().getRemainingSp() > 0) {
            levelup();
            decideAttackSkills();
            putBuffSkills();
            return;
        }
        // todo: use mp pots
        for (int i : buffSkills) {
            if (getPlayer().getExpirationTime(i) - System.currentTimeMillis() < 10000 && getPlayer().getMp() > SkillFactory.getSkill(i).getEffect(getPlayer().getSkillLevel(i)).getMpCon()) {
                useBuff(i);
                return;
            }
        }
        int time = Math.min((int) (System.currentTimeMillis() - previousAction), 1000); // amount of time for actions
        previousAction = System.currentTimeMillis();
        if (delay > time) {
            delay -= time;
            return;
        }
        time -= delay;
        delay = 0;
        switch (currentMode) {
            case MANAGE_INVENTORY -> {
                checkEquips();
                // todo: put items in fm shop
                createInventorySpace();
                chooseMode();
            }
            case LEAVE_PARTY -> leaveParty();
            case WAITING -> chooseMode();
            case GRINDING -> grind(time);
            case PQ -> doPQ(time);
        }
    }

    public void followerUpdate() {
        if (loggedOut) {
            return;
        }
        //System.out.println(currentMode);
        getPlayer().setHp(getPlayer().getMaxHp());
        getPlayer().setMp(getPlayer().getMaxMp()); // todo: accurate potion usage, for now just refresh their hp/mp each update
        if (getPlayer().getLevel() > level || getPlayer().getRemainingSp() > 0) {
            levelup();
            decideAttackSkills();
            putBuffSkills();
            return;
        }
        if (!currentMode.equals(Mode.PQ) && getPlayer().getMapId() != following.getMapId()) {
            changeMap(following.getMap(), following.getMap().findClosestPortal(following.getPosition()));
            return;
        }
        for (int i : buffSkills) {
            if (getPlayer().getExpirationTime(i) - System.currentTimeMillis() < 10000 && getPlayer().getMp() > SkillFactory.getSkill(i).getEffect(getPlayer().getSkillLevel(i)).getMpCon()) {
                useBuff(i);
                return;
            }
        }
        if (!currentMode.equals(Mode.PQ) && isPQMap(getPlayer().getMapId())) {
            currentMode = Mode.PQ;
        }
        //System.out.println("current time: " + System.currentTimeMillis() + ", previous action: " + previousAction + ", delay: " + delay);
        int time = Math.min((int) (System.currentTimeMillis() - previousAction), 1000); // amount of time for actions
        //System.out.println("time: " + time);
        previousAction = System.currentTimeMillis();
        if (delay > time) {
            delay -= time;
            return;
        }
        time -= delay;
        delay = 0;
        switch (currentMode) {
            case MANAGE_INVENTORY -> {
                checkEquips();
                createInventorySpace();
                chooseMode();
            }
            case WAITING, LEAVE_PARTY -> currentMode = Mode.GRINDING; // followers don't need to do these
            case GRINDING -> grind(time);
            case PQ -> doPQ(time);
        }
    }

    private int moveBot(short targetX, short targetY, int time) {
        if (time == 0) {
            return 0;
        }
        int timeRemaining = time;
        short nextX, nextY;
        byte nextState;
        int xDiff = getPlayer().getPosition().x - targetX, yDiff = getPlayer().getPosition().y - targetY;
        //System.out.println("xDiff: " + xDiff + ", yDiff: " + yDiff);
        if (yDiff < 0) {
            nextX = (short) (getPlayer().getPosition().x);
            if (-8 * yDiff < timeRemaining) {
                nextY = targetY;
                nextState = 16;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= -8 * yDiff;
            } else {
                nextY = (short) (getPlayer().getPosition().y + timeRemaining / 8.0);
                nextState = 16;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining = 0;
            }
        } else if (yDiff > 0) {
            nextX = (short) (getPlayer().getPosition().x);
            if (8 * yDiff < timeRemaining) {
                nextY = targetY;
                nextState = 16;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= 8 * yDiff;
            } else {
                nextY = (short) (getPlayer().getPosition().y - timeRemaining / 8.0);
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
                nextY = (short) (getPlayer().getPosition().y);
                nextState = 2;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= -8 * xDiff;
            } else {
                nextX = (short) (getPlayer().getPosition().x + timeRemaining / 8.0);
                nextY = (short) (getPlayer().getPosition().y);
                nextState = 2;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining = 0;
            }
        } else if (xDiff > 0) {
            facingLeft = true;
            if (8 * xDiff < timeRemaining) {
                nextX = targetX;
                nextY = (short) (getPlayer().getPosition().y);
                nextState = 3;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining -= 8 * xDiff;
            } else {
                nextX = (short) (getPlayer().getPosition().x - timeRemaining / 8.0);
                nextY = (short) (getPlayer().getPosition().y);
                nextState = 3;
                c.handlePacket(PacketCreator.createPlayerMovementPacket(nextX, nextY, nextState, (short) timeRemaining), (short) 41);
                timeRemaining = 0;
            }
        }
        if (hasTargetMonster) {
            facingLeft = targetMonster.getPosition().x < getPlayer().getPosition().x;
        }
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) (getPlayer().getPosition().x), (short) (getPlayer().getPosition().y), (byte) (facingLeft ? 5 : 4), (short) 10), (short) 41);
        return timeRemaining - 10;
    }

    private void pickupItem() {
        c.handlePacket(PacketCreator.createPickupItemPacket(targetItem.getObjectId()), (short) 202);
        hasTargetItem = false;
    }

    private void decideAttackSkills() {
        switch (getPlayer().getJob()) {
            case WARRIOR:
            case FIGHTER:
            case PAGE:
            case SPEARMAN:
            case CRUSADER:
                if (getPlayer().getSkillLevel(Warrior.POWER_STRIKE) > 0) {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                }
                if (getPlayer().getSkillLevel(Warrior.SLASH_BLAST) > 0) {
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case HERO:
                if (getPlayer().getSkillLevel(Hero.BRANDISH) > 0) {
                    singleTargetAttack = Hero.BRANDISH;
                    mobAttack = Hero.BRANDISH;
                } else {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case WHITEKNIGHT:
                singleTargetAttack = Warrior.POWER_STRIKE;
                if (getPlayer().getSkillLevel(WhiteKnight.CHARGE_BLOW) > 0) {
                    mobAttack = WhiteKnight.CHARGE_BLOW;
                } else {
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case PALADIN:
                if (getPlayer().getSkillLevel(Paladin.BLAST) > 0) {
                    singleTargetAttack = Paladin.BLAST;
                } else {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                }
                mobAttack = WhiteKnight.CHARGE_BLOW;
                break;
            case DRAGONKNIGHT:
                if (getPlayer().getSkillLevel(DragonKnight.SPEAR_CRUSHER) > 0) {
                    singleTargetAttack = DragonKnight.SPEAR_CRUSHER;
                } else {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                }
                if (getPlayer().getSkillLevel(DragonKnight.SPEAR_DRAGON_FURY) > 0) {
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
                if (getPlayer().getSkillLevel(Magician.MAGIC_CLAW) > 0) {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                    mobAttack = Magician.MAGIC_CLAW;
                } else if (getPlayer().getSkillLevel(Magician.ENERGY_BOLT) > 0) {
                    singleTargetAttack = Magician.ENERGY_BOLT;
                    mobAttack = Magician.ENERGY_BOLT;
                }
                break;
            case FP_WIZARD:
                if (getPlayer().getSkillLevel(FPWizard.FIRE_ARROW) > 0) {
                    singleTargetAttack = FPWizard.FIRE_ARROW;
                    mobAttack = FPWizard.FIRE_ARROW;
                } else {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                    mobAttack = Magician.MAGIC_CLAW;
                }
                break;
            case FP_MAGE:
                if (getPlayer().getSkillLevel(FPMage.ELEMENT_AMPLIFICATION) > 0) {
                    singleTargetAttack = FPMage.ELEMENT_AMPLIFICATION;
                } else {
                    singleTargetAttack = FPWizard.FIRE_ARROW;
                }
                if (getPlayer().getSkillLevel(FPMage.EXPLOSION) > 0) {
                    mobAttack = FPMage.EXPLOSION;
                } else {
                    mobAttack = FPWizard.FIRE_ARROW;
                }
                break;
            case FP_ARCHMAGE:
                if (getPlayer().getSkillLevel(FPArchMage.PARALYZE) > 0) {
                    singleTargetAttack = FPArchMage.PARALYZE;
                } else {
                    singleTargetAttack = FPMage.ELEMENT_AMPLIFICATION;
                }
                if (getPlayer().getSkillLevel(FPArchMage.METEOR_SHOWER) > 0) {
                    mobAttack = FPArchMage.METEOR_SHOWER;
                } else {
                    mobAttack = FPMage.EXPLOSION;
                }
                break;
            case IL_WIZARD:
                if (getPlayer().getSkillLevel(ILWizard.COLD_BEAM) > 0) {
                    singleTargetAttack = ILWizard.COLD_BEAM;
                } else {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                }
                if (getPlayer().getSkillLevel(ILWizard.THUNDERBOLT) > 0) {
                    mobAttack = ILWizard.THUNDERBOLT;
                } else {
                    mobAttack = Magician.MAGIC_CLAW;
                }
                break;
            case IL_MAGE:
                if (getPlayer().getSkillLevel(ILMage.ELEMENT_COMPOSITION) > 0) {
                    singleTargetAttack = ILMage.ELEMENT_COMPOSITION;
                } else {
                    singleTargetAttack = ILWizard.COLD_BEAM;
                }
                if (getPlayer().getSkillLevel(ILMage.ICE_STRIKE) > 0) {
                    mobAttack = ILMage.ICE_STRIKE;
                } else {
                    mobAttack = ILWizard.THUNDERBOLT;
                }
                break;
            case IL_ARCHMAGE:
                if (getPlayer().getSkillLevel(ILArchMage.CHAIN_LIGHTNING) > 0) {
                    singleTargetAttack = ILArchMage.CHAIN_LIGHTNING;
                } else {
                    singleTargetAttack = ILMage.ELEMENT_COMPOSITION;
                }
                if (getPlayer().getSkillLevel(ILArchMage.BLIZZARD) > 0) {
                    mobAttack = ILArchMage.BLIZZARD;
                } else {
                    mobAttack = ILMage.ICE_STRIKE;
                }
                break;
            case CLERIC:
                if (getPlayer().getSkillLevel(Cleric.HOLY_ARROW) > 0) {
                    singleTargetAttack = Cleric.HOLY_ARROW;
                    mobAttack = Cleric.HOLY_ARROW;
                } else {
                    singleTargetAttack = Magician.MAGIC_CLAW;
                    mobAttack = Magician.MAGIC_CLAW;
                }
                break;
            case PRIEST:
                if (getPlayer().getSkillLevel(Priest.SHINING_RAY) > 0) {
                    singleTargetAttack = Priest.SHINING_RAY;
                    mobAttack = Priest.SHINING_RAY;
                } else {
                    singleTargetAttack = Cleric.HOLY_ARROW;
                    mobAttack = Cleric.HOLY_ARROW;
                }
                break;
            case BISHOP:
                if (getPlayer().getSkillLevel(Bishop.ANGEL_RAY) > 0) {
                    singleTargetAttack = Bishop.ANGEL_RAY;
                } else {
                    singleTargetAttack = Priest.SHINING_RAY;
                }
                if (getPlayer().getSkillLevel(Bishop.GENESIS) > 0) {
                    singleTargetAttack = Bishop.GENESIS;
                } else {
                    singleTargetAttack = Priest.SHINING_RAY;
                }
                break;
            case BOWMAN:
                if (getPlayer().getSkillLevel(Archer.DOUBLE_SHOT) > 0) {
                    singleTargetAttack = Archer.DOUBLE_SHOT;
                    mobAttack = Archer.DOUBLE_SHOT;
                } else if (getPlayer().getSkillLevel(Archer.ARROW_BLOW) > 0) {
                    singleTargetAttack = Archer.ARROW_BLOW;
                    mobAttack = Archer.ARROW_BLOW;
                }
                break;
            case HUNTER:
                singleTargetAttack = Archer.DOUBLE_SHOT;
                if (getPlayer().getSkillLevel(Hunter.ARROW_BOMB) > 0) {
                    mobAttack = Hunter.ARROW_BOMB;
                } else {
                    mobAttack = Archer.DOUBLE_SHOT;
                }
                break;
            case RANGER:
                if (getPlayer().getSkillLevel(Ranger.STRAFE) > 0) {
                    singleTargetAttack = Ranger.STRAFE;
                } else {
                    singleTargetAttack = Archer.DOUBLE_SHOT;
                }
                if (getPlayer().getSkillLevel(Ranger.ARROW_RAIN) > 0) {
                    mobAttack = Ranger.ARROW_RAIN;
                } else {
                    mobAttack = Hunter.ARROW_BOMB;
                }
                break;
            case BOWMASTER:
                if (getPlayer().getSkillLevel(Bowmaster.HURRICANE) > 0) {
                    singleTargetAttack = Bowmaster.HURRICANE;
                } else {
                    singleTargetAttack = Ranger.STRAFE;
                }
                mobAttack = Ranger.ARROW_RAIN;
                break;
            case CROSSBOWMAN:
                singleTargetAttack = Archer.DOUBLE_SHOT;
                if (getPlayer().getSkillLevel(Crossbowman.IRON_ARROW) > 0) {
                    mobAttack = Crossbowman.IRON_ARROW;
                } else {
                    mobAttack = Archer.DOUBLE_SHOT;
                }
                break;
            case SNIPER:
                if (getPlayer().getSkillLevel(Sniper.STRAFE) > 0) {
                    singleTargetAttack = Sniper.STRAFE;
                } else {
                    singleTargetAttack = Archer.DOUBLE_SHOT;
                }
                if (getPlayer().getSkillLevel(Sniper.ARROW_ERUPTION) > 0) {
                    mobAttack = Sniper.ARROW_ERUPTION;
                } else {
                    mobAttack = Crossbowman.IRON_ARROW;
                }
                break;
            case MARKSMAN:
                singleTargetAttack = Sniper.STRAFE;
                if (getPlayer().getSkillLevel(Marksman.PIERCING_ARROW) > 0) {
                    mobAttack = Marksman.PIERCING_ARROW;
                } else {
                    mobAttack = Sniper.ARROW_ERUPTION;
                }
                break;
            case THIEF:
                if (getPlayer().getSkillLevel(Rogue.LUCKY_SEVEN) > 0) {
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
                if (getPlayer().getSkillLevel(Hermit.AVENGER) > 0) {
                    mobAttack = Hermit.AVENGER;
                } else {
                    mobAttack = Rogue.LUCKY_SEVEN;
                }
                break;
            case NIGHTLORD:
                if (getPlayer().getSkillLevel(NightLord.TRIPLE_THROW) > 0) {
                    singleTargetAttack = NightLord.TRIPLE_THROW;
                } else {
                    singleTargetAttack = Rogue.LUCKY_SEVEN;
                }
                mobAttack = Hermit.AVENGER;
                break;
            case BANDIT:
                if (getPlayer().getSkillLevel(Bandit.SAVAGE_BLOW) > 0) {
                    singleTargetAttack = Bandit.SAVAGE_BLOW;
                    mobAttack = Bandit.SAVAGE_BLOW;
                } else {
                    singleTargetAttack = Rogue.DOUBLE_STAB;
                    mobAttack = Rogue.DOUBLE_STAB;
                }
                break;
            case CHIEFBANDIT:
                singleTargetAttack = Bandit.SAVAGE_BLOW;
                if (getPlayer().getSkillLevel(ChiefBandit.BAND_OF_THIEVES) > 0) {
                    mobAttack = ChiefBandit.BAND_OF_THIEVES;
                } else {
                    mobAttack = Bandit.SAVAGE_BLOW;
                }
                break;
            case SHADOWER:
                if (getPlayer().getSkillLevel(Shadower.BOOMERANG_STEP) > 0) {
                    singleTargetAttack = Shadower.BOOMERANG_STEP;
                } else {
                    singleTargetAttack = Bandit.SAVAGE_BLOW;
                }
                mobAttack = ChiefBandit.BAND_OF_THIEVES;
                break;
            case PIRATE:
                if (getPlayer().getSkillLevel(Pirate.SOMERSAULT_KICK) > 0) {
                    mobAttack = Pirate.SOMERSAULT_KICK;
                }
                if (getPlayer().getWeaponType().equals(WeaponType.GUN)) {
                    if (getPlayer().getSkillLevel(Pirate.DOUBLE_SHOT) > 0) {
                        singleTargetAttack = Pirate.DOUBLE_SHOT;
                        mobAttack = Pirate.DOUBLE_SHOT;
                    }
                } else {
                    if (getPlayer().getSkillLevel(Pirate.FLASH_FIST) > 0) {
                        singleTargetAttack = Pirate.FLASH_FIST;
                    }
                }
                break;
            case BRAWLER:
                if (getPlayer().getSkillLevel(Brawler.DOUBLE_UPPERCUT) > 0) {
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
                if (getPlayer().getSkillLevel(Buccaneer.BARRAGE) > 0) {
                    singleTargetAttack = Buccaneer.BARRAGE;
                } else {
                    singleTargetAttack = Brawler.DOUBLE_UPPERCUT;
                }
                if (getPlayer().getSkillLevel(Buccaneer.DRAGON_STRIKE) > 0) {
                    singleTargetAttack = Buccaneer.DRAGON_STRIKE;
                } else {
                    mobAttack = Pirate.SOMERSAULT_KICK;
                }
                break;
            case GUNSLINGER:
                if (getPlayer().getSkillLevel(Gunslinger.INVISIBLE_SHOT) > 0) {
                    singleTargetAttack = Gunslinger.INVISIBLE_SHOT;
                    mobAttack = Gunslinger.INVISIBLE_SHOT;
                } else {
                    singleTargetAttack = Pirate.DOUBLE_SHOT;
                    mobAttack = Pirate.DOUBLE_SHOT;
                }
                break;
            case OUTLAW:
                if (getPlayer().getSkillLevel(Outlaw.BURST_FIRE) > 0) {
                    singleTargetAttack = Pirate.DOUBLE_SHOT;
                } else {
                    singleTargetAttack = Gunslinger.INVISIBLE_SHOT;
                }
                mobAttack = Gunslinger.INVISIBLE_SHOT;
                break;
            case CORSAIR:
                if (getPlayer().getSkillLevel(Corsair.RAPID_FIRE) > 0) {
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
        switch (getPlayer().getJob()) {
            case WARRIOR:
                if (getPlayer().getSkillLevel(Warrior.IRON_BODY) > 0) {
                    buffSkills.add(Warrior.IRON_BODY);
                }
                break;
            case HERO:
                if (getPlayer().getSkillLevel(Hero.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Hero.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(Hero.STANCE) > 2) {
                    buffSkills.add(Hero.STANCE);
                }
                /*if (getPlayer().getSkillLevel(Hero.ENRAGE) > 3) {
                    buffSkills.add(Hero.ENRAGE);
                }*/ // todo: consumes orbs
            case CRUSADER:
                if (getPlayer().getSkillLevel(Crusader.COMBO) > 0) {
                    buffSkills.add(Crusader.COMBO);
                }
            case FIGHTER:
                if (getPlayer().getSkillLevel(Fighter.RAGE) > 0) {
                    buffSkills.add(Fighter.RAGE);
                }
                if (getPlayer().getSkillLevel(Fighter.POWER_GUARD) > 9) { // don't use if it will last less than 30 seconds
                    buffSkills.add(Fighter.POWER_GUARD);
                }
                if (getPlayer().getSkillLevel(Fighter.SWORD_BOOSTER) > 2) {
                    buffSkills.add(Fighter.SWORD_BOOSTER);
                }
                break;
            case PALADIN:
                if (getPlayer().getSkillLevel(Paladin.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Paladin.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(Paladin.STANCE) > 2) {
                    buffSkills.add(Paladin.STANCE);
                }
                if (getPlayer().getSkillLevel(Paladin.BW_HOLY_CHARGE) > 1) {
                    buffSkills.add(Paladin.BW_HOLY_CHARGE);
                }
            case WHITEKNIGHT:
                if (getPlayer().getSkillLevel(Paladin.BW_HOLY_CHARGE) < 2 && getPlayer().getSkillLevel(WhiteKnight.BW_FIRE_CHARGE) > 3) {
                    buffSkills.add(WhiteKnight.BW_FIRE_CHARGE);
                }
            case PAGE:
                if (getPlayer().getSkillLevel(Page.POWER_GUARD) > 9) {
                    buffSkills.add(Page.POWER_GUARD);
                }
                if (getPlayer().getSkillLevel(Page.BW_BOOSTER) > 2) {
                    buffSkills.add(Page.BW_BOOSTER);
                }
            case DARKKNIGHT:
                if (getPlayer().getSkillLevel(DarkKnight.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(DarkKnight.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(DarkKnight.STANCE) > 0) {
                    buffSkills.add(DarkKnight.STANCE);
                }
                if (getPlayer().getSkillLevel(DarkKnight.BEHOLDER) > 0) {
                    buffSkills.add(DarkKnight.BEHOLDER);
                }
            case DRAGONKNIGHT:
                /*if (getPlayer().getSkillLevel(DragonKnight.DRAGON_BLOOD) > 3) {
                    buffSkills.add(DragonKnight.DRAGON_BLOOD);
                }*/ // todo: this drains hp
            case SPEARMAN:
                if (getPlayer().getSkillLevel(Spearman.SPEAR_BOOSTER) > 2) {
                    buffSkills.add(Spearman.SPEAR_BOOSTER);
                }
                if (getPlayer().getSkillLevel(Spearman.HYPER_BODY) > 2) {
                    buffSkills.add(Spearman.HYPER_BODY);
                }
                break;
            case MAGICIAN:
                if (getPlayer().getSkillLevel(Magician.MAGIC_GUARD) > 0) {
                    buffSkills.add(Magician.MAGIC_GUARD);
                }
                if (getPlayer().getSkillLevel(Magician.MAGIC_ARMOR) > 0) {
                    buffSkills.add(Magician.MAGIC_ARMOR);
                }
                break;
            case FP_ARCHMAGE:
                if (getPlayer().getSkillLevel(FPArchMage.MAPLE_WARRIOR) > 2) {
                    buffSkills.add(FPArchMage.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(FPArchMage.MANA_REFLECTION) > 2) {
                    buffSkills.add(FPArchMage.MANA_REFLECTION);
                }
            case FP_MAGE:
                if (getPlayer().getSkillLevel(FPMage.SPELL_BOOSTER) > 2) {
                    buffSkills.add(FPMage.SPELL_BOOSTER);
                }
            case FP_WIZARD:
                if (getPlayer().getSkillLevel(FPWizard.MEDITATION) > 2) {
                    buffSkills.add(FPWizard.MEDITATION);
                }
                buffSkills.add(Magician.MAGIC_GUARD);
                break;
            case IL_ARCHMAGE:
                if (getPlayer().getSkillLevel(ILArchMage.MAPLE_WARRIOR) > 2) {
                    buffSkills.add(ILArchMage.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(ILArchMage.MANA_REFLECTION) > 2) {
                    buffSkills.add(ILArchMage.MANA_REFLECTION);
                }
            case IL_MAGE:
                if (getPlayer().getSkillLevel(ILMage.SPELL_BOOSTER) > 2) {
                    buffSkills.add(ILMage.SPELL_BOOSTER);
                }
            case IL_WIZARD:
                if (getPlayer().getSkillLevel(ILWizard.MEDITATION) > 2) {
                    buffSkills.add(ILWizard.MEDITATION);
                }
                buffSkills.add(Magician.MAGIC_GUARD);
                break;
            case BISHOP:
                if (getPlayer().getSkillLevel(Bishop.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Bishop.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(Bishop.MANA_REFLECTION) > 0) {
                    buffSkills.add(Bishop.MANA_REFLECTION);
                }
            case PRIEST:
                if (getPlayer().getSkillLevel(Priest.HOLY_SYMBOL) > 0) {
                    buffSkills.add(Priest.HOLY_SYMBOL);
                }
            case CLERIC:
                if (getPlayer().getSkillLevel(Cleric.BLESS) > 2) {
                    buffSkills.add(Cleric.BLESS);
                }
                if (getPlayer().getSkillLevel(Cleric.INVINCIBLE) > 1) {
                    buffSkills.add(Cleric.INVINCIBLE);
                }
                break;
            case BOWMAN:
                if (getPlayer().getSkillLevel(Archer.FOCUS) > 0) {
                    buffSkills.add(Archer.FOCUS);
                }
                break;
            case BOWMASTER:
                if (getPlayer().getSkillLevel(Bowmaster.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Bowmaster.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(Bowmaster.SHARP_EYES) > 2) {
                    buffSkills.add(Bowmaster.SHARP_EYES);
                }
                if (getPlayer().getSkillLevel(Bowmaster.CONCENTRATE) > 0) {
                    buffSkills.add(Bowmaster.CONCENTRATE);
                }
            case RANGER:
            case HUNTER:
                if (getPlayer().getSkillLevel(Hunter.BOW_BOOSTER) > 2) {
                    buffSkills.add(Hunter.BOW_BOOSTER);
                }
                if (getPlayer().getSkillLevel(Hunter.SOUL_ARROW) > 0) {
                    buffSkills.add(Hunter.SOUL_ARROW);
                }
                break;
            case MARKSMAN:
                if (getPlayer().getSkillLevel(Marksman.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Marksman.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(Marksman.SHARP_EYES) > 2) {
                    buffSkills.add(Marksman.SHARP_EYES);
                }
            case SNIPER:
            case CROSSBOWMAN:
                if (getPlayer().getSkillLevel(Crossbowman.CROSSBOW_BOOSTER) > 2) {
                    buffSkills.add(Crossbowman.CROSSBOW_BOOSTER);
                }
                if (getPlayer().getSkillLevel(Crossbowman.SOUL_ARROW) > 0) {
                    buffSkills.add(Crossbowman.SOUL_ARROW);
                }
                break;
            case NIGHTLORD:
                if (getPlayer().getSkillLevel(NightLord.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(NightLord.MAPLE_WARRIOR);
                }
            case HERMIT:
                if (getPlayer().getSkillLevel(Hermit.MESO_UP) > 1) {
                    buffSkills.add(Hermit.MESO_UP);
                }
                if (getPlayer().getSkillLevel(Hermit.SHADOW_PARTNER) > 0) {
                    buffSkills.add(Hermit.SHADOW_PARTNER);
                }
            case ASSASSIN:
                if (getPlayer().getSkillLevel(Assassin.HASTE) > 2) {
                    buffSkills.add(Assassin.HASTE);
                }
                if (getPlayer().getSkillLevel(Assassin.CLAW_BOOSTER) > 2) {
                    buffSkills.add(Assassin.CLAW_BOOSTER);
                }
                break;
            case SHADOWER:
                if (getPlayer().getSkillLevel(Shadower.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Shadower.MAPLE_WARRIOR);
                }
            case CHIEFBANDIT:
                if (getPlayer().getSkillLevel(ChiefBandit.MESO_GUARD) > 2) {
                    buffSkills.add(ChiefBandit.MESO_GUARD);
                }
            case BANDIT:
                if (getPlayer().getSkillLevel(Bandit.HASTE) > 2) {
                    buffSkills.add(Bandit.HASTE);
                }
                if (getPlayer().getSkillLevel(Bandit.DAGGER_BOOSTER) > 2) {
                    buffSkills.add(Bandit.DAGGER_BOOSTER);
                }
                break;
            case BUCCANEER:
                if (getPlayer().getSkillLevel(Buccaneer.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Buccaneer.MAPLE_WARRIOR);
                }
                if (getPlayer().getSkillLevel(Buccaneer.SPEED_INFUSION) > 0) {
                    buffSkills.add(Buccaneer.SPEED_INFUSION);
                }
            case MARAUDER:
            case BRAWLER:
                if (getPlayer().getSkillLevel(Brawler.KNUCKLER_BOOSTER) > 2) {
                    buffSkills.add(Brawler.KNUCKLER_BOOSTER);
                }
                break;
            case CORSAIR:
                if (getPlayer().getSkillLevel(Corsair.MAPLE_WARRIOR) > 0) {
                    buffSkills.add(Corsair.MAPLE_WARRIOR);
                }
            case OUTLAW:
            case GUNSLINGER:
                if (getPlayer().getSkillLevel(Gunslinger.GUN_BOOSTER) > 2) {
                    buffSkills.add(Gunslinger.GUN_BOOSTER);
                }
                break;
        }
    }

    private void useBuff(int skillId) {
        c.handlePacket(PacketCreator.createUseBuffPacket(skillId, getPlayer().getSkillLevel(skillId)), (short) 91);
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
        StatEffect effect = SkillFactory.getSkill(skillId).getEffect(getPlayer().getSkillLevel(skillId));
        if (effect.getMobCount() > 1) {
            boolean added;
            for (int i = 0; i < effect.getMobCount() - 1; i++) {
                added = false;
                for (Monster m : getPlayer().getMap().getAllMonsters()) {
                    if (!targets.contains(m) && getPlayer().getPosition().distance(m.getPosition()) < 100) { // todo: accurate range
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
        attack.skilllevel = getPlayer().getSkillLevel(skillId);
        attack.numAttacked = targets.size();
        attack.numDamage = Math.max(effect.getAttackCount(), effect.getBulletCount());
        attack.numAttackedAndDamage = attack.numAttacked * 16 + attack.numDamage;
        attack.allDamage = new HashMap<>();
        attack.speed = getPlayer().getWeaponSpeed();
        attack.display = 0; // todo: if using any attacks that use diplay update this
        attack.position = getPlayer().getPosition();
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
        int leveldelta = Math.max(0, target.getLevel() - getPlayer().getLevel());
        if (Randomizer.nextDouble() >= calculateHitchance(leveldelta,
                getPlayer().getMagicAccuracy(), target.getStats().getEva())) {
            return 0;
        }
        int minDamage = getPlayer().calculateMinBaseMagicAttackDamage(skillId),
                maxDamage = getPlayer().calculateMaxBaseMagicAttackDamage(skillId);
        int monsterMagicDefense = target.getStats().getMDDamage();
        maxDamage = (int) (maxDamage - monsterMagicDefense * 0.5 * (1 + 0.01 * leveldelta));
        minDamage = (int) (minDamage - monsterMagicDefense * 0.6 * (1 + 0.01 * leveldelta));
        if (getPlayer().getJob() == Job.IL_ARCHMAGE || getPlayer().getJob() == Job.IL_MAGE) {
            int skillLvl = getPlayer().getSkillLevel(ILMage.ELEMENT_AMPLIFICATION);
            if (skillLvl > 0) {
                maxDamage = (int) (maxDamage * SkillFactory.getSkill(ILMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
                minDamage = (int) (minDamage * SkillFactory.getSkill(ILMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
            }
        } else if (getPlayer().getJob() == Job.FP_ARCHMAGE || getPlayer().getJob() == Job.FP_MAGE) {
            int skillLvl = getPlayer().getSkillLevel(FPMage.ELEMENT_AMPLIFICATION);
            if (skillLvl > 0) {
                maxDamage = (int) (maxDamage * SkillFactory.getSkill(FPMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
                minDamage = (int) (minDamage * SkillFactory.getSkill(FPMage.ELEMENT_AMPLIFICATION).getEffect(skillLvl).getY() / 100.0);
            }
        }
        return Math.max((Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage), 1);
    }

    private int calcRangedDamage(int skillId, Monster target) {
        int leveldelta = Math.max(0, target.getLevel() - getPlayer().getLevel());
        if (Randomizer.nextDouble() >= calculateHitchance(leveldelta,
                getPlayer().getAccuracy(), target.getStats().getEva())) {
            return 0;
        }
        int minDamage, maxDamage;
        if (skillId == Rogue.LUCKY_SEVEN || skillId == NightLord.TRIPLE_THROW) {
            maxDamage = (int) ((getPlayer().getTotalLuk() * 5) * getPlayer().getTotalWatk() / 100.0);
            minDamage = (int) ((getPlayer().getTotalLuk() * 2.5) * getPlayer().getTotalWatk() / 100.0);
        } else {
            minDamage = getPlayer().calculateMinBaseDamage(getPlayer().getTotalWatk());
            maxDamage = getPlayer().calculateMaxBaseDamage(getPlayer().getTotalWatk());
        }
        double multiplier = SkillFactory.getSkill(skillId).getEffect(getPlayer().getSkillLevel(skillId)).getDamage() / 100.0;
        int monsterPhysicalDefense = target.getStats().getPDDamage();
        if (Randomizer.nextDouble() < getPlayer().getCritRate()) {
            multiplier += getPlayer().getCritBonus();
        }
        minDamage = (int) (minDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.6);
        maxDamage = (int) (maxDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.5);
        return Math.max((int) ((Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage) * multiplier), 1);
    }

    private int calcCloseRangeDamage(int skillId, Monster target) {
        int leveldelta = Math.max(0, target.getLevel() - getPlayer().getLevel());
        if (Randomizer.nextDouble() >= calculateHitchance(leveldelta,
                getPlayer().getAccuracy(), target.getStats().getEva())) {
            return 0;
        }
        int minDamage = getPlayer().calculateMinBaseDamage(getPlayer().getTotalWatk()),
                maxDamage = getPlayer().calculateMaxBaseDamage(getPlayer().getTotalWatk());
        int monsterPhysicalDefense = target.getStats().getPDDamage();
        double multiplier = SkillFactory.getSkill(skillId).getEffect(getPlayer().getSkillLevel(skillId)).getDamage() / 100.0;
        if (Randomizer.nextDouble() < getPlayer().getCritRate()) {
            multiplier += getPlayer().getCritBonus();
        }
        minDamage = (int) (minDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.6);
        maxDamage = (int) (maxDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.5);
        return Math.max((int) ((Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage) * multiplier), 1);
    }

    private void doRegularAttack() {
        int monsterAvoid = targetMonster.getStats().getEva();
        float playerAccuracy = getPlayer().getAccuracy();
        int leveldelta = Math.max(0, targetMonster.getLevel() - getPlayer().getLevel());
        int damage;
        if (Randomizer.nextDouble() < calculateHitchance(leveldelta, playerAccuracy, monsterAvoid)) {
            damage = calcRegularAttackDamage(leveldelta);
            if (Randomizer.nextDouble() < getPlayer().getCritRate()) {
                damage = (int) (damage * getPlayer().getCritBonus());
            }
        } else {
            damage = 0;
        }
        int[] directions;
        Equip weapon = (Equip) getPlayer().getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            System.out.println(getPlayer().getName() + " has no weapon");
            currentMode = Mode.MANAGE_INVENTORY;
            return;
        }
        if (ItemConstants.isStaff(weapon.getItemId()) || ItemConstants.isWand(weapon.getItemId())) {
            directions = new int[]{6};
        } else if (ItemConstants.isKnuckle(weapon.getItemId())) {
            directions = new int[]{16, 17};
        } else if (ItemConstants.isSpear(weapon.getItemId()) || ItemConstants.isPolearm(weapon.getItemId())) {
            directions = new int[]{10, 13, 14, 19, 20};
        } else if (ItemConstants.is2hWeapon(weapon.getItemId())) {
            directions = new int[]{9, 10, 11, 16, 17};
        } else {
            directions = new int[]{5, 6, 7, 16, 17};
        }
        c.handlePacket(PacketCreator.createRegularAttackPacket(targetMonster.getObjectId(), damage, directions[Randomizer.nextInt(directions.length)], facingLeft), (short) 44);
    }

    private int calcRegularAttackDamage(int leveldelta) {
        int maxDamage = getPlayer().calculateMaxBaseDamage(getPlayer().getTotalWatk());
        int minDamage = getPlayer().calculateMinBaseDamage(getPlayer().getTotalWatk());
        int monsterPhysicalDefense = targetMonster.getStats().getPDDamage();
        minDamage = Math.max(1, (int) (minDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.6));
        maxDamage = Math.max(1, (int) (maxDamage * (1 - 0.01 * leveldelta) - monsterPhysicalDefense * 0.5));
        return Randomizer.nextInt(maxDamage - minDamage + 1) + minDamage;
    }

    private static float calculateHitchance(int leveldelta, float playerAccuracy, int avoid) {
        float hitchance = (playerAccuracy + 10) / (((1.84f + 0.07f * leveldelta) * avoid) + 1.0f); // note: add 10 to accuracy to emulate accuracy pill usage
        if (hitchance < 0.01f) {
            hitchance = 0.01f;
        }
        return hitchance;
    }

    /*private static float calculateMagicHitchance(int leveldelta, int player_int, int playerluk, int avoid) {
        float x =  (player_int / 10 + playerluk / 10) / (avoid + 1f) * (1 + 0.0415f * leveldelta);
        float hitchance = -2.5795f * x * x + 5.2343f * x - 1.6749f;
        if (hitchance < 0.01f) {
            hitchance = 0.01f;
        }
        return hitchance;
    }*/ // this formula appears to be incorrect, due to the negative first term of the quadratic it often gives a negative hitchance

    private void chooseMode() {
        currentModeStartTime = System.currentTimeMillis();
        if (!currentMode.equals(Mode.MANAGE_INVENTORY)) { // manage inventory between modes
            currentMode = Mode.MANAGE_INVENTORY;
            // todo: go to henesys or fm
            return;
        }
        if (getPlayer().getJob().equals(Job.BEGINNER)) {
            currentMode = Mode.GRINDING;
            pickMap();
            return;
        }
        // todo: pick mode randomly
        currentMode = Mode.GRINDING;
        pickMap();
    }

    public void leaveParty() {
        Party party = getPlayer().getParty();
        if (party != null) {
            List<Character> partymembers = getPlayer().getPartyMembersOnline();

            Party.leaveParty(party, c);
            getPlayer().updatePartySearchAvailability(true);
            getPlayer().partyOperationUpdate(party, partymembers);
        }
        currentMode = Mode.WAITING;
    }

    private void pickMap() {
        if (getPlayer().getLevel() < 6) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv1to5Maps[Randomizer.nextInt(lv1to5Maps.length)]));
        } else if (getPlayer().getLevel() < 11) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv6to10Maps[Randomizer.nextInt(lv6to10Maps.length)]));
        } else if (getPlayer().getLevel() < 16) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv11to15Maps[Randomizer.nextInt(lv11to15Maps.length)]));
        } else if (getPlayer().getLevel() < 21) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv16to20Maps[Randomizer.nextInt(lv16to20Maps.length)]));
        } else if (getPlayer().getLevel() < 26) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv21to25Maps[Randomizer.nextInt(lv21to25Maps.length)]));
        } else if (getPlayer().getLevel() < 31) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv26to30Maps[Randomizer.nextInt(lv26to30Maps.length)]));
        } else if (getPlayer().getLevel() < 36) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv31to35Maps[Randomizer.nextInt(lv31to35Maps.length)]));
        } else if (getPlayer().getLevel() < 41) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv36to40Maps[Randomizer.nextInt(lv36to40Maps.length)]));
        } else if (getPlayer().getLevel() < 46) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv41to45Maps[Randomizer.nextInt(lv41to45Maps.length)]));
        } else if (getPlayer().getLevel() < 51) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv46to50Maps[Randomizer.nextInt(lv46to50Maps.length)]));
        } else if (getPlayer().getLevel() < 56) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv51to55Maps[Randomizer.nextInt(lv51to55Maps.length)]));
        } else if (getPlayer().getLevel() < 61) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv56to60Maps[Randomizer.nextInt(lv56to60Maps.length)]));
        } else if (getPlayer().getLevel() < 66) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv61to65Maps[Randomizer.nextInt(lv61to65Maps.length)]));
        } else if (getPlayer().getLevel() < 71) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv66to70Maps[Randomizer.nextInt(lv66to70Maps.length)]));
        } else if (getPlayer().getLevel() < 76) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)]));
        } else if (getPlayer().getLevel() < 81) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 86) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 91) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 96) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 101) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 106) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 111) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 116) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 121) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 126) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 131) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 136) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 141) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 146) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 151) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 156) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 161) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 166) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 171) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 176) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 181) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 186) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 191) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else if (getPlayer().getLevel() < 196) {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        } else {
            changeMap(c.getChannelServer().getMapFactory().getMap(lv71to75Maps[Randomizer.nextInt(lv71to75Maps.length)])); // todo: add maps for this range and update this
        }
    }

    public void changeMap(MapleMap target) {
        changeMap(target, target.getRandomPlayerSpawnpoint());
    }

    private void changeMap(MapleMap target, Portal targetPortal) {
        getPlayer().changeMap(target, targetPortal);
        Foothold foothold = getPlayer().getMap().getFootholds().findBelow(getPlayer().getPosition());
        c.handlePacket(PacketCreator.createPlayerMovementPacket((short) getPlayer().getPosition().x, (short) foothold.getY1(), (byte) 4, (short) 100), (short) 41);
        hasTargetItem = false;
        hasTargetMonster = false;
        if (isPQMap(target.getId())) {
            currentMode = Mode.PQ;
            doneWithPQTask = false;
        }
    }

    private int grind(int time) {
        boolean didAction = true;
        long startTime = System.currentTimeMillis(), otherStartTime, time1 = 0, time2 = 0, time3 = 0, time4 = 0;
        int loops = 0;
        while (time > 0 && didAction) {
            loops++;
            didAction = false;
            if (!hasTargetItem || ((MapItem) targetItem).isPickedUp()) {
                otherStartTime = System.currentTimeMillis();
                if (!getPlayer().getMap().getItems().isEmpty()) {
                    double minDistance = 1000000.0, nextDistance;
                    for (MapObject it : getPlayer().getMap().getItems()) {
                        if (!((MapItem) it).isPickedUp() && ((MapItem) it).canBePickedBy(getPlayer())) {
                            if (((MapItem) it).getItem() != null && !InventoryManipulator.checkSpace(c, ((MapItem) it).getItemId(), ((MapItem) it).getItem().getQuantity(), ((MapItem) it).getItem().getOwner())) {
                                continue;
                            }
                            nextDistance = getPlayer().getPosition().distance(it.getPosition());
                            if (nextDistance < minDistance) {
                                minDistance = nextDistance;
                                targetItem = it;
                                hasTargetItem = true;
                            }
                        }
                    }
                    /*for (MapObject it : getPlayer().getMap().getItems()) {
                        if (!((MapItem) it).isPickedUp() && ((MapItem) it).canBePickedBy(getPlayer())) {
                            if (((MapItem) it).getItem() != null && !InventoryManipulator.checkSpace(c, ((MapItem) it).getItemId(), ((MapItem) it).getItem().getQuantity(), ((MapItem) it).getItem().getOwner())) {
                                continue;
                            }
                            targetItem = it;
                            hasTargetItem = true;
                        }
                    }*/
                } else {
                    hasTargetItem = false;
                }
                time1 += System.currentTimeMillis() - otherStartTime;
            }
            if (!hasTargetMonster || !targetMonster.isAlive()) {
                otherStartTime = System.currentTimeMillis();
                if (!getPlayer().getMap().getAllMonsters().isEmpty()) {
                    double minDistance = 1000000.0, nextDistance;
                    for (Monster m : getPlayer().getMap().getAllMonsters()) {
                        if (!m.isAlive()) {
                            continue;
                        }
                        nextDistance = getPlayer().getPosition().distance(m.getPosition());
                        if (nextDistance < minDistance) {
                            minDistance = nextDistance;
                            targetMonster = m;
                            hasTargetMonster = true;
                        }
                    }
                    /*List<Monster> shuffled = getPlayer().getMap().getAllMonsters();
                    Collections.shuffle(shuffled);*/
                    /*for (Monster m : getPlayer().getMap().getAllMonsters()) {
                        if (m.isAlive()) {
                            targetMonster = m;
                            hasTargetMonster = true;
                            break;
                        }
                    }*/
                } else {
                    hasTargetMonster = false;
                }
                time2 += System.currentTimeMillis() - otherStartTime;
            }
            if (hasTargetItem) {
                otherStartTime = System.currentTimeMillis();
                if (!getPlayer().getPosition().equals(targetItem.getPosition())) {
                    time = moveBot((short) targetItem.getPosition().x, (short) targetItem.getPosition().y, time);
                    didAction = true;
                }
                if (getPlayer().getPosition().equals(targetItem.getPosition())) {
                    pickupItem();
                    time -= 50;
                    didAction = true;
                }
                time3 += System.currentTimeMillis() - otherStartTime;
            } else if (hasTargetMonster) {
                otherStartTime = System.currentTimeMillis();
                if (getPlayer().getPosition().distance(targetMonster.getPosition().x - (isRangedJob() ? 300 : 50), targetMonster.getPosition().y) < (isRangedJob() ? 100 : 50)) { // todo: accurate range
                    attack(time);
                    time = 0;
                    didAction = true;
                } else {
                    time = moveBot((short) (targetMonster.getPosition().x - (isRangedJob() ? 300 : 50)), (short) targetMonster.getPosition().y, time);
                    didAction = true;
                }
                time4 += System.currentTimeMillis() - otherStartTime;
            }
        }
        long grindTime = System.currentTimeMillis() - startTime;
        if (grindTime > 10) {
            System.out.println("Grind time: " + grindTime + ", loops: " + loops + ", time1: " + time1 + ", time2: " + time2 + ", time3: " + time3 + ", time4: " + time4);
            if (time4 > 10) {
                System.out.println("job: " + getPlayer().getJob());
            }
        }
        return time;
    }

    private boolean grind(int time, int targetItemId, boolean seek) {
        boolean didAction = true;
        long startTime = System.currentTimeMillis(), otherStartTime, time1 = 0, time2 = 0, time3 = 0, time4 = 0;
        int loops = 0;
        while (time > 0 && didAction) {
            loops++;
            didAction = false;
            if (!hasTargetItem || ((MapItem) targetItem).isPickedUp()) {
                otherStartTime = System.currentTimeMillis();
                if (!getPlayer().getMap().getItems().isEmpty()) {
                    double minDistance = 1000000.0, nextDistance;
                    for (MapObject it : getPlayer().getMap().getItems()) {
                        if (!((MapItem) it).isPickedUp() && ((MapItem) it).canBePickedBy(getPlayer())) {
                            if ((seek == (((MapItem) it).getItemId() != targetItemId)) || (((MapItem) it).getItem() != null && !InventoryManipulator.checkSpace(c, ((MapItem) it).getItemId(), ((MapItem) it).getItem().getQuantity(), ((MapItem) it).getItem().getOwner()))) {
                                continue;
                            }
                            nextDistance = getPlayer().getPosition().distance(it.getPosition());
                            if (nextDistance < minDistance) {
                                minDistance = nextDistance;
                                targetItem = it;
                                hasTargetItem = true;
                            }
                        }
                    }
                    /*for (MapObject it : getPlayer().getMap().getItems()) {
                        if (!((MapItem) it).isPickedUp() && ((MapItem) it).canBePickedBy(getPlayer())) {
                            if (((MapItem) it).getItem() != null && !InventoryManipulator.checkSpace(c, ((MapItem) it).getItemId(), ((MapItem) it).getItem().getQuantity(), ((MapItem) it).getItem().getOwner())) {
                                continue;
                            }
                            targetItem = it;
                            hasTargetItem = true;
                        }
                    }*/
                } else {
                    hasTargetItem = false;
                }
                time1 += System.currentTimeMillis() - otherStartTime;
            }
            if (!hasTargetMonster || !targetMonster.isAlive()) {
                otherStartTime = System.currentTimeMillis();
                if (!getPlayer().getMap().getAllMonsters().isEmpty()) {
                    double minDistance = 1000000.0, nextDistance;
                    for (Monster m : getPlayer().getMap().getAllMonsters()) {
                        if (!m.isAlive()) {
                            continue;
                        }
                        nextDistance = getPlayer().getPosition().distance(m.getPosition());
                        if (nextDistance < minDistance) {
                            minDistance = nextDistance;
                            targetMonster = m;
                            hasTargetMonster = true;
                        }
                    }
                    /*List<Monster> shuffled = getPlayer().getMap().getAllMonsters();
                    Collections.shuffle(shuffled);*/
                    /*for (Monster m : getPlayer().getMap().getAllMonsters()) {
                        if (m.isAlive()) {
                            targetMonster = m;
                            hasTargetMonster = true;
                            break;
                        }
                    }*/
                } else {
                    hasTargetMonster = false;
                }
                time2 += System.currentTimeMillis() - otherStartTime;
            }
            if (hasTargetItem) {
                otherStartTime = System.currentTimeMillis();
                if (!getPlayer().getPosition().equals(targetItem.getPosition())) {
                    time = moveBot((short) targetItem.getPosition().x, (short) targetItem.getPosition().y, time);
                    didAction = true;
                }
                if (getPlayer().getPosition().equals(targetItem.getPosition())) {
                    pickupItem();
                    time -= 50;
                    didAction = true;
                }
                time3 += System.currentTimeMillis() - otherStartTime;
            } else if (hasTargetMonster) {
                otherStartTime = System.currentTimeMillis();
                if (getPlayer().getPosition().distance(targetMonster.getPosition().x - (isRangedJob() ? 300 : 50), targetMonster.getPosition().y) < (isRangedJob() ? 100 : 50)) { // todo: accurate range
                    attack(time);
                    time = 0;
                    didAction = true;
                } else {
                    time = moveBot((short) (targetMonster.getPosition().x - (isRangedJob() ? 300 : 50)), (short) targetMonster.getPosition().y, time);
                    didAction = true;
                }
                time4 += System.currentTimeMillis() - otherStartTime;
            }
        }
        long grindTime = System.currentTimeMillis() - startTime;
        if (grindTime > 10) {
            System.out.println("Grind time: " + grindTime + ", loops: " + loops + ", time1: " + time1 + ", time2: " + time2 + ", time3: " + time3 + ", time4: " + time4);
            if (time4 > 10) {
                System.out.println("job: " + getPlayer().getJob());
            }
        }
        return didAction;
    }

    private void levelup() {
        assignSP(); // do this before doing job advance
        if (getPlayer().getJob().equals(Job.BEGINNER)) {
            if (getPlayer().getLevel() == 8 && Randomizer.nextInt(5) == 0) { // 1/5 chance to choose magician
                jobAdvance(Job.MAGICIAN);
            } else if (getPlayer().getLevel() == 10 && Randomizer.nextInt(500) != 0) {
                // randomly pick between the 4 non-magician explorer jobs
                // 1/500 chance to be a perma beginner, technically this makes mages slightly more common than the other 4, but it's negligible on this scale
                Job[] jobs = {Job.WARRIOR, Job.BOWMAN, Job.THIEF, Job.PIRATE};
                jobAdvance(jobs[Randomizer.nextInt(jobs.length)]);
            }
        } else if (getPlayer().getLevel() == 30) { // note: these assume the bot will not gain more than 1 level between updates
            switch (getPlayer().getJob()) {
                case WARRIOR -> jobAdvance(Job.getById(100 + 10 + 10 * Randomizer.nextInt(3)));
                case MAGICIAN -> jobAdvance(Job.getById(200 + 10 + 10 * Randomizer.nextInt(3)));
                case BOWMAN -> jobAdvance(Job.getById(300 + 10 + 10 * Randomizer.nextInt(2)));
                case THIEF -> jobAdvance(Job.getById(400 + 10 + 10 * Randomizer.nextInt(2)));
                case PIRATE -> jobAdvance(getPlayer().getWeaponType().equals(WeaponType.GUN) ? Job.GUNSLINGER : Job.BRAWLER);
            }
        } else if (getPlayer().getLevel() == 70) {
            jobAdvance(Job.getById(getPlayer().getJob().getId() + 1));
        } else if (getPlayer().getLevel() == 120) {
            jobAdvance(Job.getById(getPlayer().getJob().getId() + 1));
            // todo: 4th job skills
        }
        int remainingAP = getPlayer().getRemainingAp(), nextAP;
        // todo: even with max secondary stat, they may be unable to equip the highest level items if the item being replaced give the stat, need to address this eventually
        if (getPlayer().getJob().equals(Job.BEGINNER)) {
            if (getPlayer().getTotalDex() < 60) {
                nextAP = Math.min(remainingAP, 60 - getPlayer().getTotalDex());
                remainingAP -= nextAP;
                getPlayer().assignDex(nextAP);
            }
            getPlayer().assignStr(remainingAP);
        } else if (getPlayer().getJob().getId() / 100 == 1) { // warrior
            if (getPlayer().getTotalDex() < 60 && getPlayer().getTotalDex() < getPlayer().getLevel() + 10) {
                nextAP = Math.min(Math.min(remainingAP, 60 - getPlayer().getTotalDex()), getPlayer().getLevel() + 10 - getPlayer().getTotalDex());
                remainingAP -= nextAP;
                getPlayer().assignDex(nextAP);
            }
            getPlayer().assignStr(remainingAP);
        } else if (getPlayer().getJob().getId() / 100 == 2) { // magician
            if (getPlayer().getTotalLuk() < 123 && getPlayer().getTotalLuk() < getPlayer().getLevel() + 3) {
                nextAP = Math.min(Math.min(remainingAP, 123 - getPlayer().getTotalLuk()), getPlayer().getLevel() + 3 - getPlayer().getTotalLuk());
                remainingAP -= nextAP;
                getPlayer().assignLuk(nextAP);
            }
            getPlayer().assignInt(remainingAP);
        } else if (getPlayer().getJob().getId() / 100 == 3) { // bowman
            if (getPlayer().getWeaponType().equals(WeaponType.BOW)) {
                if (getPlayer().getTotalStr() < 125 && getPlayer().getTotalStr() < getPlayer().getLevel() + 5) {
                    nextAP = Math.min(Math.min(remainingAP, 125 - getPlayer().getTotalStr()), getPlayer().getLevel() + 5 - getPlayer().getTotalStr());
                    remainingAP -= nextAP;
                    getPlayer().assignStr(nextAP);
                }
            } else { // crossbow
                if (getPlayer().getTotalStr() < 120 && getPlayer().getTotalStr() < getPlayer().getLevel()) {
                    nextAP = Math.min(Math.min(remainingAP, 120 - getPlayer().getTotalStr()), getPlayer().getLevel() - getPlayer().getTotalStr());
                    remainingAP -= nextAP;
                    getPlayer().assignStr(nextAP);
                }
            }
            getPlayer().assignDex(remainingAP);
        } else if (getPlayer().getJob().getId() / 100 == 4) { // thief
            if (getPlayer().getTotalDex() < 160 && getPlayer().getTotalDex() < getPlayer().getLevel() + 40) {
                nextAP = Math.min(Math.min(remainingAP, 160 - getPlayer().getTotalDex()), getPlayer().getLevel() + 40 - getPlayer().getTotalDex());
                remainingAP -= nextAP;
                getPlayer().assignDex(nextAP);
            }
            /*if (weaponType.equals(WeaponType.DAGGER_THIEVES)) {
                // todo: str daggers?
            }*/
            getPlayer().assignLuk(remainingAP);
        } else if (getPlayer().getJob().getId() / 100 == 5) { // pirate
            if (getPlayer().getWeaponType().equals(WeaponType.GUN)) {
                if (getPlayer().getTotalStr() < 120 && getPlayer().getTotalStr() < getPlayer().getLevel()) {
                    nextAP = Math.min(Math.min(remainingAP, 120 - getPlayer().getTotalStr()), getPlayer().getLevel() - getPlayer().getTotalStr());
                    remainingAP -= nextAP;
                    getPlayer().assignStr(nextAP);
                }
                getPlayer().assignDex(remainingAP);
            } else { // knuckle
                if (getPlayer().getTotalDex() < 120 && getPlayer().getTotalDex() < getPlayer().getLevel()) {
                    nextAP = Math.min(Math.min(remainingAP, 120 - getPlayer().getTotalDex()), getPlayer().getLevel() - getPlayer().getTotalDex());
                    remainingAP -= nextAP;
                    getPlayer().assignDex(nextAP);
                }
                getPlayer().assignStr(remainingAP);
            }
        }
        this.level = getPlayer().getLevel();
        if (currentMode != Mode.PQ) {
            currentMode = Mode.WAITING; // pick new map? todo: only do this if you get into a new level range for maps probably
        }
    }

    private void jobAdvance(Job newJob) {
        boolean firstJob = getPlayer().getJob().equals(Job.BEGINNER),
                secondJob = getPlayer().getJob().getId() % 100 == 0;
        getPlayer().changeJob(newJob);
        if (firstJob) {
            getPlayer().resetStats();
            switch (newJob) {
                case WARRIOR -> gainAndEquip(1302077);
                case MAGICIAN -> gainAndEquip(1372043);
                case BOWMAN -> {
                    gainAndEquip(1452051);
                    gainItem(2060000, (short) 1000); // arrows
                }
                case THIEF -> {
                    gainAndEquip(1472061);
                    gainItem(2070000, (short) 500); // stars
                }
                case PIRATE -> {
                    if (Randomizer.nextInt(2) == 0) {
                        gainAndEquip(1492000);
                        gainItem(2330000, (short) 1000); // bullets
                    } else {
                        gainAndEquip(1482000);
                    }
                }
            }
        } else if (secondJob) {
            switch (newJob) {
                case FIGHTER -> gainAndEquip(1402002);
                case PAGE -> gainAndEquip(1422001);
                case SPEARMAN -> gainAndEquip(1432002);
                case FP_WIZARD, IL_WIZARD, CLERIC -> gainAndEquip(1372003);
                case HUNTER -> gainAndEquip(1452005);
                case CROSSBOWMAN -> gainAndEquip(1462000);
                case ASSASSIN -> gainAndEquip(1472007);
                case BANDIT -> gainAndEquip(1332012);
                case BRAWLER -> gainAndEquip(1482004);
                case GUNSLINGER -> gainAndEquip(1492004);
            }
        }
        assignSP();
    }

    private void assignSP() {
        int remainingSP = getPlayer().getRemainingSp(), i = 0;
        int[][] skillOrder = skillOrders.get(getPlayer().getJob());
        while (i < skillOrder.length && remainingSP > 0) {
            if (getPlayer().getSkillLevel(skillOrder[i][0]) < skillOrder[i][1]) {
                AssignSPProcessor.SPAssignAction(c, skillOrder[i][0]);
            }
            if (remainingSP == getPlayer().getRemainingSp()) {
                i++;
            }
            remainingSP = getPlayer().getRemainingSp();
        }
    }

    private void rechargeProjectiles() {
        gainProjectileIfMissing();
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (Item torecharge : getPlayer().getInventory(InventoryType.USE).list()) {
            if (ItemConstants.isThrowingStar(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                getPlayer().forceUpdateItem(torecharge);
            } else if (ItemConstants.isArrow(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                getPlayer().forceUpdateItem(torecharge);
            } else if (ItemConstants.isBullet(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                getPlayer().forceUpdateItem(torecharge);
            } else if (ItemConstants.isConsumable(torecharge.getItemId())) {
                torecharge.setQuantity(ii.getSlotMax(c, torecharge.getItemId()));
                getPlayer().forceUpdateItem(torecharge);
            }
        }
    }

    private void gainProjectileIfMissing() {
        if (getPlayer().getJob().getId() / 100 == 3 && getPlayer().getJob().getId() % 100 / 10 == 2) {
            if (!hasXBowArrow()) {
                gainItem(2061000, (short) 1000);
            }
        } else if (getPlayer().getJob().getId() / 100 == 3) {
            if (!hasArrow()) {
                gainItem(2060000, (short) 1000);
            }
        } else if (getPlayer().getJob().getId() / 100 == 4) {
            if (!hasStar()) {
                gainItem(2070000, (short) 500);
            }
        } else if (getPlayer().getJob().getId() / 100 == 5) {
            if (!hasBullet()) {
                gainItem(2330000, (short) 1000);
            }
        }
    }

    private boolean hasXBowArrow() {
        return getPlayer().getItemQuantity(2061000, false) > 0 ||
                getPlayer().getItemQuantity(2061001, false) > 0 ||
                getPlayer().getItemQuantity(2061002, false) > 0 ||
                getPlayer().getItemQuantity(2061003, false) > 0 ||
                getPlayer().getItemQuantity(2061004, false) > 0;
    }

    private boolean hasArrow() {
        return getPlayer().getItemQuantity(2060000, false) > 0 ||
                getPlayer().getItemQuantity(2060001, false) > 0 ||
                getPlayer().getItemQuantity(2060002, false) > 0 ||
                getPlayer().getItemQuantity(2060003, false) > 0 ||
                getPlayer().getItemQuantity(2060004, false) > 0;
    }

    private boolean hasStar() {
        return getPlayer().getItemQuantity(2070000, false) > 0 ||
                getPlayer().getItemQuantity(2070001, false) > 0 ||
                getPlayer().getItemQuantity(2070002, false) > 0 ||
                getPlayer().getItemQuantity(2070003, false) > 0 ||
                getPlayer().getItemQuantity(2070004, false) > 0 ||
                getPlayer().getItemQuantity(2070006, false) > 0 ||
                getPlayer().getItemQuantity(2070007, false) > 0 ||
                getPlayer().getItemQuantity(2070011, false) > 0 ||
                getPlayer().getItemQuantity(2070015, false) > 0 ||
                getPlayer().getItemQuantity(2070016, false) > 0;
    }

    private boolean hasBullet() {
        return getPlayer().getItemQuantity(2330000, false) > 0 ||
                getPlayer().getItemQuantity(2330001, false) > 0 ||
                getPlayer().getItemQuantity(2330002, false) > 0 ||
                getPlayer().getItemQuantity(2330003, false) > 0 ||
                getPlayer().getItemQuantity(2330004, false) > 0 ||
                getPlayer().getItemQuantity(2330005, false) > 0 ||
                getPlayer().getItemQuantity(2330006, false) > 0;
    }

    private void gainItem(int itemId, short quantity) {
        if (quantity > 0) {
            InventoryType inventoryType = ItemConstants.getInventoryType(itemId);
            while (!InventoryManipulator.checkSpace(c, itemId, quantity, "")) {
                // make sure not to give a quantity that can't fit in their inventory or this will get stuck
                short lowestValueItemPos = getLowestValueItemPos(inventoryType);
                InventoryManipulator.drop(c, inventoryType, lowestValueItemPos, InventoryManipulator.getQuantity(c, inventoryType, lowestValueItemPos));
            }
            InventoryManipulator.addById(c, itemId, quantity);
        } else{
            InventoryManipulator.removeById(c, ItemConstants.getInventoryType(itemId), itemId, -quantity, true, false);
        }
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

    /**
     * checks each equip to see if it is better than the currently equipped item and equips it if so
     */
    private void checkEquips() {
        getPlayer().getInventory(InventoryType.EQUIP).lockInventory();
        getPlayer().getInventory(InventoryType.EQUIPPED).lockInventory();
        try {
            boolean notDone = true;
            short equipPos, ringSlot = 0;
            Equip nextEquip = null;
            Iterator<Item> it;
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            while (notDone) {
                notDone = false;
                equipPos = -1;
                it = getPlayer().getInventory(InventoryType.EQUIP).iterator();
                while (it.hasNext()) {
                    nextEquip = (Equip) it.next();
                    if (!jobAppropriateEquip(nextEquip.getItemId()) || !ii.canWearEquipment(getPlayer(), nextEquip, ItemConstants.getEquipSlot(nextEquip.getItemId()))) {
                        continue;
                    }
                    if (ItemConstants.isRing(nextEquip.getItemId())) {
                        ringSlot = isBetterRing(nextEquip);
                        if (ringSlot != 0) {
                            equipPos = nextEquip.getPosition();
                            notDone = true;
                            break;
                        }
                    } else if (isBetterEquip(nextEquip)) {
                        equipPos = nextEquip.getPosition();
                        notDone = true;
                        break;
                    }
                }
                if (equipPos != -1) {
                    if (ItemConstants.isRing(nextEquip.getItemId())) {
                        InventoryManipulator.equip(c, equipPos, ringSlot);
                    } else {
                        // System.out.println("equipped item: " + nextEquip.getItemId() + " with att: " + nextEquip.getWatk());
                        InventoryManipulator.equip(c, equipPos, ItemConstants.getEquipSlot(nextEquip.getItemId()));
                    }
                }
            }
        } finally {
            getPlayer().getInventory(InventoryType.EQUIP).unlockInventory();
            getPlayer().getInventory(InventoryType.EQUIPPED).unlockInventory();
        }
    }

    private boolean jobAppropriateEquip(int itemId) {
        if (ItemConstants.isWeapon(itemId) && !isJobAppropriateWeapon(itemId)) {
            return false;
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int reqJob = ii.getEquipStats(itemId).get("reqJob");
        if (reqJob == 0) {
            return true;
        }
        if (getPlayer().getJob().getId() / 100 == 1) {
            return reqJob % 2 == 1;
        } else if (getPlayer().getJob().getId() / 100 == 2) {
            return reqJob % 4 / 2 == 1;
        } else if (getPlayer().getJob().getId() / 100 == 3) {
            return reqJob % 8 / 4 == 1;
        } else if (getPlayer().getJob().getId() / 100 == 4) {
            return reqJob % 16 / 8 == 1;
        } else if (getPlayer().getJob().getId() / 100 == 5) {
            return reqJob % 32 / 16 == 1;
        }
        return false;
    }

    private boolean isJobAppropriateWeapon(int itemId) {
        if (getPlayer().getJob().getId() / 100 == 1) {
            if (getPlayer().getJob().getId() % 100 / 10 == 1) {
                return ItemConstants.is1hSword(itemId) || ItemConstants.is2hSword(itemId);
            }
            if (getPlayer().getJob().getId() % 100 / 10 == 2) {
                return ItemConstants.is1hBluntWeapon(itemId) || ItemConstants.is2hBluntWeapon(itemId);
            }
            if (getPlayer().getJob().getId() % 100 / 10 == 3) {
                return ItemConstants.isSpear(itemId);
            }
            return ItemConstants.is1hSword(itemId) || ItemConstants.is2hSword(itemId) || ItemConstants.is1hAxe(itemId) ||
                    ItemConstants.is2hAxe(itemId) || ItemConstants.is1hBluntWeapon(itemId) || ItemConstants.is2hBluntWeapon(itemId) ||
                    ItemConstants.isSpear(itemId) || ItemConstants.isPolearm(itemId);
        }
        if (getPlayer().getJob().getId() / 100 == 2) {
            return ItemConstants.isWand(itemId) || ItemConstants.isStaff(itemId);
        }
        if (getPlayer().getJob().getId() / 100 == 3) {
            if (getPlayer().getJob().getId() % 100 / 10 == 2) {
                return ItemConstants.isCrossbow(itemId);
            }
            return ItemConstants.isBow(itemId);
        }
        if (getPlayer().getJob().getId() / 100 == 4) {
            if (getPlayer().getJob().getId() % 100 / 10 == 2) {
                return ItemConstants.isDagger(itemId);
            }
            return ItemConstants.isClaw(itemId);
        }
        if (getPlayer().getJob().getId() / 100 == 5) {
            if (getPlayer().getWeaponType().equals(WeaponType.GUN)) {
                return ItemConstants.isGun(itemId);
            }
            return ItemConstants.isKnuckle(itemId);
        }
        return true;
    }

    private boolean isBetterEquip(Equip equip) {
        short equipSlot = ItemConstants.getEquipSlot(equip.getItemId());
        if (equipSlot == 0) {
            System.out.println("equip slot could not be determined for item with id " + equip.getItemId());
            return false;
        }
        return isBetterEquip(equip, equipSlot);
    }

    private boolean isBetterEquip(Equip equip, short equipSlot) {
        Equip currentEquip = (Equip) getPlayer().getInventory(InventoryType.EQUIPPED).getItem(equipSlot);
        if (currentEquip == null) {
            if (ItemConstants.isShield(equip.getItemId())) {
                Equip weapon = (Equip) getPlayer().getInventory(InventoryType.EQUIPPED).getItem((short) -11);
                return weapon == null || !ItemConstants.is2hWeapon(weapon.getItemId());
            } else if (ItemConstants.isBottom(equip.getItemId())) {
                Equip top = (Equip) getPlayer().getInventory(InventoryType.EQUIPPED).getItem((short) -5);
                return top == null || !ItemConstants.isOverall(top.getItemId());
            } else {
                return true;
            }
        }
        int attack, mainStat, secondaryStat, accuracy = currentEquip.getAcc();
        if (getPlayer().getJob().getId() / 100 == 2) {
            attack = currentEquip.getMatk();
            mainStat = currentEquip.getInt();
            secondaryStat = currentEquip.getLuk();
        } else {
            attack = currentEquip.getWatk();
            if (getPlayer().getJob().getId() / 100 == 1 || (getPlayer().getJob().getId() / 100 == 5 && !getPlayer().getWeaponType().equals(WeaponType.GUN))) {
                mainStat = currentEquip.getStr();
                secondaryStat = currentEquip.getDex();
            } else if (getPlayer().getJob().getId() / 100 == 3 || getPlayer().getJob().getId() / 100 == 5) {
                mainStat = currentEquip.getDex();
                secondaryStat = currentEquip.getStr();
            } else { // thief
                mainStat = currentEquip.getLuk();
                secondaryStat = currentEquip.getDex();
            }
        }
        if (ItemConstants.is2hWeapon(equip.getItemId())) { // compare against weapon + shield
            currentEquip = (Equip) getPlayer().getInventory(InventoryType.EQUIPPED).getItem((short) -10);
            if (currentEquip != null) {
                if (getPlayer().getJob().getId() / 100 == 2) {
                    attack += currentEquip.getMatk();
                    mainStat += currentEquip.getInt();
                    secondaryStat += currentEquip.getLuk();
                } else {
                    attack += currentEquip.getWatk();
                    if (getPlayer().getJob().getId() / 100 == 1 || (getPlayer().getJob().getId() / 100 == 5 && !getPlayer().getWeaponType().equals(WeaponType.GUN))) {
                        mainStat += currentEquip.getStr();
                        secondaryStat += currentEquip.getDex();
                    } else if (getPlayer().getJob().getId() / 100 == 3 || getPlayer().getJob().getId() / 100 == 5) {
                        mainStat += currentEquip.getDex();
                        secondaryStat += currentEquip.getStr();
                    } else { // thief
                        mainStat = currentEquip.getLuk();
                        secondaryStat += currentEquip.getDex();
                    }
                }
            }
        }
        if (ItemConstants.isOverall(equip.getItemId())) { // compare against top + bottom
            currentEquip = (Equip) getPlayer().getInventory(InventoryType.EQUIPPED).getItem((short) -6);
            if (currentEquip != null) {
                if (getPlayer().getJob().getId() / 100 == 2) {
                    attack += currentEquip.getMatk();
                    mainStat += currentEquip.getInt();
                    secondaryStat += currentEquip.getLuk();
                } else {
                    attack += currentEquip.getWatk();
                    if (getPlayer().getJob().getId() / 100 == 1 || (getPlayer().getJob().getId() / 100 == 5 && !getPlayer().getWeaponType().equals(WeaponType.GUN))) {
                        mainStat += currentEquip.getStr();
                        secondaryStat += currentEquip.getDex();
                    } else if (getPlayer().getJob().getId() / 100 == 3 || getPlayer().getJob().getId() / 100 == 5) {
                        mainStat += currentEquip.getDex();
                        secondaryStat += currentEquip.getStr();
                    } else { // thief
                        mainStat = currentEquip.getLuk();
                        secondaryStat += currentEquip.getDex();
                    }
                }
            }
        }
        if (getPlayer().getJob().getId() / 100 == 2) { // magic users
            if (equip.getInt() + equip.getMatk() > mainStat + attack) {
                return true;
            }
            if (equip.getInt() + equip.getMatk() == mainStat + attack &&
                    equip.getLuk() > secondaryStat) {
                return true;
            }
        } else {
            if (equip.getWatk() > attack) {
                return true;
            }
            if (equip.getWatk() == attack) {
                if (getPlayer().getJob().getId() / 100 == 1 || (getPlayer().getJob().getId() / 100 == 5 && !getPlayer().getWeaponType().equals(WeaponType.GUN))) {
                    if (equip.getStr() > mainStat) {
                        return true;
                    }
                    if (equip.getStr() == mainStat) {
                        if (equip.getDex() > secondaryStat) {
                            return true;
                        }
                        if (equip.getDex() == secondaryStat) {
                            if (equip.getAcc() > accuracy) {
                                return true;
                            }
                        }
                    }
                } else if (getPlayer().getJob().getId() / 100 == 3 || getPlayer().getJob().getId() / 100 == 5) {
                    if (equip.getDex() > mainStat) {
                        return true;
                    }
                    if (equip.getDex() == mainStat) {
                        if (equip.getStr() > secondaryStat) {
                            return true;
                        }
                    }
                } else { // thief
                    if (equip.getLuk() > mainStat) {
                        return true;
                    }
                    if (equip.getLuk() == mainStat) {
                        if (equip.getDex() > secondaryStat) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * check against all 4 rings
     * @param ring the new ring
     * @return the position of the equipped ring which was found to be worse, either -12, -13, -15, or -16, or 0 if none were worse
     */
    private short isBetterRing(Equip ring) {
        for (short equipSlot : new short[]{-12, -13, -15, -16}) {
            if (isBetterEquip(ring, equipSlot)) {
                return equipSlot;
            }
        }
        return 0;
    }

    private void createInventorySpace() {
        getPlayer().getInventory(InventoryType.EQUIP).lockInventory();
        getPlayer().getInventory(InventoryType.USE).lockInventory();
        getPlayer().getInventory(InventoryType.ETC).lockInventory();
        try {
            short space, nextPos;
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            for (InventoryType type : new InventoryType[]{InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC}) {
                space = getPlayer().getInventory(type).getNumFreeSlot();
                while (space < 48) {
                    nextPos = getLowestValueItemPos(type);
                    getPlayer().gainMeso(ii.getPrice(getPlayer().getInventory(type).getItem(nextPos).getItemId(), getPlayer().getInventory(type).getItem(nextPos).getQuantity()));
                    getPlayer().getInventory(type).removeItem(nextPos);
                    space = getPlayer().getInventory(type).getNumFreeSlot();
                }
            }
        } finally {
            getPlayer().getInventory(InventoryType.EQUIP).unlockInventory();
            getPlayer().getInventory(InventoryType.USE).unlockInventory();
            getPlayer().getInventory(InventoryType.ETC).unlockInventory();
        }
    }

    private short getLowestValueItemPos(InventoryType inventoryType) {
        getPlayer().getInventory(inventoryType).lockInventory();
        try {
            int lowestValue = Integer.MAX_VALUE, nextValue;
            short pos = -1;
            Iterator<Item> it = getPlayer().getInventory(inventoryType).iterator();
            Item next;
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            while (it.hasNext()) {
                next = it.next();
                nextValue = ii.getPrice(next.getItemId(), next.getQuantity());
                if (nextValue < lowestValue) {
                    lowestValue = nextValue;
                    pos = next.getPosition();
                }
            }
            return pos;
        } finally {
            getPlayer().getInventory(inventoryType).unlockInventory();
        }
    }

    private boolean isRangedJob() {
        int jobId = getPlayer().getJob().getId();
        if (jobId == 500 && getPlayer().getWeaponType().equals(WeaponType.GUN)) {
            return true;
        }
        return jobId / 100 == 2 || jobId / 100 == 3 || (jobId / 100 == 4 && jobId % 100 / 10 != 2) || (jobId / 100 == 5 && jobId % 100 / 10 == 2);
    }

    public Character getFollowing() {
        return following;
    }

    private int getDirection(int skillId) {
        return switch (skillId) {
            case Warrior.POWER_STRIKE, Warrior.SLASH_BLAST -> {
                int[] directions;
                Equip weapon = (Equip) getPlayer().getInventory(InventoryType.EQUIPPED).getItem((short) -11);
                if (ItemConstants.isSpear(weapon.getItemId()) || ItemConstants.isPolearm(weapon.getItemId())) {
                    directions = new int[]{10, 13, 14, 19, 20};
                } else if (ItemConstants.is2hWeapon(weapon.getItemId())) {
                    directions = new int[]{9, 10, 11, 16, 17};
                } else {
                    directions = new int[]{5, 6, 7, 16, 17};
                }
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
            case Archer.ARROW_BLOW, Archer.DOUBLE_SHOT -> getPlayer().getWeaponType().equals(WeaponType.BOW) ? (Randomizer.nextInt(2) == 0 ? 22 : 27) : 23;
            case Hunter.ARROW_BOMB -> Randomizer.nextInt(2) == 0 ? 22 : 27;
            case Crossbowman.IRON_ARROW, Sniper.ARROW_ERUPTION, Sniper.BLIZZARD, Sniper.STRAFE, Marksman.SNIPE -> 23;
            case Rogue.LUCKY_SEVEN, NightLord.TRIPLE_THROW -> {
                int[] directions = new int[]{24, 25, 26};
                yield directions[Randomizer.nextInt(directions.length)];
            }
            case Hermit.AVENGER -> 56;
            case Rogue.DOUBLE_STAB -> Randomizer.nextInt(2) == 0 ? 16 : 17;
            case Bandit.SAVAGE_BLOW -> 55;
            case ChiefBandit.BAND_OF_THIEVES -> {
                int[] directions = new int[]{5, 6, 7, 16, 17};
                yield directions[Randomizer.nextInt(directions.length)];
            }
            case Shadower.BOOMERANG_STEP -> 44;
            case Pirate.SOMERSAULT_KICK -> 78;
            case Pirate.DOUBLE_SHOT -> getPlayer().getSkillLevel(Outlaw.BURST_FIRE) > 0 ? 87 : 86;
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

    // pq stuff

    private void doPQ(int time) {
        //System.out.println(getPlayer().getName() + " is " + (getPlayer().getEventInstance().isEventLeader(getPlayer()) ? "" : "not") + " the party leader");
        //System.out.println("event id: " + getPlayer().getEventInstance().getPlayerEventId(getPlayer().getId()));
        switch (getPlayer().getMap().getId()) {
            case 103000000 -> {} // do nothing
            case 103000800 -> kpqStage1(time);
            case 103000801 -> kpqStage2(time);
            case 103000802 -> kpqStage3(time);
            case 103000803 -> kpqStage4(time);
            case 103000804 -> kpqStage5(time);
            default -> currentMode = Mode.WAITING; // if not in a PQ map then switch modes
        }
    }

    private void kpqStage1(int time) {
        if (getPlayer().getEventInstance().getProperty("1stageclear") != null) {
            if (getPlayer().getPosition().x == 708 && getPlayer().getPosition().y == 115) {
                doneWithPQTask = false;
                changeMap(c.getChannelServer().getMapFactory().getMap(103000801));
            } else {
                moveBot((short) 708, (short) 115, time);
            }
            return;
        }
        if (getPlayer().getEventInstance().isEventLeader(getPlayer())) {
            if (getPlayer().getItemQuantity(4001008, false) >= getPlayer().getParty().getPartyMembers().size() - 1) {
                gainItem(4001008, (short) -(getPlayer().getParty().getPartyMembers().size() - 1));
                getPlayer().getEventInstance().setProperty("1stageclear", "true");
                getPlayer().getEventInstance().showClearEffect(true);
                getPlayer().getEventInstance().linkToNextStage(1, "kpq", 103000800);
            } else {
                grind(time, 4001007, false);
            }
        } else {
            if (getPlayer().getItemQuantity(4001007, false) >= 22) { // average number of passes needed is 22, so just use that
                doneWithPQTask = true;
                gainItem(4001007, (short) -22);
                getPlayer().getMap().spawnItemDrop(getPlayer(), getPlayer(), new Item(4001008, (short) 0, (short) 1), getPlayer().getPosition(), true, true);
            } else {
                grind(time, doneWithPQTask ? -1 : 4001007, true); // stop looting tickets once finished
            }
        }
    }

    private void kpqStage2(int time) {
        if (getPlayer().getEventInstance().getProperty("2stageclear") != null) {
            if (getPlayer().getPosition().x == -227 && getPlayer().getPosition().y == 99) {
                changeMap(c.getChannelServer().getMapFactory().getMap(103000802));
            } else {
                moveBot((short) -227, (short) 99, time);
            }
            return;
        }
        if (getPlayer().getEventInstance().isEventLeader(getPlayer())) {
            if (getPlayer().getEventInstance().getPlayerCount() == 3) {
                Point nextPosition = getPQPosition(getPlayer().getEventInstance().getPlayerEventId(getPlayer().getId()),
                        kpqStage2Permutations[getPlayer().getEventInstance().getNextPQValue() % kpqStage2Permutations.length],
                        kpqStage2Positions);
                if (getPlayer().getPosition().equals(nextPosition)) {
                    if (rectangleStages(getPlayer().getEventInstance(), "stg2Property", kpqStage2Permutations, kpqStage2Rectangles)) {
                        getPlayer().getEventInstance().setProperty("2stageclear", "true");
                        getPlayer().getEventInstance().showClearEffect(true);
                        getPlayer().getEventInstance().linkToNextStage(2, "kpq", 103000801);
                    } else {
                        getPlayer().getEventInstance().showWrongEffect();
                        getPlayer().getEventInstance().advancePQValue();
                        delay = 4000;
                    }
                } else {
                    moveBot((short) nextPosition.x, (short) nextPosition.y, time);
                }
            } else {
                if (getPlayer().getPosition().equals(kpqStage2Positions[0])) {
                    if (rectangleStages(getPlayer().getEventInstance(), "stg2Property", kpqStage2Permutations, kpqStage2Rectangles)) {
                        getPlayer().getEventInstance().setProperty("2stageclear", "true");
                        getPlayer().getEventInstance().showClearEffect(true);
                        getPlayer().getEventInstance().linkToNextStage(2, "kpq", 103000801);
                    } else {
                        getPlayer().getEventInstance().showWrongEffect();
                        getPlayer().getEventInstance().advancePQValue();
                        delay = 3000;
                    }
                } else {
                    moveBot((short) kpqStage2Positions[0].x, (short) kpqStage2Positions[0].y, time);
                }
            }
        } else {
            Point nextPosition = getPQPosition(getPlayer().getEventInstance().getPlayerEventId(getPlayer().getId()),
                    kpqStage2Permutations[getPlayer().getEventInstance().getNextPQValue() % kpqStage2Permutations.length],
                    kpqStage2Positions);
            if (!getPlayer().getPosition().equals(nextPosition)) {
                moveBot((short) nextPosition.x, (short) nextPosition.y, time);
            } // else just wait
        }
    }

    private void kpqStage3(int time) {
        if (getPlayer().getEventInstance().getProperty("3stageclear") != null) {
            if (getPlayer().getPosition().x == 1341 && getPlayer().getPosition().y == -121) {
                changeMap(c.getChannelServer().getMapFactory().getMap(103000803));
            } else {
                moveBot((short) 1341, (short) -121, time);
            }
            return;
        }
        if (getPlayer().getEventInstance().isEventLeader(getPlayer())) {
            if (getPlayer().getEventInstance().getPlayerCount() == 3) {
                Point nextPosition = getPQPosition(getPlayer().getEventInstance().getPlayerEventId(getPlayer().getId()),
                        kpqStage3Permutations[getPlayer().getEventInstance().getNextPQValue() % kpqStage3Permutations.length],
                        kpqStage3Positions);
                if (getPlayer().getPosition().equals(nextPosition)) {
                    if (rectangleStages(getPlayer().getEventInstance(), "stg3Property", kpqStage3Permutations, kpqStage3Rectangles)) {
                        getPlayer().getEventInstance().setProperty("3stageclear", "true");
                        getPlayer().getEventInstance().showClearEffect(true);
                        getPlayer().getEventInstance().linkToNextStage(3, "kpq", 103000802);
                    } else {
                        getPlayer().getEventInstance().showWrongEffect();
                        getPlayer().getEventInstance().advancePQValue();
                        delay = 3000;
                    }
                } else {
                    moveBot((short) nextPosition.x, (short) nextPosition.y, time);
                }
            } else {
                if (getPlayer().getPosition().equals(kpqStage3Positions[0])) {
                    if (rectangleStages(getPlayer().getEventInstance(), "stg3Property", kpqStage3Permutations, kpqStage3Rectangles)) {
                        getPlayer().getEventInstance().setProperty("3stageclear", "true");
                        getPlayer().getEventInstance().showClearEffect(true);
                        getPlayer().getEventInstance().linkToNextStage(3, "kpq", 103000802);
                    } else {
                        getPlayer().getEventInstance().showWrongEffect();
                        getPlayer().getEventInstance().advancePQValue();
                        delay = 3000;
                    }
                } else {
                    moveBot((short) kpqStage3Positions[0].x, (short) kpqStage3Positions[0].y, time);
                }
            }
        } else {
            Point nextPosition = getPQPosition(getPlayer().getEventInstance().getPlayerEventId(getPlayer().getId()),
                    kpqStage3Permutations[getPlayer().getEventInstance().getNextPQValue() % kpqStage3Permutations.length],
                    kpqStage3Positions);
            if (!getPlayer().getPosition().equals(nextPosition)) {
                moveBot((short) nextPosition.x, (short) nextPosition.y, time);
            } // else just wait
        }
    }

    private void kpqStage4(int time) {
        if (getPlayer().getEventInstance().getProperty("4stageclear") != null) {
            if (getPlayer().getPosition().x == 1345 && getPlayer().getPosition().y == -121) {
                changeMap(c.getChannelServer().getMapFactory().getMap(103000804));
            } else {
                moveBot((short) 1345, (short) -121, time);
            }
            return;
        }
        if (getPlayer().getEventInstance().isEventLeader(getPlayer())) {
            if (getPlayer().getEventInstance().getPlayerCount() == 3) {
                Point nextPosition = getPQPosition(getPlayer().getEventInstance().getPlayerEventId(getPlayer().getId()),
                        kpqStage4Permutations[getPlayer().getEventInstance().getNextPQValue() % kpqStage4Permutations.length],
                        kpqStage4Positions);
                if (getPlayer().getPosition().equals(nextPosition)) {
                    if (rectangleStages(getPlayer().getEventInstance(), "stg4Property", kpqStage4Permutations, kpqStage4Rectangles)) {
                        getPlayer().getEventInstance().setProperty("4stageclear", "true");
                        getPlayer().getEventInstance().showClearEffect(true);
                        getPlayer().getEventInstance().linkToNextStage(4, "kpq", 103000803);
                    } else {
                        getPlayer().getEventInstance().showWrongEffect();
                        getPlayer().getEventInstance().advancePQValue();
                        delay = 2000;
                    }
                } else {
                    moveBot((short) nextPosition.x, (short) nextPosition.y, time);
                }
            } else {
                if (getPlayer().getPosition().equals(kpqStage4Positions[0])) {
                    if (rectangleStages(getPlayer().getEventInstance(), "stg4Property", kpqStage4Permutations, kpqStage4Rectangles)) {
                        getPlayer().getEventInstance().setProperty("4stageclear", "true");
                        getPlayer().getEventInstance().showClearEffect(true);
                        getPlayer().getEventInstance().linkToNextStage(4, "kpq", 103000803);
                    } else {
                        getPlayer().getEventInstance().showWrongEffect();
                        getPlayer().getEventInstance().advancePQValue();
                        delay = 3000;
                    }
                } else {
                    moveBot((short) kpqStage4Positions[0].x, (short) kpqStage4Positions[0].y, time);
                }
            }
        } else {
            Point nextPosition = getPQPosition(getPlayer().getEventInstance().getPlayerEventId(getPlayer().getId()),
                    kpqStage4Permutations[getPlayer().getEventInstance().getNextPQValue() % kpqStage4Permutations.length],
                    kpqStage4Positions);
            if (!getPlayer().getPosition().equals(nextPosition)) {
                moveBot((short) nextPosition.x, (short) nextPosition.y, time);
            } // else just wait
        }
    }

    private void kpqStage5(int time) {
        if (getPlayer().getEventInstance().getProperty("5stageclear") != null) {
            time = grind(time); // continue killing monsters and picking things up until nothing left
            if (getPlayer().getPosition().x == 378 && getPlayer().getPosition().y == -2760) {
                getPlayer().getEventInstance().giveEventReward(getPlayer()); // if they don't have space too bad
                changeMap(c.getChannelServer().getMapFactory().getMap(103000000)); // go back to kerning instead of bonus stage
                currentMode = Mode.LEAVE_PARTY;
            } else {
                moveBot((short) 378, (short) -2760, time);
            }
            return;
        }
        if (getPlayer().getEventInstance().isEventLeader(getPlayer())) {
            if (getPlayer().getItemQuantity(4001008, false) == 10) {
                gainItem(4001008, (short) -10);
                getPlayer().getEventInstance().setProperty("5stageclear", "true");
                getPlayer().getEventInstance().showClearEffect(true);
                getPlayer().getEventInstance().linkToNextStage(5, "kpq", 103000004);
                getPlayer().getEventInstance().clearPQ();
            } else {
                grind(time);
            }
        } else {
            grind(time, 4001008, false);
        }
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
