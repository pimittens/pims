package economysimulation;

import net.server.market.MarketItem;
import tools.Randomizer;

public class EconomySimulator {

    public static void main(String[] args) {
        MarketItem scroll = new MarketItem(0, 1000000, 1000000, 0, false);
        MarketItem equip = new MarketItem(0, 10000000, 10000000, 0, true);
        int[] players = new int[40];
        for (int hours = 1; hours < 100; hours++) {
            scroll.addQuantity(Randomizer.rand(2,10));
            equip.addQuantity(Randomizer.rand(1,3));
            for (int i = 0; i < players.length; i++) {
                players[i] += 1000000;
                if (scroll.getQuantity() >= 7 && equip.getQuantity() >= 1 && players[i] > 14 * scroll.getMarketPrice() + 2 * equip.getMarketPrice()) {
                    players[i] -= 7 * scroll.getMarketPrice() + equip.getMarketPrice();
                    scroll.itemSold(scroll.getMarketPrice(), 7);
                    equip.itemSold(equip.getMarketPrice(), 1);
                }
            }
            System.out.println("Scroll - quantity: " + scroll.getQuantity() + ", price: " + scroll.getMarketPrice());
            System.out.println("equip - quantity: " + equip.getQuantity() + ", price: " + equip.getMarketPrice());
        }
    }
}
