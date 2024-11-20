/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

public class ForeignTypeEntry extends ClassEntry {
    private final String typedefName;
    private final ForeignTypeEntry parent;
    private final TypeEntry pointerTo;
    private final boolean isWord;
    private final boolean isStruct;
    private final boolean isPointer;
    private final boolean isInteger;
    private final boolean isSigned;
    private final boolean isFloat;

    public ForeignTypeEntry(String typeName, int size, long classOffset, long typeSignature,
                            long layoutTypeSignature, ClassEntry superClass, FileEntry fileEntry, LoaderEntry loader,
                            String typedefName, ForeignTypeEntry parent, TypeEntry pointerTo, boolean isWord,
                            boolean isStruct, boolean isPointer, boolean isInteger, boolean isSigned, boolean isFloat) {
        super(typeName, size, classOffset, typeSignature, typeSignature, layoutTypeSignature, layoutTypeSignature, superClass, fileEntry, loader);
        this.typedefName = typedefName;
        this.parent = parent;
        this.pointerTo = pointerTo;
        this.isWord = isWord;
        this.isStruct = isStruct;
        this.isPointer = isPointer;
        this.isInteger = isInteger;
        this.isSigned = isSigned;
        this.isFloat = isFloat;
    }

    @Override
    public boolean isForeign() {
        return true;
    }

    @Override
    public boolean isInstance() {
        return false;
    }

    public String getTypedefName() {
        return typedefName;
    }

    public ForeignTypeEntry getParent() {
        return parent;
    }

    public TypeEntry getPointerTo() {
        return pointerTo;
    }

    public boolean isWord() {
        return isWord;
    }

    public boolean isStruct() {
        return isStruct;
    }

    public boolean isPointer() {
        return isPointer;
    }

    public boolean isInteger() {
        return isInteger;
    }

    public boolean isSigned() {
        return isSigned;
    }

    public boolean isFloat() {
        return isFloat;
    }
}
