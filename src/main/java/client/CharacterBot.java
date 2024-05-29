package client;

import client.inventory.InventoryType;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
import client.processor.stat.AssignSPProcessor;
import constants.inventory.ItemConstants;
import constants.skills.*;
import net.PacketProcessor;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.StatEffect;
import server.life.Monster;
import server.life.MonsterDropEntry;
import server.maps.*;
import tools.PacketCreator;
import tools.Randomizer;

import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (loggedOut || true) { // temporarily disabled bot updates for testing purposes
            return;
        }
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
        int time = (int) (System.currentTimeMillis() - previousAction - delay); // amount of time for actions
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
        if (c.getPlayer().getPosition().x == targetX && c.getPlayer().getPosition().y == targetY) {
            c.handlePacket(PacketCreator.createPlayerMovementPacket((short) (c.getPlayer().getPosition().x), (short) (c.getPlayer().getPosition().y), (byte) (facingLeft ? 5 : 4), (short) 10), (short) 41);
        }
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
                if (c.getPlayer().getSkillLevel(Warrior.POWER_STRIKE) > 0) {
                    singleTargetAttack = Warrior.POWER_STRIKE;
                }
                if (c.getPlayer().getSkillLevel(Warrior.SLASH_BLAST) > 0) {
                    mobAttack = Warrior.SLASH_BLAST;
                }
                break;
            case CRUSADER:
            case WHITEKNIGHT:
            case DRAGONKNIGHT:
            case HERO:
            case PALADIN:
            case DARKKNIGHT:
                break; // todo
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
            case IL_WIZARD:
            case CLERIC:
            case FP_MAGE:
            case IL_MAGE:
            case PRIEST:
            case FP_ARCHMAGE:
            case IL_ARCHMAGE:
            case BISHOP:
                break; // todo
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
            case CROSSBOWMAN:
            case RANGER:
            case SNIPER:
            case BOWMASTER:
            case MARKSMAN:
                break; // todo
            case THIEF:
                if (c.getPlayer().getSkillLevel(Rogue.LUCKY_SEVEN) > 0) {
                    singleTargetAttack = Rogue.LUCKY_SEVEN;
                    mobAttack = Rogue.LUCKY_SEVEN;
                }
                break;
            case ASSASSIN:
            case BANDIT:
            case HERMIT:
            case CHIEFBANDIT:
            case NIGHTLORD:
            case SHADOWER:
                break; // todo
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
            case GUNSLINGER:
            case MARAUDER:
            case OUTLAW:
            case BUCCANEER:
            case CORSAIR:
                break; // todo
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
            case HERO: // todo
            case CRUSADER:
            case FIGHTER:
                buffSkills.add(Warrior.IRON_BODY);
                if (c.getPlayer().getSkillLevel(Fighter.RAGE) > 0) {
                    buffSkills.add(Fighter.RAGE);
                }
                if (c.getPlayer().getSkillLevel(Fighter.POWER_GUARD) > 9) { // don't use if it will last less than 30 seconds
                    buffSkills.add(Fighter.POWER_GUARD);
                }
                if (c.getPlayer().getSkillLevel(Fighter.SWORD_BOOSTER) > 2) {
                    buffSkills.add(Fighter.SWORD_BOOSTER);
                }
                if (c.getPlayer().getSkillLevel(Fighter.AXE_BOOSTER) > 2) {
                    buffSkills.add(Fighter.AXE_BOOSTER);
                }
                break;
            case PALADIN: // todo
            case WHITEKNIGHT:
            case PAGE:
                buffSkills.add(Warrior.IRON_BODY);
                if (c.getPlayer().getSkillLevel(Page.POWER_GUARD) > 9) {
                    buffSkills.add(Page.POWER_GUARD);
                }
                if (c.getPlayer().getSkillLevel(Page.SWORD_BOOSTER) > 2) {
                    buffSkills.add(Page.SWORD_BOOSTER);
                }
                if (c.getPlayer().getSkillLevel(Page.BW_BOOSTER) > 2) {
                    buffSkills.add(Page.BW_BOOSTER);
                }
            case DARKKNIGHT: // todo
            case DRAGONKNIGHT:
            case SPEARMAN:
                buffSkills.add(Warrior.IRON_BODY);
                if (c.getPlayer().getSkillLevel(Spearman.SPEAR_BOOSTER) > 2) {
                    buffSkills.add(Spearman.SPEAR_BOOSTER);
                }
                if (c.getPlayer().getSkillLevel(Spearman.POLEARM_BOOSTER) > 2) {
                    buffSkills.add(Spearman.POLEARM_BOOSTER);
                }
                if (c.getPlayer().getSkillLevel(Spearman.IRON_WILL) > 1) {
                    buffSkills.add(Spearman.IRON_WILL);
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
            case FP_MAGE:
            case FP_WIZARD:
            case IL_ARCHMAGE:
            case IL_MAGE:
            case IL_WIZARD:
            case BISHOP:
            case PRIEST:
            case CLERIC:
                break; // todo
            case BOWMAN:
                if (c.getPlayer().getSkillLevel(Archer.FOCUS) > 0) {
                    buffSkills.add(Archer.FOCUS);
                }
                break;
            case BOWMASTER:
            case RANGER:
            case HUNTER:
            case MARKSMAN:
            case SNIPER:
            case CROSSBOWMAN:
                break; // todo
            case NIGHTLORD:
            case HERMIT:
            case ASSASSIN:
            case SHADOWER:
            case CHIEFBANDIT:
            case BANDIT:
                break; // todo
            case BUCCANEER:
            case MARAUDER:
            case BRAWLER:
            case CORSAIR:
            case OUTLAW:
            case GUNSLINGER:
                break; // todo
        }
    }

    private void useBuff(int skillId) {
        c.handlePacket(PacketCreator.createUseBuffPacket(skillId, c.getPlayer().getSkillLevel(skillId)), (short) 91);
        c.getPlayer().setMp(c.getPlayer().getMaxMp()); // todo: accurate potion usage, for now just refresh their mp after using a buff
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
            delay = System.currentTimeMillis() + 500 - time; // todo: accurate delay
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
                    if (!targets.contains(m) && c.getPlayer().getPosition().distance(m.getPosition()) < 1000) { // todo: accurate range
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
        // todo: stance, direction, ranged direction, charge, display, speed, position
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = skillId;
        attack.skilllevel = c.getPlayer().getSkillLevel(skillId);
        attack.numAttacked = targets.size();
        attack.numDamage = Math.max(effect.getAttackCount(), effect.getBulletCount());
        attack.numAttackedAndDamage = attack.numAttacked * 16 + attack.numDamage;
        attack.allDamage = new HashMap<>();
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
        delay = System.currentTimeMillis() + 500 - time; // todo: accurate delay
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
        if (Randomizer.nextDouble() < calculateHitchance(leveldelta, playerAccuracy, monsterAvoid)) {
            // todo: criticals
            c.handlePacket(PacketCreator.createRegularAttackPacket(targetMonster.getObjectId(), calcRegularAttackDamage(leveldelta), facingLeft), (short) 44);
        } else {
            c.handlePacket(PacketCreator.createRegularAttackPacket(targetMonster.getObjectId(), 0, facingLeft), (short) 44);
        }
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
            if (!hasTargetItem || ((MapItem) targetItem).isPickedUp()) {
                if (!c.getPlayer().getMap().getItems().isEmpty()) {
                    for (MapObject it : c.getPlayer().getMap().getItems()) {
                        if (!((MapItem) it).isPickedUp() && ((MapItem) it).canBePickedBy(c.getPlayer()) && InventoryManipulator.checkSpace(c, ((MapItem) it).getItemId(), ((MapItem) it).getItem().getQuantity(), ((MapItem) it).getItem().getOwner())) {
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
                if (c.getPlayer().getPosition().distance(targetMonster.getPosition()) < 500) { // todo: accurate range
                    attack(time);
                    time = 0;
                    didAction = true;
                } else {
                    time = moveBot((short) targetMonster.getPosition().x, (short) targetMonster.getPosition().y, time);
                    didAction = true;
                }
            }
        }
        return didAction;
    }

    private void levelup() {
        assignSP(); // do this before doing job advance
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
        currentMode = Mode.WAITING; // pick new map
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
        return jobId / 100 == 3 || (jobId / 100 == 4 && jobId % 100 / 10 == 1) || (jobId / 100 == 5 && jobId % 100 / 10 == 2);
    }

    public Character getFollowing() {
        return following;
    }

    public static void putSkillOrdersAndDelayTimes() {
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
        skillOrders.putIfAbsent(Job.FIGHTER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.PAGE, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.SPEARMAN, new int[][]{}); // todo
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
        skillOrders.putIfAbsent(Job.FP_WIZARD, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.IL_WIZARD, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.CLERIC, new int[][]{}); // todo
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
        skillOrders.putIfAbsent(Job.HUNTER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.CROSSBOWMAN, new int[][]{}); // todo
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
        skillOrders.putIfAbsent(Job.ASSASSIN, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.BANDIT, new int[][]{}); // todo
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
        skillOrders.putIfAbsent(Job.BRAWLER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.GUNSLINGER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.MARAUDER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.OUTLAW, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.BUCCANEER, new int[][]{}); // todo
        skillOrders.putIfAbsent(Job.CORSAIR, new int[][]{}); // todo

        // todo: delay times, decide what to do for different speeds
        // skillDelayTimes.putIfAbsent(-1, );
    }
}
