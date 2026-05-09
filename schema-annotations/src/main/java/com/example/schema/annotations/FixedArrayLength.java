package com.example.schema.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines the fixed number of elements for a binary-schema array field.
 *
 * Example:
 *
 * {@code
 * @FixedArrayLength(10)
 * private TrackData[] tracks;
 * }
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface FixedArrayLength {
    int value();
}
