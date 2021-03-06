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
                JsonGenerator jsonGenerator = createJsonGenerator(writer)) {

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
                    jsonGenerator.write(jsonParser.getValue());
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

    @SuppressWarnings("resource")
    JsonGenerator createJsonGenerator(JSONObfuscatorWriter writer) {
        return new ObfuscatingJsonGenerator(jsonGeneratorFactory.createGenerator(new DontCloseWriter(writer)), writer, properties);
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
         * This can be overridden per property using {@link PropertyConfigurer#includeObjects()}
         * <p>
         * Note that this will not change what will be obfuscated for any property that was already added.
         *
         * @return This object.
         */
        public abstract Builder excludeObjectsByDefault();

        /**
         * Indicates that by default properties will not be obfuscated if they are JSON arrays.
         * This can be overridden per property using {@link PropertyConfigurer#includeArrays()}
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
}
