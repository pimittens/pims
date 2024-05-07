package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;
import net.server.Server;
import tools.PacketCreator;

public class SaveCharacterCommand extends Command {
    {
        setDescription("Save character to database.");
    }

    @Override
    public void execute(Client c, String[] params) {
        c.getPlayer().saveCharToDB();
        c.getPlayer().message("Character saved.");
    }
}
