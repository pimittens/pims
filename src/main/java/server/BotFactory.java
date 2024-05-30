package server;

import client.DefaultDates;
import client.Job;
import client.inventory.*;
import config.YamlConfig;
import constants.id.MapId;
import net.server.handlers.login.LoginPasswordHandler;
import tools.*;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BotFactory {

    private static final String[] coolnouns = {"Action", "Adult", "Advance", "Agent", "Alarm", "Alcohol", "Ambition",
            "Analysis", "Angle", "Anger", "Animal", "Answer", "Apple", "Area", "Attack", "Baby", "Balance", "Basis",
            "Battle", "Beach", "Bear", "Being", "Benefit", "Beyond", "Bicycle", "Birth", "Black", "Blank", "Blind",
            "Blue", "Brain", "Branch", "Brave", "Breath", "Brick", "Bridge", "Brilliant", "Brown", "Burn", "Cake",
            "Cancel", "Candle", "Candy", "Card", "Cash", "Chain", "Challenge", "Chance", "Chapter", "Charge", "Charity",
            "Chemical", "Chip", "Chocolate", "Choice", "Cloud", "Constant", "Cookie", "Courage", "Crazy", "Cycle",
            "Damage", "Dare", "Dark", "Data", "Death", "Deep", "Design", "Desire", "Detail", "Devil", "Device",
            "Disaster", "Disk", "Distance", "Divide", "Doctor", "Drama", "Draw", "Drink", "Earth", "East", "Edge",
            "Effort", "Energy", "Engine", "Equal", "Error", "Escape", "Essay", "Estate", "Evening", "Event", "Excuse",
            "Expert", "Face", "Fact", "Factor", "Family", "Fear", "Feel", "Field", "Fight", "Figure", "Final", "Finger",
            "Fish", "Flight", "Flower", "Focus", "Fold", "Fruit", "Funny", "Game", "Gift", "Glove", "Ghost", "Goal",
            "Gold", "Good", "Grab", "Grand", "Grand", "Grass", "Gray", "Green", "Ground", "Growth", "Guard", "Guess",
            "Guide", "Habit", "Half", "Hand", "Harm", "Health", "Heart", "Heavy", "Hello", "Help", "Hide", "High",
            "Hold", "Home", "Honey", "Hope", "Horse", "Human", "Hunt", "Idea", "Ideal", "Illegal", "Image", "Impact",
            "Impress", "Incident", "Increase", "Initial", "Injury", "Insect", "Inside", "Iron", "Island", "Issue",
            "Jape", "Jazz", "Jelly", "Jerk", "Jewel", "Joke", "Joust", "Judge", "Juice", "Juke", "Jumble", "Jump",
            "Jury", "Junk", "Justice", "Kind", "King", "Knife", "Kill", "Kiss", "Kick", "Knight", "Kidney", "Knob",
            "Kitten", "Knack", "Kiwi", "Keep", "Knuckle", "Kestrel", "Ladder", "Laugh", "Layer", "Lead", "Leader",
            "Lesson", "Letter", "Level", "Library", "Life", "Light", "Limit", "Line", "Link", "Lock", "Love", "Luck",
            "Machine", "Mail", "Major", "Maniac", "March", "Master", "Match", "Math", "Matter", "Might", "Minute",
            "Mission", "Mouse", "Monkey", "Muscle", "Nail", "Name", "Napkin", "Nasty", "Nature", "Neat", "Needle",
            "Nerve", "Night", "Noise", "Normal", "North", "Nose", "Note", "Number", "Oasis", "Object", "Oblivion",
            "Oblong", "Ocean", "Octopus", "Offer", "Olive", "Omen", "Onion", "Opinion", "Option", "Orange", "Order",
            "Other", "Owner", "Pace", "Page", "Pain", "Paint", "Panic", "Paper", "Party", "Passion", "Pause",
            "Peace", "People", "Person", "Photo", "Piano", "Piece", "Pizza", "Plastic", "Poem", "Potato", "Pride",
            "Prize", "Proof", "Purple", "Quack", "Quagmire", "Quail", "Quake", "Quality", "Qualm", "Quantity",
            "Quarrel", "Quarter", "Quartz", "Queen", "Query", "Quest", "Queue", "Quibble", "Quill", "Quilt", "Quota",
            "Race", "Rabble", "Rabbit", "Ratio", "Realm", "Reading", "Reality", "Read", "Realism", "Real", "Reach",
            "River", "Ring", "Rock", "Rule", "Saga", "Scale", "Script", "Season", "Secret", "Shade", "Shadow",
            "Silver", "Skeleton", "Sneeze", "Society", "Solution", "Spaghetti", "Sparkle", "Star", "Table", "Talent",
            "Tank", "Target", "Taste", "Teacher", "Team", "Theme", "Theory", "Thing", "Thread", "Time", "Title",
            "Train", "Tree", "Trust", "Truth", "Type", "Udder", "Ukulele", "Umbrage", "Umbrella", "Unicorn", "Unit",
            "Unrest", "Update", "Upgrade", "Upside", "Urgency", "Utensil", "Vacation", "Vacuum", "Valley", "Value",
            "Valve", "Vanilla", "Variable", "Variant", "Variety", "Vault", "Velocity", "Verdict", "Verse", "Version",
            "Veteran", "Victory", "Violet", "Virtue", "Virus", "Visitor", "Vitamin", "Voice", "Void", "Voltage", "Wall",
            "Waste", "Water", "Wave", "Wealth", "Weapon", "Weather", "Weight", "West", "Wheel", "White", "Wind",
            "Winner", "Winter", "Witness", "Xenon", "Yacht", "Yahoo", "Yakuza", "Year", "Yeast", "Yelp", "Yellow",
            "Yeti", "Yoga", "Yoke", "Yore", "Youth", "Yucca", "Yule", "Yurt", "Zeal", "Zealot", "Zebra", "Zeitgeist",
            "Zenith", "Zephyr", "Zero", "Zest", "Zilch", "Zinc", "Zing", "Zipper", "Zombie", "Zone", "Zucchini"};
    private static final int[] colorHairsMale = {0,2,3,4,5,6,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,
            29,30,31,32,33,34,35,36,37,40,41,42,43,44,45,46,47,48,49,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,
            67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,99,300,304,310};
    private static final int[] noColorHairsMale = {1,7,8,9};
    private static final int[] colorHairsFemale = {100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,
            117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,140,141,142,143,144,145,146,147,
            148,149,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,
            176,177,178,179,180,181,182,183,184,185,186,187,188,189,191,192,193,194,195,400,401,402,403,405,411};
    private static final int[] colorEyesMale = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,
            27,28,29,31,32};
    private static final int[] whiteEyesMale = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,16,17,18,19,20,21,22,23,24,26,28};
    private static final int[] colorEyesFemale = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,16,17,18,19,20,21,22,23,24,25,26,
            27,29,30};
    private static final int[] whiteEyesFemale = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,16,17,18,19,20,21,22,23,24,25,26};



    public static void createBots() { // create bots and insert them into database
        int numAccounts;
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM accounts WHERE name LIKE \"bot%\";");
                 ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    numAccounts = rs.getInt("COUNT(*)");
                 }
        } catch (SQLException se) {
            System.out.println("error creating bots");
            return;
        }
        if (numAccounts >= 1000) {
            return; // might increase to 5k or 10k if needed
        }
        int count;
        if (numAccounts == 0) {
            System.out.println("creating initial bots");
            count = 200; // setup initial bots
        } else {
            count = Math.min(50, 999 - numAccounts); // only create up to 50 at a time
        }
        int accountID, gender;
        String accountName, characterName;
        for (int i = 0; i < count; i++) {
            accountName = "bot" + String.format("%05d", numAccounts);
            characterName = pickCharacterName();
            if (characterName.equals("-1")) {
                System.out.println("error picking bot name");
                continue;
            }
            gender = Randomizer.nextInt(2);
            accountID = createAccount(accountName, gender);
            if (accountID == -1) {
                System.out.println("error creating bot account with name " + accountName);
                continue;
            }
            if (!createBot(accountID, characterName, gender, pickSkin(), pickHair(gender), pickFace(gender))) {
                System.out.println("error creating bot character with account name " + accountName + " and character name " + characterName);
            }
            numAccounts++;
        }
    }

    private static int createAccount(String accountName, int gender) {
        int accountid;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO accounts (name, password, birthday, tempban, gender, tos) VALUES (?, ?, ?, ?, ?, ?);", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, accountName);
            ps.setString(2, YamlConfig.config.server.BCRYPT_MIGRATION ? BCrypt.hashpw("botpw", BCrypt.gensalt(12)) : LoginPasswordHandler.hashpwSHA512("botpw"));
            ps.setDate(3, Date.valueOf(DefaultDates.getBirthday()));
            ps.setTimestamp(4, Timestamp.valueOf(DefaultDates.getTempban()));
            ps.setInt(5, gender);
            ps.setInt(6, 1);
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

    private static boolean createBot(int accountid, String name, int gender, int skin, int hair, int face) {
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

                List<Pair<Item, InventoryType>> itemsWithType = new ArrayList<>();
                Item eq_weapon = ItemInformationProvider.getInstance().getEquipById(1302000);
                eq_weapon.setPosition((byte) -11);
                itemsWithType.add(new Pair<>(eq_weapon, InventoryType.EQUIPPED));
                ItemFactory.INVENTORY.saveItems(itemsWithType, id, con);

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

    private static String pickCharacterName() {
        String name = coolnouns[Randomizer.nextInt(coolnouns.length)];
        List<String> existingNames = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT name FROM characters;");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existingNames.add(rs.getString("name"));
                }

            }
        } catch (SQLException se) {
            System.out.println("error reading bot names");
            return "-1";
        }
        char[] alphabet = "0123456789ZYXWVUTSRQPONMLKJIHGFEDCBA".toCharArray();
        int nextChar = -1, additionalChar1 = -1, additionalChar2 = -1;
        String originalName = name, currentName = name;
        while (existingNames.contains(name)) {
            nextChar++;
            if (nextChar > 25) {
                nextChar -= 26;
                additionalChar1++;
                if (additionalChar1 > 25) {
                    additionalChar1 -= 26;
                    additionalChar2++;
                    if (additionalChar2 > 25) {
                        return pickCharacterName(); // name couldn't be made unique, so pick a new one
                    }
                }
                if (additionalChar2 > -1) {
                    currentName = originalName + alphabet[additionalChar1] + alphabet[additionalChar2];
                } else {
                    currentName = originalName + alphabet[additionalChar1];
                }
            }
            name = currentName + alphabet[nextChar];
            if (name.length() > 12) {
                return pickCharacterName(); // name couldn't be made unique, so pick a new one
            }
        }
        return name;
    }

    private static int pickSkin() {
        if (Randomizer.nextInt(4) < 3) {
            return 0; // 75% to have normal skin since that was the most popular
        }
        int skin = Randomizer.nextInt(7) + 1;
        if (skin > 5) {
            skin += 3;
        }
        return skin;
    }

    private static int pickHair(int gender) {
        if (gender == 0) {
            if (Randomizer.nextInt(colorHairsMale.length + noColorHairsMale.length) < noColorHairsMale.length) {
                return 30000 + noColorHairsMale[Randomizer.nextInt(noColorHairsMale.length)] * 10;
            }
            return 30000 + colorHairsMale[Randomizer.nextInt(colorHairsMale.length)] * 10 + Randomizer.nextInt(8);
        }
        return 30000 + colorHairsFemale[Randomizer.nextInt(colorHairsFemale.length)] * 10 + Randomizer.nextInt(8);
    }

    private static int pickFace(int gender) {
        int color = Randomizer.nextInt(9);
        if (color == 8) { // white
            if (gender == 0) {
                return 20000 + color * 100 + whiteEyesMale[Randomizer.nextInt(whiteEyesMale.length)];
            }
            return 21000 + color * 100 + whiteEyesFemale[Randomizer.nextInt(whiteEyesFemale.length)];
        }
        if (gender == 0) {
            return 20000 + color * 100 + colorEyesMale[Randomizer.nextInt(colorEyesMale.length)];
        }
        return 21000 + color * 100 + colorEyesFemale[Randomizer.nextInt(colorEyesFemale.length)];
    }
}
