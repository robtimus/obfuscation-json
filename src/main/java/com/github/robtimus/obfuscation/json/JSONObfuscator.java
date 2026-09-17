/*
 * JSONObfuscator.java
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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.appendAtMost;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.counting;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.reader;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.writer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.CountingReader;
import com.github.robtimus.obfuscation.support.LimitAppendable;
import com.github.robtimus.obfuscation.support.MapBuilder;

/**
 * An obfuscator that obfuscates JSON properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class JSONObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    private static final JsonProvider JSON_PROVIDER = JsonProvider.provider();

    final Map<ValueType, Map<String, PropertyConfig>> properties;
    private final String propertiesRepresentation;

    private final JsonGeneratorFactory jsonGeneratorFactory;

    private final boolean prettyPrint;
    private final boolean produceValidJSON;
    private final String malformedJSONWarning;

    private final long limit;
    private final String truncatedIndicator;

    private JSONObfuscator(Builder builder) {
        properties = builder.properties();
        propertiesRepresentation = builder.propertiesRepresentation();

        prettyPrint = builder.prettyPrint;
        Map<String, ?> config = prettyPrint ? Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true) : Collections.emptyMap();
        jsonGeneratorFactory = JSON_PROVIDER.createGeneratorFactory(config);

        produceValidJSON = builder.produceValidJSON;
        malformedJSONWarning = builder.malformedJSONWarning;

        limit = builder.limit;
        truncatedIndicator = builder.truncatedIndicator;
    }

    @Override
    public CharSequence obfuscateText(CharSequence s, int start, int end) {
        checkStartAndEnd(s, start, end);
        StringBuilder sb = new StringBuilder(end - start);
        obfuscateText(s, start, end, sb);
        return sb.toString();
    }

    @Override
    public void obfuscateText(CharSequence s, int start, int end, Appendable destination) throws IOException {
        checkStartAndEnd(s, start, end);
        @SuppressWarnings("resource")
        Reader reader = reader(s, start, end);
        LimitAppendable appendable = appendAtMost(destination, limit);
        @SuppressWarnings("resource")
        JSONObfuscatorWriter writer = new JSONObfuscatorWriter(writer(appendable));
        obfuscateText(reader, writer, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, end - start));
        }
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        @SuppressWarnings("resource")
        CountingReader reader = counting(input);
        LimitAppendable appendable = appendAtMost(destination, limit);
        @SuppressWarnings("resource")
        JSONObfuscatorWriter writer = new JSONObfuscatorWriter(writer(appendable));
        obfuscateText(reader, writer, appendable);
        if (appendable.limitExceeded() && truncatedIndicator != null) {
            destination.append(String.format(truncatedIndicator, reader.count()));
        }
    }

    @SuppressWarnings("resource")
    private void obfuscateText(Reader input, JSONObfuscatorWriter writer, LimitAppendable appendable) throws IOException {
        try (JsonParser jsonParser = JSON_PROVIDER.createParser(new DontCloseReader(input));
                ObfuscatingJsonGenerator jsonGenerator = createJsonGenerator(writer, appendable)) {

            // Cannot abort early as that could lead to errors due to incomplete JSON
            while (jsonParser.hasNext()) {
                Event event = jsonParser.next();
                switch (event) {
                    case START_OBJECT:
                        jsonGenerator.writeStartObject();
                        break;
                    case END_OBJECT:
                        jsonGenerator.writeEnd();
                        break;
                    case START_ARRAY:
                        jsonGenerator.writeStartArray();
                        break;
                    case END_ARRAY:
                        jsonGenerator.writeEnd();
                        break;
                    case KEY_NAME:
                        jsonGenerator.writeKey(jsonParser.getString());
                        break;
                    case VALUE_STRING:
                        jsonGenerator.write(jsonParser.getString());
                        break;
                    case VALUE_NUMBER:
                        jsonGenerator.write((JsonNumber) jsonParser.getValue()); // NOSONAR, the cast is necessary
                        break;
                    case VALUE_TRUE:
                        jsonGenerator.write(true);
                        break;
                    case VALUE_FALSE:
                        jsonGenerator.write(false);
                        break;
                    case VALUE_NULL:
                        jsonGenerator.writeNull();
                        break;
                    default:
                        LOGGER.warn(Messages.JSONObfuscator.unexpectedEvent(event));
                        break;
                }
            }

            writer.assertNonObfuscating();
        } catch (JsonParsingException e) {
            LOGGER.warn(Messages.JSONObfuscator.malformedJSON.warning(), e);
            writer.endObfuscating();
            if (malformedJSONWarning != null) {
                writer.write(malformedJSONWarning);
            }
        } catch (JsonException e) {
            throw new IOException(e);
        }
    }

    private ObfuscatingJsonGenerator createJsonGenerator(JSONObfuscatorWriter writer, LimitAppendable appendable) {
        return new ObfuscatingJsonGenerator(jsonGeneratorFactory, writer, appendable, properties, produceValidJSON);
    }

    @Override
    public Writer streamTo(Appendable destination) {
        return new CachingObfuscatingWriter(this, destination);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        JSONObfuscator other = (JSONObfuscator) o;
        return properties.equals(other.properties)
                && prettyPrint == other.prettyPrint
                && produceValidJSON == other.produceValidJSON
                && Objects.equals(malformedJSONWarning, other.malformedJSONWarning)
                && limit == other.limit
                && Objects.equals(truncatedIndicator, other.truncatedIndicator);
    }

    @Override
    public int hashCode() {
        return properties.hashCode() ^ Boolean.hashCode(prettyPrint) ^ Boolean.hashCode(produceValidJSON)
                ^ Objects.hashCode(malformedJSONWarning) ^ Long.hashCode(limit) ^ Objects.hashCode(truncatedIndicator);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[properties=" + propertiesRepresentation
                + ",prettyPrint=" + prettyPrint
                + ",produceValidJSON=" + produceValidJSON
                + ",malformedJSONWarning=" + malformedJSONWarning
                + ",limit=" + limit
                + ",truncatedIndicator=" + truncatedIndicator
                + "]";
    }

    /**
     * Returns a builder that will create {@code JSONObfuscators}.
     *
     * @return A builder that will create {@code JSONObfuscators}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for {@link JSONObfuscator JSONObfuscators}.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private final Map<ValueType, MapBuilder<PropertyConfig>> properties;
        private final StringBuilder propertiesRepresentation;

        private CaseSensitivity defaultCaseSensitivity;
        private Set<ValueType> defaultValueTypes;

        private boolean prettyPrint;
        private boolean produceValidJSON;

        private String malformedJSONWarning;

        private long limit;
        private String truncatedIndicator;

        // default settings
        private ObfuscationMode forObjectsByDefault;
        private ObfuscationMode forArraysByDefault;

        private final PropertyConfigurer propertyConfigurer;
        private final LimitConfigurer limitConfigurer;

        private Builder() {
            properties = new EnumMap<>(ValueType.class);
            propertiesRepresentation = new StringBuilder().append('{');

            defaultCaseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            defaultValueTypes = EnumSet.of(ValueType.ALL);

            prettyPrint = true;
            produceValidJSON = false;

            malformedJSONWarning = Messages.JSONObfuscator.malformedJSON.text();

            limit = Long.MAX_VALUE;
            truncatedIndicator = "... (total: %d)"; //$NON-NLS-1$

            forObjectsByDefault = ObfuscationMode.OBFUSCATE;
            forArraysByDefault = ObfuscationMode.OBFUSCATE;

            propertyConfigurer = new PropertyConfigurer();
            limitConfigurer = new LimitConfigurer();
        }

        /**
         * Adds a property to obfuscate.
         * This method is equivalent to calling for {@link #withProperty(String, Obfuscator, Consumer)} with a {@link Consumer} that does nothing.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @return This object.
         * @throws NullPointerException If the given property name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added for the property's value
         *                                  types.
         */
        public Builder withProperty(String property, Obfuscator obfuscator) {
            addProperty(property, obfuscator, null);
            return this;
        }

        /**
         * Adds a property to obfuscate.
         * This property will use the defaults set using {@link #caseSensitiveByDefault()}, {@link #caseInsensitiveByDefault()},
         * {@link #withValueTypesByDefault(ValueType, ValueType...)},
         * {@link #forObjectsByDefault(ObfuscationMode)} and {@link #forArraysByDefault(ObfuscationMode)}, unless explicitly replaced by the given
         * {@link Consumer}.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @param configurer A {@link Consumer} that can be used to update its argument, to override any setting for the property.
         * @return This object.
         * @throws NullPointerException If the given property name, obfuscator or {@link Consumer} is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added for the property's value
         *                                  types.
         * @since 3.0
         */
        public Builder withProperty(String property, Obfuscator obfuscator, Consumer<PropertyConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            addProperty(property, obfuscator, configurer);
            return this;
        }

        private void addProperty(String property, Obfuscator obfuscator, Consumer<PropertyConfigurer> configurer) {
            Objects.requireNonNull(property);
            Objects.requireNonNull(obfuscator);
            try {
                propertyConfigurer.caseSensitivity = defaultCaseSensitivity;
                propertyConfigurer.valueTypes.clear();
                propertyConfigurer.valueTypes.addAll(defaultValueTypes);
                propertyConfigurer.forObjects = forObjectsByDefault;
                propertyConfigurer.forArrays = forArraysByDefault;
                if (configurer != null) {
                    configurer.accept(propertyConfigurer);
                }

                PropertyConfig propertyConfig = new PropertyConfig(obfuscator, propertyConfigurer.forObjects, propertyConfigurer.forArrays);

                propertyConfigurer.valueTypes.stream()
                        .flatMap(valueType -> ValueType.DE_ALIASED_TYPES.get(valueType).stream())
                        .distinct()
                        .forEach(valueType -> properties.computeIfAbsent(valueType, k -> new MapBuilder<>())
                                .withEntry(property, propertyConfig, propertyConfigurer.caseSensitivity));

                addPropertyRepresenation(property, obfuscator);
            } finally {
                propertyConfigurer.reset();
            }
        }

        @SuppressWarnings("nls")
        private void addPropertyRepresenation(String property, Obfuscator obfuscator) {
            if (propertiesRepresentation.length() > 1) {
                propertiesRepresentation.append(", ");
            }
            propertiesRepresentation.append(property).append("=[");
            if (propertyConfigurer.caseSensitivity == CaseSensitivity.CASE_INSENSITIVE) {
                propertiesRepresentation.append("caseInsensitive, ");
            }
            propertiesRepresentation.append("valueTypes=").append(propertyConfigurer.valueTypes);
            propertiesRepresentation.append(",obfuscator=").append(obfuscator);
            propertiesRepresentation.append(",forObjects=").append(propertyConfigurer.forObjects);
            propertiesRepresentation.append(",forArrays=").append(propertyConfigurer.forArrays);
            propertiesRepresentation.append("]");
        }

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public Builder caseSensitiveByDefault() {
            defaultCaseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public Builder caseInsensitiveByDefault() {
            defaultCaseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        /**
         * Sets several value types for which property should be obfuscated by default.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param valueType The first value type to set.
         * @param additionalValueTypes Additional value types to set.
         * @return This object.
         * @throws NullPointerException If any of the given value types is {@code null}.
         * @since 3.0
         */
        public Builder withValueTypesByDefault(ValueType valueType, ValueType... additionalValueTypes) {
            defaultValueTypes.clear();
            defaultValueTypes.add(valueType);
            Collections.addAll(defaultValueTypes, additionalValueTypes);
            return this;
        }

        /**
         * Indicates how to handle properties if they are JSON objects. The default is {@link ObfuscationMode#OBFUSCATE}.
         * This can be overridden per property using {@link PropertyConfigurer#forObjects(ObfuscationMode)}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        public Builder forObjectsByDefault(ObfuscationMode obfuscationMode) {
            forObjectsByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        /**
         * Indicates how to handle properties if they are JSON arrays. The default is {@link ObfuscationMode#OBFUSCATE}.
         * This can be overridden per property using {@link PropertyConfigurer#forArrays(ObfuscationMode)}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        public Builder forArraysByDefault(ObfuscationMode obfuscationMode) {
            forArraysByDefault = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        /**
         * Sets whether or not to pretty-print obfuscated JSON. The default is {@code true}.
         *
         * @param prettyPrint {@code true} to pretty-print obfuscated JSON, or {@code false} otherwise.
         * @return This object.
         */
        public Builder withPrettyPrinting(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * If called, obfuscation produces valid JSON, provided the input is valid JSON. This is done by converting obfuscated values to strings.
         * For values that were already strings this changes nothing.
         * <p>
         * An exception is made for {@link Obfuscator#none()}. This still allows skipping obfuscating values inside certain properties.
         * <p>
         * Note that if obfuscated objects or arrays are converted to strings, any line breaks that remain after obfuscation will be escaped.
         *
         * @return This object.
         * @since 1.1
         */
        public Builder produceValidJSON() {
            produceValidJSON = true;
            return this;
        }

        /**
         * Sets the warning to include if a {@link JsonParsingException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public Builder withMalformedJSONWarning(String warning) {
            malformedJSONWarning = warning;
            return this;
        }

        /**
         * Sets the limit for the obfuscated result.
         * This method is equivalent to calling for {@link #limitTo(long, Consumer)} with a {@link Consumer} that does nothing.
         *
         * @param limit The limit to use.
         * @return This object.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 1.2
         */
        public Builder limitTo(long limit) {
            setLimit(limit, null);
            return this;
        }

        /**
         * Sets the limit for the obfuscated result.
         *
         * @param limit The limit to use.
         * @param configurer A {@link Consumer} that can be used to update its argument, to set any limit-specific properties.
         * @return This object.
         * @throws IllegalArgumentException If the given limit is negative.
         * @since 3.0
         */
        public Builder limitTo(long limit, Consumer<LimitConfigurer> configurer) {
            Objects.requireNonNull(configurer);
            setLimit(limit, configurer);
            return this;
        }

        private void setLimit(long limit, Consumer<LimitConfigurer> configurer) {
            if (limit < 0) {
                throw new IllegalArgumentException(limit + " < 0"); //$NON-NLS-1$
            }
            try {
                limitConfigurer.truncatedIndicator = truncatedIndicator;
                if (configurer != null) {
                    configurer.accept(limitConfigurer);
                }

                this.limit = limit;
                this.truncatedIndicator = limitConfigurer.truncatedIndicator;
            } finally {
                limitConfigurer.reset();
            }
        }

        /**
         * This method allows the application of a function to this builder.
         * <p>
         * Any exception thrown by the function will be propagated to the caller.
         *
         * @param <R> The type of the result of the function.
         * @param f The function to apply.
         * @return The result of applying the function to this builder.
         */
        public <R> R transform(Function<? super Builder, ? extends R> f) {
            return f.apply(this);
        }

        private Map<ValueType, Map<String, PropertyConfig>> properties() {
            return properties.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().build(),
                            // This will never be called because entries have unique keys
                            (t1, t2) -> null,
                            () -> new EnumMap<>(ValueType.class)));
        }

        private String propertiesRepresentation() {
            propertiesRepresentation.append('}');
            String result = propertiesRepresentation.toString();
            propertiesRepresentation.deleteCharAt(propertiesRepresentation.length() - 1);
            return result;
        }

        /**
         * Creates a new {@code JSONObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code JSONObfuscator}.
         */
        public JSONObfuscator build() {
            return new JSONObfuscator(this);
        }
    }

    /**
     * An object that can be used to configure a property that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public static final class PropertyConfigurer {

        private final Set<ValueType> valueTypes = EnumSet.noneOf(ValueType.class);

        private CaseSensitivity caseSensitivity;
        private ObfuscationMode forObjects;
        private ObfuscationMode forArrays;

        private PropertyConfigurer() {
        }

        /**
         * Sets the case sensitivity for the property to {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @return This object.
         * @since 3.0
         */
        public PropertyConfigurer caseSensitive() {
            caseSensitivity = CaseSensitivity.CASE_SENSITIVE;
            return this;
        }

        /**
         * Sets the case sensitivity for the property to {@link CaseSensitivity#CASE_INSENSITIVE}.
         *
         * @return This object.
         * @since 3.0
         */
        public PropertyConfigurer caseInsensitive() {
            caseSensitivity = CaseSensitivity.CASE_INSENSITIVE;
            return this;
        }

        /**
         * Sets several value types for which the property should be obfuscated.
         *
         * @param valueType The first value type to set.
         * @param additionalValueTypes Additional value types to set.
         * @return This object.
         * @throws NullPointerException If any of the given value types is {@code null}.
         * @since 3.0
         */
        public PropertyConfigurer withValueTypes(ValueType valueType, ValueType... additionalValueTypes) {
            valueTypes.clear();
            valueTypes.add(valueType);
            Collections.addAll(valueTypes, additionalValueTypes);
            return this;
        }

        /**
         * Indicates how to handle properties if they are JSON objects. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        public PropertyConfigurer forObjects(ObfuscationMode obfuscationMode) {
            forObjects = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        /**
         * Indicates how to handle properties if they are JSON arrays. The default is {@link ObfuscationMode#OBFUSCATE}.
         *
         * @param obfuscationMode The obfuscation mode that determines how to handle properties.
         * @return This object.
         * @throws NullPointerException If the given obfuscation mode is {@code null}.
         * @since 1.3
         */
        public PropertyConfigurer forArrays(ObfuscationMode obfuscationMode) {
            forArrays = Objects.requireNonNull(obfuscationMode);
            return this;
        }

        private void reset() {
            valueTypes.clear();
            caseSensitivity = null;
            forObjects = null;
            forArrays = null;
        }
    }

    /**
     * An object that can be used to configure handling when the obfuscated result exceeds a pre-defined limit.
     *
     * @author Rob Spoor
     * @since 1.2
     */
    public static final class LimitConfigurer {

        private String truncatedIndicator;

        private LimitConfigurer() {
        }

        /**
         * Sets the indicator to use when the obfuscated result is truncated due to the limit being exceeded.
         * There can be one place holder for the total number of characters. Defaults to {@code ... (total: %d)}.
         * Use {@code null} to omit the indicator.
         *
         * @param pattern The pattern to use as indicator.
         * @return This object.
         */
        public LimitConfigurer withTruncatedIndicator(String pattern) {
            this.truncatedIndicator = pattern;
            return this;
        }

        private void reset() {
            this.truncatedIndicator = null;
        }
    }

    /**
     * The possible value types.
     *
     * @author Rob Spoor
     * @since 3.0
     */
    public enum ValueType {
        /**
         * Represents string values.
         */
        STRING,
        /**
         * Represent numeric values.
         */
        NUMBER,
        /**
         * Represents boolean values.
         */
        BOOLEAN,
        /**
         * Represents object values.
         */
        OBJECT,
        /**
         * Represents array values.
         */
        ARRAY,
        /**
         * Represents {@code null} values.
         */
        NULL,
        /**
         * Represents scalar values: strings, numbers and booleans.
         * This is an alias for combining {@link #STRING}, {@link #NUMBER} and {@link #BOOLEAN}.
         */
        SCALAR,
        /**
         * Represents all possible values.
         * This is an alias for combining {@link #STRING}, {@link #NUMBER} {@link #BOOLEAN}, {@link #OBJECT} and {@link #ARRAY} but not {@link #NULL}.
         */
        NON_NULL,
        /**
         * Represents all possible values.
         * This is an alias for combining {@link #STRING}, {@link #NUMBER} {@link #BOOLEAN}, {@link #OBJECT}, {@link #ARRAY} and {@link #NULL}.
         */
        ALL,
        ;

        private static final Map<ValueType, Set<ValueType>> DE_ALIASED_TYPES = deAliasedTypes();

        private static Map<ValueType, Set<ValueType>> deAliasedTypes() {
            Map<ValueType, Set<ValueType>> result = new EnumMap<>(ValueType.class);
            result.put(STRING, EnumSet.of(STRING));
            result.put(NUMBER, EnumSet.of(NUMBER));
            result.put(BOOLEAN, EnumSet.of(BOOLEAN));
            result.put(OBJECT, EnumSet.of(OBJECT));
            result.put(ARRAY, EnumSet.of(ARRAY));
            result.put(NULL, EnumSet.of(NULL));

            result.put(SCALAR, EnumSet.of(STRING, NUMBER, BOOLEAN));
            result.put(NON_NULL, EnumSet.of(STRING, NUMBER, BOOLEAN, OBJECT, ARRAY));
            result.put(ALL, EnumSet.of(STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL));

            return result;
        }
    }

    /**
     * The possible ways to deal with nested objects and arrays.
     *
     * @author Rob Spoor
     * @since 1.3
     */
    public enum ObfuscationMode {
        /** Obfuscate nested objects and arrays completely. **/
        OBFUSCATE,

        /** Don't obfuscate nested objects or arrays, but use the obfuscator for all nested scalar properties. **/
        INHERIT,

        /**
         * Don't obfuscate nested objects or arrays, but use the obfuscator for all nested scalar properties.
         * If a nested property has its own obfuscator defined this will be used instead.
         **/
        INHERIT_OVERRIDABLE,
    }
}
