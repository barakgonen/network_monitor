package com.example.serdesgenerator;

import com.example.binaryserdes.config.ProtocolConfig;
import com.example.serdesgenerator.fixtures.AllScalarTypesMessage;
import com.example.serdesgenerator.fixtures.ArrayLengthInferredMessage;
import com.example.serdesgenerator.fixtures.ArrayOfStructsMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelfCheckValidatorTest {

    private final ProtocolJsonGenerator generator = new ProtocolJsonGenerator();
    private final SelfCheckValidator validator = new SelfCheckValidator();

    @Test
    void passesForCorrectlyGeneratedScalarMessage() {
        List<ProtocolJsonGenerator.RootMessage> roots = List.of(
                new ProtocolJsonGenerator.RootMessage(AllScalarTypesMessage.class, 1));
        ProtocolConfig config = generator.generate(roots);

        assertThat(validator.validate(config, roots)).isEmpty();
    }

    @Test
    void passesForCorrectlyGeneratedMessageWithArraysAndNestedStructs() {
        List<ProtocolJsonGenerator.RootMessage> roots = List.of(
                new ProtocolJsonGenerator.RootMessage(ArrayOfStructsMessage.class, 1));
        ProtocolConfig config = generator.generate(roots);

        assertThat(validator.validate(config, roots)).isEmpty();
    }

    @Test
    void passesForMessageWhoseArrayLengthWasInferredRatherThanAnnotated() {
        // StructSizeCalculator resolves array length the same way the generator does (shared
        // StructSizeCalculator.resolveArrayLength), so a message accepted via default-constructor
        // inference still gets a real, independently-derived size to cross-check against.
        List<ProtocolJsonGenerator.RootMessage> roots = List.of(
                new ProtocolJsonGenerator.RootMessage(ArrayLengthInferredMessage.class, 1));
        ProtocolConfig config = generator.generate(roots);

        assertThat(validator.validate(config, roots)).isEmpty();
    }
}
