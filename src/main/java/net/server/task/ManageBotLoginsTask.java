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
import java.util.List;

public class ManageBotLoginsTask implements Runnable {

    @Override
    public void run() {
        // todo: decide which bots to login based on levels
        List<String> loggedIn = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM accounts WHERE name LIKE \"bot%\" AND loggedin = 2;");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    loggedIn.add(rs.getString("name"));
                }
            }
        } catch (SQLException se) {
            System.out.println("error - bot login task could not access database");
            return;
        }
        if (loggedIn.size() < 100) {
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

            for (Pair<String, Integer> pair : toLogin) {
                try (Connection con = DatabaseConnection.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE accountid = " + pair.getRight() + ";");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            pair.right = rs.getInt("id");
                        } else {
                            toLogin.remove(pair);
                        }
                    }
                } catch (SQLException se) {
                    System.out.println("error - bot login task could not access database");
                    toLogin.remove(pair);
                }
            }

            while (toLogin.size() - loggedIn.size() > 100) { // log in 100 random bots for now
                toLogin.remove(Randomizer.nextInt(toLogin.size()));
            }

            Server.getInstance().getBotManager().loginBots(toLogin);
        }
    }
}
