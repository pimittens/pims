package net.server;

import client.Character;
import client.CharacterBot;
import net.server.world.Party;
import tools.DatabaseConnection;
import tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

    public void loginBots(List<Pair<String, Integer>> data) {
        lock.lock();
        try {
            CharacterBot nextBot;
            for (Pair<String, Integer> t : data) {
                nextBot = new CharacterBot();
                try {
                    nextBot.login(t.getLeft(), "botpw", t.getRight());
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

    public void createFollower(Character character) {
        // todo: choose random bot in level range
        character.message("This mode is not yet implemented");
    }

    public void createFollower(Character character, String job) {
        // todo: find a bot with the specified job in level range, if none exist then call createFollower(character);
        character.message("This mode is not yet implemented");
    }

    public void createFollower(Character character, String login, String password, String characterName) {
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
                follower.login(login, password, charId);
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
            for (CharacterBot bot : followers) {
                if (bot.getFollowing().equals(character) && bot.getPlayer().getName().equals(name)) {
                    bot.logout();
                    followers.remove(bot);
                    return;
                }
            }
            character.message("That name does not match any of your followers.");
        } finally {
            lock.unlock();
        }
    }

    public void dismissFollowers(Character character) {
        lock.lock();
        try {
            List<CharacterBot> toRemove = new ArrayList<>();
            for (CharacterBot bot : followers) {
                if (bot.getFollowing().equals(character)) {
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
}