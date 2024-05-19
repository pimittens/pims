package server;

import client.Character;
import client.DefaultDates;
import client.Job;
import client.Skill;
import client.inventory.Inventory;
import client.inventory.Item;
import client.inventory.ItemFactory;
import config.YamlConfig;
import constants.id.MapId;
import net.server.handlers.login.LoginPasswordHandler;
import tools.BCrypt;
import tools.DatabaseConnection;
import tools.Pair;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Map;

public class BotFactory {


    public void createBots() { // create bots and insert them into database

    }

    private int createAccount(String accountName) {
        int accountid;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO accounts (name, password, birthday, tempban) VALUES (?, ?, ?, ?);", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, accountName);
            ps.setString(2, YamlConfig.config.server.BCRYPT_MIGRATION ? BCrypt.hashpw("botpw", BCrypt.gensalt(12)) : LoginPasswordHandler.hashpwSHA512("botpw"));
            ps.setDate(3, Date.valueOf(DefaultDates.getBirthday()));
            ps.setTimestamp(4, Timestamp.valueOf(DefaultDates.getTempban()));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                accountid = rs.getInt(1);
            }
        } catch (SQLException | NoSuchAlgorithmException | UnsupportedEncodingException e) {
            accountid = -1;
            e.printStackTrace();
        }
        return accountid;
    }

    private boolean createBot(int accountid, String name, int gender, int skin, int hair, int face) {
        int id;

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

            try {
                // Character info
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO characters (str, dex, luk, `int`, gm, skincolor, gender, job, hair, face, map, meso, spawnpoint, accountid, name, world, hp, mp, maxhp, maxmp, level, ap, sp, equipslots, useslots, setupslots, etcslots) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, 12);
                    ps.setInt(2, 5);
                    ps.setInt(3, 4);
                    ps.setInt(4, 4);
                    ps.setInt(5, 0);
                    ps.setInt(6, skin);
                    ps.setInt(7, gender);
                    ps.setInt(8, Job.BEGINNER.getId());
                    ps.setInt(9, hair);
                    ps.setInt(10, face);
                    ps.setInt(11, MapId.MUSHROOM_TOWN);
                    ps.setInt(12, 0);
                    ps.setInt(13, 0);
                    ps.setInt(14, accountid);
                    ps.setString(15, name);
                    ps.setInt(16, 0);
                    ps.setInt(17, 50);
                    ps.setInt(18, 5);
                    ps.setInt(19, 50);
                    ps.setInt(20, 5);
                    ps.setInt(21, 1);
                    ps.setInt(22, 0);
                    ps.setString(23, "0,0,0,0,0,0,0,0,0,0");
                    ps.setInt(24, 96);
                    ps.setInt(25, 96);
                    ps.setInt(26, 96);
                    ps.setInt(27, 96);

                    int updateRows = ps.executeUpdate();
                    if (updateRows < 1) {
                        return false;
                    }

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            id = rs.getInt(1);
                        } else {
                            return false;
                        }
                    }
                }

                itemsWithType = new ArrayList<>();
                for (Inventory iv : inventory) {
                    for (Item item : iv.list()) {
                        itemsWithType.add(new Pair<>(item, iv.getType()));
                    }
                }

                ItemFactory.INVENTORY.saveItems(itemsWithType, id, con);

                if (!skills.isEmpty()) {
                    // Skills
                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setInt(1, id);
                        for (Map.Entry<Skill, Character.SkillEntry> skill : skills.entrySet()) {
                            ps.setInt(2, skill.getKey().getId());
                            ps.setInt(3, skill.getValue().skillevel);
                            ps.setInt(4, skill.getValue().masterlevel);
                            ps.setLong(5, skill.getValue().expiration);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                con.commit();
                return true;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                con.setAutoCommit(true);
            }
        } catch (Throwable t) {
            System.out.println("Error creating chr " + name + ", level: " + 1 + ", job: " + Job.BEGINNER.getId());
        }
        return false;
    }
}
