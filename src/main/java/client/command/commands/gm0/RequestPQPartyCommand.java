/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;
import net.server.Server;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import scripting.event.EventManager;

import java.util.ArrayList;
import java.util.List;

public class RequestPQPartyCommand extends Command {
    {
        setDescription("Summon a follower.");
    }

    @Override
    public void execute(Client c, String[] params) {
        if (params.length > 0) {
            if (params[0].equals("kpq")) {
                Server.getInstance().getBotManager().createFollowerPQParty(4, 21, 30, c.getPlayer(), c.getChannel());
            } else if (params[0].equals("lpq")) {
                Server.getInstance().getBotManager().createFollowerPQParty(6, 35, 50, c.getPlayer(), c.getChannel());
            } else if (params[0].equals("lmpq")) {
                Server.getInstance().getBotManager().createFollowerPQParty(6, 51, 70, c.getPlayer(), c.getChannel());
            } else {
                c.getPlayer().message("please enter a valid pq (kpq, lpq, lmpq).");
            }
        } else {
            c.getPlayer().message("please enter a pq (kpq, lpq, lmpq).");
        }
    }
}
