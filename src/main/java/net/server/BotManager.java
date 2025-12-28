package net.server;

import client.Character;
import client.CharacterBot;
import client.Client;
import net.server.world.Party;
import tools.DatabaseConnection;
import tools.Pair;
import tools.Randomizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BotManager {

    private final List<CharacterBot> bots = new ArrayList<>(), followers = new ArrayList<>();
    private final Lock lock = new ReentrantLock(true);

    public void updateBots() {
        lock.lock();
        try {
            for (CharacterBot bot : bots) {
                bot.update();
            }
        } finally {
            lock.unlock();
        }
    }

    public void updateFollowers() {
        lock.lock();
        try {
            for (CharacterBot bot : followers) {
                bot.followerUpdate();
            }
        } finally {
            lock.unlock();
        }
    }

    public void addBot(CharacterBot bot) {
        lock.lock();
        try {
            bots.add(bot);
        } finally {
            lock.unlock();
        }
    }

    public void addFollower(CharacterBot follower) {
        lock.lock();
        try {
            followers.add(follower);
        } finally {
            lock.unlock();
        }
    }

    public void toggleAutomate(Client client) {
        lock.lock();
        try {
            CharacterBot found = null;
            for (CharacterBot follower : followers) {
                if (follower.getPlayer() != null && follower.getPlayer().getName().equals(client.getPlayer().getName())) {
                    found = follower;
                    break;
                }
            }
            if (found == null) {
                CharacterBot follower = new CharacterBot();
                follower.initialize(client);
                followers.add(follower);
                client.getPlayer().message("Automate enabled.");
            } else {
                followers.remove(found);
                client.getPlayer().message("Automate disabled.");
            }
        } finally {
            lock.unlock();
        }
    }

    public void toggleFollowerLoot(Client c) {
        lock.lock();
        try {
            boolean toggle = false;
            for (CharacterBot bot : followers) {
                if (bot.getFollowing() != null && bot.getFollowing().equals(c.getPlayer())) {
                    toggle = bot.toggleFollowerLoot();
                }
            }
            if (toggle) {
                c.getPlayer().message("Follower looting enabled.");
            } else {
                c.getPlayer().message("Follower looting disabled.");
            }
        } finally {
            lock.unlock();
        }
    }

    public void loginBots(List<Pair<String, Integer>> data) {
        lock.lock();
        try {
            CharacterBot nextBot;
            for (Pair<String, Integer> t : data) {
                nextBot = new CharacterBot();
                try {
                    nextBot.login(t.getLeft(), "botpw", t.getRight(), 1);
                } catch (SQLException e) {
                    System.out.println("failed to login a bot");
                    continue;
                }
                bots.add(nextBot);
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * logout up to the specified number of bots within the level range
     * @param level the upper limit of the level range (range is 5 levels)
     * @param amount the number of bots to logout
     */
    public void logoutBots(int level, int amount) {
        lock.lock();
        try {
            List<CharacterBot> toRemove = new ArrayList<>();
            for (CharacterBot bot : bots) {
                if (bot.isLoggedIn() && bot.getPlayer().getLevel() <= level && bot.getPlayer().getLevel() > level - 5 && !bot.getMode().equals(CharacterBot.Mode.PQ)) {
                    toRemove.add(bot);
                }
            }
            while (toRemove.size() > amount) {
                toRemove.remove(Randomizer.nextInt(toRemove.size()));
            }
            for (CharacterBot bot : toRemove) {
                bot.logout();
                bots.remove(bot);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Integer> getLevels() {
        List<Integer> ret = new ArrayList<>();
        lock.lock();
        try {
            for (CharacterBot bot : bots) {
                if (bot.isLoggedIn() && bot.getPlayer() != null) {
                    ret.add(bot.getPlayer().getLevel());
                }
            }
        } finally {
            lock.unlock();
        }
        return ret;
    }

    public void createFollower(Character character, int channel) {
        /*lock.lock();
        try {

        } finally {
            lock.unlock();
        }*/
        // todo: choose random bot in level range
        character.message("This mode is not yet implemented");
    }

    public void createFollower(Character character, String job, int channel) {
        // todo: find a bot with the specified job in level range, if none exist then call createFollower(character);
        character.message("This mode is not yet implemented");
    }

    public void createFollower(Character character, String login, String password, String characterName, int channel) {
        lock.lock();;
        try {
            int accountId;
            try (Connection con = DatabaseConnection.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement("SELECT * FROM accounts WHERE name = \"" + login + "\";");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (rs.getInt("loggedin") == 0) {
                            accountId = rs.getInt("id");
                        } else {
                            character.message("The specified account is already logged in.");
                            return;
                        }
                    } else {
                        character.message("The specified account does not exist.");
                        return;
                    }
                }
            } catch (SQLException se) {
                System.out.println("error - add follower command could not access database");
                character.message("There was an error trying to access the database.");
                return;
            }
            int charId;
            try (Connection con = DatabaseConnection.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE accountid = " + accountId + " AND name = \"" + characterName + "\";");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        charId = rs.getInt("id");
                    } else {
                        character.message("The specified character was not found.");
                        return;
                    }
                }
            } catch (SQLException se) {
                System.out.println("error - add follower command could not access database");
                character.message("There was an error trying to access the database.");
                return;
            }
            CharacterBot follower = new CharacterBot();
            try {
                follower.login(login, password, charId, channel);
            } catch (SQLException e) {
                character.message("There was an error trying to log in the follower.");
                return;
            }
            follower.setFollowing(character);
            followers.add(follower);
        } finally {
            lock.unlock();
        }
    }

    public void dismissFollower(Character character, String name) {
        lock.lock();
        try {
            CharacterBot toDismiss = null;
            for (CharacterBot bot : followers) {
                if (bot.getFollowing() != null && bot.getFollowing().equals(character) && bot.getPlayer().getName().equals(name)) {
                    toDismiss = bot;
                    break;
                }
            }
            if (toDismiss != null) {
                toDismiss.logout();
                followers.remove(toDismiss);
            } else {
                character.message("That name does not match any of your followers.");
            }
        } finally {
            lock.unlock();
        }
    }

    public void dismissFollowers(Character character) {
        lock.lock();
        try {
            List<CharacterBot> toRemove = new ArrayList<>();
            for (CharacterBot bot : followers) {
                if (bot.getFollowing() != null && bot.getFollowing().equals(character)) {
                    bot.logout();
                    toRemove.add(bot);
                }
            }
            for (CharacterBot bot : toRemove) {
                followers.remove(bot);
            }
        } finally {
            lock.unlock();
        }
    }

    public int getNumFollowers(Character character) {
        lock.lock();
        try {
            int ret = 0;
            for (CharacterBot bot : followers) {
                if (bot.getFollowing().equals(character)) {
                    ret++;
                }
            }
            return ret;
        } finally {
            lock.unlock();
        }
    }

    public void partyCommand(Character character) {
        lock.lock();
        try {
            for (CharacterBot bot : followers) {
                if (character.getParty().getMembers().size() > 5) {
                    return;
                }
                if (bot.getFollowing().equals(character)) {
                    Party.joinParty(bot.getPlayer(), character.getPartyId(), false);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public Party createPQParty(int partySize, int minLevel, int maxLevel, int mapId) {
        lock.lock();
        try {
            Party ret;
            List<CharacterBot> botsInLevelRange = new ArrayList<>();
            for (CharacterBot bot : bots) {
                if (!bot.getMode().equals(CharacterBot.Mode.PQ) && bot.getPlayer().getLevel() >= minLevel && bot.getPlayer().getLevel() <= maxLevel && bot.getPlayer().getParty() == null) {
                    botsInLevelRange.add(bot);
                }
            }
            if (botsInLevelRange.size() < partySize) {
                System.out.println("not enough online bots to create party");
                return null;
            }
            Collections.shuffle(botsInLevelRange); // so different bots get a chance
            if (!Party.createParty(botsInLevelRange.getFirst().getPlayer(), false)) {
                System.out.println("party failed to be created");
                return null;
            }
            ret = botsInLevelRange.getFirst().getPlayer().getParty();
            for (int i = 1; i < partySize; i++) {
                Party.joinParty(botsInLevelRange.get(i).getPlayer(), ret.getId(), false);
            }
            if (ret.getMembers().size() == partySize) {
                for (int i = 0; i < partySize; i++) {
                    botsInLevelRange.get(i).changeMap(botsInLevelRange.get(i).getPlayer().getClient().getChannelServer().getMapFactory().getMap(mapId));
                    botsInLevelRange.get(i).setMode(CharacterBot.Mode.PQ);
                }
            }
            return ret;
        } finally {
            lock.unlock();
        }
    }

    public void createFollowerPQParty(int partySize, int minLevel, int maxLevel, Character humanPlayer, int channel) {
        lock.lock();
        List<Pair<String, String>> botInfo;
        try {
            if (!Party.createParty(humanPlayer, false)) {
                humanPlayer.message("party failed to be created");
                return;
            }
            try (Connection con = DatabaseConnection.getConnection()) {
                List<Integer> botIds = new ArrayList<>();
                List<String> accountNames = new ArrayList<>();
                botInfo = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement("SELECT id, name FROM accounts WHERE loggedin = 0;");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getString("name").contains("bot")) {
                            botIds.add(rs.getInt("id"));
                            accountNames.add(rs.getString("name"));
                        }
                    }
                }
                if (botIds.size() < partySize - 1) {
                    humanPlayer.message("not enough bots available to create party");
                    return;
                }
                try (PreparedStatement ps = con.prepareStatement("SELECT accountid, `name` FROM characters WHERE level BETWEEN " + minLevel + " AND " + maxLevel + ";");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (botIds.contains(rs.getInt("accountid"))) {
                            int location = botIds.indexOf(rs.getInt("accountid"));
                            botInfo.add(new Pair<>(accountNames.get(location), rs.getString("name")));
                        }
                    }
                }
                if (botInfo.size() < partySize - 1) {
                    humanPlayer.message("not enough bots available to create party");
                    return;
                }
            } catch (SQLException se) {
                System.out.println("error - add follower command could not access database");
                humanPlayer.message("There was an error trying to access the database.");
                return;
            }
            Collections.shuffle(botInfo); // so different bots get a chance
            for (int i = 0; i < partySize - 1; i++) {
                createFollower(humanPlayer, botInfo.get(i).left, "botpw", botInfo.get(i).right, channel);
            }
            partyCommand(humanPlayer);
        } finally {
            lock.unlock();
        }
    }

    public void logoutAllBots() {
        lock.lock();
        try {
            for (CharacterBot bot : bots) {
                bot.setLoggedOut();
            }
            for (CharacterBot bot : followers) {
                bot.setLoggedOut();
            }
        } finally {
            lock.unlock();
        }
    }
}