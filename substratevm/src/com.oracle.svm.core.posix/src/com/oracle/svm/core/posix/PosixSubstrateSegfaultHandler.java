/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.MemoryProtectionProvider;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.AdvancedSignalDispatcher;
import com.oracle.svm.core.posix.headers.Signal.siginfo_t;
import com.oracle.svm.core.posix.headers.Signal.ucontext_t;
import com.oracle.svm.core.util.VMError;

@AutomaticallyRegisteredImageSingleton({SubstrateSegfaultHandler.class, PosixSubstrateSegfaultHandler.class})
class PosixSubstrateSegfaultHandler extends SubstrateSegfaultHandler {
    static final CEntryPointLiteral<AdvancedSignalDispatcher> SIGNAL_HANDLER = CEntryPointLiteral.create(PosixSubstrateSegfaultHandler.class,
                    "dispatch", int.class, siginfo_t.class, ucontext_t.class);

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in segfault signal handler.")
    @Uninterruptible(reason = "Must be uninterruptible until it gets immune to safepoints")
    private static void dispatch(@SuppressWarnings("unused") int signalNumber, @SuppressWarnings("unused") siginfo_t sigInfo, ucontext_t uContext) {
        if (MemoryProtectionProvider.isAvailable()) {
            MemoryProtectionProvider.singleton().handleSegfault(sigInfo);
        }

        if (tryEnterIsolate(uContext)) {
            dump(sigInfo, uContext, true);
            throw VMError.shouldNotReachHereAtRuntime();
        }

        /* Attach failed - kill the process because the segfault handler must not return. */
        LibC.abort();
    }

    @Override
    protected void printSignalInfo(Log log, PointerBase signalInfo) {
        if (MemoryProtectionProvider.isAvailable()) {
            MemoryProtectionProvider.singleton().printSignalInfo(signalInfo);
        } else {
            siginfo_t sigInfo = (siginfo_t) signalInfo;
            log.string("siginfo: si_signo: ").signed(sigInfo.si_signo()).string(", si_code: ").signed(sigInfo.si_code());
            if (sigInfo.si_errno() != 0) {
                log.string(", si_errno: ").signed(sigInfo.si_errno());
            }

            VoidPointer addr = sigInfo.si_addr();
            log.string(", si_addr: ");
            printSegfaultAddressInfo(log, addr.rawValue());
            log.newline();
        }
    }

    @Override
    public void install() {
        boolean isSignalHandlingAllowed = SubstrateOptions.EnableSignalHandling.getValue();
        PosixSignalHandlerSupport.installNativeSignalHandler(Signal.SignalEnum.SIGSEGV, SIGNAL_HANDLER.getFunctionPointer(), Signal.SA_NODEFER(), isSignalHandlingAllowed);
        PosixSignalHandlerSupport.installNativeSignalHandler(Signal.SignalEnum.SIGBUS, SIGNAL_HANDLER.getFunctionPointer(), Signal.SA_NODEFER(), isSignalHandlingAllowed);
    }
}
