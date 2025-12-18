package net.server.market;

import server.ItemInformationProvider;

public class MarketItem {

    //private final int itemid;
    private int quantity;
    private final int basePrice;

    public MarketItem(int quantity, int basePrice) {
        this.quantity = quantity;
        this.basePrice = basePrice;
    }

    public void addQuantity(int amount) {
        quantity += amount;
        if (quantity < 0) {
            quantity = 0;
        }
    }

    public int getQuantity() {
        return quantity;
    }

    public int getBasePrice() {
        return basePrice;
    }

    public int getMarketPrice() {
        if (quantity == 0) {
            return basePrice * 10;
        }
        if (quantity >= 1000) {
            return basePrice / 2;
        }
        return (int) ((-9 * Math.sqrt((double) quantity / 4000) + 6) * basePrice); // todo: maybe add random fluctuations
    }
}
