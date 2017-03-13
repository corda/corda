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
 * \brief Argument parsing utilities implementation.
 */

#include <string.h>
#include "util/argutil.h"
#include "util/envutil.h"

int GetOptionIndex(int argc, char* const argv[], char const* option) {
  int result = -1;
  int ca = 0;
  for (ca = argc - 1; ca > 0; ca--) {
    if (0 == strncmp(argv[ca], option, strlen(option))) {
      if (strlen(argv[ca]) > strlen(option) &&
          '=' != *(argv[ca] + strlen(option))) {
        continue;
      } else {
        result = ca;
        break;
      }
    }
  }
  return result;
}

// this finds options that start with option=
int GetCmdOptionIndex(int argc, char* const argv[], char const* option) {
  int result = -1;
  int ca = 0;
  for (ca = argc - 1; ca > 0; ca--) {
    if (0 == strcmp(argv[ca], option)) {
      result = ca;
      break;
    }
  }
  return result;
}

int CmdOptionExists(int argc, char* const argv[], char const* option) {
  return (-1 != GetOptionIndex(argc, argv, option));
}

char const* GetCmdOption(int argc, char* const argv[], char const* option) {
  char const* optionarg = 0;
  int option_index = GetOptionIndex(argc, argv, option);
  if (-1 != option_index) {
    // we have the option
    if (strlen(argv[option_index]) > strlen(option) + 1) {
      optionarg = argv[option_index] + strlen(option) + 1;
    }
  }
  return optionarg;
}
