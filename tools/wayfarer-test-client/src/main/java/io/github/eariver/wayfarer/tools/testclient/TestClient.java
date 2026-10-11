package io.github.eariver.wayfarer.tools.testclient;

import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Automated protocol-level verification client for the isolated PoC network.
 *
 * <p>Connects to the offline Velocity instance (velocity-offline on port 25588)
 * with an arbitrary username, joins, keeps the session alive, and runs a script of
 * commands/digs. It speaks 1.21.11 natively; ViaVersion on the proxy translates to
 * MC 26.2 for Main/Lobby backends.
 *
 * <p>Usage: TestClient &lt;host&gt; &lt;port&gt; &lt;username&gt; [script.txt]
 * Script commands:
 *   send &lt;chat command without leading slash&gt;
 *   move &lt;x&gt; &lt;y&gt; &lt;z&gt;
 *   dig &lt;x&gt; &lt;y&gt; &lt;z&gt;
 *   wait &lt;ms&gt;
 *   assert-chat &lt;substring&gt;          (fails the run if not seen since last marker)
 *   marker                            (clears the chat assertion window)
 */
public final class TestClient {

    public static double MOV_X = 8.5;
    public static double MOV_Y = 80.0;
    public static double MOV_Z = 8.5;

    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().readConfiguration(
                TestClient.class.getResourceAsStream("/logging.properties"));

        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 25588;
        String username = args.length > 2 ? args[2] : "wayfarer_testbot";
        Path script = args.length > 3 ? Path.of(args[3]) : null;

        System.out.println("[client] connecting " + host + ":" + port + " as " + username);

