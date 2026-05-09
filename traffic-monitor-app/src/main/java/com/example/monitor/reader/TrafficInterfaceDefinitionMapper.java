package com.example.monitor.reader;

import com.example.messagereader.api.TrafficInterfaceDefinition;
import com.example.messagereader.api.TrafficMessageDefinition;
import com.example.messagereader.api.TransportProtocol;
import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TrafficInterfaceDefinitionMapper {
    public TrafficInterfaceDefinition toReaderDefinition(TrafficMonitorProperties.ReflectionInterface source) {
        TrafficInterfaceDefinition target = new TrafficInterfaceDefinition();

        target.setName(source.getName());
        target.setEnabled(source.isEnabled());
        target.setProtocol(parseProtocol(source.getProtocol()));
        target.setPort(source.getPort());
        target.setByteOrder(source.getByteOrder());
        target.setHeaderType(source.getHeaderType());
        target.setOpcodeFieldName(source.getOpcodeFieldName());

        Map<String, TrafficMessageDefinition> messages = new LinkedHashMap<>();

        for (Map.Entry<String, TrafficMonitorProperties.SupportedMessage> entry : source.getSupportedMessages().entrySet()) {
            messages.put(
                    entry.getKey(),
                    new TrafficMessageDefinition(
                            entry.getValue().getMessageClass(),
                            entry.getValue().getDisplayName()
                    )
            );
        }

        target.setSupportedMessages(messages);
        return target;
    }

    private TransportProtocol parseProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return TransportProtocol.UDP;
        }

        return TransportProtocol.valueOf(protocol.trim().toUpperCase());
    }
}
