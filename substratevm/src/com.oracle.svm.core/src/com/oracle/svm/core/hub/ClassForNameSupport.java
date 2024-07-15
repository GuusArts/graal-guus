/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;

import java.util.Objects;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

public final class ClassForNameSupport {

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Function<ClassLoader, ClassLoader> getRuntimeClassLoaderFunc;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ClassForNameSupport(Function<ClassLoader, ClassLoader> getRuntimeClassLoaderFunc) {
        Objects.requireNonNull(getRuntimeClassLoaderFunc);
        this.getRuntimeClassLoaderFunc = getRuntimeClassLoaderFunc;
    }

    public static ClassForNameSupport singleton() {
        return ImageSingletons.lookup(ClassForNameSupport.class);
    }

    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl") //
    private EconomicMap<String, ClassLoader> packageToLoader;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setPackageToLoader(EconomicMap<String, ClassLoader> packageToLoader) {
        this.packageToLoader = packageToLoader;
    }

    public ClassLoader findLoadedModuleLoader(String cn) {
        int pos = cn.lastIndexOf('.');
        if (pos < 0) {
            return null; /* unnamed package */
        }
        String pn = cn.substring(0, pos);
        return packageToLoader.get(pn);
    }

    private record Entry(ClassLoader loader, String className) {
        private static Entry of(String className) {
            return of(null, className);
        }

        private static Entry of(Class<?> clazz) {
            return of(clazz.getClassLoader(), clazz.getName());
        }

        private static Entry of(ClassLoader loader, String className) {
            return new Entry(loader, className);
        }
    }

    /** The map used to collect registered classes. */
    private final EconomicMap<Entry, ConditionalRuntimeValue<Object>> knownClasses = ImageHeapMap.create();

    private static final Object NEGATIVE_QUERY = new Object();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerClass(DynamicHub hub) {
        registerClass(ConfigurationCondition.alwaysTrue(), hub);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerClass(ConfigurationCondition condition, DynamicHub hub) {
        assert !hub.isPrimitive() : "primitive classes cannot be looked up by name";
        Class<?> clazz = hub.getHostedJavaClass();
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return; // must be defined at runtime before it can be looked up
        }
        synchronized (knownClasses) {
            String name = hub.getName();
            Entry entry = Entry.of(getRuntimeClassLoaderFunc.apply(hub.getClassLoader()), hub.getName());
            ConditionalRuntimeValue<Object> exisingEntry = knownClasses.get(entry);
            Object currentValue = exisingEntry == null ? null : exisingEntry.getValueUnconditionally();

            /* TODO: Remove workaround once GR-53985 is implemented */
            if (currentValue instanceof Class<?> currentClazz && clazz.getClassLoader() != currentClazz.getClassLoader()) {
                /* Ensure runtime lookup of GuestGraalClassLoader classes */
                if (isGuestGraalClass(currentClazz)) {
                    return;
                }
                if (isGuestGraalClass(clazz)) {
                    currentValue = null;
                }
            }

            if (currentValue == null || // never seen
                            currentValue == NEGATIVE_QUERY ||
                            currentValue == clazz) {
                currentValue = clazz;
                var cond = updateConditionalValue(exisingEntry, currentValue, condition);
                knownClasses.put(entry, cond);
            } else if (currentValue instanceof Throwable) { // failed at linking time
                var cond = updateConditionalValue(exisingEntry, currentValue, condition);
                /*
                 * If the class has already been seen as throwing an error, we don't overwrite this
                 * error. Nevertheless, we have to update the set of conditionals to be correct.
                 */
                knownClasses.put(entry, cond);
            } else {
                throw VMError.shouldNotReachHere("""
                                Invalid Class.forName value for %s: %s
                                If the class is already registered as negative, it means that it exists but is not
                                accessible through the builder class loader, and it was already registered by name (as
                                negative query) before this point. In that case, we update the map to contain the actual
                                class.
                                """, name, currentValue);
            }
        }
    }

    private static boolean isGuestGraalClass(Class<?> clazz) {
        var loader = clazz.getClassLoader();
        if (loader == null) {
            return false;
        }
        return "GuestGraalClassLoader".equals(loader.getName());
    }

