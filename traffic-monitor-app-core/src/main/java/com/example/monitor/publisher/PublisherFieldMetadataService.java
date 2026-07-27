package com.example.monitor.publisher;

import com.example.schemacore.reflect.AccessorNames;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflects a message class's fields (name + simple type) without needing an instance, so the
 * generic publisher UI can build an editable form before the user has entered any values.
 */
@Component
public class PublisherFieldMetadataService {

    public List<PublisherFieldDto> describeFields(Class<?> messageClass) {
        if (messageClass.isRecord()) {
            return describeRecordFields(messageClass);
        }
        return describeGetterFields(messageClass);
    }

    private List<PublisherFieldDto> describeRecordFields(Class<?> type) {
        List<PublisherFieldDto> fields = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            fields.add(new PublisherFieldDto(component.getName(), component.getType().getSimpleName()));
        }
        return fields;
    }

    private List<PublisherFieldDto> describeGetterFields(Class<?> type) {
        List<PublisherFieldDto> fields = new ArrayList<>();

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }

            String fieldName = AccessorNames.fromAccessor(method.getName());
            if (fieldName != null) {
                fields.add(new PublisherFieldDto(fieldName, method.getReturnType().getSimpleName()));
            }
        }

        return fields;
    }
}
