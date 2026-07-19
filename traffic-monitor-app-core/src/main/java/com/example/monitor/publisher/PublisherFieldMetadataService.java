package com.example.monitor.publisher;

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

            String fieldName = accessorFieldName(method.getName());
            if (fieldName != null) {
                fields.add(new PublisherFieldDto(fieldName, method.getReturnType().getSimpleName()));
            }
        }

        return fields;
    }

    private String accessorFieldName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        return null;
    }

    private String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
