package net.server.market;

import net.server.Server;

public class UpdateMarketTask implements Runnable {

    @Override
    public void run() {
        Server.getInstance().getMarket().updatePrices(true);
    }
}
