package com.example.messagereader.transport;

import com.example.messagereader.api.RawTrafficPacket;

@FunctionalInterface
public interface RawPacketHandler {
    void onPacket(RawTrafficPacket packet);
}
