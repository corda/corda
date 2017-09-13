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
 * \brief Signmsg example implementation.
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <dropt.h>
#include "util/buffutil.h"
#include "util/convutil.h"
#include "util/envutil.h"
#include "util/stdtypes.h"
#include "src/signmsg.h"

// Defaults
#define PROGRAM_NAME "signmsg"
#define MPRIVKEYFILE_DEFAULT "mprivkey.dat"
#define PUBKEYFILE_DEFAULT "pubkey.bin"
#define SIG_DEFAULT "sig.dat"
#define CACERT_DEFAULT "cacert.bin"
#define HASHALG_DEFAULT "SHA-512"

/// parses string to a hashalg type
static dropt_error HandleHashalg(dropt_context* context,
                                 const char* option_argument,
                                 void* handler_data) {
  dropt_error err = dropt_error_none;
  HashAlg* hashalg = handler_data;
  (void)context;
  if (option_argument == NULL) {
    *hashalg = kSha512;
  } else if (option_argument[0] == '\0') {
    err = dropt_error_insufficient_arguments;
  } else if (StringToHashAlg(option_argument, hashalg)) {
    err = dropt_error_none;
  } else {
    /* Reject the value as being inappropriate for this handler. */
    err = dropt_error_mismatch;
  }
  return err;
}

