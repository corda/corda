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
 * \brief Create group revocation list request
 *
 */

#include <stdlib.h>
#include <string.h>
#include <dropt.h>
#include "util/buffutil.h"
#include "util/envutil.h"
#include "util/stdtypes.h"
#include "epid/common/file_parser.h"

const OctStr16 kEpidFileVersion = {2, 0};

// Defaults
#define PROGRAM_NAME "revokegrp"
#define PUBKEYFILE_DEFAULT "pubkey.bin"
#define REQFILE_DEFAULT "grprlreq.dat"
#define REASON_DEFAULT 0
#define GROUP_PUB_KEY_SIZE \
  (sizeof(EpidFileHeader) + sizeof(GroupPubKey) + sizeof(EcdsaSignature))
#define STRINGIZE(a) #a

#pragma pack(1)
/// Group revocation request entry
typedef struct GrpInfo {
  GroupId gid;     ///< EPID Group ID
  uint8_t reason;  ///< Revocation reason
} GrpInfo;
/// Group Revocation request
typedef struct GrpRlRequest {
  EpidFileHeader header;  ///< EPID File Header
  uint32_t count;         ///< Revoked count (big endian)
  GrpInfo groups[1];      ///< Revoked group count (flexible array)
} GrpRlRequest;
#pragma pack()

/// convert host to network byte order
static uint32_t htonl(uint32_t hostlong) {
  return (((hostlong & 0xFF) << 24) | ((hostlong & 0xFF00) << 8) |
          ((hostlong & 0xFF0000) >> 8) | ((hostlong & 0xFF000000) >> 24));
}
/// convert network to host byte order
static uint32_t ntohl(uint32_t netlong) {
  return (((netlong & 0xFF) << 24) | ((netlong & 0xFF00) << 8) |
          ((netlong & 0xFF0000) >> 8) | ((netlong & 0xFF000000) >> 24));
}

/// Makes a request and appends it to file.
/*!
\param[in] cacert_file
Issuing CA certificate used to sign group public key file.
\param[in] pubkey_file
File containing group public key.
\param[in] req_file
File to write a request.
\param[in] reason
Revokation reason.
\param[in] verbose
If true function would print debug information to stdout.
*/
int MakeRequest(char const* cacert_file, char const* pubkey_file,
                char const* req_file, uint8_t reason, bool verbose);

/// Main entrypoint
int main(int argc, char* argv[]) {
  // intermediate return value for C style functions
  int ret_value = EXIT_FAILURE;

  // User Settings

  // Group revocation request file name parameter
  static char* req_file = NULL;

  // Group public key file name parameter
  static char* pubkey_file = NULL;

  // CA certificate file name parameter
  static char* cacert_file = NULL;

  // Revocation reason
  static uint32_t reason = REASON_DEFAULT;

  // help flag parameter
  static bool show_help = false;

  // Verbose flag parameter
  static bool verbose = false;

  dropt_option options[] = {
      {'\0', "gpubkey",
       "load group public key from FILE (default: " PUBKEYFILE_DEFAULT ")",
       "FILE", dropt_handle_string, &pubkey_file},
      {'\0', "capubkey", "load IoT Issuing CA public key from FILE", "FILE",
       dropt_handle_string, &cacert_file},
      {'\0', "reason",
       "revocation reason (default: " STRINGIZE(REASON_DEFAULT) ")", "FILE",
       dropt_handle_uint, &reason},
      {'\0', "req",
       "append signature revocation request to FILE (default: " REQFILE_DEFAULT
       ")",
       "FILE", dropt_handle_string, &req_file},

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
            "Usage: %s [OPTION]...\n"
            "Revoke Intel(R) EPID group\n"
            "\n"
            "Options:\n",
            PROGRAM_NAME);
        dropt_print_help(stdout, dropt_ctx, NULL);
        ret_value = EXIT_SUCCESS;
        break;
      } else if (*rest) {
        // we have unparsed (positional) arguments
        log_error("invalid argument: %s", *rest);
        fprintf(stderr, "Try '%s --help' for more information\n", PROGRAM_NAME);
        ret_value = EXIT_FAILURE;
        break;
      } else {
        if (reason > UCHAR_MAX) {
          log_error(
              "unexpected reason value. Value of the reason must be lesser or "
              "equal to %d",
              UCHAR_MAX);
          ret_value = EXIT_FAILURE;
          break;
        }
        if (verbose) {
          verbose = ToggleVerbosity();
        }
        if (!pubkey_file) {
          pubkey_file = PUBKEYFILE_DEFAULT;
        }
        if (!cacert_file) {
          log_error("issuing CA public key must be specified");
          ret_value = EXIT_FAILURE;
          break;
        }
        if (!req_file) {
          req_file = REQFILE_DEFAULT;
        }
        if (verbose) {
          log_msg("\nOption values:");
          log_msg(" pubkey_file   : %s", pubkey_file);
          log_msg(" cacert_file   : %s", cacert_file);
          log_msg(" reason        : %d", reason);
          log_msg(" req_file      : %s", req_file);
          log_msg("");
        }
      }
    }

    ret_value = MakeRequest(cacert_file, pubkey_file, req_file, (uint8_t)reason,
                            verbose);
  } while (0);
  dropt_free_context(dropt_ctx);
  return ret_value;
}

