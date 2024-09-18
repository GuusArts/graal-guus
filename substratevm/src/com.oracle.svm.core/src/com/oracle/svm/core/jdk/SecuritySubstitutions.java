/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.Target_java_lang_ThreadLocal;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import sun.security.util.Debug;
import sun.security.util.SecurityConstants;

/*
 * All security checks are disabled.
 */

@TargetClass(value = java.security.AccessController.class, onlyWith = JDK21OrEarlier.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
@SuppressWarnings({"unused"})
final class Target_java_security_AccessController {

    @Substitute
    @SuppressWarnings("deprecation")
    static AccessControlContext getStackAccessControlContext() {
        if (!CEntryPointSnippets.isIsolateInitialized()) {
            /*
             * If isolate still isn't initialized, we can assume that we are so early in the JDK
             * initialization that any attempt at stalk walk will fail as not even the basic
             * PrintWriter/Logging is available yet. This manifested when
             * UseDedicatedVMOperationThread hosted option was set, triggering a runtime crash.
             */
            return null;
        }
        return StackAccessControlContextVisitor.getFromStack();
    }

    @Substitute
    static AccessControlContext getInheritedAccessControlContext() {
        return SubstrateUtil.cast(Thread.currentThread(), Target_java_lang_Thread.class).inheritedAccessControlContext;
    }

    @Substitute
    private static ProtectionDomain getProtectionDomain(final Class<?> caller) {
        return caller.getProtectionDomain();
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    static <T> T executePrivileged(PrivilegedExceptionAction<T> action, AccessControlContext context, Class<?> caller) throws Throwable {
        if (action == null) {
            throw new NullPointerException("Null action");
        }

        PrivilegedStack.push(context, caller);
        try {
            return action.run();
        } finally {
            PrivilegedStack.pop();
        }
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    static <T> T executePrivileged(PrivilegedAction<T> action, AccessControlContext context, Class<?> caller) {
        if (action == null) {
            throw new NullPointerException("Null action");
        }

        PrivilegedStack.push(context, caller);
        try {
            return action.run();
        } finally {
            PrivilegedStack.pop();
        }
    }

    @Substitute
    @SuppressWarnings("deprecation")
    static AccessControlContext checkContext(AccessControlContext context, Class<?> caller) {

        if (context != null && context.equals(AccessControllerUtil.DISALLOWED_CONTEXT_MARKER)) {
            VMError.shouldNotReachHere(
                            "Non-allowed AccessControlContext that was replaced with a blank one at build time was invoked without being reinitialized at run time." + System.lineSeparator() +
                                            "This might be an indicator of improper build time initialization, or of a non-compatible JDK version." + System.lineSeparator() +
                                            "In order to fix this you can either:" + System.lineSeparator() +
                                            "    * Annotate the offending context's field with @RecomputeFieldValue" + System.lineSeparator() +
                                            "    * Implement a custom runtime accessor and annotate said field with @InjectAccessors" + System.lineSeparator() +
                                            "    * If this context originates from the JDK, and it doesn't leak sensitive info, you can allow it in 'AccessControlContextReplacerFeature.duringSetup'");
        }

        // check if caller is authorized to create context
        if (System.getSecurityManager() != null) {
            throw VMError.unsupportedFeature("SecurityManager isn't supported");
        }
        return context;
    }
}

@TargetClass(SecurityManager.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_lang_SecurityManager {
    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    protected Class<?>[] getClassContext() {
        final Pointer startSP = readCallerStackPointer();
        return StackTraceUtils.getClassContext(0, startSP);
    }
}

@TargetClass(className = "javax.crypto.JceSecurityManager")
@SuppressWarnings({"static-method", "unused"})
final class Target_javax_crypto_JceSecurityManager {
    @Substitute
    Target_javax_crypto_CryptoPermission getCryptoPermission(String var1) {
        return SubstrateUtil.cast(Target_javax_crypto_CryptoAllPermission.INSTANCE, Target_javax_crypto_CryptoPermission.class);
    }
}

@TargetClass(className = "javax.crypto.CryptoPermission")
final class Target_javax_crypto_CryptoPermission {
}

@TargetClass(className = "javax.crypto.CryptoAllPermission")
final class Target_javax_crypto_CryptoAllPermission {
    @Alias //
    static Target_javax_crypto_CryptoAllPermission INSTANCE;
}

@TargetClass(value = java.security.Provider.class, innerClass = "ServiceKey")
final class Target_java_security_Provider_ServiceKey {

}

@TargetClass(value = java.security.Provider.class)
final class Target_java_security_Provider {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ServiceKeyComputer.class) //
    @TargetElement(name = "previousKey", onlyWith = JDK21OrEarlier.class) //
    private static Target_java_security_Provider_ServiceKey previousKeyJDK21;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadLocalServiceKeyComputer.class) //
    @TargetElement(onlyWith = JDKLatest.class) //
    private static Target_java_lang_ThreadLocal previousKey;
}

@TargetClass(value = java.security.Provider.class, innerClass = "Service")
final class Target_java_security_Provider_Service {

    /**
     * The field is lazily initialized on first access. We already have the necessary reflection
     * configuration for the reflective lookup at image run time.
     */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private Object constructorCache;
}

@TargetClass(value = java.security.Security.class)
final class Target_java_security_Security {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    static Properties props;

    @Alias //
    private static Properties initialSecurityProperties;

    @Alias //
    private static Debug sdebug;

    @Substitute
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    private static void initialize() {
        props = SecurityProvidersSupport.singleton().getSavedInitialSecurityProperties();
        boolean overrideAll = false;

        if ("true".equalsIgnoreCase(props.getProperty("security.overridePropertiesFile"))) {
            String extraPropFile = System.getProperty("java.security.properties");
            if (extraPropFile != null && extraPropFile.startsWith("=")) {
                overrideAll = true;
                extraPropFile = extraPropFile.substring(1);
            }
            loadProps(null, extraPropFile, overrideAll);
        }
        initialSecurityProperties = (Properties) props.clone();
        if (sdebug != null) {
            for (String key : props.stringPropertyNames()) {
                sdebug.println("Initial security property: " + key + "=" + props.getProperty(key));
            }
        }
    }

    @Alias
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    private static native boolean loadProps(File masterFile, String extraPropFile, boolean overrideAll);
}

@TargetClass(value = java.security.Security.class, innerClass = "SecPropLoader", onlyWith = JDKLatest.class)
final class Target_java_security_Security_SecPropLoader {

    @Substitute
    private static void loadMaster() {
        Target_java_security_Security.props = SecurityProvidersSupport.singleton().getSavedInitialSecurityProperties();
    }
}

class ServiceKeyProvider {
    static Object getNewServiceKey() {
        Class<?> serviceKey = ReflectionUtil.lookupClass("java.security.Provider$ServiceKey");
        Constructor<?> constructor = ReflectionUtil.lookupConstructor(serviceKey, String.class, String.class, boolean.class);
        return ReflectionUtil.newInstance(constructor, "", "", false);
    }

    /**
     * Originally the thread local creates a new default service key each time. Here we always
     * return the singleton default service key. This default key will be replaced with an actual
     * key in {@code java.security.Provider.parseLegacy}
     */
    static Supplier<Object> getNewServiceKeySupplier() {
        final Object singleton = ServiceKeyProvider.getNewServiceKey();
        return () -> singleton;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ServiceKeyComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return ServiceKeyProvider.getNewServiceKey();
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadLocalServiceKeyComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        // Originally the thread local creates a new default service key each time.
        // Here we always return the singleton default service key. This default key
        // will be replaced with an actual key in Provider.parseLegacy
        return ThreadLocal.withInitial(ServiceKeyProvider.getNewServiceKeySupplier());
    }
}

@Platforms(Platform.WINDOWS.class)
@TargetClass(value = java.security.Provider.class)
final class Target_java_security_Provider_Windows {

    @Alias //
    private transient boolean initialized;

    @Alias //
    String name;

    /*
     * `Provider.checkInitialized` is called from all other Provider API methods, before any
     * computation, so it is a convenient location to do our own initialization, e.g., to ensure
     * that the required native libraries are loaded.
     */
    @Substitute
    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException();
        }
        /* Do our own initialization. */
        ProviderUtil.initialize(this);
    }
}

