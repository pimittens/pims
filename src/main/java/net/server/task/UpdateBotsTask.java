package net.server.task;

import client.CharacterBot;
import net.server.Server;

public class UpdateBotsTask implements Runnable {

    @Override
    public void run() {
        //long startTime = System.currentTimeMillis();
        Server.getInstance().getBotManager().updateBots();
        //System.out.println("update bots completed in " + (System.currentTimeMillis() - startTime) + " ms");
    }
}