int MakeRequest(char const* cacert_file, char const* pubkey_file,
                char const* req_file, uint8_t reason, bool verbose) {
  // Group index and count
  uint32_t grp_index = 0;
  uint32_t grp_count = 0;

  // Buffers and computed values
  // Group public key file
  unsigned char* pubkey_file_data = NULL;
  size_t pubkey_file_size = 0;

  // Group public key buffer
  GroupPubKey pubkey = {0};

  // CA certificate
  EpidCaCertificate cacert = {0};

  // Request buffer
  uint8_t* req_buf = NULL;
  size_t req_size = 0;
  size_t req_file_size = 0;
  GrpRlRequest* request = NULL;
  size_t req_extra_space = sizeof(GroupId) + sizeof(uint8_t);

  int ret_value = EXIT_FAILURE;
  do {
    if (!cacert_file || !pubkey_file || !req_file) {
      log_error("internal error: badarg to MakeRequest()");
      ret_value = EXIT_FAILURE;
      break;
    }

    // convert command line args to usable formats
    // CA certificate
    if (0 != ReadLoud(cacert_file, &cacert, sizeof(cacert))) {
      ret_value = EXIT_FAILURE;
      break;
    }

    // Group public key file
    pubkey_file_data = NewBufferFromFile(pubkey_file, &pubkey_file_size);
    if (!pubkey_file_data) {
      ret_value = EXIT_FAILURE;
      break;
    }

    // Security note:
    // Application must confirm group public key is
    // authorized by the issuer, e.g., signed by the issuer.
    if (GROUP_PUB_KEY_SIZE != pubkey_file_size) {
      log_error("unexpected file size for '%s'. Expected: %d; got: %d",
                pubkey_file, (int)GROUP_PUB_KEY_SIZE, pubkey_file_size);
      ret_value = EXIT_FAILURE;
      break;
    }
    if (kEpidNoErr != EpidParseGroupPubKeyFile(pubkey_file_data,
                                               pubkey_file_size, &cacert,
                                               &pubkey)) {
      log_error("group public key is not authorized");
      ret_value = EXIT_FAILURE;
      break;
    }

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Input settings:");
      log_msg("");
      log_msg(" [in]  Group ID: ");
      PrintBuffer(&pubkey.gid, sizeof(pubkey.gid));
      log_msg("");
      log_msg(" [in]  Reason: %d", reason);
      log_msg("==============================================");
    }

    // Calculate request size
    req_size = sizeof(EpidFileHeader) + sizeof(uint32_t);

    if (FileExists(req_file)) {
      req_file_size = GetFileSize_S(req_file, SIZE_MAX - req_extra_space);

      if (req_file_size < req_size) {
        log_error("output file smaller then size of empty request");
        ret_value = EXIT_FAILURE;
        break;
      }

      req_size = req_file_size;
    } else {
      log_msg("request file does not exsist, create new");
    }

    req_size += req_extra_space;

    // Allocate request buffer
    req_buf = AllocBuffer(req_size);
    if (!req_buf) {
      ret_value = EXIT_FAILURE;
      break;
    }

    request = (GrpRlRequest*)req_buf;

    // Load existing request file
    if (req_file_size > 0) {
      if (0 != ReadLoud(req_file, req_buf, req_file_size)) {
        ret_value = EXIT_FAILURE;
        break;
      }

      // Check EPID and file versions
      if (0 != memcmp(&request->header.epid_version, &kEpidFileVersion,
                      sizeof(kEpidFileVersion))) {
        ret_value = EXIT_FAILURE;
        break;
      }

      if (0 != memcmp(&request->header.file_type,
                      &kEpidFileTypeCode[kGroupRlRequestFile],
                      sizeof(kEpidFileTypeCode[kGroupRlRequestFile]))) {
        ret_value = EXIT_FAILURE;
        break;
      }

      grp_count = ntohl(request->count);

      // Update the reason if the group is in the request
      for (grp_index = 0; grp_index < grp_count; grp_index++) {
        if (0 == memcmp(&request->groups[grp_index].gid, &pubkey.gid,
                        sizeof(pubkey.gid))) {
          request->groups[grp_index].reason = reason;
          req_size = req_file_size;
          break;
        }
      }
    }

    // Append group to the request
    if (grp_index == grp_count) {
      request->header.epid_version = kEpidFileVersion;
      request->header.file_type = kEpidFileTypeCode[kGroupRlRequestFile];
      request->groups[grp_count].gid = pubkey.gid;
      request->groups[grp_count].reason = reason;
      request->count = htonl(++grp_count);
    }

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Request generated:");
      log_msg("");
      log_msg(" [in]  Request Len: %d", (int)req_size);
      log_msg(" [in]  Request: ");
      PrintBuffer(req_buf, req_size);
      log_msg("==============================================");
    }

    // Store request
    if (0 != WriteLoud(req_buf, req_size, req_file)) {
      ret_value = EXIT_FAILURE;
      break;
    }

    // Success
    ret_value = EXIT_SUCCESS;
  } while (0);

  // Free allocated buffers
  if (pubkey_file_data) free(pubkey_file_data);
  if (req_buf) free(req_buf);

  return ret_value;
}
