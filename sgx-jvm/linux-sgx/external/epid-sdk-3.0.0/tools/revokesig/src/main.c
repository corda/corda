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
 * \brief Create signature based revocation list request
 *
 */

#include <stdlib.h>
#include <string.h>
#include <dropt.h>
#include "util/buffutil.h"
#include "util/envutil.h"
#include "util/stdtypes.h"
#include "epid/common/file_parser.h"

// Defaults
#define PROGRAM_NAME "revokesig"
#define PUBKEYFILE_DEFAULT "pubkey.bin"
#define REQFILE_DEFAULT "sigrlreq.dat"
#define SIG_DEFAULT "sig.dat"
#define GROUP_PUB_KEY_SIZE \
  (sizeof(EpidFileHeader) + sizeof(GroupPubKey) + sizeof(EcdsaSignature))

#pragma pack(1)
/// Partial signature request, includes components through sig.
typedef struct SigRlRequestTop {
  EpidFileHeader header;  ///< EPID File Header
  GroupId gid;            ///< EPID Group ID
  EpidSignature sig;      ///< EPID Signature
} SigRlRequestTop;

/// Partial signature request, includes components after.
typedef struct SigRlRequestMid {
  uint32_t be_msg_size;  ///< size of message in bytes (big endian)
  uint8_t msg[1];        ///< message used to create signature (flexible array)
} SigRlRequestMid;
#pragma pack()

/// convert host to network byte order
static uint32_t htonl(uint32_t hostlong) {
  return (((hostlong & 0xFF) << 24) | ((hostlong & 0xFF00) << 8) |
          ((hostlong & 0xFF0000) >> 8) | ((hostlong & 0xFF000000) >> 24));
}

/// Fill a single SigRlRequest structure
/*!
\param[in] pubkey
Group public key.
\param[in] sig
Signature to append to request.
\param[in] sig_size
Size of the signature.
\param[in] msg_str
Message used to generate signature to revoke.
\param[in] msg_size
Length of the message.
\param[in out] req_buf
Pointer to request buffer.
\param[in] req_size
Size of request buffer.
\param[in out] req_top
Pointer to top structure of request.
*/
void FillRequest(GroupPubKey const* pubkey, EpidSignature const* sig,
                 size_t sig_size, char const* msg_str, size_t msg_size,
                 uint8_t* req_buf, size_t req_size, SigRlRequestTop* req_top);

/// Makes a request and appends it to file.
/*!
\param[in] cacert_file
Issuing CA certificate used to sign group public key file.
\param[in] sig_file
File containing signature to add to request.
\param[in] pubkey_file
File containing group public key.
\param[in] req_file
File to write a request.
\param[in] msg_str
Message used to generate signature to revoke.
\param[in] msg_size
Length of the message.
\param[in] verbose
If true function would print debug information to stdout.
*/
int MakeRequest(char const* cacert_file, char const* sig_file,
                char const* pubkey_file, char const* req_file,
                char const* msg_str, size_t msg_size, bool verbose);

/// Main entrypoint
int main(int argc, char* argv[]) {
  // intermediate return value for C style functions
  int ret_value = EXIT_FAILURE;

  // Signature file name parameter
  static char* sig_file = NULL;

  // Message string parameter
  static char* msg_str = NULL;
  size_t msg_size = 0;
  static char* msg_file = NULL;
  char* msg_buf = NULL;  // message loaded from msg_file

  // Signature revocation request file name parameter
  static char* req_file = NULL;

  // Group public key file name parameter
  static char* pubkey_file = NULL;

  // CA certificate file name parameter
  static char* cacert_file = NULL;

  // help flag parameter
  static bool show_help = false;

  // Verbose flag parameter
  static bool verbose = false;

  dropt_option options[] = {
      {'\0', "sig",
       "load signature to revoke from FILE (default: " SIG_DEFAULT ")", "FILE",
       dropt_handle_string, &sig_file},
      {'\0', "msg", "MESSAGE used to generate signature to revoke", "MESSAGE",
       dropt_handle_string, &msg_str},
      {'\0', "msgfile",
       "FILE containing message used to generate signature to revoke", "FILE",
       dropt_handle_string, &msg_file},
      {'\0', "gpubkey",
       "load group public key from FILE (default: " PUBKEYFILE_DEFAULT ")",
       "FILE", dropt_handle_string, &pubkey_file},
      {'\0', "capubkey", "load IoT Issuing CA public key from FILE", "FILE",
       dropt_handle_string, &cacert_file},
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
            "Revoke Intel(R) EPID signature\n"
            "\n"
            "Options:\n",
            PROGRAM_NAME);
        dropt_print_help(stdout, dropt_ctx, NULL);
        ret_value = EXIT_SUCCESS;
        break;
      } else if (*rest) {
        // we have unparsed (positional) arguments
        log_error("invalid argument: %s", *rest);
        fprintf(stderr, "Try '%s --help' for more information.\n",
                PROGRAM_NAME);
        ret_value = EXIT_FAILURE;
        break;
      } else {
        if (verbose) {
          verbose = ToggleVerbosity();
        }
        if (!sig_file) {
          sig_file = SIG_DEFAULT;
        }

        if (msg_str && msg_file) {
          log_error("--msg and --msgfile cannot be used together");
          ret_value = EXIT_FAILURE;
          break;
        } else if (msg_str) {
          msg_size = strlen(msg_str);
        } else if (msg_file) {
          msg_buf = NewBufferFromFile(msg_file, &msg_size);
          if (!msg_buf) {
            ret_value = EXIT_FAILURE;
            break;
          }
          msg_str = msg_buf;
        } else {
          msg_size = 0;
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
          log_msg(" sig_file      : %s", sig_file);
          log_msg(" msg_str       : %s", msg_str);
          log_msg(" pubkey_file   : %s", pubkey_file);
          log_msg(" cacert_file   : %s", cacert_file);
          log_msg(" req_file      : %s", req_file);
          log_msg("");
        }
      }
    }

    ret_value = MakeRequest(cacert_file, sig_file, pubkey_file, req_file,
                            msg_str, msg_size, verbose);
  } while (0);

  if (msg_buf) {
    free(msg_buf);
    msg_buf = NULL;
  }

  dropt_free_context(dropt_ctx);

  return ret_value;
}

