package com.sweep.jaksim31.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EmotionType {
    SINGLE("single"),
    DOUBLE("double"),
    TRIPLE("triple"),
    QUAD("quad");

    private static final Map<String, EmotionType> paramMap = Arrays.stream(EmotionType.values())
            .collect(Collectors.toMap(
                    EmotionType::getParam,
                    Function.identity()
            ));
    private final String param;

    EmotionType(String param) {
        this.param = param;
    }

    @JsonCreator
    public static EmotionType fromParam(String param) {
        return Optional.ofNullable(param)
                .map(paramMap::get)
                .orElseThrow(() -> new IllegalArgumentException("param is not valid"));
    }

    @JsonValue
    public String getParam() {
        return this.param;
    }
}