final class ProviderUtil {
    private static volatile boolean initialized = false;

    @SuppressWarnings("restricted")
    static void initialize(Target_java_security_Provider_Windows provider) {
        if (initialized) {
            return;
        }

        if ("SunMSCAPI".equals(provider.name)) {
            try {
                System.loadLibrary("sunmscapi");
            } catch (Throwable ignored) {
                /*
                 * If the loading fails, later calls to native methods will also fail. So, in order
                 * not to confuse users with unexpected stack traces, we ignore the exceptions here.
                 */
            }
            initialized = true;
        }
    }
}

@TargetClass(className = "javax.crypto.ProviderVerifier")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_ProviderVerifier {

    @TargetElement(onlyWith = ProviderVerifierJavaHomeFieldPresent.class) //
    @Alias @InjectAccessors(ProviderVerifierJavaHomeAccessors.class) //
    static String javaHome;

}

class ProviderVerifierJavaHomeFieldPresent implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        Class<?> providerVerifier = Objects.requireNonNull(ReflectionUtil.lookupClass(false, "javax.crypto.ProviderVerifier"));
        return ReflectionUtil.lookupField(true, providerVerifier, "javaHome") != null;
    }
}

@SuppressWarnings("unused")
class ProviderVerifierJavaHomeAccessors {
    private static String javaHome;

