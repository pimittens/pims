package net.server.market;

import client.inventory.Equip;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import net.server.Server;
import server.maps.HiredMerchant;
import server.maps.MapleMap;
import server.maps.PlayerShopItem;
import tools.DatabaseConnection;
import tools.PacketCreator;
import tools.Randomizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Market {

    protected final Lock lock = new ReentrantLock(true);
    private final Map<Integer, MarketItem> items;
    private final List<Equip> equips;
    private final List<HiredMerchant> merchants;

    public Market() {
        items = new HashMap<>();
        equips = new ArrayList<>();
        merchants = new ArrayList<>();
        loadItems();
        createMerchants();
    }

    private void loadItems() {
        lock.lock();
        try (Connection con = DatabaseConnection.getConnection()) {
            int itemId;
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `marketitems`")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        itemId = rs.getInt("itemid");
                        items.put(itemId, new MarketItem(rs.getInt("quantity"), rs.getInt("marketPrice"), rs.getInt("averageSalePrice"), rs.getInt("numSold"), ItemConstants.isEquipment(itemId)));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `marketequipment`")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        itemId = rs.getInt("itemid");
                        equips.add((loadMarketEquipFromResultSet(rs)));
                        if (!items.containsKey(itemId)) {
                            items.put(itemId, new MarketItem(rs.getInt("quantity"), rs.getInt("marketPrice"), rs.getInt("averageSalePrice"), rs.getInt("numSold"), true));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println(e);
        } finally {
            lock.unlock();
        }
    }


    private void saveItems() {
        lock.lock();
        try (Connection con = DatabaseConnection.getConnection()) {
            StringBuilder query = new StringBuilder();
            query.append("DELETE FROM `marketitems`");

            try (PreparedStatement ps = con.prepareStatement(query.toString())) {
                ps.executeUpdate();
            }

            query = new StringBuilder();
            query.append("DELETE FROM `marketequipment`");

            try (PreparedStatement ps = con.prepareStatement(query.toString())) {
                ps.executeUpdate();
            }

            try (PreparedStatement psItem = con.prepareStatement("INSERT INTO `marketitems` VALUES (DEFAULT, ?, ?, ?, ?, ?)")) {
                for (int i : items.keySet()) {
                    psItem.setInt(1, i);
                    psItem.setInt(2, items.get(i).getQuantity());
                    psItem.setInt(3, items.get(i).getMarketPrice());
                    psItem.setInt(4, items.get(i).getAverageSalePrice());
                    psItem.setInt(5, items.get(i).getNumSold());
                    psItem.executeUpdate();
                }
            }
            try (PreparedStatement psEquip = con.prepareStatement("INSERT INTO `marketequipment` VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Equip equip : equips) {
                    psEquip.setInt(1, equip.getItemId());
                    psEquip.setInt(2, equip.getUpgradeSlots());
                    psEquip.setInt(3, equip.getLevel());
                    psEquip.setInt(4, equip.getStr());
                    psEquip.setInt(5, equip.getDex());
                    psEquip.setInt(6, equip.getInt());
                    psEquip.setInt(7, equip.getLuk());
                    psEquip.setInt(8, equip.getHp());
                    psEquip.setInt(9, equip.getMp());
                    psEquip.setInt(10, equip.getWatk());
                    psEquip.setInt(11, equip.getMatk());
                    psEquip.setInt(12, equip.getWdef());
                    psEquip.setInt(13, equip.getMdef());
                    psEquip.setInt(14, equip.getAcc());
                    psEquip.setInt(15, equip.getAvoid());
                    psEquip.setInt(16, equip.getHands());
                    psEquip.setInt(17, equip.getSpeed());
                    psEquip.setInt(18, equip.getJump());
                    psEquip.setInt(19, 0);
                    psEquip.setInt(20, equip.getVicious());
                    psEquip.setInt(21, equip.getItemLevel());
                    psEquip.setInt(22, equip.getItemExp());
                    psEquip.setInt(23, equip.getRingId());
                    psEquip.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println(e);
        } finally {
            lock.unlock();
        }
    }

    private void createMerchants() {
        lock.lock();
        try {
            MapleMap map;
            int owner = -1;
            for (int i = 910000002; i < 910000007; i++) {
                map = Server.getInstance().getChannel(0, 1).getMapFactory().getMap(i);
                for (int j = 670; j > -153; j -= 152) {
                    addMerchant(owner, j, 34, "shop name", map);
                    owner -= 1;
                }
                addMerchant(owner, 701, -146, "shop name", map);
                owner -= 1;
                addMerchant(owner, 853, -146, "shop name", map);
                owner -= 1;
                for (int j = 284; j > -139; j -= 152) {
                    addMerchant(owner, j, -206, "shop name", map);
                    owner -= 1;
                }
                for (int j = 547; j > -245; j -= 152) {
                    addMerchant(owner, j, -416, "shop name", map);
                    owner -= 1;
                }
            }
            for (int i = 910000007; i < 910000013; i++) {
                map = Server.getInstance().getChannel(0, 1).getMapFactory().getMap(i);
                addMerchant(owner, -231, 102, "shop name", map);
                owner -= 1;
                for (int j = -477; j > -1635; j -= 152) {
                    addMerchant(owner, j, 102, "shop name", map);
                    owner -= 1;
                }
                addMerchant(owner, -1881, 102, "shop name", map);
                owner -= 1;
                for (int j = -613; j > -1631; j -= 152) {
                    addMerchant(owner, j, -108, "shop name", map);
                    owner -= 1;
                }
                for (int j = -588; j > -1662; j -= 177) {
                    addMerchant(owner, j, -318, "shop name", map);
                    owner -= 1;
                }
            }
        } finally {
            lock.unlock();
        }
        populateShops();
    }

    private void addMerchant(int owner, int x, int y, String shopName, MapleMap map) {
        HiredMerchant merchant = new HiredMerchant(owner, "Market" + owner, x, y, shopName, getRandomShop(), map);
        merchant.setOpen(true);
        map.addMapObject(merchant);
        map.broadcastMessage(PacketCreator.spawnHiredMerchantBox(merchant));
        merchants.add(merchant);
    }

    private void populateShops() {
        lock.lock();
        try {
            Iterator<Integer> itemIds = items.keySet().iterator();
            int nextId;
            Equip next;
            for (HiredMerchant merchant : merchants) {
                if (merchant.getMap().getId() < 910000007) { // use regular items for rooms 2-6 and equips for the rest
                    for (int i = 0; i < 16; i++) {
                        if (!itemIds.hasNext()) {
                            break;
                        }
                        nextId = itemIds.next();
                        while (itemIds.hasNext() && items.get(nextId).isEquip()) {
                            nextId = itemIds.next();
                        }
                        if (!items.get(nextId).isEquip() && items.get(nextId).getQuantity() > 0) {
                            merchant.addItem(new PlayerShopItem(new Item(nextId, (short) 1, (short) 1), ItemConstants.isRechargeable(nextId) ? 1 : (short) Math.min(1000, items.get(nextId).getQuantity()), items.get(nextId).getMarketPrice()));
                        }
                    }
                } else {
                    for (int i = 0; i < 16; i++) {
                        if (equips.isEmpty()) {
                            break;
                        }
                        next = equips.removeFirst();
                        merchant.addItem(new PlayerShopItem(next, (short) 1, items.get(next.getItemId()).getMarketPrice()));
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private int getRandomShop() {
        return 5030002 + 2 * Randomizer.nextInt(6);
    }

    /**
     * sell an item to the market, adds the item to the market items list and returns the price
     * @param item the item to sell
     * @return the market price of the item
     */
    public int sellItem(Item item) { // todo: don't let bots sell certain worthless items, and for rechargable items the quantity here should be 1
        lock.lock();
        try {
            if (items.containsKey(item.getItemId())) {
                items.get(item.getItemId()).addQuantity(item.getQuantity());
            } else {
                items.put(item.getItemId(), new MarketItem(item.getQuantity(), ItemConstants.getItemValue(item.getItemId(), item.getQuantity()), 0, 0, false));
            }
            return items.get(item.getItemId()).getMarketPrice() * item.getQuantity();
        } finally {
            lock.unlock();
        }
    }

    /**
     * same as sellItem but for equips
     * @param equip the equip
     * @return the market price of the equip
     */
    public int sellEquip(Equip equip) {
        lock.lock();
        try {
            equips.add((Equip) equip.copy());
            if (items.containsKey(equip.getItemId())) {
                items.get(equip.getItemId()).addQuantity(1);
            } else {
                items.put(equip.getItemId(), new MarketItem(1, ItemConstants.getItemValue(equip.getItemId(), 1), 0, 0, true));
            }
            return items.get(equip.getItemId()).getMarketPrice();
        } finally {
            lock.unlock();
        }
    }

    public void updatePrices(boolean populate) { // todo: this doesn't update for players in the shop
        lock.lock();
        try {
            for (HiredMerchant merchant : merchants) {
                List<HiredMerchant.SoldItem> soldItems = merchant.getSold();
                for (PlayerShopItem item : merchant.getItems()) {
                    if (item.isExist() && ItemConstants.isEquipment(item.getItem().getItemId())) {
                        equips.add((Equip) item.getItem().copy());
                    }
                }
                if (soldItems.isEmpty()) {
                    merchant.clearItems();
                    continue;
                }
                List<Integer> itemIds = new ArrayList<>();
                for (HiredMerchant.SoldItem item : soldItems) {
                    if (!itemIds.contains(item.getItemId())) {
                        itemIds.add(item.getItemId());
                    }
                }
                int quantity, price;
                for (Integer id : itemIds) {
                    quantity = 0;
                    price = 0;
                    for (HiredMerchant.SoldItem item : soldItems) {
                        if (item.getItemId() == id) {
                            price = item.getUnitPrice();
                            quantity += item.getQuantity();
                        }
                    }
                    if (items.containsKey(id)) {
                        items.get(id).itemSold(price, quantity);
                    }
                }
                merchant.clearSoldItems();
                merchant.clearItems();
            }
        } finally {
            lock.unlock();
        }
        saveItems();
        if (populate) {
            populateShops();
        }
    }

    private static Equip loadMarketEquipFromResultSet(ResultSet rs) throws SQLException {
        Equip equip = new Equip(rs.getInt("itemid"), (short) 0);
        equip.setQuantity((short) rs.getInt("quantity"));
        equip.setAcc((short) rs.getInt("acc"));
        equip.setAvoid((short) rs.getInt("avoid"));
        equip.setDex((short) rs.getInt("dex"));
        equip.setHands((short) rs.getInt("hands"));
        equip.setHp((short) rs.getInt("hp"));
        equip.setInt((short) rs.getInt("int"));
        equip.setJump((short) rs.getInt("jump"));
        equip.setVicious((short) rs.getInt("vicious"));
        equip.setLuk((short) rs.getInt("luk"));
        equip.setMatk((short) rs.getInt("matk"));
        equip.setMdef((short) rs.getInt("mdef"));
        equip.setMp((short) rs.getInt("mp"));
        equip.setSpeed((short) rs.getInt("speed"));
        equip.setStr((short) rs.getInt("str"));
        equip.setWatk((short) rs.getInt("watk"));
        equip.setWdef((short) rs.getInt("wdef"));
        equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
        equip.setLevel(rs.getByte("level"));
        equip.setItemExp(rs.getInt("itemexp"));
        equip.setItemLevel(rs.getByte("itemlevel"));
        equip.setRingId(rs.getInt("ringid"));
        equip.setQuantity((short) 1);

        return equip;
    }
}