        MinecraftProtocol protocol = new MinecraftProtocol(username);
        ClientSession client = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(host, port))
                .setProtocol(protocol)
                .create();
        client.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, null);

        List<String> chatLog = new ArrayList<>();
        CountDownLatch loginLatch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger(1);
        BotListener listener = new BotListener(chatLog, loginLatch, sequence);
        client.addListener(listener);

        client.connect();
        if (!loginLatch.await(60, TimeUnit.SECONDS)) {
            System.out.println("[client] FAIL: no login within 60s");
            System.exit(2);
        }
        // wait for the play state to settle
        Thread.sleep(2000);

        List<String> lines = new ArrayList<>();
        if (script != null && Files.exists(script)) {
            try (BufferedReader r = new BufferedReader(new FileReader(script.toFile()))) {
                String l;
                while ((l = r.readLine()) != null) {
                    if (l.isBlank() || l.startsWith("#")) continue;
                    lines.add(l.trim());
                }
            }
        } else {
            lines.add("send list");
            lines.add("wait 1000");
        }

        for (String line : lines) {
            if (line.startsWith("send ")) {
                String cmd = line.substring(5);
                System.out.println("[client] send /" + cmd);
                client.send(new ServerboundChatCommandPacket(cmd));
            } else if (line.startsWith("move ")) {
                String[] p = line.split("\\s+");
                client.send(new ServerboundMovePlayerPosPacket(
                        true, false, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])));
            } else if (line.startsWith("dig ")) {
                String[] p = line.split("\\s+");
                int x = (int) Double.parseDouble(p[1]);
                int y = (int) Double.parseDouble(p[2]);
                int z = (int) Double.parseDouble(p[3]);
                Vector3i pos = Vector3i.from(x, y, z);
                // walk the bot onto the block being mined so it stays within reach
                ServerboundMovePlayerPosPacket walk = new ServerboundMovePlayerPosPacket(true, false, x + 0.5, y + 1.0, z + 0.5);
                client.send(walk);
                Thread.sleep(60);
                ServerboundPlayerActionPacket start = new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, pos, Direction.UP, sequence.getAndIncrement());
                ServerboundPlayerActionPacket finish = new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING, pos, Direction.UP, sequence.getAndIncrement());
                ServerboundSwingPacket swing = new ServerboundSwingPacket(Hand.MAIN_HAND);
                client.send(start);
                Thread.sleep(150);
                client.send(swing);
                Thread.sleep(150);
                client.send(finish);
            } else if (line.startsWith("use-on ")) {
                String[] p = line.split("\s+");
                int x = (int) Double.parseDouble(p[1]);
                int y = (int) Double.parseDouble(p[2]);
                int z = (int) Double.parseDouble(p[3]);
                Vector3i pos = Vector3i.from(x, y, z);
                ServerboundUseItemOnPacket use = new ServerboundUseItemOnPacket(
                        pos, Direction.UP, Hand.MAIN_HAND, 0.5f, 1.0f, 0.5f, false, false,
                        sequence.getAndIncrement());
                client.send(use);
                Thread.sleep(250);
            } else if (line.startsWith("hotbar ")) {
                int slot = Integer.parseInt(line.substring(7));
                client.send(new ServerboundSetCarriedItemPacket(slot));
                Thread.sleep(200);
            } else if (line.startsWith("wait ")) {
                Thread.sleep(Long.parseLong(line.substring(5)));
            } else if (line.startsWith("chat-wait ")) {
                String needle = line.substring("chat-wait ".length());
                long deadline = System.currentTimeMillis() + 20000;
                boolean found = false;
                while (System.currentTimeMillis() < deadline) {
                    synchronized (chatLog) {
                        if (chatLog.stream().anyMatch(m -> m.contains(needle))) {
                            found = true;
                            break;
                        }
                    }
                    Thread.sleep(200);
                }
                System.out.println("[client] chat-wait " + (found ? "ok" : "TIMEOUT") + ": " + needle);
            } else if (line.startsWith("assert-chat ")) {
                String needle = line.substring("assert-chat ".length());
                synchronized (chatLog) {
                    boolean found = chatLog.stream().anyMatch(m -> m.contains(needle));
                    if (!found) {
                        System.out.println("[client] ASSERT FAILED: '" + needle + "' not in chat log");
                        Path out = Path.of("wayfarer-evidence/chat.log");
                        Files.createDirectories(out.getParent());
                        Files.write(out, chatLog);
                        System.exit(3);
                    }
                    System.out.println("[client] assert ok: " + needle);
                }
            } else if (line.equals("marker")) {
                synchronized (chatLog) {
                    chatLog.clear();
                }
            }
        }

        Path out = Path.of("wayfarer-evidence/chat.log");
        Files.createDirectories(out.getParent());
        synchronized (chatLog) {
            Files.write(out, chatLog);
        }
        System.out.println("[client] script complete, disconnecting");
        client.disconnect("script complete");
        Thread.sleep(1000);
        System.exit(0);
    }

    private static final class BotListener extends SessionAdapter {
        private final List<String> chatLog;
        private final CountDownLatch loginLatch;
        private final java.util.concurrent.atomic.AtomicInteger sequence;

        BotListener(List<String> chatLog, CountDownLatch loginLatch, java.util.concurrent.atomic.AtomicInteger sequence) {
            this.chatLog = chatLog;
            this.loginLatch = loginLatch;
            this.sequence = sequence;
        }

        @Override
        public void packetReceived(org.geysermc.mcprotocollib.network.Session session,
                                   org.geysermc.mcprotocollib.network.packet.Packet packet) {
            if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                session.send(new ServerboundKeepAlivePacket(keepAlive.getPingId()));
            } else if (packet instanceof ClientboundLoginPacket) {
                System.out.println("[client] login accepted");
                loginLatch.countDown();
            } else if (packet instanceof ClientboundSystemChatPacket chat) {
                String plain = PlainTextComponentSerializer.plainText().serialize(chat.getContent());
                System.out.println("CHAT> " + plain);
                synchronized (chatLog) {
                    chatLog.add(plain);
                }
            } else if (packet instanceof ClientboundDisconnectPacket disconnect) {
                System.out.println("[client] disconnected: " + disconnect.getReason());
            }
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            System.out.println("[client] connection closed: " + event.getReason()
                    + (event.getCause() != null ? " cause=" + event.getCause() : ""));
            loginLatch.countDown();
        }
    }

    private TestClient() {
    }
}
