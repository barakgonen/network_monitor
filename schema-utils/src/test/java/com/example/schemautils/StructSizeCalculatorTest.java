package com.example.schemautils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructSizeCalculatorTest {

    static class Header {
        private short msgType;
        private short msgSize;
        private byte version;
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

        @StructSizeCalculator.FixedArrayLength(10)
        private Item[] items;
    }

    static class MissingArrayLength {
        private Item[] items;
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
    void failsForArrayWithoutFixedLengthAnnotation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StructSizeCalculator.calculateStructSize(MissingArrayLength.class)
        );
    }

    @Test
    void calculatesByClassName() {
        assertEquals(5, ReflectionStructSizeCalculator.calculateStructSize(Header.class.getName()));
    }
}
