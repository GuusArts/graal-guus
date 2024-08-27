/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.impl.shared;

import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;

public abstract class PackageTable<M, R, PE extends PackageTable.PackageEntry<M, R, ME>, ME extends ModuleTable.ModuleEntry<M, R>> extends EntryTable<PE, ME> {
    public PackageTable(ReadWriteLock lock) {
        super(lock);
    }

    public abstract static class PackageEntry<M, R, ME extends ModuleTable.ModuleEntry<M, R>> extends EntryTable.NamedEntry {
        protected PackageEntry(Symbol<Name> name, ME module) {
            super(name);
            this.module = module;
        }

        private final ME module;
        private ArrayList<ME> exports;
        private boolean isUnqualifiedExported;
        private boolean isExportedAllUnnamed;

        public void addExports(ME m) {
            if (isUnqualifiedExported()) {
                return;
            }
            synchronized (this) {
                if (m == null) {
                    setUnqualifiedExports();
                }
                if (exports == null) {
                    exports = new ArrayList<>();
                }
                if (!contains(m)) {
                    exports.add(m);
                }
            }
        }

        public boolean isQualifiedExportTo(ME m) {
            if (isExportedAllUnnamed() && !m.isNamed()) {
                return true;
            }
            if (isUnqualifiedExported() || exports == null) {
                return false;
            }
            return contains(m);
        }

        public boolean isUnqualifiedExported() {
            return module().isOpen() || isUnqualifiedExported;
        }

        public void setUnqualifiedExports() {
            if (isUnqualifiedExported()) {
                return;
            }
            isUnqualifiedExported = true;
            isExportedAllUnnamed = true;
            exports = null;
        }

        public boolean isExportedAllUnnamed() {
            return module().isOpen() || isExportedAllUnnamed;
        }

        public void setExportedAllUnnamed() {
            if (isExportedAllUnnamed()) {
                return;
            }
            synchronized (this) {
                isExportedAllUnnamed = true;
            }
        }

        public boolean contains(ME m) {
            return exports.contains(m);
        }

        public ME module() {
            return module;
        }
    }
}
