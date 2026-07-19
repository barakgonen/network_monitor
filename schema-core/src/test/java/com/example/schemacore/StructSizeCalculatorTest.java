package com.example.schemacore;

import com.example.schemaannotations.EnumWireSize;
import com.example.schemaannotations.FixedArrayLength;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructSizeCalculatorTest {

    enum Status {
        READY,
        FAILED
    }

    static class Header {
        private short msgType;
        private short msgSize;
        private byte version;
    }

    static class HeaderWithDefaultEnum {
        private short msgType;
        private Status status;
    }

    static class HeaderWithByteEnum {
        private short msgType;

        @EnumWireSize(1)
        private Status status;
    }

    static class HeaderWithShortEnum {
        private short msgType;

        @EnumWireSize(2)
        private Status status;
    }

    static class Nested {
        private Header header = new Header();
        private double latitude;
        private float heading;
    }

    static class Item {
        private int id;
        private float value;
    }

    static class WithArray {
        private Header header = new Header();

        @FixedArrayLength(10)
        private Item[] items;
    }

    static class WithEnumArray {
        @FixedArrayLength(3)
        @EnumWireSize(1)
        private Status[] statuses;
    }

    static class MissingArrayLength {
        private Item[] items;
    }

    static class InvalidEnumWireSize {
        @EnumWireSize(3)
        private Status status;
    }

    @Test
    void calculatesPrimitiveStructSize() {
        assertEquals(5, StructSizeCalculator.calculateStructSize(Header.class));
    }

    @Test
    void calculatesNestedStructSize() {
        assertEquals(5 + 8 + 4, StructSizeCalculator.calculateStructSize(Nested.class));
    }

    @Test
    void calculatesFixedComplexArraySize() {
        assertEquals(5 + (10 * (4 + 4)), StructSizeCalculator.calculateStructSize(WithArray.class));
    }

    @Test
    void calculatesDefaultEnumAsIntSize() {
        assertEquals(2 + 4, StructSizeCalculator.calculateStructSize(HeaderWithDefaultEnum.class));
    }

    @Test
    void calculatesEnumWithByteWireSize() {
        assertEquals(2 + 1, StructSizeCalculator.calculateStructSize(HeaderWithByteEnum.class));
    }

    @Test
    void calculatesEnumWithShortWireSize() {
        assertEquals(2 + 2, StructSizeCalculator.calculateStructSize(HeaderWithShortEnum.class));
    }

    @Test
    void calculatesEnumArrayWithConfiguredWireSize() {
        assertEquals(3, StructSizeCalculator.calculateStructSize(WithEnumArray.class));
    }

    @Test
    void failsForInvalidEnumWireSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StructSizeCalculator.calculateStructSize(InvalidEnumWireSize.class)
        );
    }

    @Test
    void failsForArrayWithoutFixedLengthAnnotation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StructSizeCalculator.calculateStructSize(MissingArrayLength.class)
        );
    }

    @Test
    void calculatesByClassName() {
        assertEquals(5, StructSizeCalculator.calculateStructSize(Header.class.getName()));
    }
}
