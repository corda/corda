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
 *
 * \brief Extract group keys from EPID group key output file
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <dropt.h>

#include "util/envutil.h"
#include "util/stdtypes.h"
#include "util/buffutil.h"
#include "util/strutil.h"
#include "epid/common/types.h"
#include "epid/common/file_parser.h"

#define PROGRAM_NAME "extractgrps"

#pragma pack(1)
/// EPID Key Output File Entry
typedef struct EpidBinaryGroupCertificate {
  EpidFileHeader header;     ///< EPID binary file header
  GroupPubKey pubkey;        ///< EPID 2.0 group public key
  EcdsaSignature signature;  ///< ECDSA Signature on SHA-256 of above values
} EpidBinaryGroupCertificate;
#pragma pack()

/// Main entrypoint
int main(int argc, char* argv[]) {
  // intermediate return value for C style functions
  int ret_value = EXIT_SUCCESS;

  size_t keyfile_size = 0;
  size_t num_keys_extracted = 0;
  size_t num_keys_in_file = 0;

  char* end = NULL;
  FILE* file = NULL;

  unsigned int i = 0;
  size_t bytes_read = 0;

  // File to extract keys from
  static char* keyfile_name = NULL;

  // Number of keys to extract
  static size_t num_keys_to_extract = 0;

  // help flag parameter
  static bool show_help = false;

  // Verbose flag parameter
  static bool verbose = false;

  dropt_option options[] = {
      {'h', "help", "display this help and exit", NULL, dropt_handle_bool,
       &show_help, dropt_attr_halt},
      {'v', "verbose", "print status messages to stdout", NULL,
       dropt_handle_bool, &verbose},

      {0} /* Required sentinel value. */
  };

  dropt_context* dropt_ctx = NULL;
  // set program name for logging
  set_prog_name(PROGRAM_NAME);
  do {
    dropt_ctx = dropt_new_context(options);
    if (!dropt_ctx) {
      ret_value = EXIT_FAILURE;
      break;
    } else if (argc > 0) {
      /* Parse the arguments from argv.
      *
      * argv[1] is always safe to access since argv[argc] is guaranteed
      * to be NULL and since we've established that argc > 0.
      */
      char** rest = dropt_parse(dropt_ctx, -1, &argv[1]);
      if (dropt_get_error(dropt_ctx) != dropt_error_none) {
        log_error(dropt_get_error_message(dropt_ctx));
        if (dropt_error_invalid_option == dropt_get_error(dropt_ctx)) {
          fprintf(stderr, "Try '%s --help' for more information.\n",
                  PROGRAM_NAME);
        }
        ret_value = EXIT_FAILURE;
        break;
      } else if (show_help) {
        log_fmt(
            "Usage: %s [OPTION]... [FILE] [NUM]\n"
            "Extract the first NUM group certs from FILE to current "
            "directory\n"
            "\n"
            "Options:\n",
            PROGRAM_NAME);
        dropt_print_help(stdout, dropt_ctx, NULL);
        ret_value = EXIT_SUCCESS;
        break;
      } else {
        // number of arguments rest
        size_t rest_count = 0;

        if (verbose) {
          verbose = ToggleVerbosity();
        }

        // count number of arguments rest
        while (rest[rest_count]) rest_count++;

        if (2 != rest_count) {
          log_error("unexpected number of arguments", *rest);
          fprintf(stderr, "Try '%s --help' for more information.\n",
                  PROGRAM_NAME);
          ret_value = EXIT_FAILURE;
          break;
        }

        keyfile_name = rest[0];

        num_keys_to_extract = strtoul(rest[1], &end, 10);
        if ('\0' != *end) {
          log_error("input '%s' is invalid: not a valid number of group keys",
                    rest[1]);
          ret_value = EXIT_FAILURE;
          break;
        }
      }
    }

    // check file existence
    if (!FileExists(keyfile_name)) {
      log_error("cannot access '%s'", keyfile_name);
      ret_value = EXIT_FAILURE;
      break;
    }

    keyfile_size = GetFileSize(keyfile_name);
    if (0 != keyfile_size % sizeof(EpidBinaryGroupCertificate)) {
      log_error(
          "input file '%s' is invalid: does not contain integral number of "
          "group keys",
          keyfile_name);
      ret_value = EXIT_FAILURE;
      break;
    }
    num_keys_in_file = keyfile_size / sizeof(EpidBinaryGroupCertificate);

    if (num_keys_to_extract > num_keys_in_file) {
      log_error("can not extract %d keys: only %d in file", num_keys_to_extract,
                num_keys_in_file);
      ret_value = EXIT_FAILURE;
      break;
    }

    file = fopen(keyfile_name, "rb");
    if (!file) {
      log_error("failed read from '%s'", keyfile_name);
      ret_value = EXIT_FAILURE;
      break;
    }

    // start extraction
    for (i = 0; i < num_keys_to_extract; i++) {
      EpidBinaryGroupCertificate temp;
      int seek_failed = 0;
      seek_failed = fseek(file, i * sizeof(temp), SEEK_SET);
      bytes_read = fread(&temp, 1, sizeof(temp), file);
      if (seek_failed || bytes_read != sizeof(temp)) {
        log_error("failed to extract key #%lu from '%s'", i, keyfile_name);
      } else {
        // ulong max = 4294967295
        char outkeyname[256] = {0};
        if (memcmp(&kEpidVersionCode[kEpid2x], &temp.header.epid_version,
                   sizeof(temp.header.epid_version)) ||
            memcmp(&kEpidFileTypeCode[kGroupPubKeyFile], &temp.header.file_type,
                   sizeof(temp.header.file_type))) {
          log_error("failed to extract key #%lu from '%s': file is invalid", i,
                    keyfile_name);
          ret_value = EXIT_FAILURE;
          break;
        }
        snprintf(outkeyname, sizeof(outkeyname), "pubkey%010u.bin", i);
        if (FileExists(outkeyname)) {
          log_error("file '%s' already exists", outkeyname);
          ret_value = EXIT_FAILURE;
          break;
        }
        if (0 != WriteLoud(&temp, sizeof(temp), outkeyname)) {
          log_error("failed to write key #%lu from '%s'", i, keyfile_name);
        } else {
          num_keys_extracted++;
        }
      }
    }
    if (EXIT_FAILURE == ret_value) {
      break;
    }

    log_msg("extracted %lu of %lu keys", num_keys_extracted, num_keys_in_file);
  } while (0);

  if (file) {
    fclose(file);
    file = NULL;
  }

  dropt_free_context(dropt_ctx);

  return ret_value;
}
