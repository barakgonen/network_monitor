package com.example.schemacore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;

/**
 * Reflectively invokes a hand-written codec pair on a message/header class, so the ingestion
 * pipeline and publisher never need per-message decode/encode branches.
 *
 * Decode dispatch (first match wins):
 * - public static T fromByteBuffer(ByteBuffer) — records use this (immutable, no in-place mutation)
 * - public T(byte[]) constructor — mutable structs use this (they parse themselves in the ctor)
 *
 * Encode dispatch (first match wins):
 * - public byte[] toByteArray() — self-sizing, used by variable-length messages (e.g. Strings)
 * - public void toByteArray(ByteBuffer) — fixed-layout messages, buffer sized via StructSizeCalculator
 */
public final class ReflectiveStructCodec {
    private ReflectiveStructCodec() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T decode(Class<T> type, byte[] payload) {
        try {
            Method factory = findStaticFactory(type);
            if (factory != null) {
                Object instance = factory.invoke(null, ByteBuffer.wrap(payload));
                return (T) instance;
            }

            Constructor<T> constructor = findByteArrayConstructor(type);
            if (constructor != null) {
                return constructor.newInstance((Object) payload);
            }

            throw new IllegalArgumentException(
                    "Class does not expose a supported decoder. Expected static fromByteBuffer(ByteBuffer) "
                            + "or a (byte[]) constructor: " + type.getName());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode " + type.getName() + ": " + rootMessage(e), e);
        }
    }

    public static byte[] encode(Object message) {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }

        try {
            byte[] direct = tryNoArgByteArrayEncode(message);
            if (direct != null) {
                return direct;
            }

            Method sizedEncode = findSizedEncodeMethod(message.getClass());
            if (sizedEncode != null) {
                int size = StructSizeCalculator.calculateStructSize(message.getClass());
                ByteBuffer buffer = ByteBuffer.allocate(size);
                sizedEncode.invoke(message, buffer);

                if (buffer.hasRemaining()) {
                    throw new IllegalArgumentException(
                            "Encoding did not fill the allocated buffer. position=" + buffer.position()
                                    + ", limit=" + buffer.limit());
                }

                return buffer.array();
            }

            throw new IllegalArgumentException(
                    "Class does not expose a supported encoder. Expected toByteArray() or toByteArray(ByteBuffer): "
                            + message.getClass().getName());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode " + message.getClass().getName() + ": " + rootMessage(e), e);
        }
    }

    private static Method findStaticFactory(Class<?> type) {
        try {
            Method method = type.getDeclaredMethod("fromByteBuffer", ByteBuffer.class);
            if (Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                return method;
            }
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> findByteArrayConstructor(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(byte[].class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static byte[] tryNoArgByteArrayEncode(Object message) throws Exception {
        try {
            Method method = message.getClass().getDeclaredMethod("toByteArray");
            if (method.getReturnType() != byte[].class) {
                return null;
            }
            method.setAccessible(true);
            return (byte[]) method.invoke(message);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findSizedEncodeMethod(Class<?> type) {
        try {
            Method method = type.getDeclaredMethod("toByteArray", ByteBuffer.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