    private static String getJavaHome() {
        if (javaHome == null) {
            javaHome = System.getProperty("java.home", "");
        }
        return javaHome;
    }

    private static void setJavaHome(String newJavaHome) {
        javaHome = newJavaHome;
    }
}

@TargetClass(className = "javax.crypto.JceSecurity")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/java.base/share/classes/javax/crypto/JceSecurity.java.template")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    /*
     * The JceSecurity.verificationResults cache is initialized by the SecurityServicesFeature at
     * build time, for all registered providers. The cache is used by JceSecurity.canUseProvider()
     * at runtime to check whether a provider is properly signed and can be used by JCE. It does
     * that via jar verification which we cannot support.
     */

    // Checkstyle: stop
    @Alias //
    private static Object PROVIDER_VERIFIED;
    // Checkstyle: resume

    // Map<Provider,?> of the providers we already have verified
    // value == PROVIDER_VERIFIED is successfully verified
    // value is failure cause Exception in error case
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Object, Object> verificationResults;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Provider, Object> verifyingProviders;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static Map<Class<?>, URL> codeBaseCacheRef = new WeakHashMap<>();

    @Substitute
    static Exception getVerificationResult(Provider p) {
        /* Start code block copied from original method. */
        /* The verification results map key is an identity wrapper object. */
        Object o = SecurityProvidersSupport.singleton().getSecurityProviderVerificationResult(p.getName());
        if (o == PROVIDER_VERIFIED) {
            return null;
        } else if (o != null) {
            return (Exception) o;
        }
        /* End code block copied from original method. */
        /*
         * If the verification result is not found in the verificationResults map JDK proceeds to
         * verify it. That requires accessing the code base which we don't support. The substitution
         * for getCodeBase() would be enough to take care of this too, but substituting
         * getVerificationResult() allows for a better error message.
         */
        throw VMError.unsupportedFeature("Trying to verify a provider that was not registered at build time: " + p + ". " +
                        "All providers must be registered and verified in the Native Image builder. ");
    }
}

class JceSecurityAccessor {
    private static volatile SecureRandom RANDOM;

    static SecureRandom get() {
        SecureRandom result = RANDOM;
        if (result == null) {
            /* Lazy initialization on first access. */
            result = initializeOnce();
        }
        return result;
    }

    private static synchronized SecureRandom initializeOnce() {
        SecureRandom result = RANDOM;
        if (result != null) {
            /* Double-checked locking is OK because INSTANCE is volatile. */
            return result;
        }

        result = new SecureRandom();
        RANDOM = result;
        return result;
    }
}

