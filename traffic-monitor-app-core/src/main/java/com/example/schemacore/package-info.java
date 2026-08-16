/**
 * Core, protocol-agnostic message model: {@link com.example.schemacore.MessageDefinition}
 * (per-message opcode/codec contract) and {@link com.example.schemacore.MessageDefinitionRegistry}
 * (lookup by opcode / interface+type / message class). Message classes are plain {@code Object}s -
 * no marker interface required - identified purely by {@code Class<?>} and by following the
 * reflective codec convention (see {@link com.example.schemacore.reflect}), so classes owned by
 * an external dependency can be wired in without depending on this engine at all.
 *
 * <p>Sibling packages build on these types: {@link com.example.schemacore.annotation} declares the
 * wire-layout annotations, {@link com.example.schemacore.envelope} implements the legacy fixed
 * envelope, and {@link com.example.schemacore.reflect} implements the reflective codec engine that
 * produces {@code MessageDefinition} instances from plain message classes.
 */
package com.example.schemacore;
