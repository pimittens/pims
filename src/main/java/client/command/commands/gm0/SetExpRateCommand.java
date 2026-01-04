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

public class SetExpRateCommand extends Command {
    {
        setDescription("Set personal exp rate.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();

        int level = player.getLevel();
        if (level < 11) {
            return;
        }
        int maxRate = level / 10;

        int amount;
        if (params.length > 0) {
            try {
                amount = Math.max(Math.min(Integer.parseInt(params[0]), maxRate), 0);
            } catch (NumberFormatException e) {
                amount = 1;
            }
        } else {
            amount = maxRate;
        }
        player.setPersonalExpRate(amount);
        player.dropMessage("Your exp rate has been set to " + amount);
        player.dropMessage("Your current max exp rate is " + maxRate);
    }
}
