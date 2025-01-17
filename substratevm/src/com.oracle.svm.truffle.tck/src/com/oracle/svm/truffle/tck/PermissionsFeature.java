/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.tck;

import static com.oracle.graal.pointsto.reports.ReportUtils.report;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.util.LogUtils;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.polyglot.io.FileSystem;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.BundleMember;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.util.ClassUtil;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.spi.TrackedUnsafeAccess;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A Truffle TCK {@code Feature} detecting privileged calls done by Truffle language. The
 * {@code PermissionsFeature} finds calls of privileged methods originating in Truffle language. The
 * calls going through {@code Truffle} library, GraalVM SDK or compiler are treated as safe calls
 * and are not reported.
 * <p>
 * To execute the {@code PermissionsFeature} you need to enable it using
 * {@code --features=com.oracle.svm.truffle.tck.PermissionsFeature} native-image option, specify
 * report file using {@code -H:TruffleTCKPermissionsReportFile} option and specify the language
 * packages by {@code -H:TruffleTCKPermissionsLanguagePackages} option. You also need to disable
 * folding of {@code System.getSecurityManager} using {@code -H:-FoldSecurityManagerGetter} option.
 */
public class PermissionsFeature implements Feature {

    private static final String CONFIG = "truffle-language-permissions-config.json";

    public enum ActionKind {
        Ignore,
        Warn,
        Throw
    }

    public static class Options {
        @Option(help = "Path to file where to store report of Truffle language privilege access.")//
        public static final HostedOptionKey<String> TruffleTCKPermissionsReportFile = new HostedOptionKey<>(null);

        @BundleMember(role = BundleMember.Role.Input)//
        @Option(help = "Comma separated list of exclude files.")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Paths> TruffleTCKPermissionsExcludeFiles = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Paths.buildWithCommaDelimiter());

        @Option(help = "Maximal depth of a stack trace.", type = OptionType.Expert)//
        public static final HostedOptionKey<Integer> TruffleTCKPermissionsMaxStackTraceDepth = new HostedOptionKey<>(-1);

        @Option(help = "Maximum number of erroneous privileged accesses reported.", type = OptionType.Expert)//
        public static final HostedOptionKey<Integer> TruffleTCKPermissionsMaxErrors = new HostedOptionKey<>(100);