/// Main entrypoint
int main(int argc, char* argv[]) {
  // intermediate return value for C style functions
  int ret_value = EXIT_SUCCESS;

  // intermediate return value for EPID functions
  EpidStatus result = kEpidErr;

  // User Settings

  // Signature file name parameter
  static char* sig_file = NULL;

  // Message string parameter
  static char* msg_str = NULL;
  size_t msg_size = 0;

  // Basename string parameter
  static char* basename_str = NULL;
  size_t basename_size = 0;

  // SigRl file name parameter
  static char* sigrl_file = NULL;

  // Group public key file name parameter
  static char* pubkey_file = NULL;

  // Member private key file name parameter
  static char* mprivkey_file = NULL;

  // Member pre-computed settings input file name parameter
  static char* mprecmpi_file = NULL;

  // Member pre-computed settings output file name parameter
  static char* mprecmpo_file = NULL;

  // CA certificate file name parameter
  static char* cacert_file = NULL;

  // help flag parameter
  static bool show_help = false;

  // Verbose flag parameter
  static bool verbose = false;

  // Buffers and computed values

  // Signature buffer
  EpidSignature* sig = NULL;
  size_t sig_size = 0;

  // SigRl file
  unsigned char* signed_sig_rl = NULL;
  size_t signed_sig_rl_size = 0;

  // Group public key file
  unsigned char* signed_pubkey = NULL;
  size_t signed_pubkey_size = 0;

  // CA certificate
  EpidCaCertificate cacert = {0};

  // Member private key buffer
  unsigned char* mprivkey = NULL;
  size_t mprivkey_size = 0;

  // Member pre-computed settings
  MemberPrecomp member_precmp = {0};

  // Flag that Member pre-computed settings input is valid
  bool use_precmp_in;

  // Hash algorithm
  static HashAlg hashalg = kSha512;

  dropt_option options[] = {
      {'\0', "sig", "write signature to FILE (default: " SIG_DEFAULT ")",
       "FILE", dropt_handle_string, &sig_file},
      {'\0', "msg", "MESSAGE to sign", "MESSAGE", dropt_handle_string,
       &msg_str},
      {'\0', "bsn", "BASENAME to sign with (default: random)", "BASENAME",
       dropt_handle_string, &basename_str},

      {'\0', "sigrl", "load signature based revocation list from FILE", "FILE",
       dropt_handle_string, &sigrl_file},
      {'\0', "gpubkey",
       "load group public key from FILE (default: " PUBKEYFILE_DEFAULT ")",
       "FILE", dropt_handle_string, &pubkey_file},
      {'\0', "mprivkey",
       "load member private key from FILE "
       "(default:" MPRIVKEYFILE_DEFAULT ")",
       "FILE", dropt_handle_string, &mprivkey_file},
      {'\0', "mprecmpi", "load pre-computed member data from FILE", "FILE",
       dropt_handle_string, &mprecmpi_file},
      {'\0', "mprecmpo", "write pre-computed member data to FILE", "FILE",
       dropt_handle_string, &mprecmpo_file},
      {'\0', "capubkey",
       "load IoT Issuing CA public key from FILE (default: " CACERT_DEFAULT ")",
       "FILE", dropt_handle_string, &cacert_file},

      {'\0', "hashalg",
       "use specified hash algorithm (default: " HASHALG_DEFAULT ")",
       "{SHA-256 | SHA-384 | SHA-512}", HandleHashalg, &hashalg},
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
            "Create Intel(R) EPID signature of message\n"
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
        if (!pubkey_file) {
          pubkey_file = PUBKEYFILE_DEFAULT;
        }
        if (!mprivkey_file) {
          mprivkey_file = MPRIVKEYFILE_DEFAULT;
        }
        if (!cacert_file) {
          cacert_file = CACERT_DEFAULT;
        }

        if (msg_str) {
          msg_size = strlen(msg_str);
        }
        if (basename_str) {
          basename_size = strlen(basename_str);
        }
        if (verbose) {
          log_msg("\nOption values:");
          log_msg(" sig_file      : %s", sig_file);
          log_msg(" msg_str       : %s", msg_str);
          log_msg(" basename_str  : %s", basename_str);
          log_msg(" pubkey_file   : %s", pubkey_file);
          log_msg(" mprivkey_file : %s", mprivkey_file);
          log_msg(" mprecmpi_file : %s", mprecmpi_file);
          log_msg(" mprecmpo_file : %s", mprecmpo_file);
          log_msg(" hashalg       : %s", HashAlgToString(hashalg));
          log_msg(" cacert_file   : %s", cacert_file);
          log_msg("");
        }
      }
    }
    // convert command line args to usable formats

    // CA certificate
    if (0 != ReadLoud(cacert_file, &cacert, sizeof(cacert))) {
      ret_value = EXIT_FAILURE;
      break;
    }
    // Security note:
    // Application must confirm that IoT EPID Issuing CA certificate is
    // authorized by IoT EPID Root CA, e.g., signed by IoT EPID Root CA.
    if (!IsCaCertAuthorizedByRootCa(&cacert, sizeof(cacert))) {
      log_error("CA certificate is not authorized");
      ret_value = EXIT_FAILURE;
      break;
    }
    // SigRl
    if (sigrl_file) {
      if (FileExists(sigrl_file)) {
        signed_sig_rl = NewBufferFromFile(sigrl_file, &signed_sig_rl_size);
        if (!signed_sig_rl) {
          ret_value = EXIT_FAILURE;
          break;
        }

        if (0 != ReadLoud(sigrl_file, signed_sig_rl, signed_sig_rl_size)) {
          ret_value = EXIT_FAILURE;
          break;
        }
      } else {
        log_error("SigRL file %s does not exist", sigrl_file);
        ret_value = EXIT_FAILURE;
        break;
      }
    }
    // Group public key file
    signed_pubkey = NewBufferFromFile(pubkey_file, &signed_pubkey_size);
    if (!signed_pubkey) {
      ret_value = EXIT_FAILURE;
      break;
    }
    if (0 != ReadLoud(pubkey_file, signed_pubkey, signed_pubkey_size)) {
      ret_value = EXIT_FAILURE;
      break;
    }
    // Member private key
    mprivkey = NewBufferFromFile(mprivkey_file, &mprivkey_size);
    if (!mprivkey) {
      ret_value = EXIT_FAILURE;
      break;
    }
    if (mprivkey_size != sizeof(PrivKey) &&
        mprivkey_size != sizeof(CompressedPrivKey)) {
      log_error("Private Key file size is inconsistent");
      ret_value = EXIT_FAILURE;
      break;
    }
    if (0 != ReadLoud(mprivkey_file, mprivkey, mprivkey_size)) {
      ret_value = EXIT_FAILURE;
      break;
    }
    // Load Member pre-computed settings
    use_precmp_in = false;
    if (mprecmpi_file) {
      if (sizeof(MemberPrecomp) != GetFileSize(mprecmpi_file)) {
        log_error("incorrect input precomp size");
        ret_value = EXIT_FAILURE;
        break;
      }
      use_precmp_in = true;

      if (0 != ReadLoud(mprecmpi_file, &member_precmp, sizeof(MemberPrecomp))) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Signing Message:");
      log_msg("");
      log_msg(" [in]  Message Len: %d", (int)msg_size);
      log_msg(" [in]  Message: ");
      PrintBuffer(msg_str, msg_size);
      log_msg("");
      log_msg(" [in]  BaseName Len: %d", (int)basename_size);
      log_msg(" [in]  BaseName: ");
      PrintBuffer(basename_str, basename_size);
      log_msg("");
      log_msg(" [in]  SigRl Len: %d", (int)signed_sig_rl_size);
      log_msg(" [in]  SigRl: ");
      PrintBuffer(signed_sig_rl, signed_sig_rl_size);
      log_msg("");
      log_msg(" [in]  Group Public Key: ");
      PrintBuffer(signed_pubkey, signed_pubkey_size);
      log_msg("");
      log_msg(" [in]  Member Private Key: ");
      PrintBuffer(&mprivkey, sizeof(mprivkey));
      log_msg("");
      log_msg(" [in]  Hash Algorithm: %s", HashAlgToString(hashalg));
      log_msg("");
      log_msg(" [in]  IoT EPID Issuing CA Certificate: ");
      PrintBuffer(&cacert, sizeof(cacert));
      if (use_precmp_in) {
        log_msg("");
        log_msg(" [in]  Member PreComp: ");
        PrintBuffer(&member_precmp, sizeof(member_precmp));
      }
      log_msg("==============================================");
    }

    // Sign
    result = SignMsg(msg_str, msg_size, basename_str, basename_size,
                     signed_sig_rl, signed_sig_rl_size, signed_pubkey,
                     signed_pubkey_size, mprivkey, mprivkey_size, hashalg,
                     &member_precmp, use_precmp_in, &sig, &sig_size, &cacert);

    // Report Result
    if (kEpidNoErr != result) {
      if (kEpidSigRevokedInSigRl == result) {
        log_error("signature revoked in SigRL");
      } else {
        log_error("function SignMsg returned %s", EpidStatusToString(result));
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    if (sig && sig_size != 0) {
      // Store signature
      if (0 != WriteLoud(sig, sig_size, sig_file)) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Store Member pre-computed settings
    if (mprecmpo_file) {
      if (0 !=
          WriteLoud(&member_precmp, sizeof(member_precmp), mprecmpo_file)) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Success
    ret_value = EXIT_SUCCESS;
  } while (0);

  // Free allocated buffers
  if (sig) free(sig);
  if (signed_sig_rl) free(signed_sig_rl);
  if (signed_pubkey) free(signed_pubkey);
  if (mprivkey) free(mprivkey);

  dropt_free_context(dropt_ctx);

  return ret_value;
}
