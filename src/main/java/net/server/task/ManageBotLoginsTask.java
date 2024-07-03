package net.server.task;

import net.server.Server;
import tools.DatabaseConnection;
import tools.Pair;
import tools.Randomizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ManageBotLoginsTask implements Runnable {

    @Override
    public void run() {
        List<Integer> levels = Server.getInstance().getBotManager().getLevels();
        /*List<Integer> loggedIn = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM accounts WHERE name LIKE \"bot%\" AND loggedin = 2;");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    loggedIn.add(rs.getInt("id"));
                }
            }
        } catch (SQLException se) {
            System.out.println("error - bot login task could not access database");
            return;
        }*/
        int nextCount;
        // logout bots down to 10 in each level bracket (except the brackets below 10)
        /*for (int i = 200; i > 10; i -= 5) {
            nextCount = 0;
            for (Integer level : levels) {
                if (level <= i && level > i - 5) {
                    nextCount++;
                }
            }
            if (nextCount > 10) {
                Server.getInstance().getBotManager().logoutBots(i, nextCount - 5);
            }
        }*/ // todo: logoutbots() has a deadlock so need to fix it
        // login bots up to 10 in each level bracket
        for (int i = 200; i > 10; i -= 5) {
            nextCount = 0;
            for (Integer level : levels) {
                if (level <= i && level > i - 5) {
                    nextCount++;
                }
            }
            if (nextCount < 10) {
                List<Pair<String, Integer>> toLogin = new ArrayList<>();
                try (Connection con = DatabaseConnection.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement("SELECT * FROM accounts WHERE name LIKE \"bot%\" AND loggedin = 0;");
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            toLogin.add(new Pair<>(rs.getString("name"), rs.getInt("id")));
                        }
                    }
                } catch (SQLException se) {
                    System.out.println("error - bot login task could not access database");
                    return;
                }

                Iterator<Pair<String, Integer>> it = toLogin.iterator();
                Pair<String, Integer> next;
                while (it.hasNext()) {
                    next = it.next();
                    try (Connection con = DatabaseConnection.getConnection()) {
                        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE accountid = " + next.getRight() + " AND level < " + (i + 1) + " AND level > " + (i - 5) + ";");
                             ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                next.right = rs.getInt("id");
                            } else {
                                it.remove();
                            }
                        }
                    } catch (SQLException se) {
                        System.out.println("error - bot login task could not access database");
                        it.remove();
                    }
                }

                while (toLogin.size() > 10 - nextCount) {
                    toLogin.remove(Randomizer.nextInt(toLogin.size()));
                }

                Server.getInstance().getBotManager().loginBots(toLogin);

                for (int j = 0; j < toLogin.size(); j++) {
                    levels.add(i);
                }
            }
        }
        if (levels.size() < 200) {

            List<Pair<String, Integer>> toLogin = new ArrayList<>();
            try (Connection con = DatabaseConnection.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement("SELECT * FROM accounts WHERE name LIKE \"bot%\" AND loggedin = 0;");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        toLogin.add(new Pair<>(rs.getString("name"), rs.getInt("id")));
                    }
                }
            } catch (SQLException se) {
                System.out.println("error - bot login task could not access database");
                return;
            }


            Iterator<Pair<String, Integer>> it = toLogin.iterator();
            Pair<String, Integer> next;
            while (it.hasNext()) {
                next = it.next();
                try (Connection con = DatabaseConnection.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE accountid = " + next.getRight() + " AND level < 11;");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            next.right = rs.getInt("id");
                        } else {
                            it.remove();
                        }
                    }
                } catch (SQLException se) {
                    System.out.println("error - bot login task could not access database");
                    it.remove();
                }
            }

            while (toLogin.size() - levels.size() > 200) {
                toLogin.remove(Randomizer.nextInt(toLogin.size()));
            }

            Server.getInstance().getBotManager().loginBots(toLogin);
        }
    }
}
