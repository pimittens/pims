/*
    This file is part of the HeavenMS MapleStory Server
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
package net.server.task;

import client.Character;
import net.server.Server;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import scripting.event.EventManager;
import server.expeditions.ExpeditionBossLog;

import java.util.List;

/**
 * @author Ronan
 */
public class BotKPQManagerTask implements Runnable {

    @Override
    public void run() {
        EventManager em = Server.getInstance().getChannel(0, 1).getEventSM().getEventManager("KerningPQ");
        if (em == null) {
            return;
        }
        Party party = Server.getInstance().getBotManager().createPQParty(4, 21, 30, 103000000);
        if (party == null || party.getMembers().size() != 4) {
            return;
        }
        em.getEligibleParty(party);
        if (!em.startInstance(party, party.getLeader().getPlayer().getMap(), 1)) {
            List<Character> partymembers = party.getLeader().getPlayer().getPartyMembersOnline();
            Party.leaveParty(party, party.getLeader().getPlayer().getClient());
            party.getLeader().getPlayer().updatePartySearchAvailability(true);
            party.getLeader().getPlayer().partyOperationUpdate(party, partymembers);
        } else {
            System.out.println("KPQ started with the following party members:");
            for (PartyCharacter p : party.getPartyMembers()) {
                System.out.println(p.getName());
            }
        }
    }
}
