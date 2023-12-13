package com.swirlds.config.impl.converters;

import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class EnumConverter<T> implements ConfigConverter<T> {

    private final Class<T> valueType;

    public EnumConverter(Class<T> valueType) {
        this.valueType = valueType;
    }

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public T convert(@NonNull String value) throws IllegalArgumentException, NullPointerException {
        try {
            return (T) Enum.valueOf((Class<Enum>) valueType, value);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Can not convert value '%s' of Enum '%s' by default. Please add a custom config converter."
                            .formatted(value, valueType),
                    e);
        }
    }
}