    public static ConditionalRuntimeValue<Object> updateConditionalValue(ConditionalRuntimeValue<Object> existingConditionalValue, Object newValue,
                    ConfigurationCondition additionalCondition) {
        if (existingConditionalValue == null) {
            return new ConditionalRuntimeValue<>(RuntimeConditionSet.createHosted(additionalCondition), newValue);
        } else {
            existingConditionalValue.getConditions().addCondition(additionalCondition);
            existingConditionalValue.updateValue(newValue);
            return existingConditionalValue;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerExceptionForClass(ConfigurationCondition condition, String className, Throwable t) {
        updateCondition(condition, className, t);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(ConfigurationCondition condition, String className) {
        /*
         * If the class is not accessible by the builder class loader, but was already registered
         * through registerClass(Class<?>), we don't overwrite the actual class or exception.
         */
        updateCondition(condition, className, NEGATIVE_QUERY);
    }

    private void updateCondition(ConfigurationCondition condition, String className, Object value) {
        synchronized (knownClasses) {
            var runtimeConditions = knownClasses.putIfAbsent(Entry.of(className), new ConditionalRuntimeValue<>(RuntimeConditionSet.createHosted(condition), value));
            if (runtimeConditions != null) {
                runtimeConditions.getConditions().addCondition(condition);
            }
        }
    }

    public Class<?> forNameOrNull(String className, ClassLoader classLoader) {
        try {
            return forName(className, classLoader, true);
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("ClassForNameSupport#forNameOrNull should not throw", e);
        }
    }

    public Class<?> forName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        return forName(className, classLoader, false);
    }

    private Class<?> forName(String className, ClassLoader classLoader, boolean returnNullOnException) throws ClassNotFoundException {
        if (className == null) {
            return null;
        }
        var conditional = knownClasses.get(Entry.of(classLoader, className));
        Object result = conditional == null ? null : conditional.getValue();
        if (result == NEGATIVE_QUERY) {
            assert classLoader == null : "Unexpected NEGATIVE_QUERY result from classloader " + classLoader;
            /* The class was registered for reflective access but not available at build-time */
            result = new ClassNotFoundException(className);
        } else if (className.endsWith("[]")) {
            /* Querying array classes with their "TypeName[]" name always throws */
            result = new ClassNotFoundException(className);
        }
        if (result == null) {
            result = PredefinedClassesSupport.getLoadedForNameOrNull(className, classLoader);
        }
        // Note: for non-predefined classes, we (currently) don't need to check the provided loader
        // TODO rewrite stack traces (GR-42813)
        if (result instanceof Class<?>) {
            return (Class<?>) result;
        } else if (result instanceof Throwable) {
            if (returnNullOnException) {
                return null;
            }

            if (result instanceof Error) {
                throw (Error) result;
            } else if (result instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) result;
            }
        } else if (result == null) {
            if (classLoader == null && throwMissingRegistrationErrors()) {
                MissingReflectionRegistrationUtils.forClass(className);
            }

            if (returnNullOnException) {
                return null;
            } else {
                throw new ClassNotFoundException(className);
            }
        }
        throw VMError.shouldNotReachHere("Class.forName result should be Class, ClassNotFoundException or Error: " + result);
    }

    public int count() {
        return knownClasses.size();
    }

    public RuntimeConditionSet getConditionFor(Class<?> jClass) {
        Objects.requireNonNull(jClass);
        ConditionalRuntimeValue<Object> conditionalClass = knownClasses.get(Entry.of(jClass));
        if (conditionalClass == null) {
            return RuntimeConditionSet.unmodifiableEmptySet();
        } else {
            return conditionalClass.getConditions();
        }
    }

    public Class<?> dynamicHubForName0(String name, ClassLoader loader) throws ClassNotFoundException {
        ClassLoader current = loader;
        while (true) {
            Class<?> result = forNameOrNull(name, current);
            if (result != null) {
                return result;
            }
            if (current != null) {
                Target_java_lang_ClassLoader loaderInternal = SubstrateUtil.cast(current, Target_java_lang_ClassLoader.class);
                current = SubstrateUtil.cast(loaderInternal.parent, ClassLoader.class);
            } else {
                break;
            }
        }
        throw new ClassNotFoundException(name);
    }
}
