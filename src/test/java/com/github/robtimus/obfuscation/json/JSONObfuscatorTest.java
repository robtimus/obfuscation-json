/*
 * JSONObfuscatorTest.java
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
import static com.github.robtimus.obfuscation.json.JSONObfuscator.builder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.json.JSONObfuscator.Builder;
import com.github.robtimus.obfuscation.json.JSONObfuscator.ObfuscationMode;
import com.github.robtimus.obfuscation.json.JSONObfuscator.PropertyConfigurer;
import com.github.robtimus.obfuscation.json.JSONObfuscator.ValueType;

@SuppressWarnings("nls")
@TestInstance(Lifecycle.PER_CLASS)
class JSONObfuscatorTest {

    @ParameterizedTest(name = "{1}")
    @MethodSource
    @DisplayName("equals(Object)")
    void testEquals(Obfuscator obfuscator, Object object, boolean expected) {
        assertEquals(expected, obfuscator.equals(object));
    }

    Arguments[] testEquals() {
        Obfuscator obfuscator = createObfuscator(builder().withProperty("test", none()));
        return new Arguments[] {
                arguments(obfuscator, obfuscator, true),
                arguments(obfuscator, null, false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).withPrettyPrinting(true)), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none(), p -> p.caseSensitive())), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", fixedLength(3))), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none(), p -> p.withValueTypes(ValueType.SCALAR))), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none(), p -> p.forObjects(ObfuscationMode.INHERIT))), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none(), p -> p.forArrays(ObfuscationMode.INHERIT))), false),
                arguments(obfuscator, builder().build(), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).produceValidJSON()), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).withPrettyPrinting(false)), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).withMalformedJSONWarning(null)), false),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).limitTo(Long.MAX_VALUE)), true),
                arguments(obfuscator, createObfuscator(builder().withProperty("test", none()).limitTo(1024)), false),
                arguments(obfuscator,
                        createObfuscator(builder().withProperty("test", none()).limitTo(Long.MAX_VALUE, l -> l.withTruncatedIndicator(null))),
                        false),
                arguments(obfuscator, createObfuscator(true, false), false),
                arguments(obfuscator, "foo", false),
        };
    }

    @Test
    @DisplayName("hashCode()")
    void testHashCode() {
        Obfuscator obfuscator = createObfuscator(true);
        assertEquals(obfuscator.hashCode(), obfuscator.hashCode());
        assertEquals(obfuscator.hashCode(), createObfuscator(true).hashCode());
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Nested
        @DisplayName("withProperty")
        class WithProperty {

            @Test
            @DisplayName("duplicate property with exact match")
            void testDuplicatePropertyWithExactMatch() {
                Obfuscator obfuscator = Obfuscator.all();
                Builder builder = builder().withProperty("property", obfuscator);
                assertThrows(IllegalArgumentException.class, () -> builder.withProperty("property", obfuscator));
            }

            @Test
            @DisplayName("duplicate property with some overlap")
            void testDuplicatePropertyWithSomeOverlap() {
                Obfuscator obfuscator = Obfuscator.all();
                Builder builder = builder()
                        .withValueTypesByDefault(ValueType.STRING, ValueType.NULL)
                        .withProperty("property", obfuscator, property -> property.withValueTypes(ValueType.SCALAR));
                assertThrows(IllegalArgumentException.class, () -> builder.withProperty("property", obfuscator));
            }
        }

        @Nested
        @DisplayName("limitTo")
        class LimitTo {

            @Test
            @DisplayName("negative limit")
            void testNegativeLimit() {
                Builder builder = builder();
                assertThrows(IllegalArgumentException.class, () -> builder.limitTo(-1));
            }
        }
    }

    @Nested
    @DisplayName("valid JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    class ValidJSON {

        @Nested
        @DisplayName("caseSensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseSensitively extends ObfuscatorTest {

            ObfuscatingCaseSensitively() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all.pretty-printed",
                        () -> createObfuscator(builder().caseSensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("caseInsensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseInsensitively extends ObfuscatorTest {

            ObfuscatingCaseInsensitively() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all.pretty-printed",
                        () -> createObfuscatorCaseInsensitive(builder().caseInsensitiveByDefault()));
            }
        }

        @Nested
        @DisplayName("caseInsensitive(), overriding caseSensitiveByDefault()")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingCaseSensitivelyOverridden extends ObfuscatorTest {

            ObfuscatingCaseSensitivelyOverridden() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all.pretty-printed",
                        () -> createObfuscatorCaseInsensitive(builder().caseSensitiveByDefault(), PropertyConfigurer::caseInsensitive));
            }
        }

        @Nested
        @DisplayName("obfuscating all (default)")
        class ObfuscatingAll {

            @Nested
            @DisplayName("pretty printed")
            class PrettyPrinted extends ObfuscatorTest {

                PrettyPrinted() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all.pretty-printed",
                            () -> createObfuscator(builder().withPrettyPrinting(true)));
                }
            }

            @Nested
            @DisplayName("not pretty printed")
            class NotPrettyPrinted extends ObfuscatorTest {

                NotPrettyPrinted() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all",
                            () -> createObfuscator(builder().withPrettyPrinting(false)));
                }
            }

            @Nested
            @DisplayName("producing valid JSON")
            class ProducingValidJSON {

                @Nested
                @DisplayName("pretty printed")
                class PrettyPrinted extends ObfuscatorTest {

                    PrettyPrinted() {
                        super("JSONObfuscator.input.valid.json", "valid-json/JSONObfuscator.expected.valid.all.pretty-printed",
                                () -> createObfuscator(builder().withPrettyPrinting(true).produceValidJSON()));
                    }

                    @Override
                    @Test
                    @DisplayName("produces valid JSON")
                    void testExpectedIsValidJSON() {
                        super.testExpectedIsValidJSON();
                    }
                }

                @Nested
                @DisplayName("not pretty printed")
                class NotPrettyPrinted extends ObfuscatorTest {

                    NotPrettyPrinted() {
                        super("JSONObfuscator.input.valid.json", "valid-json/JSONObfuscator.expected.valid.all",
                                () -> createObfuscator(builder().withPrettyPrinting(false).produceValidJSON()));
                    }

                    @Override
                    @Test
                    @DisplayName("produces valid JSON")
                    void testExpectedIsValidJSON() {
                        super.testExpectedIsValidJSON();
                    }
                }
            }
        }

        @Nested
        @DisplayName("obfuscating all, overriding scalars only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingAllOverridden extends ObfuscatorTest {

            ObfuscatingAllOverridden() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.all.pretty-printed",
                        () -> createObfuscatorObfuscatingAll(builder().withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL)));
            }
        }

        @Nested
        @DisplayName("obfuscating scalars only by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingScalars {

            @Nested
            @DisplayName("pretty printed")
            class PrettyPrinted extends ObfuscatorTest {

                PrettyPrinted() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.scalar.pretty-printed",
                            () -> createObfuscator(builder().withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL).withPrettyPrinting(true)));
                }
            }

            @Nested
            @DisplayName("not pretty printed")
            class NotPrettyPrinted extends ObfuscatorTest {

                NotPrettyPrinted() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.scalar",
                            () -> createObfuscator(builder().withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL).withPrettyPrinting(false)));
                }
            }

            @Nested
            @DisplayName("producing valid JSON")
            class ProducingValidJSON {

                @Nested
                @DisplayName("pretty printed")
                class PrettyPrinted extends ObfuscatorTest {

                    PrettyPrinted() {
                        super("JSONObfuscator.input.valid.json", "valid-json/JSONObfuscator.expected.valid.scalar.pretty-printed",
                                () -> createObfuscator(builder()
                                        .withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL)
                                        .withPrettyPrinting(true)
                                        .produceValidJSON()));
                    }

                    @Override
                    @Test
                    @DisplayName("produces valid JSON")
                    void testExpectedIsValidJSON() {
                        super.testExpectedIsValidJSON();
                    }
                }

                @Nested
                @DisplayName("not pretty printed")
                class NotPrettyPrinted extends ObfuscatorTest {

                    NotPrettyPrinted() {
                        super("JSONObfuscator.input.valid.json", "valid-json/JSONObfuscator.expected.valid.scalar",
                                () -> createObfuscator(builder()
                                        .withValueTypesByDefault(ValueType.SCALAR, ValueType.NULL)
                                        .withPrettyPrinting(false)
                                        .produceValidJSON()));
                    }

                    @Override
                    @Test
                    @DisplayName("produces valid JSON")
                    void testExpectedIsValidJSON() {
                        super.testExpectedIsValidJSON();
                    }
                }
            }
        }

        @Nested
        @DisplayName("obfuscating scalars only, overriding all by default")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingScalarsOverridden extends ObfuscatorTest {

            ObfuscatingScalarsOverridden() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.scalar.pretty-printed",
                        () -> createObfuscatorObfuscatingScalarsOnly(builder().withValueTypesByDefault(ValueType.ALL)));
            }
        }

        @Nested
        @DisplayName("obfuscating with INHERIT mode")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingInherited extends ObfuscatorTest {

            ObfuscatingInherited() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.inherited",
                        () -> createObfuscatorWithObfuscatorMode(builder(), ObfuscationMode.INHERIT));
            }
        }

        @Nested
        @DisplayName("obfuscating with INHERITED_OVERRIDABLE mode")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingInheritedOverridable extends ObfuscatorTest {

            ObfuscatingInheritedOverridable() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.inherited-overridable",
                        () -> createObfuscatorWithObfuscatorMode(builder(), ObfuscationMode.INHERIT_OVERRIDABLE));
            }
        }

        @Nested
        @DisplayName("obfuscating none")
        @TestInstance(Lifecycle.PER_CLASS)
        class ObfuscatingNone extends ObfuscatorTest {

            ObfuscatingNone() {
                super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.obfuscating-none.pretty-printed",
                        () -> createObfuscator(builder(), none(), none(), none()));
            }
        }

        @Nested
        @DisplayName("limited")
        @TestInstance(Lifecycle.PER_CLASS)
        class Limited {

            @Nested
            @DisplayName("with truncated indicator")
            @TestInstance(Lifecycle.PER_CLASS)
            class WithTruncatedIndicator extends ObfuscatorTest {

                WithTruncatedIndicator() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.limited.with-indicator",
                            () -> createObfuscator(builder().limitTo(657)));
                }
            }

            @Nested
            @DisplayName("without truncated indicator")
            @TestInstance(Lifecycle.PER_CLASS)
            class WithoutTruncatedIndicator extends ObfuscatorTest {

                WithoutTruncatedIndicator() {
                    super("JSONObfuscator.input.valid.json", "JSONObfuscator.expected.valid.limited.without-indicator",
                            () -> createObfuscator(builder().limitTo(657, limit -> limit.withTruncatedIndicator(null))));
                }
            }
        }
    }

    @Nested
    @DisplayName("invalid JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    class InvalidJSON {

        @Nested
        @DisplayName("pretty printed")
        class PrettyPrinted extends InvalidJSONTest {

            PrettyPrinted() {
                super("JSONObfuscator.expected.invalid.pretty-printed", true);
            }
        }

        @Nested
        @DisplayName("not pretty printed")
        class NotPrettyPrinted extends InvalidJSONTest {

            NotPrettyPrinted() {
                super("JSONObfuscator.expected.invalid", false);
            }
        }

        @Nested
        @DisplayName("producing valid JSON")
        class ProducingValidJSON {

            @Nested
            @DisplayName("pretty printed")
            class PrettyPrinted extends InvalidJSONTest {

                PrettyPrinted() {
                    super("valid-json/JSONObfuscator.expected.invalid.pretty-printed", true, () -> builder().produceValidJSON());
                }
            }

            @Nested
            @DisplayName("not pretty printed")
            class NotPrettyPrinted extends InvalidJSONTest {

                NotPrettyPrinted() {
                    super("valid-json/JSONObfuscator.expected.invalid", false, () -> builder().produceValidJSON());
                }
            }
        }

        private abstract class InvalidJSONTest extends ObfuscatorTest {

            InvalidJSONTest(String expectedResource, boolean prettyPrint) {
                super("JSONObfuscator.input.invalid", expectedResource, () -> createObfuscator(prettyPrint));
            }

            InvalidJSONTest(String expectedResource, boolean prettyPrint, Supplier<Builder> builderSupplier) {
                super("JSONObfuscator.input.invalid", expectedResource, () -> createObfuscator(builderSupplier.get(), prettyPrint));
            }
        }
    }

    @Nested
    @DisplayName("truncated JSON")
    @TestInstance(Lifecycle.PER_CLASS)
    class TruncatedJSON {

        @Nested
        @DisplayName("pretty printed with warning")
        class PrettyPrintedWithWarning extends TruncatedJSONTest {

            PrettyPrintedWithWarning() {
                super("JSONObfuscator.expected.truncated.pretty-printed", true, true);
            }
        }

        @Nested
        @DisplayName("pretty printed without warning")
        class PrettyPrintedWithoutWarning extends TruncatedJSONTest {

            PrettyPrintedWithoutWarning() {
                super("JSONObfuscator.expected.truncated.no-warning.pretty-printed", true, false);
            }
        }

        @Nested
        @DisplayName("not pretty printed with warning")
        class NotPrettyPrintedWithWarning extends TruncatedJSONTest {

            NotPrettyPrintedWithWarning() {
                super("JSONObfuscator.expected.truncated", false, true);
            }
        }

        @Nested
        @DisplayName("not pretty printed without warning")
        class NotPrettyPrintedWithoutWarning extends TruncatedJSONTest {

            NotPrettyPrintedWithoutWarning() {
                super("JSONObfuscator.expected.truncated.no-warning", false, false);
            }
        }

        @Nested
        @DisplayName("producing valid JSON")
        class ProducingValidJSON {

            @Nested
            @DisplayName("pretty printed with warning")
            class PrettyPrintedWithWarning extends TruncatedJSONTest {

                PrettyPrintedWithWarning() {
                    super("valid-json/JSONObfuscator.expected.truncated.pretty-printed", true, true, () -> builder().produceValidJSON());
                }
            }

            @Nested
            @DisplayName("pretty printed without warning")
            class PrettyPrintedWithoutWarning extends TruncatedJSONTest {

                PrettyPrintedWithoutWarning() {
                    super("valid-json/JSONObfuscator.expected.truncated.no-warning.pretty-printed", true, false, () -> builder().produceValidJSON());
                }
            }

            @Nested
            @DisplayName("not pretty printed with warning")
            class NotPrettyPrintedWithWarning extends TruncatedJSONTest {

                NotPrettyPrintedWithWarning() {
                    super("valid-json/JSONObfuscator.expected.truncated", false, true, () -> builder().produceValidJSON());
                }
            }

            @Nested
            @DisplayName("not pretty printed without warning")
            class NotPrettyPrintedWithoutWarning extends TruncatedJSONTest {

                NotPrettyPrintedWithoutWarning() {
                    super("valid-json/JSONObfuscator.expected.truncated.no-warning", false, false, () -> builder().produceValidJSON());
                }
            }
        }

        private abstract class TruncatedJSONTest extends ObfuscatorTest {

            TruncatedJSONTest(String expectedResource, boolean prettyPrint, boolean includeWarning) {
                super("JSONObfuscator.input.truncated", expectedResource, () -> createObfuscator(prettyPrint, includeWarning));
            }

            TruncatedJSONTest(String expectedResource, boolean prettyPrint, boolean includeWarning, Supplier<Builder> builderSupplier) {
                super("JSONObfuscator.input.truncated", expectedResource, () -> createObfuscator(builderSupplier.get(), prettyPrint, includeWarning));
            }
        }
    }

    @Nested
    @DisplayName("different obfuscators per type")
    @TestInstance(Lifecycle.PER_CLASS)
    class DifferentObfuscatorsPerType {

        @Nested
        @DisplayName("separate obfuscators")
        class SeparateObfuscators extends ObfuscatorTest {

            SeparateObfuscators() {
                super("JSONObfuscator.input.values.json", "JSONObfuscator.expected.values.separate", () -> builder()
                        .withProperty("value", Obfuscator.fixedValue("<string>"), property -> property.withValueTypes(ValueType.STRING))
                        .withProperty("value", Obfuscator.fixedValue("<number>"), property -> property.withValueTypes(ValueType.NUMBER))
                        .withProperty("value", Obfuscator.fixedValue("<boolean>"), property -> property.withValueTypes(ValueType.BOOLEAN))
                        .withProperty("value", Obfuscator.fixedValue("<object>"), property -> property.withValueTypes(ValueType.OBJECT))
                        .withProperty("value", Obfuscator.fixedValue("<array>"), property -> property.withValueTypes(ValueType.ARRAY))
                        .withProperty("value", Obfuscator.fixedValue("<null>"), property -> property.withValueTypes(ValueType.NULL))
                        .build());
            }
        }

        @Nested
        @DisplayName("using scalar")
        class UsingScalar extends ObfuscatorTest {

            UsingScalar() {
                super("JSONObfuscator.input.values.json", "JSONObfuscator.expected.values.scalar", () -> builder()
                        .withProperty("value", Obfuscator.fixedValue("<scalar>"), property -> property.withValueTypes(ValueType.SCALAR))
                        .withProperty("value", Obfuscator.fixedValue("<object>"), property -> property.withValueTypes(ValueType.OBJECT))
                        .withProperty("value", Obfuscator.fixedValue("<array>"), property -> property.withValueTypes(ValueType.ARRAY))
                        .withProperty("value", Obfuscator.fixedValue("<null>"), property -> property.withValueTypes(ValueType.NULL))
                        .build());
            }
        }

        @Nested
        @DisplayName("non-null")
        class NonNull extends ObfuscatorTest {

            NonNull() {
                super("JSONObfuscator.input.values.json", "JSONObfuscator.expected.values.non-null", () -> builder()
                        .withProperty("value", Obfuscator.fixedValue("<non-null>"), property -> property.withValueTypes(ValueType.NON_NULL))
                        .build());
            }
        }
    }

    abstract static class ObfuscatorTest {

        private final String input;
        private final String expected;
        private final Supplier<Obfuscator> obfuscatorSupplier;

        ObfuscatorTest(String inputResource, String expectedResource, Supplier<Obfuscator> obfuscatorSupplier) {
            this.input = readResource(inputResource);
            this.expected = readResource(expectedResource);
            this.obfuscatorSupplier = obfuscatorSupplier;
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int)")
        void testObfuscateTextCharSequence() {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            assertEquals(expected, obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length()).toString());
        }

        @Test
        @DisplayName("obfuscateText(CharSequence, int, int, Appendable)")
        void testObfuscateTextCharSequenceToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            obfuscator.obfuscateText("x" + input + "x", 1, 1 + input.length(), destination);
            assertEquals(expected, destination.toString());
            verify(destination, never()).close();
        }

        @Test
        @DisplayName("obfuscateText(Reader, Appendable)")
        @SuppressWarnings("resource")
        void testObfuscateTextReaderToAppendable() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            StringWriter destination = spy(new StringWriter());
            Reader reader = spy(new StringReader(input));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expected, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();

            destination.getBuffer().delete(0, destination.getBuffer().length());
            reader = spy(new BufferedReader(new StringReader(input)));
            obfuscator.obfuscateText(reader, destination);
            assertEquals(expected, destination.toString());
            verify(reader, never()).close();
            verify(destination, never()).close();
        }

        @Test
        @DisplayName("streamTo(Appendable")
        void testStreamTo() throws IOException {
            Obfuscator obfuscator = obfuscatorSupplier.get();

            Writer writer = spy(new StringWriter());
            try (Writer w = obfuscator.streamTo(writer)) {
                int index = 0;
                while (index < input.length()) {
                    int to = Math.min(index + 5, input.length());
                    w.write(input, index, to - index);
                    index = to;
                }
            }
            assertEquals(expected, writer.toString());
            verify(writer, never()).close();
        }

        void testExpectedIsValidJSON() {
            assertDoesNotThrow(() -> {
                try (JsonParser parser = JsonProvider.provider().createParser(new StringReader(expected))) {
                    while (parser.hasNext()) {
                        parser.next();
                    }
                }
            });
        }
    }

    private static Obfuscator createObfuscator(boolean prettyPrint) {
        return createObfuscator(builder(), prettyPrint);
    }

    private static Obfuscator createObfuscator(boolean prettyPrint, boolean includeWarning) {
        return createObfuscator(builder(), prettyPrint, includeWarning);
    }

    private static Obfuscator createObfuscator(Builder builder, boolean prettyPrint) {
        return builder
                .withPrettyPrinting(prettyPrint)
                .transform(JSONObfuscatorTest::createObfuscator);
    }

    private static Obfuscator createObfuscator(Builder builder, boolean prettyPrint, boolean includeWarning) {
        builder.withPrettyPrinting(prettyPrint);
        if (!includeWarning) {
            builder.withMalformedJSONWarning(null);
        }
        return builder.transform(JSONObfuscatorTest::createObfuscator);
    }

    static JSONObfuscator createObfuscator(Builder builder) {
        return createObfuscator(builder, fixedLength(3), fixedLength(3, 'o'), fixedLength(3, 'a'));
    }

    static JSONObfuscator createObfuscator(Builder builder, Obfuscator obfuscator, Obfuscator objectObfuscator, Obfuscator arrayObfuscator) {
        return builder
                .withProperty("string", obfuscator)
                .withProperty("int", obfuscator)
                .withProperty("bigInt", obfuscator)
                .withProperty("float", obfuscator)
                .withProperty("booleanTrue", obfuscator)
                .withProperty("booleanFalse", obfuscator)
                .withProperty("object", objectObfuscator)
                .withProperty("array", arrayObfuscator)
                .withProperty("null", obfuscator)
                .withProperty("notObfuscated", none())
                .build();
    }

    private static Obfuscator createObfuscatorCaseInsensitive(Builder builder) {
        return createObfuscatorCaseInsensitive(builder, property -> { /* do nothing */ });
    }

    private static Obfuscator createObfuscatorCaseInsensitive(Builder builder, Consumer<PropertyConfigurer> configurer) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("STRING", obfuscator, configurer)
                .withProperty("INT", obfuscator, configurer)
                .withProperty("BIGINT", obfuscator, configurer)
                .withProperty("FLOAT", obfuscator, configurer)
                .withProperty("BOOLEANTRUE", obfuscator, configurer)
                .withProperty("BOOLEANFALSE", obfuscator, configurer)
                .withProperty("OBJECT", fixedLength(3, 'o'), configurer)
                .withProperty("ARRAY", fixedLength(3, 'a'), configurer)
                .withProperty("NULL", obfuscator, configurer)
                .withProperty("NOTOBFUSCATED", none(), configurer)
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingAll(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator, property -> property.withValueTypes(ValueType.ALL))
                .withProperty("int", obfuscator, property -> property.withValueTypes(ValueType.ALL))
                .withProperty("bigInt", obfuscator, property -> property.withValueTypes(ValueType.ALL))
                .withProperty("float", obfuscator, property -> property.withValueTypes(ValueType.ALL))
                .withProperty("booleanTrue", obfuscator, property -> property.withValueTypes(ValueType.ALL))
                .withProperty("booleanFalse", obfuscator, property -> property.withValueTypes(ValueType.ALL))
                .withProperty("object", fixedLength(3, 'o'), property -> property.withValueTypes(ValueType.ALL))
                .withProperty("array", fixedLength(3, 'a'), property -> property.withValueTypes(ValueType.ALL))
                .withProperty("null", obfuscator, property -> property.withValueTypes(ValueType.ALL))
                .withProperty("notObfuscated", none(), property -> property.withValueTypes(ValueType.ALL))
                .build();
    }

    private static Obfuscator createObfuscatorObfuscatingScalarsOnly(Builder builder) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .withProperty("string", obfuscator, property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("int", obfuscator, property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("bigInt", obfuscator, property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("float", obfuscator, property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("booleanTrue", obfuscator, property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("booleanFalse", obfuscator, property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("object", fixedLength(3, 'o'), property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("array", fixedLength(3, 'a'), property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("null", obfuscator, property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .withProperty("notObfuscated", none(), property -> property.withValueTypes(ValueType.SCALAR, ValueType.NULL))
                .build();
    }

    private static Obfuscator createObfuscatorWithObfuscatorMode(Builder builder, ObfuscationMode obfuscationMode) {
        Obfuscator obfuscator = fixedLength(3);
        return builder
                .forObjectsByDefault(obfuscationMode)
                .forArraysByDefault(obfuscationMode)
                .withProperty("string", obfuscator)
                .withProperty("int", obfuscator)
                .withProperty("bigInt", obfuscator)
                .withProperty("float", obfuscator)
                .withProperty("booleanTrue", obfuscator)
                .withProperty("booleanFalse", obfuscator)
                .withProperty("object", fixedLength(3, 'o'))
                .withProperty("array", fixedLength(3, 'a'))
                .withProperty("null", obfuscator)
                .withProperty("notObfuscated", none())
                .build();
    }

    static String readResource(String name) {
        StringBuilder sb = new StringBuilder();
        try (Reader input = new InputStreamReader(JSONObfuscatorTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int len;
            while ((len = input.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString().replace("\r", "");
    }
}
