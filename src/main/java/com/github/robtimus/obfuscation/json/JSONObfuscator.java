/*
 * JSONObfuscator.java
 * Copyright 2019 Rob Spoor
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

import static com.github.robtimus.obfuscation.ObfuscatorUtils.checkStartAndEnd;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.copyTo;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.discardAll;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.reader;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.writer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import javax.json.JsonException;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.obfuscation.CachingObfuscatingWriter;
import com.github.robtimus.obfuscation.Obfuscator;
import com.github.robtimus.obfuscation.PropertyObfuscator;

/**
 * An obfuscator that obfuscates JSON properties in {@link CharSequence CharSequences} or the contents of {@link Reader Readers}.
 *
 * @author Rob Spoor
 */
public final class JSONObfuscator extends PropertyObfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    private static final JsonProvider JSON_PROVIDER = JsonProvider.provider();

    private final JsonGeneratorFactory jsonGeneratorFactory;

    private final String malformedJSONWarning;

    private JSONObfuscator(Builder builder) {
        super(builder);

        Map<String, ?> config = builder.prettyPrint ? Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true) : Collections.emptyMap();
        jsonGeneratorFactory = JSON_PROVIDER.createGeneratorFactory(config);

        malformedJSONWarning = builder.malformedJSONWarning;
    }

    @Override
    public CharSequence obfuscateText(CharSequence s, int start, int end) {
        checkStartAndEnd(s, start, end);
        StringBuilder sb = new StringBuilder(end - start);
        try {
            obfuscateText(s, start, end, sb);
            return sb.toString();
        } catch (IOException e) {
            // will not occur
            throw new IllegalStateException(e);
        }
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

    private void obfuscateText(Reader input, CharSequence s, int end, JSONObfuscatorWriter writer) throws IOException {

        try {
            @SuppressWarnings("resource")
            JsonParser jsonParser = JSON_PROVIDER.createParser(input);
            @SuppressWarnings("resource")
            JsonGenerator jsonGenerator = jsonGeneratorFactory.createGenerator(writer);

            // depth > 0 means that an entire object or array needs to be obfuscated.
            int depth = 0;
            // obfuscator != null means the current value needs be obfuscated.
            Obfuscator obfuscator = null;
            Event event = null;

            // The below will flush the jsonGenerator after each write, so writes are done to the correct writer at the time
            while (jsonParser.hasNext()) {
                event = jsonParser.next();
                switch (event) {
                case START_OBJECT:
                case START_ARRAY:
                    if (obfuscator != null) {
                        if (depth == 0) {
                            // The start of obfuscating an object or array; the object or array should be obfuscated completely
                            writer.startObfuscate(obfuscator);
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
                            obfuscator = null;
                        }
                    }
                    break;
                case KEY_NAME:
                    String propertyName = jsonParser.getString();
                    if (obfuscator == null) {
                        obfuscator = getObfuscator(propertyName);
                    }
                    jsonGenerator.writeKey(propertyName);
                    jsonGenerator.flush();
                    break;
                case VALUE_STRING:
                    if (obfuscator != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue(jsonParser.getString(), obfuscator, jsonGenerator, writer, true);
                        obfuscator = null;
                   } else {
                        // Either not obfuscating, or nested in an object or array that will be obfuscated; write the value as-is.
                       jsonGenerator.write(jsonParser.getString());
                    }
                    break;
                case VALUE_NULL:
                    if (obfuscator != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue("null", obfuscator, jsonGenerator, writer, false); //$NON-NLS-1$
                        obfuscator = null;
                    } else {
                        // Either not obfuscating, or nested in an object or array that will be obfuscated
                        jsonGenerator.writeNull();
                    }
                    break;
                case VALUE_NUMBER:
                    if (obfuscator != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue(jsonParser.getString(), obfuscator, jsonGenerator, writer, false);
                        obfuscator = null;
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
                    if (obfuscator != null && depth == 0) {
                        // Only this value needs to be obfuscated
                        obfuscateValue(Boolean.toString(event == Event.VALUE_TRUE), obfuscator, jsonGenerator, writer, false);
                        obfuscator = null;
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
    public static final class Builder extends AbstractBuilder<Builder> {

        private boolean prettyPrint = true;

        private String malformedJSONWarning = Messages.JSONObfuscator.malformedJSON.text.get();

        private Builder() {
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

        @Override
        public JSONObfuscator build() {
            return new JSONObfuscator(this);
        }
    }
}
