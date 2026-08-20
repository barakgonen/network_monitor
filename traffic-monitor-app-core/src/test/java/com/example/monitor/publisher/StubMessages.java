package com.example.monitor.publisher;

import com.example.schemacore.annotation.FixedArrayLength;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Test-only message fixtures standing in for real shared-schemas message classes (candy/rada),
 * so publisher unit tests don't need shared-schemas on the classpath -- shared-schemas
 * compile-depends on this module, so a test dependency back onto it would be circular. Mirror the
 * two shapes {@code ReflectiveStructCodec} supports: a self-sizing record (legacy envelope path)
 * and a mutable class with its own embedded header (dedicated-port path).
 */
final class StubMessages {

    private StubMessages() {
    }

    /** Mirrors {@code CandyMessage}: self-sizing record, exercises the legacy-envelope encode path. */
    public record StubLegacyMessage(String name, double calories) {
        public static StubLegacyMessage fromByteBuffer(ByteBuffer buffer) {
            int nameLength = buffer.getInt();
            byte[] nameBytes = new byte[nameLength];
            buffer.get(nameBytes);
            double calories = buffer.getDouble();
            return new StubLegacyMessage(new String(nameBytes, StandardCharsets.UTF_8), calories);
        }

        public byte[] toByteArray() {
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + nameBytes.length + Double.BYTES);
            buffer.putInt(nameBytes.length);
            buffer.put(nameBytes);
            buffer.putDouble(calories);
            return buffer.array();
        }
    }

    /** Small embedded header, mirrors {@code RadaHeader}'s role inside {@code RadaStatus}. */
    public static class StubHeader {
        private int msgType;

        public int getMsgType() {
            return msgType;
        }

        public void setMsgType(int msgType) {
            this.msgType = msgType;
        }

        void toByteArray(ByteBuffer buffer) {
            buffer.putInt(msgType);
        }

        void fromByteArray(ByteBuffer buffer) {
            buffer.order(ByteOrder.BIG_ENDIAN);
            msgType = buffer.getInt();
        }
    }

    /**
     * Mirrors {@code RadaStatus}: mutable class embedding its own header, exercises the
     * dedicated-port encode path (no {@code ProtocolHeaderCodec} envelope).
     */
    public static class StubDedicatedPortMessage {
        private StubHeader header = new StubHeader();
        private int radarSoftwareVersion;
        private short statusFlags;

        public StubDedicatedPortMessage() {
        }

        public StubDedicatedPortMessage(byte[] bytes) {
            fromByteArray(ByteBuffer.wrap(bytes));
        }

        public StubHeader getHeader() {
            return header;
        }

        public void setHeader(StubHeader header) {
            this.header = header;
        }

        public int getRadarSoftwareVersion() {
            return radarSoftwareVersion;
        }

        public void setRadarSoftwareVersion(int radarSoftwareVersion) {
            this.radarSoftwareVersion = radarSoftwareVersion;
        }

        public int getStatusFlags() {
            return statusFlags;
        }

        public void setStatusFlags(int statusFlags) {
            this.statusFlags = (short) statusFlags;
        }

        void toByteArray(ByteBuffer buffer) {
            header.toByteArray(buffer);
            buffer.putInt(radarSoftwareVersion);
            buffer.putShort(statusFlags);
        }

        void fromByteArray(ByteBuffer buffer) {
            buffer.order(ByteOrder.BIG_ENDIAN);
            header.fromByteArray(buffer);
            radarSoftwareVersion = buffer.getInt();
            statusFlags = buffer.getShort();
        }
    }

    /** Small embedded struct, mirrors {@code RadaTrackData}'s role inside {@code trackData[]}. */
    public static class StubTrackData {
        private int id;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    /**
     * Mirrors {@code RadaTracksExtended}: a fixed-length array of embedded structs, exercises
     * the array-of-struct publisher metadata/apply path.
     */
    public static class StubArrayMessage {
        @FixedArrayLength(3)
        private StubTrackData[] trackData = new StubTrackData[]{
                new StubTrackData(), new StubTrackData(), new StubTrackData()};

        public StubTrackData[] getTrackData() {
            return trackData;
        }

        public void setTrackData(StubTrackData[] trackData) {
            this.trackData = trackData;
        }
    }
}
