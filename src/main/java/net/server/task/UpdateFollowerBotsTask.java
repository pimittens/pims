package net.server.task;

import client.CharacterBot;
import net.server.Server;

public class UpdateFollowerBotsTask implements Runnable {

    @Override
    public void run() {
        for (CharacterBot bot : Server.getInstance().getFollowers()) {
            bot.followerUpdate();
        }
    }
}
