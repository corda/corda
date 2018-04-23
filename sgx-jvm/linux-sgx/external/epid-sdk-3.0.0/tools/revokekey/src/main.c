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
 * \brief Create private key revocation list request
 *
 */

#include <stdlib.h>
#include <string.h>
#include <dropt.h>

#include "util/buffutil.h"
#include "util/envutil.h"
#include "util/stdtypes.h"
#include "epid/common/file_parser.h"
#include "epid/member/api.h"

const OctStr16 kEpidFileVersion = {2, 0};

// Defaults
#define PROGRAM_NAME "revokekey"
#define PRIVKEY_DEFAULT "mprivkey.dat"
#define REQFILE_DEFAULT "privreq.dat"
#define PUBKEYFILE_DEFAULT "pubkey.bin"

/// Partial signature request, includes all but message.
typedef struct PrivRlRequestTop {
  EpidFileHeader header;  ///< EPID File Header
  PrivKey privkey;        ///< EPID Private Key
} PrivRlRequestTop;

int OpenKey(char const* privkey_file, char const* gpubkey_file,
            char const* cacert_file, PrivKey* priv_key) {
  int retval = EXIT_FAILURE;
  size_t file_size = GetFileSize(privkey_file);

  if (0 == file_size && !FileExists(privkey_file)) {
    log_error("cannot access '%s'", privkey_file);
    return EXIT_FAILURE;
  }

  if (file_size == sizeof(PrivKey)) {
    if (0 != ReadLoud(privkey_file, priv_key, sizeof(PrivKey))) {
      return EXIT_FAILURE;
    }
    retval = EXIT_SUCCESS;
  } else if (file_size == sizeof(CompressedPrivKey)) {
    void* signed_pubkey = NULL;
    if (!cacert_file) {
      log_error("issuing CA public key must be specified for compressed key");
      return EXIT_FAILURE;
    }
    if (!gpubkey_file) {
      log_error("group public key must be specified for compressed key");
      return EXIT_FAILURE;
    }

    do {
      size_t signed_pubkey_size = 0;
      CompressedPrivKey cmp_key;
      EpidCaCertificate cacert;
      GroupPubKey pub_key;
      EpidStatus sts;
      if (0 != ReadLoud(privkey_file, &cmp_key, sizeof(CompressedPrivKey))) {
        retval = EXIT_FAILURE;
        break;
      }
      signed_pubkey = NewBufferFromFile(gpubkey_file, &signed_pubkey_size);
      if (!signed_pubkey) {
        retval = EXIT_FAILURE;
        break;
      }
      if (0 != ReadLoud(gpubkey_file, signed_pubkey, signed_pubkey_size)) {
        retval = EXIT_FAILURE;
        break;
      }
      if (0 != ReadLoud(cacert_file, &cacert, sizeof(cacert))) {
        retval = EXIT_FAILURE;
        break;
      }
      sts = EpidParseGroupPubKeyFile(signed_pubkey, signed_pubkey_size, &cacert,
                                     &pub_key);
      if (kEpidNoErr != sts) {
        log_error("error while parsing group public key");
        retval = EXIT_FAILURE;
        break;
      }
      sts = EpidDecompressPrivKey(&pub_key, &cmp_key, priv_key);
      if (kEpidNoErr != sts) {
        log_error("error while decompressing member private key");
        retval = EXIT_FAILURE;
        break;
      }
      retval = EXIT_SUCCESS;
    } while (0);
    free(signed_pubkey);
  } else {
    log_error("unexpected file size for '%s'", privkey_file);
    retval = EXIT_FAILURE;
  }
  return retval;
}

