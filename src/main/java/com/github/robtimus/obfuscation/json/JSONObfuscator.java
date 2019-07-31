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
import static com.github.robtimus.obfuscation.ObfuscatorUtils.readAll;
import static com.github.robtimus.obfuscation.ObfuscatorUtils.reader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;
import javax.json.JsonException;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonParser;
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
public class JSONObfuscator extends PropertyObfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSONObfuscator.class);

    /** The obfuscator type, as can be used in {@link PropertyObfuscator#ofType(String)}. */
    public static final String TYPE = "json"; //$NON-NLS-1$

    private static final JSONObfuscatorFactory FACTORY = new JSONObfuscatorFactory();

    private static final boolean DEFAULT_INCLUDE_MALFORMED_JSON_WARNING = true;
    private static final MalformedJSONStrategy DEFAULT_MALFORMED_JSON_STRATEGY = MalformedJSONStrategy.DISCARD_REMAINDER;

    private final boolean includeMalformedJSONWarning;
    private final MalformedJSONStrategy malformedJSONStrategy;

    private final JsonProvider jsonProvider;

    JSONObfuscator(Builder builder) {
        super(builder);
        if (builder instanceof JSONBuilder) {
            JSONBuilder jsonBuilder = (JSONBuilder) builder;
            includeMalformedJSONWarning = jsonBuilder.includeMalformedJSONWarning;
            malformedJSONStrategy = jsonBuilder.malformedJSONStrategy;
        } else {
            includeMalformedJSONWarning = DEFAULT_INCLUDE_MALFORMED_JSON_WARNING;
            malformedJSONStrategy = DEFAULT_MALFORMED_JSON_STRATEGY;
        }

        jsonProvider = JsonProvider.provider();
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
        obfuscateText(input, s, start, end, destination);
    }

    @Override
    public void obfuscateText(Reader input, Appendable destination) throws IOException {
        StringBuilder contents = new StringBuilder();
        @SuppressWarnings("resource")
        Reader reader = copyTo(input, contents);
        obfuscateText(reader, contents, 0, -1, destination);
    }

    private void obfuscateText(Reader input, CharSequence s, int start, int end, Appendable destination) throws IOException {
        JsonParser jsonParser = jsonProvider.createParser(input);
        Context context = new Context(jsonParser, s, start, destination);
        try {
            while (context.update(true)) {
                if (context.event == JsonParser.Event.KEY_NAME) {
                    String property = context.tokenValue;
                    Obfuscator obfuscator = getObfuscator(property);
                    if (obfuscator != null) {
                        obfuscateProperty(obfuscator, context);
                    }
                }
            }
            // read the remainder so the final append will include all text
            readAll(input);
            destination.append(s, context.textIndex, end == -1 ? s.length() : end);
        } catch (JsonParsingException e) {
            LOGGER.warn(Messages.JSONObfuscator.malformedJSON.warning.get(), e);
            if (includeMalformedJSONWarning) {
                context.destination.append(Messages.JSONObfuscator.malformedJSON.text.get());
            }
            // read the remainder, because the strategy may need it
            readAll(input);
            malformedJSONStrategy.handleMalformedJSON(context, end);
        } catch (JsonException e) {
            throw new IOException(e);
        }
    }

    private void obfuscateProperty(Obfuscator obfuscator, Context context) throws IOException {
        context.update(true);
        switch (context.event) {
        case START_ARRAY:
            obfuscateNested(obfuscator, context, JsonParser.Event.START_ARRAY, JsonParser.Event.END_ARRAY);
            break;
        case START_OBJECT:
            obfuscateNested(obfuscator, context, JsonParser.Event.START_OBJECT, JsonParser.Event.END_OBJECT);
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            obfuscateScalar(obfuscator, context);
            break;
        default:
            // do nothing
        }
    }

    private void obfuscateNested(Obfuscator obfuscator, Context context, JsonParser.Event beginEvent, JsonParser.Event endEvent) throws IOException {
        int originalTokenStart = context.tokenStart;
        int depth = 1;
        while (depth > 0) {
            context.update(false);
            if (context.event == beginEvent) {
                depth++;
            } else if (context.event == endEvent) {
                depth--;
            }
        }
        context.destination.append(context.text, context.textIndex, originalTokenStart);
        obfuscator.obfuscateText(context.text, originalTokenStart, context.tokenEnd, context.destination);
        context.textIndex = context.tokenEnd;
    }

    private void obfuscateScalar(Obfuscator obfuscator, Context context) throws IOException {
        context.destination.append(context.text, context.textIndex, context.tokenStart);
        obfuscator.obfuscateText(context.tokenValue, context.destination);
        context.textIndex = context.tokenEnd;
    }

    private static final class Context {
        private final JsonParser jsonParser;
        private final CharSequence text;
        private final Appendable destination;

        private final int textOffset;
        private int textIndex;

        private JsonParser.Event event;
        private String tokenValue;
        private int tokenStart;
        private int tokenEnd;

        private Context(JsonParser jsonParser, CharSequence source, int start, Appendable destination) {
            this.jsonParser = jsonParser;
            this.text = source;
            this.textOffset = start;
            this.textIndex = start;
            this.tokenEnd = start;
            this.destination = destination;
        }

        private boolean update(boolean append) throws IOException {
            /*
             * This method could be split into separate methods:
             * 1) update token only
             * 2) update the other token values,
             * This would mean that indexOfTokenValue will take longer if tokens are ignored though. In then end the same scanning is performed.
             * Plus, it could possibly lead to wrong values being matched if two properties have the same value but only the second needs to be
             * obfuscated.
             */
            if (jsonParser.hasNext()) {
                tokenStart = (int) jsonParser.getLocation().getStreamOffset();
int before = tokenStart;
                event = jsonParser.next();
long after = jsonParser.getLocation().getStreamOffset();
System.out.printf("%d vs %d%n", before, after);
                tokenValue = getTokenValue();
                tokenStart = indexOfTokenValue();
                tokenEnd = tokenStart + tokenValue.length();
                if (append) {
                    destination.append(text, textIndex, tokenStart);
                    textIndex = tokenStart;
                }
                return true;
            }
            return false;
        }

        private String getTokenValue() {
            switch (event) {
            case START_ARRAY:
                return "["; //$NON-NLS-1$
            case END_ARRAY:
                return "]"; //$NON-NLS-1$
            case START_OBJECT:
                return "{"; //$NON-NLS-1$
            case END_OBJECT:
                return "}"; //$NON-NLS-1$
            case KEY_NAME:
                return jsonParser.getString();
            case VALUE_STRING:
            case VALUE_NUMBER:
                // use String representation
                return jsonParser.getString();
            case VALUE_TRUE:
                return "true"; //$NON-NLS-1$
            case VALUE_FALSE:
                return "false"; //$NON-NLS-1$
            case VALUE_NULL:
                return "null"; //$NON-NLS-1$
            default:
                throw new IllegalStateException(Messages.JSONObfuscator.unexpectedEvent.get(event));
            }
        }

        private int indexOfTokenValue() {
            System.out.printf("%s: %s (%d) vs %d%n", tokenValue, jsonParser.getLocation(), jsonParser.getLocation().getStreamOffset(), tokenStart);
            System.out.printf("%s vs %s%n", tokenValue, text.subSequence(textOffset + tokenStart, textOffset + (int) jsonParser.getLocation().getStreamOffset()));
//            return (int) jsonParser.getLocation().getStreamOffset();
            int end = text.length() - tokenValue.length();
            for (int i = tokenEnd; i <= end; i++) {
                if (isTokenValue(i)) {
                    return i;
                }
            }
            //throw new IllegalStateException(Messages.JSONObfuscator.tokenNotFound.get(tokenValue));
            throw new IllegalStateException("not found: " + tokenValue);
        }

        private boolean isTokenValue(int index) {
            for (int i = 0, j = index; i < tokenValue.length(); i++, j++) {
                char c1 = tokenValue.charAt(i);
                char c2 = text.charAt(j);
                if (c1 != c2) {
                    return false;
                }
            }
            return true;
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
    public static JSONBuilder builder() {
        return new JSONBuilder();
    }

    /**
     * A builder for {@link JSONObfuscator JSONObfuscators}.
     *
     * @author Rob Spoor
     */
    public static final class JSONBuilder extends Builder {

        private boolean includeMalformedJSONWarning = DEFAULT_INCLUDE_MALFORMED_JSON_WARNING;
        private MalformedJSONStrategy malformedJSONStrategy = DEFAULT_MALFORMED_JSON_STRATEGY;

        private JSONBuilder() {
            super(FACTORY);
        }

        @Override
        public JSONBuilder withProperty(String property, Obfuscator obfuscator) {
            super.withProperty(property, obfuscator);
            return this;
        }

        /**
         * Sets whether or not to include a warning if a {@link JsonParsingException} is thrown. The default is {@code true}.
         *
         * @param include {@code true} to include a warning, or {@code false} otherwise.
         * @return This object.
         */
        public JSONBuilder includeMalformedJSONWarning(boolean include) {
            includeMalformedJSONWarning = include;
            return this;
        }

        /**
         * Sets the malformed JSON strategy to use. The default is {@link MalformedJSONStrategy#DISCARD_REMAINDER}.
         *
         * @param strategy The malformed JSON strategy to use.
         * @return This object.
         */
        public JSONBuilder withMalformedJSONStrategy(MalformedJSONStrategy strategy) {
            this.malformedJSONStrategy = Objects.requireNonNull(strategy);
            return this;
        }

        @Override
        public JSONObfuscator build() {
            return (JSONObfuscator) super.build();
        }
    }

    /**
     * A strategy for dealing with {@link JsonParsingException JsonParsingExceptions} while obfuscating text.
     * If a {@code JsonParsingException} is thrown, a warning may be appended indicating that obfuscation is aborted (depending on the setting).
     * The strategy determines what happens next.
     *
     * @author Rob Spoor
     */
    public enum MalformedJSONStrategy {
        /** Indicates that the remainder of the text should be discarded. Only the warning will be appended. This is the default strategy. */
        DISCARD_REMAINDER {
            @Override
            void handleMalformedJSON(Context context, int end) throws IOException {
                // don't do anything
            }
        },
        /** Indicates that the remainder of the text should be included after the warning. */
        INCLUDE_REMAINDER {
            @Override
            void handleMalformedJSON(Context context, int end) throws IOException {
                context.destination.append(context.text, context.textIndex, end == -1 ? context.text.length() : end);
            }
        },
        ;

        abstract void handleMalformedJSON(Context context, int end) throws IOException;
    }
}
