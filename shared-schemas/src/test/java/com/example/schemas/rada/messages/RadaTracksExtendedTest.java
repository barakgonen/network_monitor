package com.example.schemas.rada.messages;

import com.example.schemacore.ReflectiveStructCodec;
import com.example.schemacore.StructSizeCalculator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class RadaTracksExtendedTest {

    @Test
    void toByteArray_thenConstructor_roundTripsNestedArraysOfStructs() {
        RadaTracksExtended original = new RadaTracksExtended();
        original.getHeader().setMsgCounter(1);
        original.setNumberOfTracksInThisMessage(3);
        original.getTrackData()[0].setId(123);
        original.getTrackData()[0].setPosX(4.5f);
        original.getPlotData()[2].setRange(9.75f);

        int size = StructSizeCalculator.calculateStructSize(RadaTracksExtended.class);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        original.toByteArray(buffer);

        assertThat(buffer.position()).isEqualTo(size);

        RadaTracksExtended decoded = new RadaTracksExtended(buffer.array());

        assertThat(decoded.getHeader().getMsgCounter()).isEqualTo(1);
        assertThat(decoded.getNumberOfTracksInThisMessage()).isEqualTo(3);
        assertThat(decoded.getTrackData()[0].getId()).isEqualTo(123);
        assertThat(decoded.getTrackData()[0].getPosX()).isEqualTo(4.5f);
        assertThat(decoded.getPlotData()[2].getRange()).isEqualTo(9.75f);
    }

    @Test
    void reflectiveStructCodec_encodeThenDecode_roundTrips() {
        RadaTracksExtended original = new RadaTracksExtended();
        original.setUpdateTimeTag(999L);

        byte[] encoded = ReflectiveStructCodec.encode(original);
        RadaTracksExtended decoded = ReflectiveStructCodec.decode(RadaTracksExtended.class, encoded);

        assertThat(decoded.getUpdateTimeTag()).isEqualTo(999L);
    }
}