int MakeRequest(PrivKey priv_key, char const* req_file, bool verbose) {
  // Request buffer
  uint8_t* req_buf = NULL;
  size_t req_size = 0;
  size_t req_extra_space = 0;
  int ret_value = EXIT_FAILURE;
  do {
    size_t entry_size = sizeof(EpidFileHeader) + sizeof(PrivKey);
    size_t req_file_size = 0;
    bool duplicate = false;
    size_t i = 0;
    PrivRlRequestTop* req_top = NULL;

    if (!req_file) {
      log_error("internal error: badarg to MakeRequest()");
      ret_value = EXIT_FAILURE;
      break;
    }

    // convert command line args to usable formats

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Input settings:");
      log_msg("");
      log_msg(" [in]  Group ID: ");
      PrintBuffer(&(priv_key.gid), sizeof(priv_key.gid));
      log_msg("");
      log_msg(" [in]  Private Key Len: %d", sizeof(PrivKey));
      log_msg(" [in]  Private Key: ");
      PrintBuffer(&(priv_key), sizeof(PrivKey));
      log_msg("");
      log_msg("==============================================");
    }

    req_extra_space += entry_size;
    if (FileExists(req_file)) {
      req_file_size = GetFileSize_S(req_file, SIZE_MAX - req_extra_space);

      if (req_file_size < entry_size) {
        log_error("output file smaller then size of one entry");
        ret_value = EXIT_FAILURE;
        break;
      }

      if (req_file_size % entry_size != 0) {
        log_error("size of output file is not multiple of the entry size");
        ret_value = EXIT_FAILURE;
        break;
      }
    } else {
      log_msg("request file does not exsist, create new");
    }

    req_size = req_file_size + req_extra_space;

    req_buf = AllocBuffer(req_size);
    if (!req_buf) {
      ret_value = EXIT_FAILURE;
      break;
    }

    // Load existing request file
    if (req_file_size > 0) {
      if (0 != ReadLoud(req_file, req_buf, req_file_size)) {
        ret_value = EXIT_FAILURE;
        break;
      }

      for (i = 0; i < req_file_size / entry_size; i++) {
        if (0 == memcmp(req_buf + entry_size * i + sizeof(EpidFileHeader),
                        &(priv_key), sizeof(PrivKey))) {
          duplicate = true;
          break;
        }
      }
      if (duplicate) {
        log_error("this private key already exists in output file");
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Append to the request
    req_top = (PrivRlRequestTop*)(req_buf + req_file_size);
    req_top->header.epid_version = kEpidFileVersion;
    req_top->header.file_type = kEpidFileTypeCode[kPrivRlRequestFile];
    req_top->privkey = priv_key;

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Request generated:");
      log_msg("");
      log_msg(" [in]  Request Len: %d", sizeof(PrivRlRequestTop));
      log_msg(" [in]  Request: ");
      PrintBuffer(&req_top, sizeof(PrivRlRequestTop));
      log_msg("==============================================");
    }

    // Store request
    if (0 != WriteLoud(req_buf, req_size, req_file)) {
      ret_value = EXIT_FAILURE;
      break;
    }

    ret_value = EXIT_SUCCESS;
  } while (0);

  // Free allocated buffers
  if (req_buf) free(req_buf);

  return ret_value;
}

/// Main entrypoint
int main(int argc, char* argv[]) {
  int retval = EXIT_FAILURE;

  // User Settings
  // Private key file name parameter
  static char* privkey_file = NULL;
  static char* gpubkey_file = NULL;
  static char* capubkey_file = NULL;

  // Private key revocation request file name parameter
  static char* req_file = NULL;

  // help flag parameter
  static bool show_help = false;

  // Verbose flag parameter
  static bool verbose = false;

  // Private key
  PrivKey priv_key;

  dropt_option options[] = {
      {'\0', "mprivkey",
       "load private key to revoke from FILE (default: " PRIVKEY_DEFAULT ")",
       "FILE", dropt_handle_string, &privkey_file},
      {'\0', "req",
       "append signature revocation request to FILE (default: " REQFILE_DEFAULT
       ")",
       "FILE", dropt_handle_string, &req_file},
      {'h', "help", "display this help and exit", NULL, dropt_handle_bool,
       &show_help, dropt_attr_halt},
      {'v', "verbose", "print status messages to stdout", NULL,
       dropt_handle_bool, &verbose},
      {'\0', '\0', "The following options are only needed for compressed keys:",
       NULL, dropt_handle_string, NULL},
      {'\0', "gpubkey",
       "load group public key from FILE (default: " PUBKEYFILE_DEFAULT ")",
       "FILE", dropt_handle_string, &gpubkey_file},
      {'\0', "capubkey", "load IoT Issuing CA public key from FILE", "FILE",
       dropt_handle_string, &capubkey_file},
      {0} /* Required sentinel value. */
  };

  dropt_context* dropt_ctx = NULL;

  // set program name for logging
  set_prog_name(PROGRAM_NAME);

  do {
    dropt_ctx = dropt_new_context(options);
    if (!dropt_ctx) {
      retval = EXIT_FAILURE;
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
        retval = EXIT_FAILURE;
        break;
      } else if (show_help) {
        log_fmt(
            "Usage: %s [OPTION]...\n"
            "Revoke Intel(R) EPID signature\n"
            "\n"
            "Options:\n",
            PROGRAM_NAME);
        dropt_print_help(stdout, dropt_ctx, NULL);
        retval = EXIT_SUCCESS;
        break;
      } else if (*rest) {
        // we have unparsed (positional) arguments
        log_error("invalid argument: %s", *rest);
        fprintf(stderr, "Try '%s --help' for more information.\n",
                PROGRAM_NAME);
        retval = EXIT_FAILURE;
        break;
      } else {
        if (verbose) {
          verbose = ToggleVerbosity();
        }
        if (!privkey_file) {
          privkey_file = PRIVKEY_DEFAULT;
        }
        if (!gpubkey_file) {
          gpubkey_file = PUBKEYFILE_DEFAULT;
        }
        if (!req_file) {
          req_file = REQFILE_DEFAULT;
        }
        if (verbose) {
          log_msg("\nOption values:");
          log_msg(" mprivkey  : %s", privkey_file);
          log_msg(" req       : %s", req_file);
          log_msg(" gpubkey   : %s", gpubkey_file);
          log_msg(" capubkey  : %s", capubkey_file);
          log_msg("");
        }
      }
    }

    retval = OpenKey(privkey_file, gpubkey_file, capubkey_file, &priv_key);
    if (EXIT_SUCCESS != retval) {
      break;
    }
    retval = MakeRequest(priv_key, req_file, verbose);
  } while (0);

  dropt_free_context(dropt_ctx);

  return retval;
}
