package com.example.monitor.publisher;

import com.example.schemautils.ReflectionStructSizeCalculator;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public class ReflectionMessageSerializer {

    public byte[] serialize(Object message, ByteOrder byteOrder) {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }

        byte[] direct = tryNoArgByteArraySerialization(message);

        if (direct != null) {
            return direct;
        }

        int size = ReflectionStructSizeCalculator.calculateStructSize(message.getClass());
        ByteBuffer buffer = ByteBuffer.allocate(size).order(byteOrder);

        if (tryByteBufferSerialization(message, buffer)) {
            if (buffer.hasRemaining()) {
                throw new IllegalArgumentException(
                        "Serialization did not fill the allocated buffer. position="
                                + buffer.position()
                                + ", limit="
                                + buffer.limit()
                                + ", remaining="
                                + buffer.remaining()
                );
            }

            return buffer.array();
        }

        throw new IllegalArgumentException(
                "Message class does not expose supported serializer. Expected toByteArray(ByteBuffer), serialize(ByteBuffer), writeTo(ByteBuffer), toByteArray(), or serialize(): "
                        + message.getClass().getName()
        );
    }

    private boolean tryByteBufferSerialization(Object message, ByteBuffer buffer) {
        for (String methodName : java.util.List.of("toByteArray", "serialize", "writeTo")) {
            try {
                Method method = message.getClass().getMethod(methodName, ByteBuffer.class);
                method.setAccessible(true);
                method.invoke(message, buffer);
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed invoking serializer "
                                + message.getClass().getName()
                                + "."
                                + methodName
                                + "(ByteBuffer): "
                                + rootMessage(e),
                        e
                );
            }
        }

        return false;
    }

    private byte[] tryNoArgByteArraySerialization(Object message) {
        for (String methodName : java.util.List.of("toByteArray", "serialize")) {
            try {
                Method method = message.getClass().getMethod(methodName);

                if (method.getReturnType() != byte[].class) {
                    continue;
                }

                method.setAccessible(true);
                return (byte[]) method.invoke(message);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed invoking serializer "
                                + message.getClass().getName()
                                + "."
                                + methodName
                                + "(): "
                                + rootMessage(e),
                        e
                );
            }
        }

        return null;
    }

    private String rootMessage(Exception e) {
        Throwable current = e;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
