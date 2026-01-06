package client;

import scripting.event.EventInstanceManager;
import server.maps.MapleMap;
import tools.Randomizer;

import java.awt.*;
import java.util.List;

public class PQConstants {

    // first map for each pq, need to include all the lmpq ones since it puts you in a random room
    public static final int[] pqMaps = {103000800, 922010100, 809050000, 809050001, 809050002, 809050003, 809050004,
            809050005, 809050006, 809050007, 809050008, 809050009, 809050010, 809050011, 809050012, 809050013, 809050014, 809050015};
    // for positions the party leader should stand at position 0 if there are too many party members
    public static Point[] kpqStage2Positions = {new Point(-623, 118), new Point(-753, -11),
            new Point(-719, -254), new Point(-584, -254), new Point(-481, -11)},
            kpqStage3Positions = {new Point(987, -15), new Point(669, -135), new Point(855, -75),
            new Point(1036, -135), new Point(950, -195), new Point(762, -195)},
            kpqStage4Positions = {new Point(1069, -75), new Point(926, -234), new Point(894, -182),
            new Point(966, -182), new Point(862, -130), new Point(927, -130), new Point(994, -130)},
            lpqStage6Positions = {new Point(225, -554), new Point(-52, -723), new Point(64, -913),
                    new Point(-138, -1090), new Point(144, -1267), new Point(-209, -1437),
                    new Point(60, -1601), new Point(-235, -1769), new Point(138, -1934),
                    new Point(-224, -2127), new Point(224, -2304), new Point(-136, -2476),
                    new Point(60, -2657), new Point(-215, -2822), new Point(222, -2992),
                    new Point(-1, -3162)},
            lpqStage8Positions = {new Point(95, -160), new Point(-219, -223), new Point(-153, -223),
                    new Point(-217, -184), new Point(-151, -184), new Point(-88, -184),
                    new Point(-221, -145), new Point(-152, -145), new Point(-84, -145),
                    new Point(-14, -145)};
    public static int[][] kpqStage2Permutations = {{0, 1, 1, 1}, {1, 0, 1, 1}, {1, 1, 0, 1}, {1, 1, 1, 0}},
            kpqStage3Permutations = {{0, 0, 1, 1, 1}, {0, 1, 0, 1, 1}, {0, 1, 1, 0, 1}, {0, 1, 1, 1, 0},
                    {1, 0, 0, 1, 1}, {1, 0, 1, 0, 1}, {1, 0, 1, 1, 0}, {1, 1, 0, 0, 1}, {1, 1, 0, 1, 0},
                    {1, 1, 1, 0, 0}},
            kpqStage4Permutations = {{0, 0, 0, 1, 1, 1}, {0, 0, 1, 0, 1, 1}, {0, 0, 1, 1, 0, 1}, {0, 0, 1, 1, 1, 0},
                    {0, 1, 0, 0, 1, 1}, {0, 1, 0, 1, 0, 1}, {0, 1, 0, 1, 1, 0}, {0, 1, 1, 0, 0, 1}, {0, 1, 1, 0, 1, 0},
                    {0, 1, 1, 1, 0, 0}, {1, 0, 0, 0, 1, 1}, {1, 0, 0, 1, 0, 1}, {1, 0, 0, 1, 1, 0}, {1, 0, 1, 0, 0, 1},
                    {1, 0, 1, 0, 1, 0}, {1, 0, 1, 1, 0, 0}, {1, 1, 0, 0, 0, 1}, {1, 1, 0, 0, 1, 0}, {1, 1, 0, 1, 0, 0},
                    {1, 1, 1, 0, 0, 0}},
            lpqStage8Permutations = {{1, 1, 1, 1, 1, 0, 0, 0, 0}, {0, 1, 1, 1, 1, 1, 0, 0, 0}, {1, 0, 1, 1, 1, 1, 0, 0, 0},
                    {1, 1, 0, 1, 1, 1, 0, 0, 0}, {1, 1, 1, 0, 1, 1, 0, 0, 0}, {1, 1, 1, 1, 0, 1, 0, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0}, {1, 0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 0, 1, 1, 1, 1, 0, 0},
                    {1, 0, 1, 0, 1, 1, 1, 0, 0}, {0, 1, 1, 0, 1, 1, 1, 0, 0}, {1, 1, 0, 0, 1, 1, 1, 0, 0},
                    {1, 1, 1, 0, 0, 1, 1, 0, 0}, {0, 1, 1, 1, 0, 1, 1, 0, 0}, {1, 0, 1, 1, 0, 1, 1, 0, 0},
                    {1, 1, 0, 1, 0, 1, 1, 0, 0}, {1, 0, 1, 1, 1, 0, 1, 0, 0}, {0, 1, 1, 1, 1, 0, 1, 0, 0},
                    {1, 1, 0, 1, 1, 0, 1, 0, 0}, {1, 1, 1, 0, 1, 0, 1, 0, 0}, {1, 1, 1, 1, 0, 0, 1, 0, 0},
                    {0, 1, 1, 1, 1, 0, 0, 1, 0}, {1, 0, 1, 1, 1, 0, 0, 1, 0}, {1, 1, 0, 1, 1, 0, 0, 1, 0},
                    {1, 1, 1, 0, 1, 0, 0, 1, 0}, {1, 1, 1, 1, 0, 0, 0, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 1, 0},
                    {1, 0, 0, 1, 1, 1, 0, 1, 0}, {0, 1, 0, 1, 1, 1, 0, 1, 0}, {1, 0, 1, 0, 1, 1, 0, 1, 0},
                    {0, 1, 1, 0, 1, 1, 0, 1, 0}, {1, 1, 0, 0, 1, 1, 0, 1, 0}, {1, 1, 1, 0, 0, 1, 0, 1, 0},
                    {0, 1, 1, 1, 0, 1, 0, 1, 0}, {1, 0, 1, 1, 0, 1, 0, 1, 0}, {1, 1, 0, 1, 0, 1, 0, 1, 0},
                    {0, 0, 0, 1, 1, 1, 1, 1, 0}, {1, 0, 0, 0, 1, 1, 1, 1, 0}, {0, 1, 0, 0, 1, 1, 1, 1, 0},
                    {0, 0, 1, 0, 1, 1, 1, 1, 0}, {1, 1, 0, 0, 0, 1, 1, 1, 0}, {0, 1, 1, 0, 0, 1, 1, 1, 0},
                    {1, 0, 1, 0, 0, 1, 1, 1, 0}, {0, 1, 0, 1, 0, 1, 1, 1, 0}, {1, 0, 0, 1, 0, 1, 1, 1, 0},
                    {0, 0, 1, 1, 0, 1, 1, 1, 0}, {1, 0, 0, 1, 1, 0, 1, 1, 0}, {0, 1, 0, 1, 1, 0, 1, 1, 0},
                    {0, 0, 1, 1, 1, 0, 1, 1, 0}, {1, 1, 0, 0, 1, 0, 1, 1, 0}, {0, 1, 1, 0, 1, 0, 1, 1, 0},
                    {1, 0, 1, 0, 1, 0, 1, 1, 0}, {1, 1, 1, 0, 0, 0, 1, 1, 0}, {0, 1, 1, 1, 0, 0, 1, 1, 0},
                    {1, 0, 1, 1, 0, 0, 1, 1, 0}, {1, 1, 0, 1, 0, 0, 1, 1, 0}, {0, 0, 0, 0, 1, 1, 1, 1, 1},
                    {1, 0, 0, 0, 0, 1, 1, 1, 1}, {0, 1, 0, 0, 0, 1, 1, 1, 1}, {0, 0, 1, 0, 0, 1, 1, 1, 1},
                    {0, 0, 0, 1, 0, 1, 1, 1, 1}, {1, 0, 0, 0, 1, 0, 1, 1, 1}, {0, 1, 0, 0, 1, 0, 1, 1, 1},
                    {0, 0, 1, 0, 1, 0, 1, 1, 1}, {0, 0, 0, 1, 1, 0, 1, 1, 1}, {1, 1, 0, 0, 0, 0, 1, 1, 1},
                    {0, 1, 1, 0, 0, 0, 1, 1, 1}, {1, 0, 1, 0, 0, 0, 1, 1, 1}, {0, 1, 0, 1, 0, 0, 1, 1, 1},
                    {1, 0, 0, 1, 0, 0, 1, 1, 1}, {0, 0, 1, 1, 0, 0, 1, 1, 1}, {1, 1, 1, 0, 0, 0, 0, 1, 1},
                    {0, 1, 1, 1, 0, 0, 0, 1, 1}, {1, 0, 1, 1, 0, 0, 0, 1, 1}, {1, 1, 0, 1, 0, 0, 0, 1, 1},
                    {0, 0, 1, 1, 1, 0, 0, 1, 1}, {1, 0, 0, 1, 1, 0, 0, 1, 1}, {0, 1, 0, 1, 1, 0, 0, 1, 1},
                    {1, 0, 1, 0, 1, 0, 0, 1, 1}, {0, 1, 1, 0, 1, 0, 0, 1, 1}, {1, 1, 0, 0, 1, 0, 0, 1, 1},
                    {0, 1, 1, 0, 0, 1, 0, 1, 1}, {1, 0, 1, 0, 0, 1, 0, 1, 1}, {1, 1, 0, 0, 0, 1, 0, 1, 1},
                    {0, 0, 1, 1, 0, 1, 0, 1, 1}, {1, 0, 0, 1, 0, 1, 0, 1, 1}, {0, 1, 0, 1, 0, 1, 0, 1, 1},
                    {0, 0, 0, 1, 1, 1, 0, 1, 1}, {1, 0, 0, 0, 1, 1, 0, 1, 1}, {0, 1, 0, 0, 1, 1, 0, 1, 1},
                    {0, 0, 1, 0, 1, 1, 0, 1, 1}, {1, 0, 0, 0, 1, 1, 1, 0, 1}, {0, 1, 0, 0, 1, 1, 1, 0, 1},
                    {0, 0, 1, 0, 1, 1, 1, 0, 1}, {0, 0, 0, 1, 1, 1, 1, 0, 1}, {1, 1, 0, 0, 0, 1, 1, 0, 1},
                    {0, 1, 1, 0, 0, 1, 1, 0, 1}, {1, 0, 1, 0, 0, 1, 1, 0, 1}, {0, 1, 0, 1, 0, 1, 1, 0, 1},
                    {1, 0, 0, 1, 0, 1, 1, 0, 1}, {0, 0, 1, 1, 0, 1, 1, 0, 1}, {1, 1, 0, 0, 1, 0, 1, 0, 1},
                    {0, 1, 1, 0, 1, 0, 1, 0, 1}, {1, 0, 1, 0, 1, 0, 1, 0, 1}, {0, 1, 0, 1, 1, 0, 1, 0, 1},
                    {1, 0, 0, 1, 1, 0, 1, 0, 1}, {0, 0, 1, 1, 1, 0, 1, 0, 1}, {1, 1, 1, 0, 0, 0, 1, 0, 1},
                    {0, 1, 1, 1, 0, 0, 1, 0, 1}, {1, 0, 1, 1, 0, 0, 1, 0, 1}, {1, 1, 0, 1, 0, 0, 1, 0, 1},
                    {1, 1, 1, 1, 0, 0, 0, 0, 1}, {0, 1, 1, 1, 1, 0, 0, 0, 1}, {1, 0, 1, 1, 1, 0, 0, 0, 1},
                    {1, 1, 0, 1, 1, 0, 0, 0, 1}, {1, 1, 1, 0, 1, 0, 0, 0, 1}, {0, 1, 1, 1, 0, 1, 0, 0, 1},
                    {1, 0, 1, 1, 0, 1, 0, 0, 1}, {1, 1, 0, 1, 0, 1, 0, 0, 1}, {1, 1, 1, 0, 0, 1, 0, 0, 1},
                    {0, 0, 1, 1, 1, 1, 0, 0, 1}, {1, 0, 0, 1, 1, 1, 0, 0, 1}, {0, 1, 0, 1, 1, 1, 0, 0, 1},
                    {1, 0, 1, 0, 1, 1, 0, 0, 1}, {0, 1, 1, 0, 1, 1, 0, 0, 1}, {1, 1, 0, 0, 1, 1, 0, 0, 1}};                    ;
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

