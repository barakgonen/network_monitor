package com.example.monitor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoReplyEndToEndIT extends AbstractIntegrationTestBase {

    private DatagramSocket receiver;
    private ServerSocket tcpReceiver;

    @AfterEach
    void closeReceiver() {
        if (receiver != null && !receiver.isClosed()) {
            receiver.close();
        }
        if (tcpReceiver != null && !tcpReceiver.isClosed()) {
            try {
                tcpReceiver.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        autoReplySettingsService.setGlobalEnabled(false);
    }

    @Test
    void whenGlobalAndInterfaceAutoReplyEnabled_pingMessageTriggersPongReply() throws Exception {
        receiver = new DatagramSocket(0);
        receiver.setSoTimeout(3000);

        autoReplySettingsService.setGlobalEnabled(true);
        autoReplySettingsService.updateInterfaceSettings("Ping Interface", true, "localhost", receiver.getLocalPort(), "UDP");

        sendUdp(fruitPort, TestProtocolPayloads.ping(42));

        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        receiver.receive(packet);

        byte[] received = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, received, 0, packet.getLength());

        assertThat(TestProtocolPayloads.decodePongSequence(received)).isEqualTo(42);
    }

    @Test
    void whenGlobalAutoReplyDisabled_pingMessageDoesNotTriggerReply() throws Exception {
        receiver = new DatagramSocket(0);
        receiver.setSoTimeout(800);

        autoReplySettingsService.setGlobalEnabled(false);
        autoReplySettingsService.updateInterfaceSettings("Ping Interface", true, "localhost", receiver.getLocalPort(), "UDP");

        sendUdp(fruitPort, TestProtocolPayloads.ping(1));

        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        assertThatThrownBy(() -> receiver.receive(packet)).isInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void whenInterfaceAutoReplyDisabledButGlobalEnabled_doesNotTriggerReply() throws Exception {
        receiver = new DatagramSocket(0);
        receiver.setSoTimeout(800);

        autoReplySettingsService.setGlobalEnabled(true);
        autoReplySettingsService.updateInterfaceSettings("Ping Interface", false, "localhost", receiver.getLocalPort(), "UDP");

        sendUdp(fruitPort, TestProtocolPayloads.ping(1));

        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        assertThatThrownBy(() -> receiver.receive(packet)).isInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void orangeMessageWithNotFreshFreshness_triggersAutoReplyBananaMessage() throws Exception {
        receiver = new DatagramSocket(0);
        receiver.setSoTimeout(3000);

        autoReplySettingsService.setGlobalEnabled(true);
        autoReplySettingsService.updateInterfaceSettings("Fruit Interface", true, "localhost", receiver.getLocalPort(), "UDP");

        sendUdp(fruitPort, TestProtocolPayloads.orange("farm", (byte) 2));

        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        receiver.receive(packet);

        byte[] received = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, received, 0, packet.getLength());

        TestProtocolPayloads.BananaFields banana = TestProtocolPayloads.decodeBananaBody(received);
        assertThat(banana.color()).isEqualTo("yellow");
        assertThat(banana.weight()).isEqualTo(100.0);
    }

    @Test
    void whenGlobalAndInterfaceAutoReplyEnabledWithTcpTransport_pingMessageTriggersPongReplyOverTcp() throws Exception {
        tcpReceiver = new ServerSocket(0);
        tcpReceiver.setSoTimeout(3000);

        autoReplySettingsService.setGlobalEnabled(true);
        autoReplySettingsService.updateInterfaceSettings("Ping Interface", true, "localhost", tcpReceiver.getLocalPort(), "TCP");

        sendUdp(fruitPort, TestProtocolPayloads.ping(77));

        try (Socket accepted = tcpReceiver.accept()) {
            byte[] header = accepted.getInputStream().readNBytes(16);
            int bodyLength = java.nio.ByteBuffer.wrap(header).getInt(12);
            byte[] body = accepted.getInputStream().readNBytes(bodyLength);

            byte[] full = new byte[header.length + body.length];
            System.arraycopy(header, 0, full, 0, header.length);
            System.arraycopy(body, 0, full, header.length, body.length);

            assertThat(TestProtocolPayloads.decodePongSequence(full)).isEqualTo(77);
        }
    }

    @Test
    void orangeMessageWithFreshFreshness_doesNotTriggerAutoReply() throws Exception {
        receiver = new DatagramSocket(0);
        receiver.setSoTimeout(800);

        autoReplySettingsService.setGlobalEnabled(true);
        autoReplySettingsService.updateInterfaceSettings("Fruit Interface", true, "localhost", receiver.getLocalPort(), "UDP");

        sendUdp(fruitPort, TestProtocolPayloads.orange("farm", (byte) 1));

        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        assertThatThrownBy(() -> receiver.receive(packet)).isInstanceOf(SocketTimeoutException.class);
    }
}
