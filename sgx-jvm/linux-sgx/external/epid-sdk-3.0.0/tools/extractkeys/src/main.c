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
 * \brief Extract member private keys from EPID key output file
 *
 * Not validating SHA hashes in key file
 */

#include <stdlib.h>
#include <stdio.h>

#include <dropt.h>
#include "util/envutil.h"
#include "util/stdtypes.h"
#include "util/buffutil.h"
#include "util/strutil.h"
#include "epid/common/types.h"

#define PROGRAM_NAME "extractkeys"
#define MANDATORY_PARAM_COUNT 2

#pragma pack(1)
/// EPID Key Output File Entry
typedef struct EpidKeyOutputFileKey {
  unsigned char product_id[2];  ///< 2-byte Product ID (Big Endian)
  unsigned char key_id[8];      ///< 8-byte Key Unique Id(Big Endian)
  unsigned char svn[4];  ///< 4-byte Security Version Number (SVN) (Big Endian)
  PrivKey privkey;       ///< EPID 2.0 Private Key
  unsigned char hash[20];  ///< 20-byte SHA-1 of above
} EpidKeyOutputFileKey;

/// EPID Compressed Key Output File Entry
typedef struct EpidCompressedKeyOutputFileKey {
  unsigned char product_id[2];  ///< 2-byte Product ID (Big Endian)
  unsigned char key_id[8];      ///< 8-byte Key Unique Id(Big Endian)
  unsigned char svn[4];  ///< 4-byte Security Version Number (SVN) (Big Endian)
  CompressedPrivKey privkey;  ///< EPID 2.0 Compressed Private Key
  unsigned char hash[20];     ///< 20-byte SHA-1 of above
} EpidCompressedKeyOutputFileKey;
#pragma pack()

/// Main entrypoint
int main(int argc, char* argv[]) {
  // intermediate return value for C style functions
  int ret_value = EXIT_SUCCESS;
  // Buffer to store read key
  uint8_t temp[sizeof(EpidKeyOutputFileKey)] = {0};

  // Private key to extract
  void* privkey = 0;
  size_t privkey_size = 0;

  size_t keyfile_size = 0;
  size_t keyfile_entry_size = 0;
  size_t num_keys_extracted = 0;
  size_t num_keys_in_file = 0;

  char* end = NULL;
  FILE* file = NULL;

  // File to extract keys from
  static char* keyfile_name = NULL;

  // Number of keys to extract
  static size_t num_keys_to_extract = 0;

  // help flag parameter
  static bool show_help = false;

  // Verbose flag parameter
  static bool verbose = false;

  // Compressed flag parameter
  static bool compressed = false;

  unsigned int i = 0;
  size_t bytes_read = 0;

  dropt_option options[] = {
      {'c', "compressed", "extract compressed keys", NULL, dropt_handle_bool,
       &compressed},
      {'h', "help", "display this help and exit", NULL, dropt_handle_bool,
       &show_help, dropt_attr_halt},
      {'v', "verbose", "print status messages to stdout", NULL,
       dropt_handle_bool, &verbose},

      {0} /* Required sentinel value. */
  };

  dropt_context* dropt_ctx = NULL;
  (void)argc;
  // set program name for logging
  set_prog_name(PROGRAM_NAME);

  do {
    dropt_ctx = dropt_new_context(options);

    if (dropt_ctx && argc > 0) {
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

            "Extract the first NUM private keys from FILE to current "
            "directory.\n\n"
            "Options:\n",
            PROGRAM_NAME);
        dropt_print_help(stdout, dropt_ctx, NULL);
        ret_value = EXIT_SUCCESS;
        break;
      } else {
        size_t rest_count = 0;
        if (verbose) verbose = ToggleVerbosity();

        // count number of arguments rest
        while (rest[rest_count]) rest_count++;
        if (rest_count != MANDATORY_PARAM_COUNT) {
          log_error(
              "%s arguments: found %i positional arguments, expected %i",
              (rest_count < MANDATORY_PARAM_COUNT) ? "missing" : "too many",
              rest_count, MANDATORY_PARAM_COUNT);

          fprintf(stderr, "Try '%s --help' for more information.\n",
                  PROGRAM_NAME);
          ret_value = EXIT_FAILURE;
          break;
        }

        keyfile_name = *(rest);

        num_keys_to_extract = strtoul(*(rest + 1), &end, 10);
        if ('\0' != *end) {
          log_error("input '%s' is invalid: not a valid number of keys",
                    *(rest + 1));
          ret_value = EXIT_FAILURE;
          break;
        }
      }

    } else {
      ret_value = EXIT_FAILURE;
      break;
    }

    // check file existence
    if (!FileExists(keyfile_name)) {
      log_error("cannot access '%s'", keyfile_name);
      ret_value = EXIT_FAILURE;
      break;
    }

    keyfile_size = GetFileSize(keyfile_name);
    if (compressed) {
      privkey_size = sizeof(CompressedPrivKey);
      privkey = &(((EpidCompressedKeyOutputFileKey*)&temp[0])->privkey);
      keyfile_entry_size = sizeof(EpidCompressedKeyOutputFileKey);
    } else {
      privkey_size = sizeof(PrivKey);
      privkey = &(((EpidKeyOutputFileKey*)&temp[0])->privkey);
      keyfile_entry_size = sizeof(EpidKeyOutputFileKey);
    }

    if (0 != keyfile_size % keyfile_entry_size) {
      log_error(
          "input file '%s' is invalid: does not contain integral number of "
          "keys",
          keyfile_name);
      ret_value = EXIT_FAILURE;
      break;
    }
    num_keys_in_file = keyfile_size / keyfile_entry_size;

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
    for (i = 0; i < num_keys_to_extract; ++i) {
      int seek_failed = 0;
      seek_failed = fseek(file, (int)(i * keyfile_entry_size), SEEK_SET);
      bytes_read = fread(&temp, 1, keyfile_entry_size, file);
      if (seek_failed || bytes_read != keyfile_entry_size) {
        log_error("failed to extract key #%lu from '%s'", i, keyfile_name);
      } else {
        char outkeyname[256] = {0};
        snprintf(outkeyname, sizeof(outkeyname), "mprivkey%010u.dat", i);

        if (FileExists(outkeyname)) {
          log_error("file '%s' already exists", outkeyname);
          ret_value = EXIT_FAILURE;
          break;
        }
        if (0 != WriteLoud(privkey, privkey_size, outkeyname)) {
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
  dropt_ctx = NULL;

  return ret_value;
}
