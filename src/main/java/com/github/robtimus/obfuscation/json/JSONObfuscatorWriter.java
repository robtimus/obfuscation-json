/*
 * JSONObfuscatorWriter.java
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

import static com.github.robtimus.obfuscation.ObfuscatorUtils.writer;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import com.github.robtimus.obfuscation.Obfuscator;

final class JSONObfuscatorWriter extends Writer {

    private final Writer original;

    private final StringBuilder unquoting;
    private final Writer unquotingWriter;

    private Writer delegate;

    private boolean trimWhitespace = true;

    JSONObfuscatorWriter(Writer delegate) {
        original = Objects.requireNonNull(delegate);

        unquoting = new StringBuilder();
        unquotingWriter = writer(unquoting);

        this.delegate = original;
    }

    /**
     * Purpose: make sure no obfuscating takes place in catch or finally block.
     */
    void endObfuscating() {
        delegate = original;
    }

    /**
     * Purpose: validate that no obfuscating takes place, at the end of obfuscating.
     */
    void assertNonObfuscating() {
        assert original == delegate : "Should not be obfuscating"; //$NON-NLS-1$
    }

    /**
     * Purpose: start obfuscating all content written to this writer.
     */
    void startObfuscate(Obfuscator obfuscator) {
        assert original == delegate : "Can only obfuscate the original writer"; //$NON-NLS-1$
        delegate = obfuscator.streamTo(original);
    }

    /**
     * Purpose: restore the original writer when obfuscating is no longer needed.
     */
    void endObfuscate() throws IOException {
        assert original != delegate && unquotingWriter != delegate : "Can only restore original if obfuscating"; //$NON-NLS-1$
        delegate.close();
        delegate = original;
    }

    /**
     * Purpose: remove the start and end quotes of written JSON strings.
     */
    void startUnquote() {
        assert original == delegate : "Can ony unquote the original writer"; //$NON-NLS-1$
        delegate = unquotingWriter;
    }

    /**
     * Purpose: flush and restore the original writer after a JSON string was written.
     */
    void endUnquote() throws IOException {
        assert unquotingWriter == delegate : "Can only end unquote when unquoting"; //$NON-NLS-1$
        assert unquoting.length() >= 2 && unquoting.charAt(0) == '"' && unquoting.charAt(unquoting.length() - 1) == '"'
                : "Can only unquote a quoted value"; //$NON-NLS-1$
        original.append(unquoting, 1, unquoting.length() - 1);
        unquoting.delete(0, unquoting.length());
        delegate = original;
    }

    @Override
    public void write(int c) throws IOException {
        if (!Character.isWhitespace(c) || !trimWhitespace) {
            delegate.write(c);
            trimWhitespace = false;
        }
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        write(cbuf, 0, cbuf.length);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (trimWhitespace) {
            while (len > 0 && off < cbuf.length && Character.isWhitespace(cbuf[off])) {
                off++;
                len--;
            }
            trimWhitespace = len == 0;
        }
        if (len > 0) {
            delegate.write(cbuf, off, len);
        }
    }

    @Override
    public void write(String str) throws IOException {
        write(str, 0, str.length());
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        if (trimWhitespace) {
            while (len > 0 && Character.isWhitespace(str.charAt(off))) {
                off++;
                len--;
            }
            trimWhitespace = len == 0;
        }
        if (len > 0) {
            delegate.write(str, off, len);
        }
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        // Not going to append null
        return append(csq, 0, csq.length());
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        // Not going to append null
        if (trimWhitespace) {
            while (start < end && Character.isWhitespace(csq.charAt(start))) {
                start++;
            }
            trimWhitespace = start == end;
        }
        if (start < end) {
            delegate.append(csq, start, end);
        }
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        if (!Character.isWhitespace(c) || !trimWhitespace) {
            delegate.append(c);
            trimWhitespace = false;
        }
        return this;
    }

    @Override
    public void flush() throws IOException {
        // don't delegate, to prevent too many flushes to underlying writers
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
