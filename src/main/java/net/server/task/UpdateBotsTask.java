package net.server.task;

import client.CharacterBot;
import net.server.Server;

public class UpdateBotsTask implements Runnable {

    @Override
    public void run() {
        for (CharacterBot bot : Server.getInstance().getBots()) {
            bot.update();
        }
    }
}