        @Option(help = {"Specifies how unused methods in the language allow list should be handled.",
                        "Available options are:",
                        "  \"Ignore\": Do not report unused methods in the allow list.",
                        "  \"Warn\": Log a warning message to stderr.",
                        "  \"Throw\" (default): Throw an exception and abort the native-image build process."}, type = OptionType.Expert)//
        public static final HostedOptionKey<ActionKind> TruffleTCKUnusedAllowListEntriesAction = new HostedOptionKey<>(ActionKind.Throw);
    }

    /**
     * Predicate to enable substitutions needed by the {@link PermissionsFeature}.
     */
    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(PermissionsFeature.class);
        }
    }

    /**
     * List of safe packages.
     */
    private static final Set<String> safePackages;
    static {
        safePackages = new HashSet<>();
        safePackages.add("org.graalvm.polyglot.");
        safePackages.add("org.graalvm.home.");
        safePackages.add("jdk.graal.compiler.");
        safePackages.add("com.oracle.graalvm.");
        safePackages.add("com.oracle.svm.core.");
        safePackages.add("com.oracle.truffle.api.");
        safePackages.add("com.oracle.truffle.polyglot.");
        safePackages.add("com.oracle.truffle.host.");
        safePackages.add("com.oracle.truffle.nfi.");
        safePackages.add("com.oracle.truffle.object.");
        safePackages.add("com.oracle.truffle.runtime.");
        safePackages.add("com.oracle.truffle.runtime.debug.");
        safePackages.add("com.oracle.truffle.runtime.jfr.");
        safePackages.add("com.oracle.truffle.runtime.jfr.impl.");
        safePackages.add("com.oracle.truffle.runtime.hotspot.");
        safePackages.add("com.oracle.truffle.runtime.hotspot.libgraal.");
        safePackages.add("com.oracle.truffle.runtime.enterprise.");
        safePackages.add("com.oracle.truffle.sandbox.enterprise.");
        safePackages.add("com.oracle.truffle.polyglot.enterprise.");
        safePackages.add("com.oracle.truffle.object.enterprise.");
        safePackages.add("com.oracle.svm.truffle.api.");
        safePackages.add("com.oracle.svm.truffle.isolated.");
        safePackages.add("com.oracle.svm.enterprise.truffle.");
    }

    private static final Set<ClassLoader> systemClassLoaders;
    static {
        systemClassLoaders = new HashSet<>();
        for (ClassLoader cl = ClassLoader.getSystemClassLoader(); cl != null; cl = cl.getParent()) {
            systemClassLoaders.add(cl);
        }
    }

    /**
     * Methods which should not be found.
     */
    private final Set<BaseMethodNode> deniedMethods = new HashSet<>();

    /**
     * Path to store report into.
     */
    private Path reportFilePath;

    /**
     * JDK methods which are allowed to do privileged calls without being reported.
     */
    private Set<? extends BaseMethodNode> platformAllowList;

    /**
     * Language methods which are allowed to do privileged calls without being reported.
     */
    private Map<BaseMethodNode, Boolean> languageAllowList;

    private Set<CallGraphFilter> contextFilters;

    /**
     * Classes for reflective accesses which are opaque for permission analysis.
     */

    private InlinedUnsafeMethodNode inlinedUnsafeCall;

    private Class<?> sunMiscUnsafe;

    @Override
    public String getDescription() {
        return "Detects privileged calls in Truffle languages";
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (SubstrateOptions.FoldSecurityManagerGetter.getValue()) {
            UserError.abort("%s requires -H:-FoldSecurityManagerGetter option.", ClassUtil.getUnqualifiedName(getClass()));
        }
        String reportFile = Options.TruffleTCKPermissionsReportFile.getValue();
        if (reportFile == null) {
            UserError.abort("Path to report file must be given by -H:TruffleTCKPermissionsReportFile option.");
        }
        reportFilePath = Paths.get(reportFile);

        if (ModuleLayer.boot().findModule("jdk.unsupported").isPresent()) {
            sunMiscUnsafe = access.findClassByName("sun.misc.Unsafe");
        }

        var accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        initializeDeniedMethods(accessImpl);

        BigBang bb = accessImpl.getBigBang();
        contextFilters = new HashSet<>();
        Collections.addAll(contextFilters, new SafeInterruptRecognizer(bb), new SafePrivilegedRecognizer(bb),
                        new SafeServiceLoaderRecognizer(bb, accessImpl.getImageClassLoader()), new SafeSetThreadNameRecognizer(bb));

        /*
         * Ensure methods which are either deniedMethods or on the allow list are never inlined into
         * methods. These methods are important for identifying violations.
         */
        Set<AnalysisMethod> preventInlineBeforeAnalysis = new HashSet<>();
        deniedMethods.stream().map(BaseMethodNode::getMethod).forEach(preventInlineBeforeAnalysis::add);
        platformAllowList.stream().map(BaseMethodNode::getMethod).forEach(preventInlineBeforeAnalysis::add);
        languageAllowList.keySet().stream().map(BaseMethodNode::getMethod).forEach(preventInlineBeforeAnalysis::add);
        contextFilters.stream().map(CallGraphFilter::getInspectedMethods).forEach(preventInlineBeforeAnalysis::addAll);

        accessImpl.getHostVM().registerNeverInlineTrivialHandler((caller, callee) -> {
            if (!caller.isOriginalMethod()) {
                // we only care about tracing original methods
                return false;
            }
            if (preventInlineBeforeAnalysis.contains(callee)) {
                return true;
            }
            // We must maintain the boundary of entering a safe class
            return !isSafeClass(caller.getDeclaringClass()) && isSafeClass(callee.getDeclaringClass());
        });

        accessImpl.getHostVM().keepAnalysisGraphs();
    }

    private void initializeDeniedMethods(FeatureImpl.BeforeAnalysisAccessImpl accessImpl) {
        BigBang bb = accessImpl.getBigBang();
        if (sunMiscUnsafe != null) {
            inlinedUnsafeCall = new InlinedUnsafeMethodNode(bb.getMetaAccess().lookupJavaType(sunMiscUnsafe));
        }
        String featureName = ClassUtil.getUnqualifiedName(getClass());
        AllowListParser parser = new AllowListParser(accessImpl.getImageClassLoader(), bb);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, accessImpl.getImageClassLoader(), featureName,
                        CONFIG,
                        List.of(),
                        List.of(getClass().getPackage().getName().replace('.', '/') + "/resources/jre.json"));
        platformAllowList = parser.getLoadedAllowList();
        parser = new AllowListParser(accessImpl.getImageClassLoader(), bb);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, accessImpl.getImageClassLoader(), featureName,
                        CONFIG,
                        Options.TruffleTCKPermissionsExcludeFiles.getValue().values(),
                        List.of());
        languageAllowList = parser.getLoadedAllowList().stream().collect(Collectors.toMap(Function.identity(), key -> false));
        deniedMethods.addAll(findMethods(bb, SecurityManager.class, (m) -> m.getName().startsWith("check")));
        if (sunMiscUnsafe != null) {
            deniedMethods.addAll(findMethods(bb, sunMiscUnsafe, ModifiersProvider::isPublic));
        }
        // The type of the host Java NIO FileSystem.
        // The FileSystem obtained from the FileSystem.newDefaultFileSystem() is in the Truffle
        // package but
        // can be directly used by a language. We need to include it into deniedMethods.
        deniedMethods.addAll(findMethods(bb, FileSystem.newDefaultFileSystem().getClass(), ModifiersProvider::isPublic));
        // JDK 19 introduced BigInteger.parallelMultiply that uses the ForkJoinPool.
        // We deny this method but explicitly allow non-parallel multiply (cf. jre.json).
        deniedMethods.addAll(findMethods(bb, BigInteger.class, (m) -> m.getName().startsWith("parallel")));
        if (inlinedUnsafeCall != null) {
            deniedMethods.add(inlinedUnsafeCall);
        }
    }

    @Override
    @SuppressWarnings("try")
    public void afterAnalysis(AfterAnalysisAccess access) {
        try {
            Files.deleteIfExists(reportFilePath);
        } catch (IOException ioe) {
            throw UserError.abort("Cannot delete existing report file %s.", reportFilePath);
        }
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        DebugContext debugContext = accessImpl.getDebugContext();
        try (DebugContext.Scope s = debugContext.scope(ClassUtil.getUnqualifiedName(getClass()))) {
            BigBang bb = accessImpl.getBigBang();
            Map<BaseMethodNode, Set<BaseMethodNode>> cg = callGraph(bb, deniedMethods, debugContext, (SVMHost) bb.getHostVM());
            List<List<BaseMethodNode>> report = new ArrayList<>();
            int maxStackDepth = Options.TruffleTCKPermissionsMaxStackTraceDepth.getValue();
            maxStackDepth = maxStackDepth == -1 ? Integer.MAX_VALUE : maxStackDepth;
            for (BaseMethodNode deniedMethod : deniedMethods) {
                if (cg.containsKey(deniedMethod)) {
                    collectViolations(report, deniedMethod,
                                    maxStackDepth, Options.TruffleTCKPermissionsMaxErrors.getValue(),
                                    cg, contextFilters,
                                    new LinkedList<>(), new HashSet<>(), 1, 0);
                }
            }
            if (!report.isEmpty()) {
                report(
                                "detected privileged calls originated in language packages ",
                                reportFilePath,
                                (pw) -> {
                                    StringBuilder builder = new StringBuilder();
                                    for (List<BaseMethodNode> callPath : report) {
                                        for (BaseMethodNode call : callPath) {
                                            builder.append(call.asStackTraceElement()).append(System.lineSeparator());
                                        }
                                        builder.append(System.lineSeparator());
                                    }
                                    pw.print(builder);
                                });
            }
            List<BaseMethodNode> unusedLanguageAllowListEntries = languageAllowList.entrySet().stream().filter((e) -> !e.getValue()).map(Map.Entry::getKey).toList();
            if (!unusedLanguageAllowListEntries.isEmpty()) {
                StringBuilder errorMessageBuilder = new StringBuilder(
                                "The following methods in the language allow list were not statically reachable during points-to analysis. " + "Please review and remove them from the allow list:\n");
                for (BaseMethodNode unused : unusedLanguageAllowListEntries) {
                    errorMessageBuilder.append(" - ").append(unused.getMethod().format("%H.%n(%p)")).append("\n");
                }
                switch (Options.TruffleTCKUnusedAllowListEntriesAction.getValue()) {
                    case Ignore -> {
                    }
                    case Warn -> LogUtils.warning("[%s] %s", ClassUtil.getUnqualifiedName(getClass()), errorMessageBuilder);
                    case Throw -> throw UserError.abort(errorMessageBuilder.toString());
                    default -> throw new AssertionError(Options.TruffleTCKUnusedAllowListEntriesAction.getValue());
                }
            }
        }
    }

    private static Class<?> loadClassOrFail(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    /**
     * Creates an inverted call graph for methods given by {@code targets} parameter. For each
     * called method in {@code targets} or transitive caller of {@code targets} the resulting
     * {@code Map} contains an entry holding all direct callers of the method in the entry value.
     *
     * @param bb the {@link BigBang}
     * @param targets the target methods to build call graph for
     * @param debugContext the {@link DebugContext}
     */
    private Map<BaseMethodNode, Set<BaseMethodNode>> callGraph(BigBang bb, Set<BaseMethodNode> targets,
                    DebugContext debugContext, SVMHost hostVM) {
        Deque<AnalysisMethodNode> todo = new LinkedList<>();
        Map<BaseMethodNode, Set<BaseMethodNode>> visited = new HashMap<>();
        for (AnalysisMethodNode m : findMethods(bb, OptimizedCallTarget.class, (m) -> "profiledPERoot".equals(m.getName()))) {
            visited.put(m, new HashSet<>());
            todo.add(m);
        }
        Deque<BaseMethodNode> path = new LinkedList<>();
        for (AnalysisMethodNode m : todo) {
            callGraphImpl(m, targets, visited, path, debugContext, hostVM);
        }
        return visited;
    }

    private void findUnsafeAccesses(
                    BaseMethodNode mNode,
                    Map<BaseMethodNode, Set<BaseMethodNode>> visited,
                    SVMHost hostVM) {
        /*
         * In this situation it is unnecessary to check for unsafe accesses.
         */
        if (inlinedUnsafeCall == null || isSystemOrSafeClass(mNode)) {
            return;
        }

        if (Proxy.isProxyClass(mNode.getOwner().getJavaClass())) {
            /*
             * Starting JDK-23+26 Proxy generated code does unsafe compare and set. The generated
             * proxy method calls the used invocation handler which is checked for possible unsafe
             * access.
             */
            return;
        }

        StructuredGraph mGraph = hostVM.getAnalysisGraph(mNode.getMethod());
        for (Node node : mGraph.getNodes().filter(n -> n instanceof TrackedUnsafeAccess)) {
            /*
             * Check the origin of all tracked unsafe accesses.
             *
             * We must determine whether the access originates from a safe class. It is possible for
             * these accesses to be inlined into other methods during the method handle
             * intrinsification process.
             */
            NodeSourcePosition current = node.getNodeSourcePosition();
            boolean foundSystemClass = false;
            while (current != null) {
                var declaringClass = OriginalClassProvider.getJavaClass(current.getMethod().getDeclaringClass());
                if (!declaringClass.equals(sunMiscUnsafe) && isSystemClass(declaringClass)) {
                    foundSystemClass = true;
                    break;
                }
                current = current.getCaller();
            }
            if (!foundSystemClass) {
                visited.computeIfAbsent(inlinedUnsafeCall, (e) -> new HashSet<>()).add(mNode);
                return;
            }
        }
    }

    private boolean callGraphImpl(
                    BaseMethodNode mNode,
                    Set<BaseMethodNode> targets,
                    Map<BaseMethodNode, Set<BaseMethodNode>> visited,
                    Deque<BaseMethodNode> path,
                    DebugContext debugContext,
                    SVMHost hostVM) {
        AnalysisMethod m = mNode.getMethod();
        String mName = getMethodName(m);
        path.addFirst(mNode);
        findUnsafeAccesses(mNode, visited, hostVM);
        try {
            boolean callPathContainsTarget = false;
            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Entered method: %s.", mName);
            for (InvokeInfo invoke : m.getInvokes()) {
                for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                    AnalysisMethodNode calleeNode = new AnalysisMethodNode(callee);
                    if (callee.isImplementationInvoked()) {
                        Set<BaseMethodNode> parents = visited.get(calleeNode);
                        String calleeName = getMethodName(callee);
                        debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Callee: %s, new: %b.", calleeName, parents == null);
                        if (parents == null) {
                            parents = new HashSet<>();
                            visited.put(calleeNode, parents);
                            if (targets.contains(calleeNode)) {
                                parents.add(mNode);
                                callPathContainsTarget = true;
                                continue;
                            }
                            boolean add = callGraphImpl(calleeNode, targets, visited, path, debugContext, hostVM);
                            if (add) {
                                parents.add(mNode);
                                debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Added callee: %s for %s.", calleeName, mName);
                            }
                            callPathContainsTarget |= add;
                        } else if (!isBacktrace(calleeNode, path) || isBackTraceOverLanguageMethod(calleeNode, path)) {
                            parents.add(mNode);
                            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Added backtrace callee: %s for %s.", calleeName, mName);
                            callPathContainsTarget = true;
                        } else {
                            if (debugContext.isLogEnabled(DebugContext.VERY_DETAILED_LEVEL)) {
                                debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Ignoring backtrace callee: %s for %s.", calleeName, mName);
                            }
                        }
                    }
                }
            }
            debugContext.log(DebugContext.VERY_DETAILED_LEVEL, "Exited method: %s.", mName);
            return callPathContainsTarget;
        } finally {
            path.removeFirst();
        }
    }

    /**
     * Checks if the method is already included on call path, in other words it's a recursive call.
     *
     * @param method the {@link AnalysisMethodNode} to check
     * @param path the current call path
     */
    private static boolean isBacktrace(AnalysisMethodNode method, Deque<? extends BaseMethodNode> path) {
        return path.contains(method);
    }

    /**
     * Checks if the back call of given method crosses some language method on given call path. If
     * the back call crosses a language method the call has to be included into the call graph, the
     * crossed language method is the start method of a violation. Example: P privileged method, L
     * language method.
     *
     * <pre>
     * G((A,L),(A,P),(L,C),(C,A),(C,D))
     * </pre>
     *
     * The violation is L->C->A->P
     *
     * @param method the method being invoked
     * @param path the current call path
     * @return {@code true} if the call of given method crosses some language method.
     */
    private static boolean isBackTraceOverLanguageMethod(AnalysisMethodNode method, Deque<? extends BaseMethodNode> path) {
        if (!isSystemOrSafeClass(method)) {
            return false;
        }
        boolean found = false;
        for (Iterator<? extends BaseMethodNode> it = path.descendingIterator(); it.hasNext();) {
            BaseMethodNode pe = it.next();
            if (method.equals(pe)) {
                found = true;
            } else if (found && !isSystemOrSafeClass(pe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the calls of privileged methods originated in Truffle language.
     *
     * @param report the list to collect violations into
     * @param mNode currently processed method
     * @param maxDepth maximal call trace depth
     * @param maxReports maximal number of reports
     * @param callGraph call graph obtained from
     *            {@link PermissionsFeature#callGraph(BigBang, Set, DebugContext, SVMHost)}
     * @param contextFiltersParam filters removing known valid calls
     * @param currentPath current path from a privileged method in a call graph
     * @param visited set of already visited methods, these methods are already part of an existing
     *            report or do not lead to language class
     * @param depth current depth
     */
    private int collectViolations(
                    List<? super List<BaseMethodNode>> report,
                    BaseMethodNode mNode,
                    int maxDepth,
                    int maxReports,
                    Map<BaseMethodNode, Set<BaseMethodNode>> callGraph,
                    Set<CallGraphFilter> contextFiltersParam,
                    List<BaseMethodNode> currentPath,
                    Set<BaseMethodNode> visited,
                    int depth,
                    int initialNumReports) {
        int numReports = initialNumReports;
        if (numReports >= maxReports) {
            return numReports;
        }
        if (depth > 1) {
            // The denied method can be a method from a "safe" class
            if (isSafeClass(mNode)) {
                return numReports;
            }
            // The denied method can be excluded by a allow list
            if (isExcludedClass(mNode)) {
                return numReports;
            }
        }
        if (!visited.contains(mNode)) {
            visited.add(mNode);
            currentPath.add(mNode);
            try {
                Set<BaseMethodNode> callers = callGraph.get(mNode);
                if (depth > maxDepth) {
                    if (!callers.isEmpty()) {
                        numReports = collectViolations(report, callers.iterator().next(), maxDepth, maxReports, callGraph, contextFiltersParam, currentPath, visited, depth + 1, numReports);
                    }
                } else if (!isSystemOrSafeClass(mNode)) {
                    List<BaseMethodNode> callPath = new ArrayList<>(currentPath);
                    report.add(callPath);
                    numReports++;
                } else {
                    for (BaseMethodNode caller : callers) {
                        if (contextFiltersParam.stream().noneMatch((f) -> f.test(mNode, caller, currentPath))) {
                            numReports = collectViolations(report, caller, maxDepth, maxReports, callGraph, contextFiltersParam, currentPath, visited, depth + 1, numReports);
                        }
                    }
                }
            } finally {
                BaseMethodNode last = currentPath.removeLast();
                assert last == mNode;
            }
        }
        return numReports;
    }

    private static boolean isSystemOrSafeClass(BaseMethodNode methodNode) {
        return isSystemClass(methodNode) || isSafeClass(methodNode);
    }

    /**
     * Tests if method represented by given {@link BaseMethodNode} is loaded by a system
     * {@link ClassLoader}.
     *
     * @param methodNode the {@link BaseMethodNode} to check
     */
    private static boolean isSystemClass(BaseMethodNode methodNode) {
        return isSystemClass(methodNode.getOwner().getJavaClass());
    }

    private static boolean isSystemClass(Class<?> clz) {
        if (clz == null) {
            return false;
        }
        return clz.getClassLoader() == null || systemClassLoaders.contains(clz.getClassLoader());
    }

    /**
     * Tests if the given {@link AnalysisMethod} is from Truffle library, GraalVM SDK or compiler
     * package.
     *
     * @param method the {@link AnalysisMethod} to check
     */
    private static boolean isSafeClass(BaseMethodNode method) {
        return isSafeClass(method.getOwner());
    }

    private static boolean isSafeClass(AnalysisType type) {
        return isClassInPackage(type.toJavaName(), safePackages);
    }

    /**
     * Tests if a class of given name transitively belongs to some package given by {@code packages}
     * parameter.
     *
     * @param javaName the {@link AnalysisMethod} to check
     * @param packages the list of packages
     */
    private static boolean isClassInPackage(String javaName, Collection<? extends String> packages) {
        for (String pkg : packages) {
            if (javaName.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests if the given {@link BaseMethodNode} is excluded by allow list.
     *
     * @param methodNode the {@link BaseMethodNode} to check
     */
    private boolean isExcludedClass(BaseMethodNode methodNode) {
        if (platformAllowList.contains(methodNode)) {
            return true;
        }
        return languageAllowList.computeIfPresent(methodNode, (n, v) -> true) != null;
    }

    /**
     * Finds methods declared in {@code owner} class using {@code filter} predicate.
     *
     * @param bb the {@link BigBang}
     * @param owner the class which methods should be listed
     * @param filter the predicate filtering methods declared in {@code owner}
     * @return the methods accepted by {@code filter}
     * @throws IllegalStateException if owner cannot be resolved
     */
    private static Set<AnalysisMethodNode> findMethods(BigBang bb, Class<?> owner, Predicate<ResolvedJavaMethod> filter) {
        AnalysisType clazz = bb.getMetaAccess().lookupJavaType(owner);
        if (clazz == null) {
            throw new IllegalStateException("Cannot resolve " + owner.getName() + ".");
        }
        return findMethods(bb, clazz, filter);
    }

    /**
     * Finds methods declared in {@code owner} {@link AnalysisType} using {@code filter} predicate.
     *
     * @param bb the {@link BigBang}
     * @param owner the {@link AnalysisType} which methods should be listed
     * @param filter the predicate filtering methods declared in {@code owner}
     * @return the methods accepted by {@code filter}
     */
    static Set<AnalysisMethodNode> findMethods(BigBang bb, AnalysisType owner, Predicate<ResolvedJavaMethod> filter) {
        return findImpl(bb, owner.getWrapped().getDeclaredMethods(false), filter);
    }

    /**
     * Finds constructors declared in {@code owner} {@link AnalysisType} using {@code filter}
     * predicate.
     *
     * @param bb the {@link BigBang}
     * @param owner the {@link AnalysisType} which constructors should be listed
     * @param filter the predicate filtering constructors declared in {@code owner}
     * @return the constructors accepted by {@code filter}
     */
    static Set<AnalysisMethodNode> findConstructors(BigBang bb, AnalysisType owner, Predicate<ResolvedJavaMethod> filter) {
        return findImpl(bb, owner.getWrapped().getDeclaredConstructors(false), filter);
    }

    private static Set<AnalysisMethodNode> findImpl(BigBang bb, ResolvedJavaMethod[] methods, Predicate<ResolvedJavaMethod> filter) {
        Set<AnalysisMethodNode> result = new HashSet<>();
        for (ResolvedJavaMethod m : methods) {
            if (filter.test(m)) {
                result.add(new AnalysisMethodNode(bb.getUniverse().lookup(m)));
            }
        }
        return result;
    }

    /**
     * Returns a method name in the format: {@code ownerFQN.name(parameters)}.
     *
     * @param method to create a name for
     */
    private static String getMethodName(AnalysisMethod method) {
        return method.format("%H.%n(%p)");
    }

    /**
     * Filter to filter out known valid calls, included by points to analysis, from the report.
     */
    private interface CallGraphFilter {
        /**
         * @return whether this methodNode should not be considered a violation
         */
        boolean test(BaseMethodNode methodNode, BaseMethodNode callerNode, List<BaseMethodNode> trace);

        Collection<AnalysisMethod> getInspectedMethods();
    }

    /**
     * Filters out {@link Thread#interrupt()} calls done on {@link Thread#currentThread()}.
     */
    private static final class SafeInterruptRecognizer implements CallGraphFilter {

        private final SVMHost hostVM;
        private final AnalysisMethodNode threadInterrupt;
        private final AnalysisMethod threadCurrentThread;

        SafeInterruptRecognizer(BigBang bb) {
            this.hostVM = (SVMHost) bb.getHostVM();

            Set<AnalysisMethodNode> methods = findMethods(bb, Thread.class, (m) -> m.getName().equals("interrupt"));
            if (methods.size() != 1) {
                throw new IllegalStateException("Failed to lookup Thread.interrupt().");
            }
            threadInterrupt = methods.iterator().next();
            methods = findMethods(bb, Thread.class, (m) -> m.getName().equals("currentThread"));
            if (methods.size() != 1) {
                throw new IllegalStateException("Failed to lookup Thread.currentThread().");
            }
            threadCurrentThread = methods.iterator().next().getMethod();
        }

        @Override
        public boolean test(BaseMethodNode methodNode, BaseMethodNode callerNode, List<BaseMethodNode> trace) {
            Boolean res = null;
            if (threadInterrupt.equals(methodNode)) {
                AnalysisMethod caller = callerNode.getMethod();
                StructuredGraph graph = hostVM.getAnalysisGraph(caller);
                for (Invoke invoke : graph.getInvokes()) {
                    if (threadInterrupt.getMethod().equals(invoke.callTarget().targetMethod())) {
                        boolean vote = false;
                        ValueNode node = invoke.getReceiver();
                        if (node instanceof PiNode piNode) {
                            node = piNode.getOriginalNode();
                            if (node instanceof Invoke invokeNode) {
                                boolean isCurrentThread = threadCurrentThread.equals(invokeNode.callTarget().targetMethod());
                                vote = res == null ? isCurrentThread : (res && isCurrentThread);
                            }
                        }
                        res = vote;
                    }
                }
            }
            return res != null && res;
        }

        @Override
        public Collection<AnalysisMethod> getInspectedMethods() {
            return Set.of(threadInterrupt.getMethod(), threadCurrentThread);
        }
    }

    /**
     * Filters out {@code AccessController#doPrivileged} done by JRE.
     */
    private static final class SafePrivilegedRecognizer implements CallGraphFilter {

        private final SVMHost hostVM;
        private final Set<AnalysisMethodNode> doPrivileged;

        SafePrivilegedRecognizer(BigBang bb) {
            this.hostVM = (SVMHost) bb.getHostVM();
            this.doPrivileged = findMethods(bb, java.security.AccessController.class, (m) -> m.getName().equals("doPrivileged") || m.getName().equals("doPrivilegedWithCombiner"));
        }

        @Override
        public boolean test(BaseMethodNode methodNode, BaseMethodNode callerNode, List<BaseMethodNode> trace) {
            if (!doPrivileged.contains(methodNode)) {
                return false;
            }
            if (isSystemOrSafeClass(callerNode)) {
                return true;
            }
            AnalysisMethod method = methodNode.getMethod();
            AnalysisMethod caller = callerNode.getMethod();
            StructuredGraph graph = hostVM.getAnalysisGraph(caller);
            for (Invoke invoke : graph.getInvokes()) {
                if (method.equals(invoke.callTarget().targetMethod())) {
                    NodeInputList<ValueNode> args = invoke.callTarget().arguments();
                    if (args.isEmpty()) {
                        return false;
                    }
                    ValueNode arg0 = args.getFirst();
                    ResolvedJavaType newType = null;
                    if (arg0 instanceof NewInstanceNode newInstanceNode) {
                        newType = newInstanceNode.instanceClass();
                    } else if (arg0 instanceof Invoke invokeNode) {
                        // Constructor replaced by SVM FactoryMethod
                        AnalysisMethod targetMethod = (AnalysisMethod) invokeNode.getTargetMethod();
                        if (targetMethod.wrapped instanceof FactoryMethod factoryMethod) {
                            newType = method.getUniverse().lookup(factoryMethod.getTargetConstructor().getDeclaringClass());
                        }
                    }
                    if (newType == null) {
                        return false;
                    }
                    ResolvedJavaMethod methodCalledByAccessController = findPrivilegedEntryPoint(method, trace);
                    if (methodCalledByAccessController == null) {
                        return false;
                    }
                    if (newType.equals(methodCalledByAccessController.getDeclaringClass())) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Identifies the entry point to a {@code PrivilegedAction} invoked by
         * {@code doPrivilegedMethod}. Iterates through the inverted call stack, starting from the
         * current frame, up to the privileged call. Returns the first method that is not part of
         * {@code AccessController}.
         */
        private static ResolvedJavaMethod findPrivilegedEntryPoint(ResolvedJavaMethod doPrivilegedMethod, List<BaseMethodNode> trace) {
            ResolvedJavaType accessController = doPrivilegedMethod.getDeclaringClass();
            ListIterator<BaseMethodNode> it = trace.listIterator(trace.size());
            assert doPrivilegedMethod.equals(it.previous().getMethod()) : String.format("%s must be current stack frame.", trace);
            while (it.hasPrevious()) {
                BaseMethodNode mNode = it.previous();
                ResolvedJavaMethod method = mNode.getMethod();
                /*
                 * Ignore AccessController internal methods.
                 */
                if (!method.getDeclaringClass().equals(accessController)) {
                    return method;
                }
            }
            return null;
        }

        @Override
        public Collection<AnalysisMethod> getInspectedMethods() {
            return doPrivileged.stream().map(AnalysisMethodNode::getMethod).toList();
        }
    }

    private static final class SafeServiceLoaderRecognizer implements CallGraphFilter {

        private final AnalysisMethodNode providerImplGet;
        private final ImageClassLoader imageClassLoader;

        SafeServiceLoaderRecognizer(BigBang bb, ImageClassLoader imageClassLoader) {
            AnalysisType serviceLoaderIterator = bb.getMetaAccess().lookupJavaType(loadClassOrFail("java.util.ServiceLoader$ProviderImpl"));
            Set<AnalysisMethodNode> methods = findMethods(bb, serviceLoaderIterator, (m) -> m.getName().equals("get"));
            if (methods.size() != 1) {
                throw new IllegalStateException("Failed to lookup ServiceLoader$ProviderImpl.get().");
            }
            this.providerImplGet = methods.iterator().next();
            this.imageClassLoader = imageClassLoader;
        }

        @Override
        public boolean test(BaseMethodNode methodNode, BaseMethodNode callerNode, List<BaseMethodNode> trace) {
            if (providerImplGet.equals(methodNode)) {
                ResolvedJavaType instantiatedType = findInstantiatedType(trace);
                return instantiatedType != null && !isRegisteredInServiceLoader(instantiatedType);
            }
            return false;
        }

        /**
         * Finds last constructor invocation.
         */
        private static ResolvedJavaType findInstantiatedType(List<BaseMethodNode> trace) {
            ResolvedJavaType res = null;
            for (BaseMethodNode mNode : trace) {
                AnalysisMethod m = mNode.getMethod();
                if (m != null && "<init>".equals(m.getName())) {
                    res = m.getDeclaringClass();
                }
            }
            return res;
        }

        /**
         * Finds if the given type may be instantiated by ServiceLoader.
         */
        private boolean isRegisteredInServiceLoader(ResolvedJavaType type) {
            String resource = String.format("META-INF/services/%s", type.toClassName());
            if (imageClassLoader.getClassLoader().getResource(resource) != null) {
                return true;
            }
            for (ResolvedJavaType ifc : type.getInterfaces()) {
                if (isRegisteredInServiceLoader(ifc)) {
                    return true;
                }
            }
            ResolvedJavaType superClz = type.getSuperclass();
            if (superClz != null) {
                return isRegisteredInServiceLoader(superClz);
            }
            return false;
        }

        @Override
        public Collection<AnalysisMethod> getInspectedMethods() {
            return Set.of(providerImplGet.getMethod());
        }
    }

    private static final class SafeSetThreadNameRecognizer implements CallGraphFilter {

        private final SVMHost hostVM;
        private final AnalysisMethodNode threadSetName;
        private final Set<AnalysisMethod> envCreateThread;
        private final Set<AnalysisMethod> envCreateSystemThread;

        SafeSetThreadNameRecognizer(BigBang bb) {
            hostVM = (SVMHost) bb.getHostVM();
            Set<AnalysisMethodNode> methods = findMethods(bb, Thread.class, (m) -> m.getName().equals("setName"));
            if (methods.size() != 1) {
                throw new IllegalStateException("Failed to lookup Thread.setName().");
            }
            threadSetName = methods.iterator().next();
            envCreateThread = findMethods(bb, TruffleLanguage.Env.class, (m) -> m.getName().equals("createThread")).stream().map(AnalysisMethodNode::getMethod).collect(Collectors.toSet());
            if (envCreateThread.isEmpty()) {
                throw new IllegalStateException("Failed to lookup TruffleLanguage.Env.createThread().");
            }
            envCreateSystemThread = findMethods(bb, TruffleLanguage.Env.class, (m) -> m.getName().equals("createSystemThread")).stream().map(AnalysisMethodNode::getMethod).collect(Collectors.toSet());
            if (envCreateSystemThread.isEmpty()) {
                throw new IllegalStateException("Failed to lookup TruffleLanguage.Env.createSystemThread().");
            }
        }

        @Override
        public boolean test(BaseMethodNode methodNode, BaseMethodNode callerNode, List<BaseMethodNode> trace) {
            if (!threadSetName.equals(methodNode)) {
                return false;
            }
            AnalysisMethod caller = callerNode.getMethod();
            StructuredGraph graph = hostVM.getAnalysisGraph(caller);
            Boolean res = null;
            AnalysisMethod method = methodNode.getMethod();
            for (Invoke invoke : graph.getInvokes()) {
                if (method.equals(invoke.callTarget().targetMethod())) {
                    NodeInputList<ValueNode> args = invoke.callTarget().arguments();
                    ValueNode arg0 = args.getFirst();
                    boolean isTruffleThread = false;
                    if (arg0 instanceof PiNode piNode) {
                        arg0 = piNode.getOriginalNode();
                        if (arg0 instanceof Invoke invokeNode) {
                            ResolvedJavaMethod target = invokeNode.callTarget().targetMethod();
                            isTruffleThread = envCreateThread.contains(target) || envCreateSystemThread.contains(target);
                        }
                    }
                    res = res == null ? isTruffleThread : (res && isTruffleThread);
                }
            }
            return res != null && res;
        }

        @Override
        public Collection<AnalysisMethod> getInspectedMethods() {
            Set<AnalysisMethod> set = new HashSet<>(envCreateThread);
            set.addAll(envCreateSystemThread);
            set.add(threadSetName.getMethod());
            return set;
        }
    }

    abstract static class BaseMethodNode {
        abstract StackTraceElement asStackTraceElement();

        abstract AnalysisType getOwner();

        abstract AnalysisMethod getMethod();
    }

    private static final class InlinedUnsafeMethodNode extends BaseMethodNode {

        private final AnalysisType unsafe;

        InlinedUnsafeMethodNode(AnalysisType unsafe) {
            this.unsafe = unsafe;
        }

        @Override
        StackTraceElement asStackTraceElement() {
            return new StackTraceElement(unsafe.toJavaName(), "<inlined>", unsafe.getSourceFileName(), -1);
        }

        @Override
        AnalysisType getOwner() {
            return unsafe;
        }

        @Override
        AnalysisMethod getMethod() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InlinedUnsafeMethodNode other = (InlinedUnsafeMethodNode) o;
            return unsafe.equals(other.unsafe);
        }

        @Override
        public int hashCode() {
            return Objects.hash(unsafe);
        }

        @Override
        public String toString() {
            return String.format("%s[unsafe=%s]", ClassUtil.getUnqualifiedName(getClass()), unsafe);
        }
    }

    static final class AnalysisMethodNode extends BaseMethodNode {

        private final AnalysisMethod method;

        AnalysisMethodNode(AnalysisMethod method) {
            this.method = Objects.requireNonNull(method);
        }

        @Override
        StackTraceElement asStackTraceElement() {
            return method.asStackTraceElement(0);
        }

        @Override
        AnalysisType getOwner() {
            return method.getDeclaringClass();
        }

        @Override
        public AnalysisMethod getMethod() {
            return method;
        }

        @Override
        public int hashCode() {
            return Objects.hash(method);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AnalysisMethodNode other = (AnalysisMethodNode) o;
            return method.equals(other.method);
        }

        @Override
        public String toString() {
            return String.format("%s[method=%s]", ClassUtil.getUnqualifiedName(getClass()), method);
        }
    }
}

@TargetClass(value = java.lang.SecurityManager.class, onlyWith = PermissionsFeature.IsEnabled.class)
final class Target_java_lang_SecurityManager {

    @Substitute
    @SuppressWarnings("unused")
    private void checkSecurityAccess(String target) {
    }

    @Substitute
    private void checkSetFactory() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPackageDefinition(String pkg) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPackageAccess(String pkg) {
    }

    @Substitute
    private void checkPrintJobAccess() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPropertyAccess(String key) {
    }

    @Substitute
    private void checkPropertiesAccess() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkMulticast(InetAddress maddr) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkAccept(String host, int port) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkListen(int port) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkConnect(String host, int port, Object context) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkConnect(String host, int port) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkDelete(String file) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkWrite(String file) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkWrite(FileDescriptor fd) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkRead(String file, Object context) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkRead(String file) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkRead(FileDescriptor fd) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkLink(String lib) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkExec(String cmd) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkExit(int status) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkAccess(ThreadGroup g) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkAccess(Thread t) {
    }

    @Substitute
    private void checkCreateClassLoader() {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPermission(Permission perm, Object context) {
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkPermission(Permission perm) {
    }
}

final class SecurityManagerHolder {
    @SuppressWarnings("deprecation") // SecurityManager deprecated since 17.
    static final SecurityManager SECURITY_MANAGER = new SecurityManager();
}

@TargetClass(value = java.lang.System.class, onlyWith = PermissionsFeature.IsEnabled.class)
final class Target_java_lang_System {
    @Substitute
    private static SecurityManager getSecurityManager() {
        return SecurityManagerHolder.SECURITY_MANAGER;
    }
}

final class LoggerFinderHolder {
    static final System.LoggerFinder LOGGER_FINDER = System.LoggerFinder.getLoggerFinder();
}

@TargetClass(value = java.lang.System.LoggerFinder.class, onlyWith = PermissionsFeature.IsEnabled.class)
final class Target_java_lang_System_LoggerFinder {
    @Substitute
    private static System.LoggerFinder getLoggerFinder() {
        return LoggerFinderHolder.LOGGER_FINDER;
    }
}
