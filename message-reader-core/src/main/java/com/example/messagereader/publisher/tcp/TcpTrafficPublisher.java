package com.example.messagereader.publisher.tcp;

import com.example.messagereader.api.MessageReaderException;
import com.example.messagereader.api.PublishTarget;
import com.example.messagereader.api.TrafficPublisher;
import com.example.messagereader.api.TransportProtocol;

import java.io.OutputStream;
import java.net.Socket;

public class TcpTrafficPublisher implements TrafficPublisher {
    @Override
    public void publish(PublishTarget target, byte[] payload) {
        if (target.protocol() != TransportProtocol.TCP) {
            throw new IllegalArgumentException("TcpTrafficPublisher supports only TCP targets");
        }

        byte[] safePayload = payload == null ? new byte[0] : payload;

        try (Socket socket = new Socket(target.host(), target.port());
             OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(safePayload);
            outputStream.flush();
        } catch (Exception e) {
            throw new MessageReaderException("Failed sending TCP payload to " + target.host() + ":" + target.port(), e);
        }
    }
}
