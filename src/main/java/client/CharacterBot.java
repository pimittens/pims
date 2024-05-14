package client;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.PacketProcessor;
import net.opcodes.SendOpcode;
import net.packet.*;
import tools.PacketCreator;
import tools.Randomizer;

import java.sql.SQLException;

public class CharacterBot {

    private Character following = null;

    private Client c;

    public void setFollowing(Character following) {
        this.following = following;
    }

    public void login() throws SQLException {
        c = Client.createLoginClient(-1, "127.0.0.1", PacketProcessor.getLoginServerProcessor(), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPasswordPacket(), (short) 1);
        c.handlePacket(PacketCreator.createServerListRequestPacket(), (short) 11);
        c.handlePacket(PacketCreator.createCharListRequestPacket(), (short) 5);
        c.handlePacket(PacketCreator.createCharSelectedPacket(2), (short) 19);
        c = Client.createChannelClient(-1, "127.0.0.1", PacketProcessor.getChannelServerProcessor(0, 1), 0, 1);
        c.setBotClient();
        c.handlePacket(PacketCreator.createLoginPacket(2), (short) 20);
        c.handlePacket(PacketCreator.createPartySearchUpdatePacket(), (short) 223);
        c.handlePacket(PacketCreator.createPlayerMapTransitionPacket(), (short) 207);
        //character will be floating at this point, so update position so that they are on the ground and send a packet to change their state

    }

    private void moveBot() {
        c.handlePacket(PacketCreator.createPlayerMovementPacket(), (short) 41);
    }

    public boolean isFollower() {
        return following != null;
    }

    public void update() {
        moveBot();
    }
}
