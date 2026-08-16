package com.example.schemacore.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reflectively invokes a hand-written codec pair on a message/header class, so the ingestion
 * pipeline and publisher never need per-message decode/encode branches.
 *
 * Decode dispatch (first match wins):
 * - public static T fromByteBuffer(ByteBuffer) — records use this (immutable, no in-place mutation);
 *   the codec wraps the payload and applies the requested {@link ByteOrder} to the buffer itself
 * - public T(byte[], ByteOrder) constructor — order-aware mutable structs (self-parsing classes that
 *   want to honor a caller-supplied byte order, e.g. the rada messages)
 * - public T(byte[]) constructor — mutable structs that don't care about byte order (fixed to
 *   whatever order they hardcode internally, historically always big-endian)
 *
 * Encode dispatch (first match wins):
 * - public byte[] toByteArray(ByteOrder) — self-sizing, order-aware
 * - public byte[] toByteArray() no-arg — self-sizing, used when a field is variable-length (e.g. a
 *   String) and the class doesn't care about byte order
 * - public void toByteArray(ByteBuffer) — fixed-layout messages, buffer pre-sized via
 *   StructSizeCalculator and given the requested {@link ByteOrder} by the codec before invoking
 */
public final class ReflectiveStructCodec {
    private ReflectiveStructCodec() {
    }

    public static <T> T decode(Class<T> type, byte[] payload) {
        return decode(type, payload, ByteOrder.BIG_ENDIAN);
    }

    @SuppressWarnings("unchecked")
    public static <T> T decode(Class<T> type, byte[] payload, ByteOrder byteOrder) {
        try {
            Method factory = findStaticFactory(type);
            if (factory != null) {
                ByteBuffer buffer = ByteBuffer.wrap(payload).order(byteOrder);
                Object instance = factory.invoke(null, buffer);
                return (T) instance;
            }

            Constructor<T> orderAwareConstructor = findByteArrayByteOrderConstructor(type);
            if (orderAwareConstructor != null) {
                return orderAwareConstructor.newInstance(payload, byteOrder);
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
        return encode(message, ByteOrder.BIG_ENDIAN);
    }

    public static byte[] encode(Object message, ByteOrder byteOrder) {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }

        try {
            byte[] orderAware = tryOrderAwareByteArrayEncode(message, byteOrder);
            if (orderAware != null) {
                return orderAware;
            }

            byte[] direct = tryNoArgByteArrayEncode(message);
            if (direct != null) {
                return direct;
            }

            Method sizedEncode = findSizedEncodeMethod(message.getClass());
            if (sizedEncode != null) {
                int size = StructSizeCalculator.calculateStructSize(message.getClass());
                ByteBuffer buffer = ByteBuffer.allocate(size).order(byteOrder);
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
    private static <T> Constructor<T> findByteArrayByteOrderConstructor(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(byte[].class, ByteOrder.class);
            constructor.setAccessible(true);
            return constructor;
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

    private static byte[] tryOrderAwareByteArrayEncode(Object message, ByteOrder byteOrder) throws Exception {
        try {
            Method method = message.getClass().getDeclaredMethod("toByteArray", ByteOrder.class);
            if (method.getReturnType() != byte[].class) {
                return null;
            }
            method.setAccessible(true);
            return (byte[]) method.invoke(message, byteOrder);
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