/**
 * JDK 8 has the class `javax.crypto.JarVerifier`, but in JDK 11 and later that class is only
 * available in Oracle builds, and not in OpenJDK builds.
 */
@TargetClass(className = "javax.crypto.JarVerifier", onlyWith = PlatformHasClass.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_javax_crypto_JarVerifier {

    @Substitute
    @TargetElement(onlyWith = ContainsVerifyJars.class)
    private String verifySingleJar(URL var1) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Substitute
    @TargetElement(onlyWith = ContainsVerifyJars.class)
    private void verifyJars(URL var1, List<String> var2) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }
}

final class ContainsVerifyJars implements Predicate<Class<?>> {
    @Override
    public boolean test(Class<?> originalClass) {
        try {
            originalClass.getDeclaredMethod("verifyJars", URL.class, List.class);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }
}

@TargetClass(value = java.security.Policy.class, innerClass = "PolicyInfo", onlyWith = JDK21OrEarlier.class)
final class Target_java_security_Policy_PolicyInfo {
}

@TargetClass(value = java.security.Policy.class, onlyWith = JDK21OrEarlier.class)
final class Target_java_security_Policy {

    @Delete //
    private static Target_java_security_Policy_PolicyInfo policyInfo;

    @Substitute
    private static Policy getPolicyNoCheck() {
        return AllPermissionsPolicy.SINGLETON;
    }

    @Substitute
    private static boolean isSet() {
        return true;
    }

    @Substitute
    @SuppressWarnings("unused")
    private static void setPolicy(Policy p) {
        /*
         * We deliberately treat this as a non-recoverable fatal error. We want to prevent bugs
         * where an exception is silently ignored by an application and then necessary security
         * checks are not in place.
         */
        throw VMError.shouldNotReachHere("Installing a Policy is not yet supported");
    }
}

final class AllPermissionsPolicy extends Policy {

    static final Policy SINGLETON = new AllPermissionsPolicy();

    private AllPermissionsPolicy() {
    }

    private static PermissionCollection allPermissions() {
        Permissions result = new Permissions();
        result.add(SecurityConstants.ALL_PERMISSION);
        return result;
    }

    @Override
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    public PermissionCollection getPermissions(CodeSource codesource) {
        return allPermissions();
    }

    @Override
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return allPermissions();
    }

    @Override
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return true;
    }
}

/**
 * This class is instantiated indirectly from the {@code Policy#getInstance} methods via the
 * {@link java.security.Security#getProviders security provider} abstractions. We could just
 * substitute the Policy.getInstance methods to return {@link AllPermissionsPolicy#SINGLETON}, this
 * version is more fool-proof in case someone manually registers security providers for reflective
 * instantiation.
 */
@TargetClass(className = "sun.security.provider.PolicySpiFile", onlyWith = JDK21OrEarlier.class)
@SuppressWarnings({"unused", "static-method", "deprecation"})
final class Target_sun_security_provider_PolicySpiFile {

