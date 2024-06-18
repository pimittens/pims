package client;

import scripting.event.EventInstanceManager;
import tools.Randomizer;

import java.awt.*;
import java.util.List;

public class PQConstants {

    public static final int[] pqMaps = {103000800}; // first map for each pq
    // for positions the party leader should stand at position 0 if there too many party members
    public static Point[] kpqStage2Positions = {new Point(-623, 118), new Point(-753, -11),
            new Point(-719, -254), new Point(-584, -254), new Point(-481, -11)},
            kpqStage3Positions = {new Point(987, -15), new Point(669, -135), new Point(855, -75),
            new Point(1036, -135), new Point(950, -195), new Point(762, -195)},
            kpqStage4Positions = {new Point(1069, -75), new Point(926, -234), new Point(894, -182),
            new Point(966, -182), new Point(862, -130), new Point(927, -130), new Point(994, -130)};
    public static int[][] kpqStage2Permutations = {{0, 1, 1, 1}, {1, 0, 1, 1}, {1, 1, 0, 1}, {1, 1, 1, 0}},
            kpqStage3Permutations = {{0, 0, 1, 1, 1}, {0, 1, 0, 1, 1}, {0, 1, 1, 0, 1}, {0, 1, 1, 1, 0},
                    {1, 0, 0, 1, 1}, {1, 0, 1, 0, 1}, {1, 0, 1, 1, 0}, {1, 1, 0, 0, 1}, {1, 1, 0, 1, 0},
                    {1, 1, 1, 0, 0}},
            kpqStage4Permutations = {{0, 0, 0, 1, 1, 1}, {0, 0, 1, 0, 1, 1}, {0, 0, 1, 1, 0, 1}, {0, 0, 1, 1, 1, 0},
                    {0, 1, 0, 0, 1, 1}, {0, 1, 0, 1, 0, 1}, {0, 1, 0, 1, 1, 0}, {0, 1, 1, 0, 0, 1}, {0, 1, 1, 0, 1, 0},
                    {0, 1, 1, 1, 0, 0}, {1, 0, 0, 0, 1, 1}, {1, 0, 0, 1, 0, 1}, {1, 0, 0, 1, 1, 0}, {1, 0, 1, 0, 0, 1},
                    {1, 0, 1, 0, 1, 0}, {1, 0, 1, 1, 0, 0}, {1, 1, 0, 0, 0, 1}, {1, 1, 0, 0, 1, 0}, {1, 1, 0, 1, 0, 0},
                    {1, 1, 1, 0, 0, 0}};
    public static Rectangle[] kpqStage2Rectangles = {new Rectangle(-755, -132, 4, 218),
            new Rectangle(-721, -340, 4, 166), new Rectangle(-586, -326, 4, 150),
            new Rectangle(-483, -181, 4, 222)},
            kpqStage3Rectangles = {new Rectangle(608, -180, 140, 50),
                    new Rectangle(791, -117, 140, 45), new Rectangle(958, -180, 140, 50),
                    new Rectangle(876, -238, 140, 45), new Rectangle(702, -238, 140, 45)},
            kpqStage4Rectangles = {new Rectangle(910, -236, 35, 5), new Rectangle(877, -184, 35, 5),
                    new Rectangle(946, -184, 35, 5), new Rectangle(845, -132, 35, 5),
                    new Rectangle(910, -132, 35, 5), new Rectangle(981, -132, 35, 5)};

    public static boolean isPQMap(int mapId) {
        for (int i : pqMaps) {
            if (mapId == i) {
                return true;
            }
        }
        return false;
    }



    public static Point getPQPosition(int playerEventId, int[] permutation, Point[] positions) {
        int count = 0;
        for (int i = 0; i < permutation.length; i++) {
            if (permutation[i] == 1) {
                count++;
            }
            if (count == playerEventId) {
                return positions[i + 1];
            }
        }
        return positions[0];
    }

    public static boolean rectangleStages(EventInstanceManager eim, String property, int[][] areaCombos, Rectangle[] areaRects) {
        String c = eim.getProperty(property);
        int combo;
        if (c == null) {
            combo = Randomizer.nextInt(areaCombos.length);
            eim.setProperty(property, combo);
        } else {
            combo = Integer.parseInt(c);
        }

        // get player placement
        List<Character> players = eim.getPlayers();
        int[] playerPlacement = {0, 0, 0, 0, 0, 0};

        for (var i = 0; i < eim.getPlayerCount(); i++) {
            for (var j = 0; j < areaRects.length; j++) {
                if (areaRects[j].contains(players.get(i).getPosition())) {
                    playerPlacement[j] += 1;
                    break;
                }
            }
        }

        var curCombo = areaCombos[combo];
        boolean accept = true;
        for (var j = 0; j < curCombo.length; j++) {
            if (curCombo[j] != playerPlacement[j]) {
                accept = false;
                break;
            }
        }

        return accept;
    }
}
