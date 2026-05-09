package com.example.messagereader.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ReflectionObjectParser {
    public Object parseHeader(String headerClassName, byte[] headerBytes, ByteOrder byteOrder) {
        try {
            return parseFromByteBuffer(headerClassName, headerBytes, byteOrder, true);
        } catch (Exception byteBufferFailure) {
            return parseFromByteArrayFallback(headerClassName, headerBytes);
        }
    }

    public Object parseMessage(String messageClassName, byte[] payload, ByteOrder byteOrder) {
        try {
            return parseFromByteBuffer(messageClassName, payload, byteOrder, true);
        } catch (Exception byteBufferFailure) {
            return parseFromByteArrayFallback(messageClassName, payload);
        }
    }

    public Object parseFromByteBuffer(String className, byte[] payload, ByteOrder byteOrder, boolean requireFullConsumption) {
        try {
            Class<?> type = Class.forName(className);
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(byteOrder);

            Object instance = tryByteBufferConstructor(type, buffer);

            if (instance == null) {
                for (String methodName : java.util.List.of("parse", "fromBytes", "fromByteArray", "fromByteBuffer")) {
                    buffer = ByteBuffer.wrap(payload).order(byteOrder);
                    instance = tryStaticByteBufferFactory(type, methodName, buffer);

                    if (instance != null) {
                        break;
                    }
                }
            }

            if (instance == null) {
                throw new IllegalArgumentException("Class does not expose ByteBuffer parser: " + className);
            }

            if (requireFullConsumption && buffer.hasRemaining()) {
                throw new IllegalArgumentException(
                        "Partial parse rejected for "
                                + className
                                + ". consumedBytes="
                                + buffer.position()
                                + ", payloadBytes="
                                + payload.length
                                + ", remainingBytes="
                                + buffer.remaining()
                );
            }

            return instance;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse " + className + ": " + rootMessage(e), e);
        }
    }

    public Object parseFromByteArrayFallback(String className, byte[] payload) {
        try {
            Class<?> type = Class.forName(className);

            Object instance = tryByteArrayConstructor(type, payload);

            if (instance != null) {
                return instance;
            }

            for (String methodName : java.util.List.of("parse", "fromBytes", "fromByteArray")) {
                instance = tryStaticByteArrayFactory(type, methodName, payload);

                if (instance != null) {
                    return instance;
                }
            }

            throw new IllegalArgumentException("Class does not expose byte[] parser: " + className);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse " + className + ": " + rootMessage(e), e);
        }
    }

    private Object tryByteBufferConstructor(Class<?> type, ByteBuffer buffer) throws Exception {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(ByteBuffer.class);
            constructor.setAccessible(true);
            return constructor.newInstance(buffer);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Object tryStaticByteBufferFactory(Class<?> type, String methodName, ByteBuffer buffer) throws Exception {
        try {
            Method method = type.getDeclaredMethod(methodName, ByteBuffer.class);

            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }

            method.setAccessible(true);
            Object value = method.invoke(null, buffer);

            if (value == null) {
                throw new IllegalArgumentException("Factory method returned null: " + methodName);
            }

            return value;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Object tryByteArrayConstructor(Class<?> type, byte[] payload) throws Exception {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(byte[].class);
            constructor.setAccessible(true);
            return constructor.newInstance((Object) payload);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Object tryStaticByteArrayFactory(Class<?> type, String methodName, byte[] payload) throws Exception {
        try {
            Method method = type.getDeclaredMethod(methodName, byte[].class);

            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }

            method.setAccessible(true);
            Object value = method.invoke(null, (Object) payload);

            if (value == null) {
                throw new IllegalArgumentException("Factory method returned null: " + methodName);
            }

            return value;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private String rootMessage(Exception e) {
        Throwable current = e;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