        for (int i = 0; i < eim.getPlayerCount(); i++) {
            for (int j = 0; j < areaRects.length; j++) {
                if (areaRects[j].contains(players.get(i).getPosition())) {
                    playerPlacement[j] += 1;
                    break;
                }
            }
        }

        int[] curCombo = areaCombos[combo];
        boolean accept = true;
        for (int j = 0; j < curCombo.length; j++) {
            if (curCombo[j] != playerPlacement[j]) {
                accept = false;
                break;
            }
        }

        return accept;
    }

    public static String generateLPQCombo() {
        int[] combo = lpqStage8Permutations[Randomizer.nextInt(lpqStage8Permutations.length)];
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < combo.length; i++) {
            ret.append(combo[i]);
            if (i < combo.length - 1) {
                ret.append(",");
            }
        }
        return ret.toString();
    }

    public static boolean lpqRectangleStage(Character player) {
        int[] objset = {0, 0, 0, 0, 0, 0, 0, 0, 0};
        int playersOnCombo = 0;
        MapleMap map = player.getMap();
        List<Character> party = player.getEventInstance().getPlayers();
        for (int i = 0; i < party.size(); i++) {
            for (int y = 0; y < map.getAreas().size(); y++) {
                if (map.getArea(y).contains(party.get(i).getPosition())) {
                    playersOnCombo++;
                    objset[y] = 1;
                    break;
                }
            }
        }
        if (playersOnCombo == 5) {
            String c = player.getEventInstance().getProperty("stage8combo");
            int combo;
            if (c == null) {
                combo = Randomizer.nextInt(lpqStage8Permutations.length);
                player.getEventInstance().setProperty("stage8combo", combo);
            } else {
                combo = Integer.parseInt(c);
            }
            for (int i = 0; i < objset.length; i++) {
                if (lpqStage8Permutations[combo][i] != objset[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
