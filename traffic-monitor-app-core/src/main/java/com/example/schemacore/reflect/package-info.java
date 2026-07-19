/**
 * The reflective codec engine: lets a message class be wired up as a
 * {@link com.example.schemacore.MessageDefinition} by convention alone, without a hand-written
 * definition/codec pair.
 *
 * <ul>
 *   <li>{@link com.example.schemacore.reflect.ReflectiveStructCodec} decodes/encodes a message
 *       instance against its own {@code fromByteBuffer}/{@code toByteArray}-style methods.</li>
 *   <li>{@link com.example.schemacore.reflect.StructSizeCalculator} computes the fixed wire size
 *       of a class from its field layout, for the sized {@code toByteArray(ByteBuffer)} encode
 *       path.</li>
 *   <li>{@link com.example.schemacore.reflect.ReflectiveFieldExtractor} and
 *       {@link com.example.schemacore.reflect.ReflectiveFieldApplier} convert between a message
 *       instance and a generic {@code Map<String, Object>}, for archival/analytics and the
 *       generic publisher UI.</li>
 *   <li>{@link com.example.schemacore.reflect.ReflectiveMessageDefinition} composes the above into
 *       a single {@code MessageDefinition}, replacing what would otherwise be a hand-written class
 *       per message type.</li>
 * </ul>
 */
package com.example.schemacore.reflect;
