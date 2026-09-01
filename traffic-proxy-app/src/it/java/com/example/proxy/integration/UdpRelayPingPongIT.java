package com.example.proxy.integration;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.destination.listener.UdpEchoListener;
import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.UdpRelay;
import com.example.binaryserdes.envelope.ProtocolHeader;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.tester.schemas.ping.PingMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires a real {@link UdpRelay} (traffic-proxy-app) against a real {@link UdpEchoListener} in
 * PONG mode (traffic-destination-app) - the actual two components that make the Ping/Pong
 * feature work together, rather than each module's own unit tests standing in a hand-rolled test
 * double for the other side.
 */
class UdpRelayPingPongIT {

    private UdpRelay relay;
    private UdpEchoListener destinationListener;

    @AfterEach
    void tearDown() {
        if (relay != null) {
            relay.stop();
        }
        if (destinationListener != null) {
            destinationListener.stop();
        }
    }

    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static EndpointConfig endpoint(String host, int port) {
        EndpointConfig endpoint = new EndpointConfig();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    private static byte[] receive(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        byte[] result = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), result, 0, packet.getLength());
        return result;
    }

    @Test
    void realDestinationAppRepliesWithPong_relayedToProducer_andBothLegsMirrored() throws Exception {
        int destinationPort = freePort();
        InterfaceEntry destinationConfig = new InterfaceEntry();
        destinationConfig.setKey("ping");
        destinationConfig.setProtocol("UDP");
        destinationConfig.setPort(destinationPort);
        destinationConfig.setReplyMode(ReplyMode.PONG);
        destinationListener = new UdpEchoListener(destinationConfig);
        destinationListener.start();

        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            RelayEntry relayEntry = new RelayEntry();
            relayEntry.setKey("ping");
            relayEntry.setProtocol("UDP");
            relayEntry.setListen(endpoint("0.0.0.0", listenPort));
            relayEntry.setDestination(endpoint("127.0.0.1", destinationPort));
            relayEntry.setMirror(endpoint("127.0.0.1", mirror.getLocalPort()));
            relay = new UdpRelay(relayEntry);
            relay.start();

            PingMessage ping = new PingMessage(21);
            byte[] pingBody = ReflectiveStructCodec.encode(ping);
            byte[] pingWireBytes = ProtocolHeaderCodec.encodeMessage(3001, System.currentTimeMillis(), pingBody);
            producer.send(new DatagramPacket(pingWireBytes, pingWireBytes.length, InetAddress.getLoopbackAddress(), listenPort));

            // The real Pong (built by traffic-destination-app's PongReplyEncoder) comes back to
            // the original producer, relayed through the proxy.
            producer.setSoTimeout(3000);
            byte[] pongWireBytes = receive(producer);
            ByteBuffer pongBuffer = ByteBuffer.wrap(pongWireBytes);
            ProtocolHeader pongHeader = ProtocolHeaderCodec.decodeHeader(pongBuffer);
            assertThat(pongHeader.opcode()).isEqualTo(3002);
            byte[] pongBody = new byte[pongHeader.bodyLength()];
            pongBuffer.get(pongBody);
            assertThat(ByteBuffer.wrap(pongBody).getInt()).isEqualTo(21);

            // Both legs - the original Ping and the real Pong - were mirrored, and both decode
            // correctly as their own distinct, correct opcodes.
            mirror.setSoTimeout(3000);
            ProtocolHeader mirroredRequestHeader = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(receive(mirror)));
            assertThat(mirroredRequestHeader.opcode()).isEqualTo(3001);

            ProtocolHeader mirroredReplyHeader = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(receive(mirror)));
            assertThat(mirroredReplyHeader.opcode()).isEqualTo(3002);
        }
    }

    @Test
    void realDestinationAppWithFixedReplyPort_repliesWithPong_stillRelayedToProducer() throws Exception {
        int destinationPort = freePort();
        int replyPort = freePort();

        InterfaceEntry destinationConfig = new InterfaceEntry();
        destinationConfig.setKey("ping");
        destinationConfig.setProtocol("UDP");
        destinationConfig.setPort(destinationPort);
        destinationConfig.setReplyMode(ReplyMode.PONG);
        destinationConfig.setReplyPort(replyPort);
        destinationListener = new UdpEchoListener(destinationConfig);
        destinationListener.start();

        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            RelayEntry relayEntry = new RelayEntry();
            relayEntry.setKey("ping");
            relayEntry.setProtocol("UDP");
            relayEntry.setListen(endpoint("0.0.0.0", listenPort));
            relayEntry.setDestination(endpoint("127.0.0.1", destinationPort));
            relayEntry.setMirror(endpoint("127.0.0.1", mirror.getLocalPort()));
            relayEntry.setReplyPort(replyPort);
            relay = new UdpRelay(relayEntry);
            relay.start();

            PingMessage ping = new PingMessage(33);
            byte[] pingBody = ReflectiveStructCodec.encode(ping);
            byte[] pingWireBytes = ProtocolHeaderCodec.encodeMessage(3001, System.currentTimeMillis(), pingBody);
            producer.send(new DatagramPacket(pingWireBytes, pingWireBytes.length, InetAddress.getLoopbackAddress(), listenPort));

            // Even though the destination replies to a fixed port (not the relay's ephemeral
            // source port), the relay is listening there too - the real Pong still makes it back
            // to the original producer.
            producer.setSoTimeout(3000);
            byte[] pongWireBytes = receive(producer);
            ByteBuffer pongBuffer = ByteBuffer.wrap(pongWireBytes);
            ProtocolHeader pongHeader = ProtocolHeaderCodec.decodeHeader(pongBuffer);
            assertThat(pongHeader.opcode()).isEqualTo(3002);
            byte[] pongBody = new byte[pongHeader.bodyLength()];
            pongBuffer.get(pongBody);
            assertThat(ByteBuffer.wrap(pongBody).getInt()).isEqualTo(33);
        }
    }
}
