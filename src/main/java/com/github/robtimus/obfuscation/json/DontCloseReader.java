/*
 * DontCloseReader.java
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
import java.io.Reader;
import java.nio.CharBuffer;

final class DontCloseReader extends Reader {

    private Reader input;

    DontCloseReader(Reader input) {
        this.input = input;
    }

    @Override
    public int read(CharBuffer target) throws IOException {
        return input.read(target);
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public int read(char[] cbuf) throws IOException {
        return input.read(cbuf);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        return input.read(cbuf, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return input.skip(n);
    }

    @Override
    public boolean ready() throws IOException {
        return input.ready();
    }

    @Override
    public boolean markSupported() {
        return input.markSupported();
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        input.mark(readAheadLimit);
    }

    @Override
    public void reset() throws IOException {
        input.reset();
    }

    @Override
    public void close() throws IOException {
        // don't close input
    }
}
