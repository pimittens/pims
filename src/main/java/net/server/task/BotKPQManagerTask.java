package net.server.task;

import client.Character;
import net.server.Server;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import scripting.event.EventManager;
import server.expeditions.ExpeditionBossLog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Ronan
 */
public class BotKPQManagerTask implements Runnable {

    @Override
    public void run() {
        System.out.println("kpq manager started");
        EventManager em = Server.getInstance().getChannel(0, 1).getEventSM().getEventManager("KerningPQ");
        if (em == null) {
            System.out.println("event manager was null");
            return;
        }
        Party party = Server.getInstance().getBotManager().createPQParty(4, 21, 30, 103000000);
        if (party == null || party.getMembers().size() != 4) {
            System.out.println("party was null or wrong size");
            if (party != null) {
                System.out.println("party size: " + party.getMembers().size());
            }
            return;
        }
        em.getEligibleParty(party);
        if (!em.startInstance(party, party.getLeader().getPlayer().getMap(), 1)) {
            System.out.println("kpq failed to start");
            List<Character> characters = new ArrayList<>();
            for (PartyCharacter p : party.getMembers()) {
                characters.add(p.getPlayer());
            }
            Party nextParty;
            for (Character character : characters) {
                nextParty = character.getParty();
                List<Character> partymembers = character.getPartyMembersOnline();
                Party.leaveParty(nextParty, character.getClient());
                character.updatePartySearchAvailability(true);
                character.partyOperationUpdate(nextParty, partymembers);
            }
        } else {
            System.out.println("KPQ started with the following party members:");
            for (PartyCharacter p : party.getPartyMembers()) {
                System.out.println(p.getName());
            }
        }
    }
}
