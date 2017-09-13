/*############################################################################
  # Copyright 2016 Intel Corporation
  #
  # Licensed under the Apache License, Version 2.0 (the "License");
  # you may not use this file except in compliance with the License.
  # You may obtain a copy of the License at
  #
  #     http://www.apache.org/licenses/LICENSE-2.0
  #
  # Unless required by applicable law or agreed to in writing, software
  # distributed under the License is distributed on an "AS IS" BASIS,
  # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  # See the License for the specific language governing permissions and
  # limitations under the License.
  ############################################################################*/
/*!
 * \file
 * \brief Stack container implementation.
 */
#include <limits.h>
#include "epid/common/src/stack.h"
#include "epid/common/src/memory.h"

/// Internal representation of a Stack
struct Stack {
  size_t element_size;  ///< Size of element in bytes
  void* buf;            ///< Buffer to store elements
  size_t max_size;      ///< Numbers of elements buffer was allocated to
  size_t top;           ///< Stack top, the number of elements in the stack
};

bool CreateStack(size_t element_size, Stack** stack) {
  if (!stack || 0 == element_size) return false;
  *stack = SAFE_ALLOC(sizeof(Stack));
  if (!*stack) return false;
  (*stack)->element_size = element_size;
  return true;
}

void* StackPushN(Stack* stack, size_t n, void* elements) {
  if (!stack) return 0;
  if (n > 0) {
    size_t max_size_required = stack->top + n;
    if (n > (SIZE_MAX / stack->element_size) - stack->top)
      return 0;  // integer overflow
    if (max_size_required > stack->max_size) {
      void* reallocated =
          SAFE_REALLOC(stack->buf, max_size_required * stack->element_size);
      if (!reallocated) return 0;
      stack->buf = reallocated;
      stack->max_size = max_size_required;
    }
    if (elements) {
      // Memory copy is used to copy variable number of elements to stack
      if (0 != memcpy_S((uint8_t*)stack->buf + stack->top * stack->element_size,
                        (stack->max_size - stack->top) * stack->element_size,
                        elements, n * stack->element_size)) {
        return 0;
      }
    }
    stack->top += n;
  }
  return (uint8_t*)stack->buf + (stack->top - n) * stack->element_size;
}

bool StackPopN(Stack* stack, size_t n, void* elements) {
  if (!stack) return false;
  if (n > 0) {
    if (n > stack->top) return false;
    if (elements) {
      // Memory copy is used to copy variable number of elements from stack
      if (0 != memcpy_S(elements, n * stack->element_size,
                        (uint8_t*)stack->buf +
                            (stack->top - n) * stack->element_size,
                        n * stack->element_size)) {
        return false;
      }
      stack->top -= n;
    }
  }
  return true;
}

size_t StackGetSize(Stack const* stack) {
  return stack ? stack->top : (size_t)0;
}

void DeleteStack(Stack** stack) {
  if (stack && *stack) {
    SAFE_FREE((*stack)->buf);
    SAFE_FREE(*stack);
  }
}
