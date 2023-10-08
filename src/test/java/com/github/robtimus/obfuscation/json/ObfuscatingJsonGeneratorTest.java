/*
 * ObfuscatingJsonGeneratorTest.java
 * Copyright 2020 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.obfuscation.json;

import static com.github.robtimus.obfuscation.Obfuscator.fixedLength;
import static com.github.robtimus.obfuscation.Obfuscator.none;
import static com.github.robtimus.obfuscation.json.JSONObfuscatorTest.createObfuscator;
import static com.github.robtimus.obfuscation.json.JSONObfuscatorTest.readResource;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.appendAtMost;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.writer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Supplier;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.json.JSONObfuscator.PropertyConfigurer.ObfuscationMode;

@SuppressWarnings("nls")
class ObfuscatingJsonGeneratorTest {

    @Nested
    @DisplayName("obfuscating all")
    class ObfuscatingAll extends GeneratorTest {

        ObfuscatingAll() {
            super(JSONObfuscator::builder, fixedLength(3), fixedLength(3, 'o'), fixedLength(3, 'a'),
                    "JSONObfuscator.expected.valid.all.pretty-printed");
        }
    }

    @Nested
    @DisplayName("obfuscating with INHERIT mode")
    class ObfuscatingInherited extends GeneratorTest {

        ObfuscatingInherited() {
            super(() -> JSONObfuscator.builder().forObjectsByDefault(ObfuscationMode.INHERIT).forArraysByDefault(ObfuscationMode.INHERIT),
                    fixedLength(3), fixedLength(3, 'o'), fixedLength(3, 'a'), "JSONObfuscator.expected.valid.inherited");
        }
    }

    @Nested
    @DisplayName("obfuscating with INHERIT_OVERRIDABLE mode")
    class ObfuscatingInheritedOverridable extends GeneratorTest {

        ObfuscatingInheritedOverridable() {
            super(() -> JSONObfuscator.builder()
                    .forObjectsByDefault(ObfuscationMode.INHERIT_OVERRIDABLE)
                    .forArraysByDefault(ObfuscationMode.INHERIT_OVERRIDABLE),
                    fixedLength(3), fixedLength(3, 'o'), fixedLength(3, 'a'), "JSONObfuscator.expected.valid.inherited-overridable");
        }
    }

    @Nested
    @DisplayName("obfuscating none")
    class ObfuscatingNone extends GeneratorTest {

        ObfuscatingNone() {
            super(JSONObfuscator::builder, none(), none(), none(), "JSONObfuscator.expected.valid.obfuscating-none.pretty-printed");
        }
    }

    abstract static class GeneratorTest {

        private final Supplier<JSONObfuscator.Builder> builderSupplier;
        private final Obfuscator propertyObfuscator;
        private final Obfuscator objectPropertyObfuscator;
        private final Obfuscator arrayPropertyObfuscator;
        private final String expectedResource;

        GeneratorTest(Supplier<JSONObfuscator.Builder> builderSupplier,
                Obfuscator propertyObfuscator, Obfuscator objectPropertyObfuscator, Obfuscator arrayPropertyObfuscator,
                String expectedResource) {

            this.builderSupplier = builderSupplier;
            this.propertyObfuscator = propertyObfuscator;
            this.objectPropertyObfuscator = objectPropertyObfuscator;
            this.arrayPropertyObfuscator = arrayPropertyObfuscator;
            this.expectedResource = expectedResource;
        }

        @Test
        @DisplayName("using int and double")
        @SuppressWarnings("resource")
        void testUsingIntAndDouble() {
            StringBuilder destination = new StringBuilder();
            try (JsonGenerator jsonGenerator = createJsonGenerator(destination)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");

                jsonGenerator.writeStartObject("nonMatchedObject");
                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // "nonMatchedObject" object

                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array

                jsonGenerator.writeStartObject("notObfuscated");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // "notObfuscated" object
                jsonGenerator.writeEnd(); // root object

                jsonGenerator.flush();
            }
            assertObfuscated(destination.toString());
        }

        @Test
        @DisplayName("using long and double")
        @SuppressWarnings("resource")
        void testUsingLongAndDouble() {
            StringBuilder destination = new StringBuilder();
            try (JsonGenerator jsonGenerator = createJsonGenerator(destination)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456L);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456L);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456L);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");

                jsonGenerator.writeStartObject("nonMatchedObject");
                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456L);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // "nonMatchedObject" object

                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456L);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456L);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456L);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array

                jsonGenerator.writeStartObject("notObfuscated");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456L);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", 123456L);
                jsonGenerator.write("float", 1234.56);
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", 123456L);
                jsonGenerator.write("notMatchedFloat", 1234.56);
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // "notObfuscated" object
                jsonGenerator.writeEnd(); // root object

                jsonGenerator.flush();
            }
            assertObfuscated(destination.toString());
        }

        @Test
        @DisplayName("using BigInteger and BigDecimal")
        @SuppressWarnings("resource")
        void testUsingBigIntegerAndBigDecimal() {
            StringBuilder destination = new StringBuilder();
            try (JsonGenerator jsonGenerator = createJsonGenerator(destination)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", BigInteger.valueOf(123456));
                jsonGenerator.write("float", new BigDecimal("1234.56"));
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", BigInteger.valueOf(123456));
                jsonGenerator.write("float", new BigDecimal("1234.56"));
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", BigInteger.valueOf(123456));
                jsonGenerator.write("notMatchedFloat", new BigDecimal("1234.56"));
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");

                jsonGenerator.writeStartObject("nonMatchedObject");
                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", BigInteger.valueOf(123456));
                jsonGenerator.write("notMatchedFloat", new BigDecimal("1234.56"));
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // "nonMatchedObject" object

                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", BigInteger.valueOf(123456));
                jsonGenerator.write("float", new BigDecimal("1234.56"));
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", BigInteger.valueOf(123456));
                jsonGenerator.write("float", new BigDecimal("1234.56"));
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", BigInteger.valueOf(123456));
                jsonGenerator.write("notMatchedFloat", new BigDecimal("1234.56"));
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array

                jsonGenerator.writeStartObject("notObfuscated");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", BigInteger.valueOf(123456));
                jsonGenerator.write("float", new BigDecimal("1234.56"));
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeNull("null");

                jsonGenerator.writeStartObject("object");
                jsonGenerator.write("string", "string\"int");
                jsonGenerator.write("int", BigInteger.valueOf(123456));
                jsonGenerator.write("float", new BigDecimal("1234.56"));
                jsonGenerator.write("booleanTrue", true);
                jsonGenerator.write("booleanFalse", false);
                jsonGenerator.writeStartArray("nested");
                jsonGenerator.writeStartObject();
                jsonGenerator.write("prop1", "1");
                jsonGenerator.write("prop2", "2");
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "nested" array
                jsonGenerator.writeEnd(); // "object" object

                jsonGenerator.writeStartArray("array");
                jsonGenerator.writeStartArray();
                jsonGenerator.write("1");
                jsonGenerator.write("2");
                jsonGenerator.writeEnd(); // anonymous array
                jsonGenerator.writeStartObject();
                jsonGenerator.writeEnd(); // anonymous object
                jsonGenerator.writeEnd(); // "array" array

                jsonGenerator.write("notMatchedString", "123456");
                jsonGenerator.write("notMatchedInt", BigInteger.valueOf(123456));
                jsonGenerator.write("notMatchedFloat", new BigDecimal("1234.56"));
                jsonGenerator.write("notMatchedBooleanTrue", true);
                jsonGenerator.write("notMatchedBooleanFalse", false);
                jsonGenerator.writeNull("nonMatchedNull");
                jsonGenerator.writeEnd(); // "notObfuscated" object
                jsonGenerator.writeEnd(); // root object

                jsonGenerator.flush();
            }
            assertObfuscated(destination.toString());
        }

        @Test
        @DisplayName("using JsonValue")
        @SuppressWarnings("resource")
        void testUsingJsonValue() {
            String input = readResource("JSONObfuscator.input.valid.json");

            StringBuilder destination = new StringBuilder();
            try (JsonParser jsonParser = Json.createParser(new StringReader(input));
                    JsonGenerator jsonGenerator = createJsonGenerator(destination)) {

                assertTrue(jsonParser.hasNext());
                assertEquals(Event.START_OBJECT, jsonParser.next());
                JsonObject jsonObject = jsonParser.getObject();
                // This will recursively write all nested JsonValue objects
                jsonGenerator.write(jsonObject);

                jsonGenerator.flush();
            }
            assertObfuscated(destination.toString());
        }

        @SuppressWarnings("resource")
        private JsonGenerator createJsonGenerator(StringBuilder destination) {
            JSONObfuscatorWriter writer = new JSONObfuscatorWriter(writer(destination));
            JSONObfuscator obfuscator = createObfuscator(builderSupplier.get(),
                    propertyObfuscator, objectPropertyObfuscator, arrayPropertyObfuscator);
            return obfuscator.createJsonGenerator(writer, appendAtMost(destination, Long.MAX_VALUE));
        }

        private void assertObfuscated(String result) {
            String expected = readResource(expectedResource);
            assertEquals(expected, result);
        }
    }
}
