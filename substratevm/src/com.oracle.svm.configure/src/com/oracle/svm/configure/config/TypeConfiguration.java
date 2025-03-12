/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.impl.UnresolvedConfigurationCondition;

import com.oracle.svm.configure.ConditionalElement;
import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ReflectionConfigurationParser;
import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;

import jdk.graal.compiler.util.json.JsonWriter;

public final class TypeConfiguration extends ConfigurationBase<TypeConfiguration, TypeConfiguration.Predicate> {

    private final ConcurrentMap<ConditionalElement<ConfigurationTypeDescriptor>, ConfigurationType> types = new ConcurrentHashMap<>();

    private final String combinedFileKey;

    public TypeConfiguration(String combinedFileKey) {
        this.combinedFileKey = combinedFileKey;
    }

    public TypeConfiguration(TypeConfiguration other) {
        other.types.forEach((key, value) -> types.put(key, new ConfigurationType(value)));
        this.combinedFileKey = other.combinedFileKey;
    }

    @Override
    public TypeConfiguration copy() {
        return new TypeConfiguration(this);
    }

    @Override
    protected void merge(TypeConfiguration other) {
        other.types.forEach((key, value) -> {
            types.compute(key, (k, v) -> {
                if (v != null) {
                    return ConfigurationType.copyAndMerge(v, value);
                }
                return value;
            });
        });
    }

    @Override
    public void subtract(TypeConfiguration other) {
        other.types.forEach((key, type) -> {
            types.computeIfPresent(key, (k, v) -> ConfigurationType.copyAndSubtract(v, type));
        });
    }

    @Override
    protected void intersect(TypeConfiguration other) {
        types.forEach((key, type) -> {
            ConfigurationType intersectedType = other.types.get(key);
            if (intersectedType != null) {
                types.compute(key, (k, v) -> ConfigurationType.copyAndIntersect(type, intersectedType));
            } else {
                types.remove(key);
            }
        });
    }

    @Override
    protected void removeIf(Predicate predicate) {
        types.entrySet().removeIf(entry -> predicate.testIncludedType(entry.getKey(), entry.getValue()));
    }

    public ConfigurationType get(UnresolvedConfigurationCondition condition, ConfigurationTypeDescriptor typeDescriptor) {
        return types.get(new ConditionalElement<>(condition, typeDescriptor));
    }

    public void add(ConfigurationType type) {
        ConfigurationType previous = types.putIfAbsent(new ConditionalElement<>(type.getCondition(), type.getTypeDescriptor()), type);
        if (previous != null && previous != type) {
            throw new IllegalArgumentException("Cannot replace existing type " + previous + " with " + type);
        }
    }

    public void addOrMerge(ConfigurationType type) {
        types.compute(new ConditionalElement<>(type.getCondition(), type.getTypeDescriptor()), (key, value) -> {
            if (value == null) {
                return type;
            } else {
                value.mergeFrom(type);
                return value;
            }
        });
    }

    public ConfigurationType getOrCreateType(UnresolvedConfigurationCondition condition, String qualifiedForNameString) {
        return getOrCreateType(condition, new NamedConfigurationTypeDescriptor(qualifiedForNameString));
    }

    public ConfigurationType getOrCreateType(UnresolvedConfigurationCondition condition, ConfigurationTypeDescriptor typeDescriptor) {
        return types.computeIfAbsent(new ConditionalElement<>(condition, typeDescriptor), p -> new ConfigurationType(p.condition(), p.element(), true));
    }

    @Override
    public void mergeConditional(UnresolvedConfigurationCondition condition, TypeConfiguration other) {
        other.types.forEach((key, value) -> {
            addOrMerge(new ConfigurationType(value, condition));
        });
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        List<ConfigurationType> typesList = new ArrayList<>(this.types.values());
        typesList.sort(Comparator.comparing(ConfigurationType::getTypeDescriptor).thenComparing(ConfigurationType::getCondition));

        writer.append('[').indent();
        String prefix = "";
        for (ConfigurationType type : typesList) {
            writer.append(prefix).newline();
            type.printJson(writer);
            prefix = ",";
        }
        writer.unindent().newline().append(']');
    }

    @Override
    public ConfigurationParser createParser(boolean strictMetadata) {
        return ReflectionConfigurationParser.create(combinedFileKey, strictMetadata, ConfigurationConditionResolver.identityResolver(), new ParserConfigurationAdapter(this), true, false, false);
    }

    @Override
    public boolean isEmpty() {
        return types.isEmpty();
    }

    @Override
    public boolean supportsCombinedFile() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeConfiguration that = (TypeConfiguration) o;
        return types.equals(that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(types);
    }

    public interface Predicate {

        boolean testIncludedType(ConditionalElement<ConfigurationTypeDescriptor> conditionalElement, ConfigurationType type);
    }
}
