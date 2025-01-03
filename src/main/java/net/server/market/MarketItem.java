package net.server.market;

import server.ItemInformationProvider;

public class MarketItem {

    //private final int itemid;
    private int quantity;
    private int previousQuantity;
    private int marketPrice;
    private int averageSalePrice;
    private int numSold;
    private boolean isEquip; // the prices for equips are for a clean equip with average stats

    public MarketItem(int quantity, int marketPrice, int averageSalePrice, int numSold) {
        //this.itemid = itemid;
        this.quantity = quantity;
        this.previousQuantity = quantity;
        this.marketPrice = marketPrice;
        this.averageSalePrice = averageSalePrice;
        this.numSold = numSold;
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

    public int getMarketPrice() {
        return marketPrice;
    }

    public void updateMarketPrice() {
        marketPrice = (int) (averageSalePrice * (previousQuantity / (double) quantity));
        previousQuantity = quantity;
    }

    public void itemSold(int price, int quantity) {
        averageSalePrice = (numSold * averageSalePrice + price * quantity) / (numSold + quantity);
        numSold += quantity;
        this.quantity -= quantity;
        if (quantity > 0) {
            updateMarketPrice();
        }
    }
}
