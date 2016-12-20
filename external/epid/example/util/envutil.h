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
 * \brief Environment utilities interface.
 */
#ifndef EXAMPLE_UTIL_ENVUTIL_H_
#define EXAMPLE_UTIL_ENVUTIL_H_

/// set the program name
void set_prog_name(char const* name);

/// get the program name
char const* get_prog_name();

/// log an error
/*!
This function may add or format the message before writing it out

output is written to the error stream
*/
int log_error(char const* msg, ...);

/// log a message
/*!
This function may add or format the message before writing it out

output is written to the standard output stream
*/
int log_msg(char const* msg, ...);

/// log a formatted message
/*!
This function will not add or format the message before writing it out

output is written to the standard output stream
*/
int log_fmt(char const* msg, ...);

#endif  // EXAMPLE_UTIL_ENVUTIL_H_
