package com.example.schema.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines the fixed wire size, in bytes, of an enum field.
 *
 * Valid values:
 * - 1 byte
 * - 2 bytes
 * - 4 bytes
 * - 8 bytes
 *
 * Example:
 *
 * {@code
 * @EnumWireSize(1)
 * private Status status;
 * }
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface EnumWireSize {
    int value();
}
