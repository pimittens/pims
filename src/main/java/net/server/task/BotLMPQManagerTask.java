package net.server.task;

import client.Character;
import net.server.Server;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import scripting.event.EventManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Ronan
 */
public class BotLMPQManagerTask implements Runnable {

    @Override
    public void run() {
        System.out.println("lmpq manager started");
        EventManager em = Server.getInstance().getChannel(0, 1).getEventSM().getEventManager("LudiMazePQ");
        if (em == null) {
            System.out.println("event manager was null");
            return;
        }
        Party party = Server.getInstance().getBotManager().createPQParty(6, 51, 70, 220000000);
        if (party == null || party.getMembers().size() != 6) {
            System.out.println("party was null or wrong size");
            if (party != null) {
                System.out.println("party size: " + party.getMembers().size());
            }
            return;
        }
        em.getEligibleParty(party);
        if (!em.startInstance(party, party.getLeader().getPlayer().getMap(), 1)) {
            System.out.println("lmpq failed to start");
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
            System.out.println("LMPQ started with the following party members:");
            for (PartyCharacter p : party.getPartyMembers()) {
                // lmpq will put them in a random room so need to move them all to the one which runs the first
                // lmpq method we could run this method for every room other than the important ones but then things
                // might get messed up slightly if they initially get placed into important rooms
                p.getPlayer().changeMap(p.getPlayer().getClient().getChannelServer().getMapFactory().getMap(809050000));
                System.out.println(p.getName());
            }
        }
    }
}
