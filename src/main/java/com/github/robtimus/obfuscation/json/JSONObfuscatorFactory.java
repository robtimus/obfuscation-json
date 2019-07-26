/*
 * JSONObfuscatorFactory.java
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

import com.github.robtimus.obfuscation.PropertyObfuscator;
import com.github.robtimus.obfuscation.PropertyObfuscator.Builder;
import com.github.robtimus.obfuscation.PropertyObfuscatorFactory;

/**
 * A factory for {@link JSONObfuscator JSONObfuscators}.
 *
 * @author Rob Spoor
 */
public class JSONObfuscatorFactory implements PropertyObfuscatorFactory {

    @Override
    public String type() {
        return JSONObfuscator.TYPE;
    }

    @Override
    public PropertyObfuscator createPropertyObfuscator(Builder builder) {
        return new JSONObfuscator(builder);
    }
}
