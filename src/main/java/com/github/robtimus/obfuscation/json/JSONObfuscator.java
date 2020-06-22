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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.reader;
import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.writer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.json.JsonException;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.support.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.support.CaseSensitivity;
import com.github.robtimus.obfuscation.support.MapBuilder;

/**
 * An obfuscator that obfuscates JSON properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class JSONObfuscator extends Obfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    private static final JsonProvider JSON_PROVIDER = JsonProvider.provider();

    private final Map<String, PropertyConfig> properties;

    private final JsonGeneratorFactory jsonGeneratorFactory;

    private final boolean prettyPrint;
    private final String malformedJSONWarning;

    private JSONObfuscator(ObfuscatorBuilder builder) {
        properties = builder.properties();

        prettyPrint = builder.prettyPrint;
        Map<String, ?> config = prettyPrint ? Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true) : Collections.emptyMap();
        jsonGeneratorFactory = JSON_PROVIDER.createGeneratorFactory(config);

        malformedJSONWarning = builder.malformedJSONWarning;
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
        Reader input = reader(s, start, end);
        @SuppressWarnings("resource")
        JSONObfuscatorWriter writer = new JSONObfuscatorWriter(writer(destination));
        obfuscateText(input, s, end, writer);
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        StringBuilder contents = new StringBuilder();
        @SuppressWarnings("resource")
        Reader reader = copyTo(input, contents);
        @SuppressWarnings("resource")
        JSONObfuscatorWriter writer = new JSONObfuscatorWriter(writer(destination));
        obfuscateText(reader, contents, -1, writer);
    }

    @SuppressWarnings("resource")
    private void obfuscateText(Reader input, CharSequence s, int end, JSONObfuscatorWriter writer) throws IOException {
        try (JsonParser jsonParser = JSON_PROVIDER.createParser(new DontCloseReader(input));
                JsonGenerator jsonGenerator = jsonGeneratorFactory.createGenerator(new DontCloseWriter(writer))) {

            // depth > 0 means that an entire object or array needs to be obfuscated.
            int depth = 0;
            // propertyConfig != null means the current value needs be obfuscated.
            PropertyConfig propertyConfig = null;
            Event event = null;

            // The below will flush the jsonGenerator after each write, so writes are done to the correct writer at the time
            while (jsonParser.hasNext()) {
                event = jsonParser.next();
                switch (event) {
                case START_OBJECT:
                case START_ARRAY:
                    if (propertyConfig != null && depth == 0 && !obfuscateNonScalarValue(event, propertyConfig)) {
                        // there is an obfuscator for the object or array property, but the obfuscation mode prohibits obfuscating objects / arrays;
                        // reset the obfuscator so this property will not be obfuscated
                        propertyConfig = null;
                    }
                    if (propertyConfig != null) {
                        if (depth == 0) {
                            // The start of obfuscating an object or array; the object or array should be obfuscated completely
                            writer.startObfuscate(propertyConfig.obfuscator);
                        }
                        depth++;
                    }
                    if (event == Event.START_OBJECT) {
                        jsonGenerator.writeStartObject();
                    } else {
                        jsonGenerator.writeStartArray();
                    }
                    jsonGenerator.flush();
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    jsonGenerator.writeEnd();
                    jsonGenerator.flush();
                    if (depth > 0) {
                        depth--;
                        if (depth == 0) {
                            // The end of obfuscating an object or array; the delegate is the obfuscator's stream, so close it to finish obfuscation
                            writer.endObfuscate();
                            propertyConfig = null;
                        }
                    }
                    break;
                case KEY_NAME:
                    String propertyName = jsonParser.getString();
                    if (propertyConfig == null) {
                        propertyConfig = properties.get(propertyName);
                    }
                    jsonGenerator.writeKey(propertyName);
                    jsonGenerator.flush();
                    break;
                case VALUE_STRING:
                    if (propertyConfig != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue(jsonParser.getString(), propertyConfig.obfuscator, jsonGenerator, writer, true);
                        propertyConfig = null;
                    } else {
                        // Either not obfuscating, or nested in an object or array that will be obfuscated; write the value as-is.
                        jsonGenerator.write(jsonParser.getString());
                    }
                    break;
                case VALUE_NULL:
                    if (propertyConfig != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue("null", propertyConfig.obfuscator, jsonGenerator, writer, false); //$NON-NLS-1$
                        propertyConfig = null;
                    } else {
                        // Either not obfuscating, or nested in an object or array that will be obfuscated
                        jsonGenerator.writeNull();
                    }
                    break;
                case VALUE_NUMBER:
                    if (propertyConfig != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue(jsonParser.getString(), propertyConfig.obfuscator, jsonGenerator, writer, false);
                        propertyConfig = null;
                    } else {
                        // Either not obfuscating, or nested in an object or array that will be obfuscated
                        if (jsonParser.isIntegralNumber()) {
                            jsonGenerator.write(jsonParser.getLong());
                        } else {
                            jsonGenerator.write(jsonParser.getBigDecimal());
                        }
                    }
                    break;
                case VALUE_TRUE:
                case VALUE_FALSE:
                    if (propertyConfig != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue(Boolean.toString(event == Event.VALUE_TRUE), propertyConfig.obfuscator, jsonGenerator, writer, false);
                        propertyConfig = null;
                    } else {
                        // Either not obfuscating, or nested in an object or array that will be obfuscated
                        jsonGenerator.write(event == Event.VALUE_TRUE);
                    }
                    break;
                default:
                    LOGGER.warn(Messages.JSONObfuscator.unexpectedEvent.get(event));
                    break;
                }
            }
            // Read the remainder so the final append will include all text
            discardAll(input);
            writer.assertNonObfuscating();
            int index = end == -1 ? s.length() : end;
            writer.append(s, index, end == -1 ? s.length() : end);
        } catch (JsonParsingException e) {
            LOGGER.warn(Messages.JSONObfuscator.malformedJSON.warning.get(), e);
            writer.endObfuscating();
            if (malformedJSONWarning != null) {
                writer.write(malformedJSONWarning);
            }
        } catch (JsonException e) {
            throw new IOException(e);
        }
    }

    private boolean obfuscateNonScalarValue(Event event, PropertyConfig propertyConfig) {
        assert event == Event.START_ARRAY || event == Event.START_OBJECT : "Should only be called for array or object start"; //$NON-NLS-1$
        return event == Event.START_ARRAY ? propertyConfig.obfuscateArrays : propertyConfig.obfuscateObjects;
    }

    @SuppressWarnings("resource")
    private void obfuscateValue(String value, Obfuscator obfuscator, JsonGenerator jsonGenerator, JSONObfuscatorWriter writer, boolean quote)
            throws IOException {

        if (quote) {
            jsonGenerator.write(obfuscator.obfuscateText(value).toString());
            jsonGenerator.flush();
        } else {
            writer.startUnquote();
            jsonGenerator.write(obfuscator.obfuscateText(value).toString());
            jsonGenerator.flush();
            writer.endUnquote();
        }
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
                && Objects.equals(malformedJSONWarning, other.malformedJSONWarning);
    }

    @Override
    public int hashCode() {
        return properties.hashCode() ^ Boolean.hashCode(prettyPrint) ^ Objects.hashCode(malformedJSONWarning);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getName()
                + "[properties=" + properties
                + ",prettyPrint=" + prettyPrint
                + ",malformedJSONWarning=" + malformedJSONWarning
                + "]";
    }

    /**
     * Returns a builder that will create {@code JSONObfuscators}.
     *
     * @return A builder that will create {@code JSONObfuscators}.
     */
    public static Builder builder() {
        return new ObfuscatorBuilder();
    }

    /**
     * A builder for {@link JSONObfuscator JSONObfuscators}.
     *
     * @author Rob Spoor
     */
    public abstract static class Builder {

        private Builder() {
            super();
        }

        /**
         * Adds a property to obfuscate.
         * This method is an alias for {@link #withProperty(String, Obfuscator, CaseSensitivity)} with the last specified default case sensitivity
         * using {@link #caseSensitiveByDefault()} or {@link #caseInsensitiveByDefault()}. The default is {@link CaseSensitivity#CASE_SENSITIVE}.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         * @throws NullPointerException If the given property name or obfuscator is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public abstract PropertyConfigurer withProperty(String property, Obfuscator obfuscator);

        /**
         * Adds a property to obfuscate.
         *
         * @param property The name of the property.
         * @param obfuscator The obfuscator to use for obfuscating the property.
         * @param caseSensitivity The case sensitivity for the property.
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         * @throws NullPointerException If the given property name, obfuscator or case sensitivity is {@code null}.
         * @throws IllegalArgumentException If a property with the same name and the same case sensitivity was already added.
         */
        public abstract PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity);

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_SENSITIVE}. This is the default setting.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseSensitiveByDefault();

        /**
         * Sets the default case sensitivity for new properties to {@link CaseSensitivity#CASE_INSENSITIVE}.
         * <p>
         * Note that this will not change the case sensitivity of any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder caseInsensitiveByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #excludeObjectsByDefault()} and {@link #excludeArraysByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public Builder scalarsOnlyByDefault() {
            return excludeObjectsByDefault()
                    .excludeArraysByDefault();
        }

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON objects.
         * This can be overridden per property using {@link PropertyConfigurer#excludeObjects()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeObjectsByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON arrays.
         * This can be overridden per property using {@link PropertyConfigurer#excludeArrays()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeArraysByDefault();

        /**
         * Indicates that by default properties will be obfuscated if they are JSON objects or arrays (default).
         * This method is shorthand for calling both {@link #includeObjectsByDefault()} and {@link #includeArraysByDefault()}.
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public Builder allByDefault() {
            return includeObjectsByDefault()
                    .includeArraysByDefault();
        }

        /**
         * Indicates that by default properties will be obfuscated if they are JSON objects (default).
         * This can be overridden per property using {@link PropertyConfigurer#excludeObjects()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder includeObjectsByDefault();

        /**
         * Indicates that by default properties will be obfuscated if they are JSON arrays (default).
         * This can be overridden per property using {@link PropertyConfigurer#excludeArrays()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder includeArraysByDefault();

        /**
         * Sets whether or not to pretty-print obfuscated JSON. The default is {@code true}.
         *
         * @param prettyPrint {@code true} to pretty-print obfuscated JSON, or {@code false} otherwise.
         * @return This object.
         */
        public abstract Builder withPrettyPrinting(boolean prettyPrint);

        /**
         * Sets the warning to include if a {@link JsonParsingException} is thrown.
         * This can be used to override the default message. Use {@code null} to omit the warning.
         *
         * @param warning The warning to include.
         * @return This object.
         */
        public abstract Builder withMalformedJSONWarning(String warning);

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

        /**
         * Creates a new {@code JSONObfuscator} with the properties and obfuscators added to this builder.
         *
         * @return The created {@code JSONObfuscator}.
         */
        public abstract JSONObfuscator build();
    }

    /**
     * An object that can be used to configure a property that should be obfuscated.
     *
     * @author Rob Spoor
     */
    public abstract static class PropertyConfigurer extends Builder {

        private PropertyConfigurer() {
            super();
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #excludeObjects()} and {@link #excludeArrays()}.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public PropertyConfigurer scalarsOnly() {
            return excludeObjects()
                    .excludeArrays();
        }

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON objects.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer excludeObjects();

        /**
         * Indicates that properties with the current name will not be obfuscated if they are JSON arrays.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer excludeArrays();

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON objects or arrays.
         * This method is shorthand for calling both {@link #includeObjects()} and {@link #includeArrays()}.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public PropertyConfigurer all() {
            return includeObjects()
                    .includeArrays();
        }

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON objects.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer includeObjects();

        /**
         * Indicates that properties with the current name will be obfuscated if they are JSON arrays.
         *
         * @return An object that can be used to configure the property, or continue building {@link JSONObfuscator JSONObfuscators}.
         */
        public abstract PropertyConfigurer includeArrays();
    }

    private static final class ObfuscatorBuilder extends PropertyConfigurer {

        private final MapBuilder<PropertyConfig> properties;

        private boolean prettyPrint;

        private String malformedJSONWarning;

        // default settings
        private boolean obfuscateObjectsByDefault;
        private boolean obfuscateArraysByDefault;

        // per property settings
        private String property;
        private Obfuscator obfuscator;
        private CaseSensitivity caseSensitivity;
        private boolean obfuscateObjects;
        private boolean obfuscateArrays;

        private ObfuscatorBuilder() {
            properties = new MapBuilder<>();
            prettyPrint = true;
            malformedJSONWarning = Messages.JSONObfuscator.malformedJSON.text.get();

            obfuscateObjectsByDefault = true;
            obfuscateArraysByDefault = true;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator) {
            addLastProperty();

            properties.testEntry(property);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = null;
            this.obfuscateObjects = obfuscateObjectsByDefault;
            this.obfuscateArrays = obfuscateArraysByDefault;

            return this;
        }

        @Override
        public PropertyConfigurer withProperty(String property, Obfuscator obfuscator, CaseSensitivity caseSensitivity) {
            addLastProperty();

            properties.testEntry(property, caseSensitivity);

            this.property = property;
            this.obfuscator = obfuscator;
            this.caseSensitivity = caseSensitivity;
            this.obfuscateObjects = obfuscateObjectsByDefault;
            this.obfuscateArrays = obfuscateArraysByDefault;

            return this;
        }

        @Override
        public Builder caseSensitiveByDefault() {
            properties.caseSensitiveByDefault();
            return this;
        }

        @Override
        public Builder caseInsensitiveByDefault() {
            properties.caseInsensitiveByDefault();
            return this;
        }

        @Override
        public Builder excludeObjectsByDefault() {
            obfuscateObjectsByDefault = false;
            return this;
        }

        @Override
        public Builder excludeArraysByDefault() {
            obfuscateArraysByDefault = false;
            return this;
        }

        @Override
        public Builder includeObjectsByDefault() {
            obfuscateObjectsByDefault = true;
            return this;
        }

        @Override
        public Builder includeArraysByDefault() {
            obfuscateArraysByDefault = true;
            return this;
        }

        @Override
        public PropertyConfigurer excludeObjects() {
            obfuscateObjects = false;
            return this;
        }

        @Override
        public PropertyConfigurer excludeArrays() {
            obfuscateArrays = false;
            return this;
        }

        @Override
        public PropertyConfigurer includeObjects() {
            obfuscateObjects = true;
            return this;
        }

        @Override
        public PropertyConfigurer includeArrays() {
            obfuscateArrays = true;
            return this;
        }

        @Override
        public Builder withPrettyPrinting(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        @Override
        public Builder withMalformedJSONWarning(String warning) {
            malformedJSONWarning = warning;
            return this;
        }

        private Map<String, PropertyConfig> properties() {
            return properties.build();
        }

        private void addLastProperty() {
            if (property != null) {
                PropertyConfig propertyConfig = new PropertyConfig(obfuscator, obfuscateObjects, obfuscateArrays);
                if (caseSensitivity != null) {
                    properties.withEntry(property, propertyConfig, caseSensitivity);
                } else {
                    properties.withEntry(property, propertyConfig);
                }
            }

            property = null;
            obfuscator = null;
            caseSensitivity = null;
            obfuscateObjects = obfuscateObjectsByDefault;
            obfuscateArrays = obfuscateArraysByDefault;
        }

        @Override
        public JSONObfuscator build() {
            addLastProperty();

            return new JSONObfuscator(this);
        }
    }

    private static final class PropertyConfig {

        private final Obfuscator obfuscator;
        private final boolean obfuscateObjects;
        private final boolean obfuscateArrays;

        private PropertyConfig(Obfuscator obfuscator, boolean obfuscateObjects, boolean obfuscateArrays) {
            this.obfuscator = Objects.requireNonNull(obfuscator);
            this.obfuscateObjects = obfuscateObjects;
            this.obfuscateArrays = obfuscateArrays;
        }

        @Override
        public boolean equals(Object o) {
            // null and different types should not occur
            PropertyConfig other = (PropertyConfig) o;
            return obfuscator.equals(other.obfuscator)
                    && obfuscateObjects == other.obfuscateObjects
                    && obfuscateArrays == other.obfuscateArrays;
        }

        @Override
        public int hashCode() {
            return obfuscator.hashCode() ^ Boolean.hashCode(obfuscateObjects) ^ Boolean.hashCode(obfuscateArrays);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "[obfuscator=" + obfuscator
                    + ",obfuscateObjects=" + obfuscateObjects
                    + ",obfuscateArrays=" + obfuscateArrays
                    + "]";
        }
    }
}
