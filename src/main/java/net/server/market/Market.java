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
import java.util.List;
import java.util.Map;

public class Market {

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
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM `marketitems` LEFT JOIN `marketequipment` USING(`marketitemid`)")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int itemId = rs.getInt("itemid");
                        if (ItemConstants.isEquipment(itemId)) {
                            //items.add(new Pair<>(loadEquipFromResultSet(rs), mit));
                            // todo: equips
                        } else {
                            MarketItem item = new MarketItem(rs.getInt("quantity"), rs.getInt("marketPrice"), rs.getInt("averageSalePrice"), rs.getInt("numSold"));
                            items.put(itemId, item);
                        }
                    }
                }
            }
        } catch (SQLException e) {}
    }


    public void saveItems() {
        // todo: save items
    }

    private void createMerchants() {
        MapleMap map = Server.getInstance().getChannel(0, 1).getMapFactory().getMap(910000002);
        HiredMerchant next;
        int owner = -1;
        //for (int i = 670; i > -153; i-=152) {
            next = new HiredMerchant(owner, "Market" + owner, 670, 34, "shop name", getRandomShop(), map);
            //owner -= 1;
            next.setOpen(true);
            for (int itemId : items.keySet()) {
                MarketItem item = items.get(itemId);
                PlayerShopItem shopItem = new PlayerShopItem(new Item(itemId, (short) 1, (short) 1), (short) item.getQuantity(), item.getMarketPrice());
                next.addItem(shopItem);
            }
            map.addMapObject(next);
            map.broadcastMessage(PacketCreator.spawnHiredMerchantBox(next));
            merchants.add(next);
        //}
    }

    private int getRandomShop() {
        return 5030002 + 2 * Randomizer.nextInt(6);
    }

    public void updatePrices() { // todo: this doesn't update for players in the shop
        for (HiredMerchant merchant : merchants) {
            List<HiredMerchant.SoldItem> soldItems = merchant.getSold();
            if (soldItems.isEmpty()) {
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
                    merchant.removeItemByID(id);
                    if (items.get(id).getQuantity() > 0) {
                        merchant.addItem(new PlayerShopItem(new Item(id, (short) 0, (short) 1), (short) items.get(id).getQuantity(), items.get(id).getMarketPrice()));
                    }
                }
            }
            merchant.clearSoldItems();
        }
    }
}
