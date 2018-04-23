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
#ifndef EPID_COMMON_SRC_STACK_H_
#define EPID_COMMON_SRC_STACK_H_
/*!
 * \file
 * \brief Stack container interface.
 * \addtogroup EpidCommon
 * @{
 */
#include <stddef.h>
#include "epid/common/stdtypes.h"

/// A stack
typedef struct Stack Stack;

/// Create stack
/*!
  \param[in] element_size
  Size of stack element
  \param[out] stack
  Stack context to be created

  \returns true is operation succeed, false if stack were failed to allocate

  \see DeleteStack
*/
bool CreateStack(size_t element_size, Stack** stack);

/// Push multiple elements to the stack
/*!
  \param[in,out] stack
  Stack context
  \param[in] n
  Number of elements to push to the stack
  \param[in] elements
  Array of elements to push to the stack. Can be NULL

  \returns A pointer to an array of new elements in the stack or NULL if
    stack is empty or push operation were failed.

  \see CreateStack
*/
void* StackPushN(Stack* stack, size_t n, void* elements);

/// Pop multiple elements from the stack
/*!
  \param[in,out] stack
  Stack context
  \param[in] n
  Number of elements to pop from the stack
  \param[out] elements
  Pointer to a buffer to store elements removed from the stack

  \returns true is operation succeed, false otherwise

  \see CreateStack
*/
bool StackPopN(Stack* stack, size_t n, void* elements);

/// Get number of elements in the stack
/*!
  \param[in] stack
  Stack context

  \returns Number of elements in the stack or 0 if stack is NULL

  \see CreateStack
*/
size_t StackGetSize(Stack const* stack);

/// Deallocates memory used for the stack.
/*!
  \param[in,out] stack
  Stack context

  \see CreateStack
*/
void DeleteStack(Stack** stack);

/*! @} */
#endif  // EPID_COMMON_SRC_STACK_H_
