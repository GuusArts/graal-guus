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

package com.oracle.objectfile.runtime.dwarf.constants;

/**
 * All the Dwarf attribute forms needed to type attribute values generated by GraalVM.
 */
public enum DwarfForm {
    DW_FORM_null(0x0),
    DW_FORM_addr(0x1),
    DW_FORM_data2(0x05),
    DW_FORM_data4(0x6),
    @SuppressWarnings("unused")
    DW_FORM_data8(0x7),
    @SuppressWarnings("unused")
    DW_FORM_string(0x8),
    @SuppressWarnings("unused")
    DW_FORM_block1(0x0a),
    DW_FORM_ref_addr(0x10),
    @SuppressWarnings("unused")
    DW_FORM_ref1(0x11),
    @SuppressWarnings("unused")
    DW_FORM_ref2(0x12),
    DW_FORM_ref4(0x13),
    @SuppressWarnings("unused")
    DW_FORM_ref8(0x14),
    DW_FORM_sec_offset(0x17),
    DW_FORM_data1(0x0b),
    DW_FORM_flag(0xc),
    DW_FORM_strp(0xe),
    DW_FORM_expr_loc(0x18);

    private final int value;

    DwarfForm(int i) {
        value = i;
    }

    public int value() {
        return value;
    }
}