/// Fill a single SigRlRequest structure
/*!

  | Field                           | Size          |
  |:--------------------------------|--------------:|
  | EPID Version (0x0200)           |       2 bytes |
  | File Type (0x000B)              |       2 bytes |
  | Group ID Number                 |      16 bytes |
  | Basic Signature                 |      52 bytes |
  | SigRL Version                   |       4 bytes |
  | Number of Non-Revoked Proofs    |       4 bytes |
  | nNRP * Non-Revoked Proofs       |    160 * nNRP |
  | Message Size in Bytes (msgSize) |       4 bytes |
  | Message                         | msgSize bytes |

 */
void FillRequest(GroupPubKey const* pubkey, EpidSignature const* sig,
                 size_t sig_size, char const* msg_str, size_t msg_size,
                 uint8_t* req_buf, size_t req_size, SigRlRequestTop* req_top) {
  const OctStr16 kEpidFileVersion = {2, 0};
  size_t i = 0;
  size_t req_mid_size = sizeof(((SigRlRequestMid*)0)->be_msg_size) + msg_size;
  SigRlRequestMid* req_mid =
      (SigRlRequestMid*)(req_buf + req_size - req_mid_size);

  if (!pubkey || !sig || !req_buf || !req_top || (!msg_str && 0 != msg_size)) {
    log_error("internal error: badarg to FillRequest()");
    return;
  }

  req_top->header.epid_version = kEpidFileVersion;
  req_top->header.file_type = kEpidFileTypeCode[kSigRlRequestFile];
  req_top->gid = pubkey->gid;
  // copy signature
  for (i = 0; i < sig_size; i++) {
    ((uint8_t*)&req_top->sig)[i] = ((uint8_t*)sig)[i];
  }
  req_mid->be_msg_size = htonl((uint32_t)msg_size);
  // copy msg
  for (i = 0; i < msg_size; i++) {
    req_mid->msg[i] = msg_str[i];
  }
}

int MakeRequest(char const* cacert_file, char const* sig_file,
                char const* pubkey_file, char const* req_file,
                char const* msg_str, size_t msg_size, bool verbose) {
  // Buffers and computed values
  // Signature buffer
  EpidSignature* sig = NULL;
  size_t sig_size = 0;

  // Group public key file
  unsigned char* pubkey_file_data = NULL;
  size_t pubkey_file_size = 0;

  // CA certificate
  EpidCaCertificate cacert = {0};

  // Group public key buffer
  GroupPubKey pubkey = {0};

  // Request buffer
  uint8_t* req_buf = NULL;
  size_t req_size = 0;

  size_t req_extra_space = (sizeof(EpidFileHeader) + sizeof(GroupId));

  int ret_value = EXIT_FAILURE;
  do {
    SigRlRequestTop* req_top = NULL;
    size_t req_file_size = 0;
    const size_t kMsgSizeSize = sizeof(((SigRlRequestMid*)0)->be_msg_size);

    if (!cacert_file || !sig_file || !pubkey_file || !req_file ||
        (!msg_str && 0 != msg_size)) {
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

    // Signature
    sig = NewBufferFromFile(sig_file, &sig_size);
    if (!sig) {
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
                pubkey_file, (int)GROUP_PUB_KEY_SIZE, (int)pubkey_file_size);
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
      log_msg("Creating SigRL revocation request:");
      log_msg("");
      log_msg(" [in]  Group ID: ");
      PrintBuffer(&pubkey.gid, sizeof(pubkey.gid));
      log_msg("");
      log_msg(" [in]  Signature Len: %d", (int)sig_size);
      log_msg(" [in]  Signature: ");
      PrintBuffer(&sig, sig_size);
      log_msg("");
      log_msg(" [in]  Message Len: %d", (int)msg_size);
      log_msg(" [in]  Message: ");
      PrintBuffer(msg_str, msg_size);
      log_msg("==============================================");
    }

    req_extra_space += sig_size + kMsgSizeSize + msg_size;

    if (FileExists(req_file)) {
      req_file_size = GetFileSize_S(req_file, SIZE_MAX - req_extra_space);
    } else {
      log_msg("request file does not exsist, create new");
    }

    req_size = req_file_size + req_extra_space;

    req_buf = AllocBuffer(req_size);
    if (!req_buf) {
      ret_value = EXIT_FAILURE;
      break;
    }

    if (req_file_size > 0) {
      if (0 != ReadLoud(req_file, req_buf, req_file_size)) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    req_top = (SigRlRequestTop*)(req_buf + req_file_size);

    FillRequest(&pubkey, sig, sig_size, msg_str, msg_size, req_buf, req_size,
                req_top);

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Reqest generated:");
      log_msg("");
      log_msg(" [in]  Request Len: %d", sizeof(SigRlRequestTop));
      log_msg(" [in]  Request: ");
      PrintBuffer(&req_top, sizeof(SigRlRequestTop));
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
  if (sig) free(sig);
  if (req_buf) free(req_buf);

  return ret_value;
}
