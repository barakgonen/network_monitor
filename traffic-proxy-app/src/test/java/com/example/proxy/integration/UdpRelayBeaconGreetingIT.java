package com.example.proxy.integration;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.destination.listener.UdpEchoListener;
import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.UdpRelay;
import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.tester.schemas.greeting.BeaconMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires a real {@link UdpRelay} (traffic-proxy-app) against a real {@link UdpEchoListener} in
 * GREETING mode (traffic-destination-app) - the Beacon/Greeting analogue of
 * {@link UdpRelayPingPongIT}.
 */
class UdpRelayBeaconGreetingIT {

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
    void realDestinationAppRepliesWithGreeting_relayedToProducer_andBothLegsMirrored() throws Exception {
        int destinationPort = freePort();
        InterfaceEntry destinationConfig = new InterfaceEntry();
        destinationConfig.setKey("greeting");
        destinationConfig.setProtocol("UDP");
        destinationConfig.setPort(destinationPort);
        destinationConfig.setReplyMode(ReplyMode.GREETING);
        destinationListener = new UdpEchoListener(destinationConfig);
        destinationListener.start();

        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            RelayEntry relayEntry = new RelayEntry();
            relayEntry.setKey("greeting");
            relayEntry.setProtocol("UDP");
            relayEntry.setListen(endpoint("0.0.0.0", listenPort));
            relayEntry.setDestination(endpoint("127.0.0.1", destinationPort));
            relayEntry.setMirror(endpoint("127.0.0.1", mirror.getLocalPort()));
            relay = new UdpRelay(relayEntry);
            relay.start();

            BeaconMessage beacon = new BeaconMessage(32.0853, 34.7818);
            byte[] beaconBody = ReflectiveStructCodec.encode(beacon);
            byte[] beaconWireBytes = ProtocolHeaderCodec.encodeMessage(5001, System.currentTimeMillis(), beaconBody);
            producer.send(new DatagramPacket(beaconWireBytes, beaconWireBytes.length, InetAddress.getLoopbackAddress(), listenPort));

            // The real Greeting (built by traffic-destination-app's GreetingReplyEncoder) comes
            // back to the original producer, relayed through the proxy.
            producer.setSoTimeout(3000);
            byte[] greetingWireBytes = receive(producer);
            ByteBuffer greetingBuffer = ByteBuffer.wrap(greetingWireBytes);
            ProtocolHeader greetingHeader = ProtocolHeaderCodec.decodeHeader(greetingBuffer);
            assertThat(greetingHeader.opcode()).isEqualTo(5002);
            byte[] greetingBody = new byte[greetingHeader.bodyLength()];
            greetingBuffer.get(greetingBody);
            ByteBuffer bodyBuffer = ByteBuffer.wrap(greetingBody);
            assertThat(bodyBuffer.getInt()).isEqualTo(1);

            // Both legs - the original Beacon and the real Greeting - were mirrored, and both
            // decode correctly as their own distinct, correct opcodes.
            mirror.setSoTimeout(3000);
            ProtocolHeader mirroredRequestHeader = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(receive(mirror)));
            assertThat(mirroredRequestHeader.opcode()).isEqualTo(5001);

            ProtocolHeader mirroredReplyHeader = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(receive(mirror)));
            assertThat(mirroredReplyHeader.opcode()).isEqualTo(5002);
        }
    }

    @Test
    void realDestinationAppWithFixedReplyPort_repliesWithGreeting_stillRelayedToProducer() throws Exception {
        int destinationPort = freePort();
        int replyPort = freePort();

        InterfaceEntry destinationConfig = new InterfaceEntry();
        destinationConfig.setKey("greeting");
        destinationConfig.setProtocol("UDP");
        destinationConfig.setPort(destinationPort);
        destinationConfig.setReplyMode(ReplyMode.GREETING);
        destinationConfig.setReplyPort(replyPort);
        destinationListener = new UdpEchoListener(destinationConfig);
        destinationListener.start();

        try (DatagramSocket producer = new DatagramSocket();
             DatagramSocket mirror = new DatagramSocket(0)) {

            int listenPort = freePort();
            RelayEntry relayEntry = new RelayEntry();
            relayEntry.setKey("greeting");
            relayEntry.setProtocol("UDP");
            relayEntry.setListen(endpoint("0.0.0.0", listenPort));
            relayEntry.setDestination(endpoint("127.0.0.1", destinationPort));
            relayEntry.setMirror(endpoint("127.0.0.1", mirror.getLocalPort()));
            relayEntry.setReplyPort(replyPort);
            relay = new UdpRelay(relayEntry);
            relay.start();

            BeaconMessage beacon = new BeaconMessage(-33.8688, 151.2093);
            byte[] beaconBody = ReflectiveStructCodec.encode(beacon);
            byte[] beaconWireBytes = ProtocolHeaderCodec.encodeMessage(5001, System.currentTimeMillis(), beaconBody);
            producer.send(new DatagramPacket(beaconWireBytes, beaconWireBytes.length, InetAddress.getLoopbackAddress(), listenPort));

            producer.setSoTimeout(3000);
            byte[] greetingWireBytes = receive(producer);
            ByteBuffer greetingBuffer = ByteBuffer.wrap(greetingWireBytes);
            ProtocolHeader greetingHeader = ProtocolHeaderCodec.decodeHeader(greetingBuffer);
            assertThat(greetingHeader.opcode()).isEqualTo(5002);
        }
    }
}
