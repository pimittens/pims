package net.server.task;

import client.CharacterBot;
import net.server.Server;

public class UpdateFollowerBotsTask implements Runnable {

    @Override
    public void run() {
        Server.getInstance().getBotManager().updateFollowers();
    }
}