    @Substitute
    private Target_sun_security_provider_PolicySpiFile(Policy.Parameters params) {
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    private PermissionCollection engineGetPermissions(CodeSource codesource) {
        return AllPermissionsPolicy.SINGLETON.getPermissions(codesource);
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    private PermissionCollection engineGetPermissions(ProtectionDomain d) {
        return AllPermissionsPolicy.SINGLETON.getPermissions(d);
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    private boolean engineImplies(ProtectionDomain d, Permission p) {
        return AllPermissionsPolicy.SINGLETON.implies(d, p);
    }

    @Substitute
    private void engineRefresh() {
        AllPermissionsPolicy.SINGLETON.refresh();
    }
}

@Delete("Substrate VM does not use SecurityManager, so loading a security policy file would be misleading")
@TargetClass(className = "sun.security.provider.PolicyFile", onlyWith = JDK21OrEarlier.class)
final class Target_sun_security_provider_PolicyFile {
}

@TargetClass(className = "sun.security.jca.ProviderConfig")
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderConfig {

    @Alias //
    private String provName;

    @Alias//
    private static sun.security.util.Debug debug;

    @Alias//
    private Provider provider;

    @Alias//
    private boolean isLoading;

    @Alias//
    private int tries;

    @Alias
    private native Provider doLoadProvider();

    @Alias
    private native boolean shouldLoad();

    /**
     * The `entrypoint` for allocating security providers at runtime. The implementation is copied
     * from the JDK with a small tweak to filter out providers that are neither user-requested nor
     * reachable via a security service.
     */
    @Substitute
    @SuppressWarnings("fallthrough")
    @SuppressFBWarnings(value = "DC_DOUBLECHECK", justification = "This double-check is implemented correctly and is intentional.")
    Provider getProvider() {
        // volatile variable load
        Provider p = provider;
        if (p != null) {
            return p;
        }
        // DCL
        synchronized (this) {
            p = provider;
            if (p != null) {
                return p;
            }
            if (!shouldLoad()) {
                return null;
            }

            // Create providers which are in java.base directly
            SecurityProvidersSupport support = SecurityProvidersSupport.singleton();
            switch (provName) {
                case "SUN", "sun.security.provider.Sun": {
                    p = support.isSecurityProviderExpected("SUN", "sun.security.provider.Sun") ? new sun.security.provider.Sun() : null;
                    break;
                }
                case "SunRsaSign", "sun.security.rsa.SunRsaSign": {
                    p = support.isSecurityProviderExpected("SunRsaSign", "sun.security.rsa.SunRsaSign") ? new sun.security.rsa.SunRsaSign() : null;
                    break;
                }
                case "SunJCE", "com.sun.crypto.provider.SunJCE": {
                    p = support.isSecurityProviderExpected("SunJCE", "com.sun.crypto.provider.SunJCE") ? new com.sun.crypto.provider.SunJCE() : null;
                    break;
                }
                case "SunJSSE": {
                    p = support.isSecurityProviderExpected("SunJSSE", "sun.security.ssl.SunJSSE") ? new sun.security.ssl.SunJSSE() : null;
                    break;
                }
                case "Apple", "apple.security.AppleProvider": {
                    // need to use reflection since this class only exists on MacOsx
                    try {
                        Class<?> c = Class.forName("apple.security.AppleProvider");
                        if (Provider.class.isAssignableFrom(c)) {
                            @SuppressWarnings("deprecation")
                            Object newInstance = c.newInstance();
                            p = (Provider) newInstance;
                        }
                    } catch (Exception ex) {
                        if (debug != null) {
                            debug.println("Error loading provider Apple");
                            ex.printStackTrace();
                        }
                    }
                    break;
                }
                case "SunEC": {
                    if (JavaVersionUtil.JAVA_SPEC > 21) {
                        // Constructor inside method and then allocate. ModuleSupport to open.
                        p = support.isSecurityProviderExpected("SunEC", "sun.security.ec.SunEC") ? support.allocateSunECProvider() : null;
                        break;
                    }
                    /*
                     * On older JDK versions, SunEC was part of the `jdk.crypto.ec` module and was
                     * allocated via the service loading mechanism, so this fallthrough is
                     * intentional. On newer JDK versions, SunEC is part of `java.base` and is
                     * allocated directly.
                     */
                }
                // fall through
                default: {
                    if (isLoading) {
                        // because this method is synchronized, this can only
                        // happen if there is recursion.
                        if (debug != null) {
                            debug.println("Recursion loading provider: " + this);
                            new Exception("Call trace").printStackTrace();
                        }
                        return null;
                    }
                    try {
                        isLoading = true;
                        tries++;
                        p = doLoadProvider();
                    } finally {
                        isLoading = false;
                    }
                }
            }
            provider = p;
        }
        return p;
    }
}

@SuppressWarnings("unused")
@TargetClass(className = "sun.security.jca.ProviderConfig", innerClass = "ProviderLoader")
final class Target_sun_security_jca_ProviderConfig_ProviderLoader {
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, isFinal = true)//
    static Target_sun_security_jca_ProviderConfig_ProviderLoader INSTANCE;
}

/** Dummy class to have a class with the file's name. */
public final class SecuritySubstitutions {
}
