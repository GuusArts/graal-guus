/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

#ifndef SVM_NATIVE_GDBJITCOMPILATIONINTERFACE_H
#define SVM_NATIVE_GDBJITCOMPILATIONINTERFACE_H

#include <stdint.h>

typedef enum
{
  JIT_NOACTION = 0,
  JIT_REGISTER,
  JIT_UNREGISTER
} jit_actions_t;

struct jit_code_entry
{
  struct jit_code_entry *next_entry;
  struct jit_code_entry *prev_entry;
  const char *symfile_addr;
  uint64_t symfile_size;
};

struct jit_descriptor
{
  uint32_t version;
  /* This type should be jit_actions_t, but we use uint32_t
     to be explicit about the bitwidth.  */
  uint32_t action_flag;
  struct jit_code_entry *relevant_entry;
  struct jit_code_entry *first_entry;
};


/* Make sure to specify the version statically, because the
   debugger may check the version before we can set it.  */
// struct jit_descriptor __jit_debug_descriptor = { 1, 0, 0, 0 };

/* GDB puts a breakpoint in this function.  */
void __attribute__((noinline)) __jit_debug_register_code() { };

struct jit_code_entry *register_jit_code(const char *addr, uint64_t size);
void unregister_jit_code(struct jit_code_entry *const entry);

#endif
