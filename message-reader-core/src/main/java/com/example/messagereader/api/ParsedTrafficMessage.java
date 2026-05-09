package com.example.messagereader.api;

import java.util.Map;

public record ParsedTrafficMessage(
        RawTrafficPacket rawPacket,
        String interfaceName,
        String messageName,
        String messageClassName,
        Object parsedInstance,
        Map<String, Object> headerFields,
        Map<String, Object> bodyFields,
        Object opcode,
        boolean parsed,
        String parseError
) {
}
