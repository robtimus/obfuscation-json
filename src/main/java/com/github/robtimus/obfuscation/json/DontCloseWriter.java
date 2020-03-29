/*
 * DontCloseWriter.java
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

import java.io.IOException;
import java.io.Writer;

final class DontCloseWriter extends Writer {

    private Writer output;

    DontCloseWriter(Writer output) {
        this.output = output;
    }

    @Override
    public void write(int c) throws IOException {
        output.write(c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        output.write(cbuf);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        output.write(cbuf, off, len);
    }

    @Override
    public void write(String str) throws IOException {
        output.write(str);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        output.write(str, off, len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        output.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        output.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        output.append(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    @Override
    public void close() throws IOException {
        // don't close output
    }
}
