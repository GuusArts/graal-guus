/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.util;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

public class LoopUtility {

    /**
     * Determine if the def can use node {@code use} without the need for value proxies. This means
     * there is no loop exit between the schedule point of def and use that would require a
     * {@link ProxyNode}.
     */
    public static boolean canUseWithoutProxy(ControlFlowGraph cfg, Node def, Node use) {
        if (def.graph() instanceof StructuredGraph && ((StructuredGraph) def.graph()).isAfterStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            return true;
        }
        if (!isFixedNode(def) || !isFixedNode(use)) {
            /*
             * If def or use are not fixed nodes we cannot determine the schedule point for them.
             * Without the schedule point we cannot find their basic block in the control flow
             * graph. If we would schedule the graph we could answer the question for floating nodes
             * as well but this is too much overhead. Thus, for floating nodes we give up and assume
             * a proxy is necessary.
             */
            return false;
        }
        Block useBlock = cfg.blockFor(use);
        Block defBlock = cfg.blockFor(def);
        Loop<Block> defLoop = defBlock.getLoop();
        Loop<Block> useLoop = useBlock.getLoop();
        if (defLoop != null) {
            // the def is inside a loop, either a parent or a disjunct loop
            if (useLoop != null) {
                // we are only safe without proxies if we are included in the def loop,
                // i.e., the def loop is a parent loop
                return useLoop.isAncestorOrSelf(defLoop);
            } else {
                // the use is not in a loop but the def is, needs proxies, fail
                return false;
            }
        }
        return true;
    }

    private static boolean isFixedNode(Node n) {
        return n instanceof FixedNode;
    }

    /**
     * Remove loop proxies that became obsolete over time, i.e., they proxy a value that already
     * flowed out of a loop and dominates the loop now.
     */
    public static void removeObsoleteProxies(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        for (LoopEx loop : loopsData.loops()) {
            removeObsoleteProxiesForLoop(loop, context, canonicalizer);
        }
    }

    @SuppressWarnings("try")
    public static void removeObsoleteProxiesForLoop(LoopEx loop, CoreProviders context, CanonicalizerPhase canonicalizer) {
        StructuredGraph graph = loop.loopBegin().graph();
        final EconomicSetNodeEventListener inputChanges = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.INPUT_CHANGED));
        try (NodeEventScope s = graph.trackNodeEvents(inputChanges)) {
            for (LoopExitNode lex : loop.loopBegin().loopExits()) {
                for (ProxyNode proxy : lex.proxies().snapshot()) {
                    if (loop.isOutsideLoop(proxy.value())) {
                        proxy.replaceAtUsagesAndDelete(proxy.getOriginalNode());
                    }
                }
            }
        }
        canonicalizer.applyIncremental(graph, context, inputChanges.getNodes());
    }
}
