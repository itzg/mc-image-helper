package me.itzg.helpers.patch;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static me.itzg.helpers.patch.ValueTypeConverter.*;
import static org.assertj.core.api.Assertions.assertThat;

class ValueTypeConverterTest {

    @ParameterizedTest
    @MethodSource("successfullyConvertsArgs")
    void successfullyConverts(String typeName, String value, Object expected) {
        final ValueTypeConverter converter = new ValueTypeConverter(typeName);

        final Object result = converter.convert(value);
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> successfullyConvertsArgs() {
        return Stream.of(
                Arguments.of(TYPE_INT, "5", 5L),
                Arguments.of(TYPE_FLOAT, "5.1", 5.1D),
                Arguments.of(TYPE_BOOL, "true", Boolean.TRUE),
                Arguments.of(TYPE_AUTO, "5", 5L),
                Arguments.of(TYPE_AUTO, "5.1", 5.1D),
                Arguments.of(TYPE_AUTO, "true", Boolean.TRUE),
                Arguments.of(TYPE_AUTO, "yes", Boolean.TRUE),
                Arguments.of(TYPE_AUTO, "no", Boolean.FALSE),
                Arguments.of(TYPE_AUTO, "false", Boolean.FALSE),
                Arguments.of(TYPE_AUTO, "something else", "something else"),
                Arguments.of(null, "5", "5"),
                Arguments.of(null, "true", "true"),
                Arguments.of(null, "something else", "something else"),
                Arguments.of("list of int", "5,6,7", Arrays.asList(5L,6L,7L)),
                Arguments.of("list of float", "5.1,6.2,7.3", Arrays.asList(5.1D, 6.2D, 7.3D)),
                Arguments.of("list of bool", "true,yes,no,false", Arrays.asList(true,true,false,false)),
                Arguments.of("list of string", "5,6.2,testing,false", Arrays.asList("5","6.2","testing","false"))
        );
    }

}