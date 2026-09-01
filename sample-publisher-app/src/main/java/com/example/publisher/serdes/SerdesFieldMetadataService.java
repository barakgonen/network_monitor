package com.example.publisher.serdes;

import com.example.binaryserdes.ArrayType;
import com.example.binaryserdes.MessageField;
import com.example.binaryserdes.MessageType;
import com.example.binaryserdes.RecordType;
import com.example.binaryserdes.Type;
import com.example.publisher.dto.FieldDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@link MessageType}-walking analogue of {@code com.example.monitor.rest.RestFieldMetadataService}
 * - same flatten-to-dotted-paths / box-up-array-of-struct-fields shape, but reading the serdes
 * {@link Type}/{@link RecordType}/{@link ArrayType} tree instead of an OpenAPI schema tree, since
 * serdes messages have no backing {@code Class<?>} to reflect on at all. Produces the same
 * {@link FieldDto} shape the publisher UI's field-rendering JS already knows how to render.
 */
@Component
public class SerdesFieldMetadataService {

    private static final int MAX_DEPTH = 6;

    public List<FieldDto> describeFields(MessageType messageType) {
        List<FieldDto> out = new ArrayList<>();

        for (MessageField<?> field : messageType.getFields()) {
            describeField(field.getFieldName(), field.getType(), "", 0, out);
        }

        return out;
    }

    private void describeField(String name, Type<?> type, String prefix, int depth, List<FieldDto> out) {
        String qualifiedName = prefix.isEmpty() ? name : prefix + "." + name;

        if (type instanceof ArrayType arrayType) {
            describeArrayField(qualifiedName, arrayType, depth, out);
            return;
        }

        if (type instanceof RecordType recordType && depth < MAX_DEPTH) {
            for (Map.Entry<String, Type<?>> nestedField : recordType.getFields().entrySet()) {
                describeField(nestedField.getKey(), nestedField.getValue(), qualifiedName, depth + 1, out);
            }
            return;
        }

        out.add(new FieldDto(qualifiedName, type.getName()));
    }

    private void describeArrayField(String qualifiedName, ArrayType arrayType, int depth, List<FieldDto> out) {
        Type<?> elementType = arrayType.getElementType();
        String elementLabel = elementType.getName();

        if (depth < MAX_DEPTH && elementType instanceof RecordType recordType) {
            List<FieldDto> itemFields = new ArrayList<>();
            for (Map.Entry<String, Type<?>> itemField : recordType.getFields().entrySet()) {
                describeField(itemField.getKey(), itemField.getValue(), "", depth + 1, itemFields);
            }
            out.add(new FieldDto(qualifiedName, elementLabel + "[]", itemFields, arrayType.getLength()));
            return;
        }

        out.add(new FieldDto(qualifiedName, elementLabel + "[]"));
    }
}
