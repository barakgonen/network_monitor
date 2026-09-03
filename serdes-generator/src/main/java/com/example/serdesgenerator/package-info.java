/**
 * Reflects over Java message classes and generates the equivalent {@code binary-serdes}
 * {@code *.protocol.json} file, so classes following the "fixed-layout" reflective codec
 * convention (see {@code com.example.schemacore.reflect.ReflectiveStructCodec}/
 * {@code StructSizeCalculator}) don't need their wire format hand-described twice.
 *
 * Scope: only classes {@code StructSizeCalculator} can size - every field is a fixed scalar, a
 * {@code @FixedArrayLength} array, an enum, or a nested struct; no {@code String} fields.
 * Variable-length hand-coded message classes (ones with a {@code String} field, forced into a
 * self-sizing {@code toByteArray()}) are out of scope by design: their wire layout - including
 * whether a given enum field is written as a numeric code or as its string wire-name - is a fact
 * about hand-written method bodies, not something derivable from the class's declared shape.
 * {@code boolean} fields are also unsupported - {@code binary-serdes} has no built-in wire type
 * for them today.
 */
package com.example.serdesgenerator;
