/**
 * The legacy fixed 16-byte envelope (opcode + send time + body length) shared by the
 * fruit/weather/ping/candy interfaces: {@link com.example.schemacore.envelope.ProtocolHeaderCodec}
 * is the hand-written codec used directly by that ingestion/publish path, and
 * {@link com.example.schemacore.envelope.DefaultEnvelopeHeader} is a reflective POJO mirroring the
 * same layout, used as the default {@code headerType} for dedicated-port interfaces that don't
 * configure a custom header class.
 *
 * <p>Dedicated-port interfaces with their own header layout (e.g. rada) don't use this package at
 * all &mdash; they supply their own header class, parsed via
 * {@code com.example.schemacore.reflect.ReflectiveStructCodec} like any other message.
 */
package com.example.schemacore.envelope;
