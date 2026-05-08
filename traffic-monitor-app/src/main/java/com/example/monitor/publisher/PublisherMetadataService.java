package com.example.monitor.publisher;

import com.example.monitor.api.publisher.PublisherInterfaceDto;
import com.example.monitor.api.publisher.PublisherMessageDto;
import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PublisherMetadataService {
    private final TrafficMonitorProperties properties;

    public PublisherMetadataService(TrafficMonitorProperties properties) {
        this.properties = properties;
    }

    public List<PublisherInterfaceDto> interfaces() {
        return properties.getReflectionInterfaces()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public TrafficMonitorProperties.ReflectionInterface requireInterface(String interfaceName) {
        return properties.getReflectionInterfaces()
                .stream()
                .filter(item -> item.getName().equals(interfaceName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown interface: " + interfaceName));
    }

    public TrafficMonitorProperties.SupportedMessage requireMessage(
            TrafficMonitorProperties.ReflectionInterface reflectionInterface,
            String opcode
    ) {
        if (opcode == null || opcode.isBlank()) {
            throw new IllegalArgumentException("opcode is required");
        }

        TrafficMonitorProperties.SupportedMessage message =
                reflectionInterface.getSupportedMessages().get(opcode);

        if (message == null) {
            throw new IllegalArgumentException(
                    "Unsupported opcode "
                            + opcode
                            + " for interface "
                            + reflectionInterface.getName()
                            + ". supportedOpcodes="
                            + reflectionInterface.getSupportedMessages().keySet()
            );
        }

        return message;
    }

    private PublisherInterfaceDto toDto(TrafficMonitorProperties.ReflectionInterface reflectionInterface) {
        List<PublisherMessageDto> messages = reflectionInterface.getSupportedMessages()
                .entrySet()
                .stream()
                .map(this::toMessageDto)
                .toList();

        return new PublisherInterfaceDto(
                reflectionInterface.getName(),
                reflectionInterface.getProtocol(),
                reflectionInterface.getPort(),
                reflectionInterface.getByteOrder(),
                reflectionInterface.getHeaderType(),
                reflectionInterface.getOpcodeFieldName(),
                messages
        );
    }

    private PublisherMessageDto toMessageDto(Map.Entry<String, TrafficMonitorProperties.SupportedMessage> entry) {
        TrafficMonitorProperties.SupportedMessage value = entry.getValue();

        String displayName = value.getDisplayName();

        if (displayName == null || displayName.isBlank()) {
            displayName = simpleName(value.getMessageClass());
        }

        return new PublisherMessageDto(
                entry.getKey(),
                displayName,
                value.getMessageClass()
        );
    }

    private String simpleName(String className) {
        if (className == null) {
            return "";
        }

        int index = className.lastIndexOf('.');
        return index >= 0 ? className.substring(index + 1) : className;
    }
}
