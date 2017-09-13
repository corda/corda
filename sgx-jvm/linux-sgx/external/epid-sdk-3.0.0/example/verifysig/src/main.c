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
 * \brief Verifysig example implementation.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <dropt.h>
#include "epid/common/errors.h"
#include "epid/common/types.h"
#include "epid/common/file_parser.h"
#include "epid/verifier/api.h"
#include "epid/verifier/1.1/api.h"

#include "util/buffutil.h"
#include "util/convutil.h"
#include "util/envutil.h"
#include "src/verifysig.h"
#include "src/verifysig11.h"

// Defaults
#define PROGRAM_NAME "verifysig"
#define PUBKEYFILE_DEFAULT "pubkey.bin"
#define PRIVRL_DEFAULT NULL
#define SIGRL_DEFAULT NULL
#define GRPRL_DEFAULT "grprl.bin"
#define VERIFIERRL_DEFAULT NULL
#define SIG_DEFAULT "sig.dat"
#define CACERT_DEFAULT "cacert.bin"
#define HASHALG_DEFAULT "SHA-512"
#define UNPARSED_HASHALG (kInvalidHashAlg)
#define VPRECMPI_DEFAULT NULL
#define VPRECMPO_DEFAULT NULL

/// parses string to a hashalg type
static dropt_error HandleHashalg(dropt_context* context,
                                 const char* option_argument,
                                 void* handler_data) {
  dropt_error err = dropt_error_none;
  HashAlg* hashalg = handler_data;
  (void)context;
  if (option_argument == NULL) {
    *hashalg = UNPARSED_HASHALG;
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
  static char* sig_file = SIG_DEFAULT;

  // Message string parameter
  static char* msg_str = NULL;
  size_t msg_size = 0;

  // Basename string parameter
  static char* basename_str = NULL;
  size_t basename_size = 0;

  // PrivRl file name parameter
  static char* privrl_file = NULL;

  // SigRl file name parameter
  static char* sigrl_file = NULL;

  // GrpRl file name parameter
  static char* grprl_file = NULL;

  // VerRl file name parameter
  static char* verrl_file = NULL;

  // Group public key file name parameter
  static char* pubkey_file = NULL;

  // Verifier pre-computed settings input file name parameter
  static char* vprecmpi_file = NULL;

  // Verifier pre-computed settings output file name parameter
  static char* vprecmpo_file = NULL;

  // CA certificate file name parameter
  static char* cacert_file_name = NULL;

  // Verbose flag parameter
  static bool verbose = false;

  // help flag parameter
  static bool show_help = false;

  // Buffers and computed values

  // Signature buffer
  void* sig = NULL;
  size_t sig_size = 0;

  // PrivRl buffer
  void* signed_priv_rl = NULL;
  size_t signed_priv_rl_size = 0;

  // SigRl buffer
  void* signed_sig_rl = NULL;
  size_t signed_sig_rl_size = 0;

  // GrpRl buffer
  void* signed_grp_rl = NULL;
  size_t signed_grp_rl_size = 0;

  // VerRl buffer
  VerifierRl* ver_rl = NULL;
  size_t ver_rl_size = 0;

  // Group public key buffer
  void* signed_pubkey = NULL;
  size_t signed_pubkey_size = 0;

  // Verifier pre-computed settings
  void* verifier_precmp = NULL;
  size_t verifier_precmp_size = 0;
  size_t vprecmpi_file_size = 0;

  // Flag that Verifier pre-computed settings input is valid
  bool use_precmp_in;

  // CA certificate
  EpidCaCertificate cacert = {0};
  // Hash algorithm
  static HashAlg hashalg = UNPARSED_HASHALG;

  dropt_option options[] = {
      {'\0', "sig", "load signature from FILE (default: " SIG_DEFAULT ")",
       "FILE", dropt_handle_string, &sig_file},
      {'\0', "msg", "MESSAGE that was signed (default: empty)", "MESSAGE",
       dropt_handle_string, &msg_str},
      {'\0', "bsn", "BASENAME used in signature (default: random)", "BASENAME",
       dropt_handle_string, &basename_str},
      {'\0', "privrl", "load private key revocation list from FILE", "FILE",
       dropt_handle_string, &privrl_file},
      {'\0', "sigrl", "load signature based revocation list from FILE", "FILE",
       dropt_handle_string, &sigrl_file},
      {'\0', "grprl",
       "load group revocation list from FILE\n (default: " GRPRL_DEFAULT ")",
       "FILE", dropt_handle_string, &grprl_file},
      {'\0', "verifierrl", "load verifier revocation list from FILE", "FILE",
       dropt_handle_string, &verrl_file},
      {'\0', "gpubkey",
       "load group public key from FILE (default: " PUBKEYFILE_DEFAULT ")",
       "FILE", dropt_handle_string, &pubkey_file},
      {'\0', "vprecmpi", "load pre-computed verifier data from FILE", "FILE",
       dropt_handle_string, &vprecmpi_file},
      {'\0', "vprecmpo", "write pre-computed verifier data to FILE", "FILE",
       dropt_handle_string, &vprecmpo_file},
      {'\0', "capubkey",
       "load IoT Issuing CA public key from FILE\n (default: " CACERT_DEFAULT
       ")",
       "FILE", dropt_handle_string, &cacert_file_name},
      {'\0', "hashalg",
       "use specified hash algorithm for 2.0 groups "
       "(default: " HASHALG_DEFAULT ")",
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
    EpidVersion epid_version = kNumEpidVersions;
    // Read command line args

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
            "Verify signature was created by group member in good standing\n"
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
        if (!sig_file) sig_file = SIG_DEFAULT;
        if (!grprl_file) grprl_file = GRPRL_DEFAULT;
        if (!pubkey_file) pubkey_file = PUBKEYFILE_DEFAULT;
        if (!cacert_file_name) cacert_file_name = CACERT_DEFAULT;
        if (msg_str) msg_size = strlen(msg_str);
        if (basename_str) basename_size = strlen(basename_str);

        if (verbose) {
          log_msg("\nOption values:");
          log_msg(" sig_file      : %s", sig_file);
          log_msg(" msg_str       : %s", msg_str);
          log_msg(" basename_str  : %s", basename_str);
          log_msg(" privrl_file   : %s", privrl_file);
          log_msg(" sigrl_file   : %s", sigrl_file);
          log_msg(" grprl_file   : %s", grprl_file);
          log_msg(" verrl_file : %s", verrl_file);
          log_msg(" vprecmpi_file : %s", vprecmpi_file);
          log_msg(" vprecmpo_file : %s", vprecmpo_file);
          log_msg(" hashalg       : %s", (UNPARSED_HASHALG == hashalg)
                                             ? "(default)"
                                             : HashAlgToString(hashalg));
          log_msg(" cacert_file_name   : %s", cacert_file_name);
          log_msg("");
        }
      }
    }
    // convert command line args to usable formats

    // Signature
    sig = NewBufferFromFile(sig_file, &sig_size);
    if (!sig) {
      ret_value = EXIT_FAILURE;
      break;
    }

    // PrivRl
    if (privrl_file) {
      signed_priv_rl = NewBufferFromFile(privrl_file, &signed_priv_rl_size);
      if (!signed_priv_rl) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // SigRl
    if (sigrl_file) {
      signed_sig_rl = NewBufferFromFile(sigrl_file, &signed_sig_rl_size);
      if (!signed_sig_rl) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // GrpRl
    signed_grp_rl = NewBufferFromFile(grprl_file, &signed_grp_rl_size);
    if (!signed_grp_rl) {
      ret_value = EXIT_FAILURE;
      break;
    }
    // VerRl
    if (verrl_file) {
      ver_rl = (VerifierRl*)NewBufferFromFile(verrl_file, &ver_rl_size);
      if (!ver_rl) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Group public key
    signed_pubkey = NewBufferFromFile(pubkey_file, &signed_pubkey_size);
    if (!signed_pubkey) {
      ret_value = EXIT_FAILURE;
      break;
    }

    // CA certificate
    if (0 != ReadLoud(cacert_file_name, &cacert, sizeof(cacert))) {
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

    // Detect EPID version
    result = EpidParseFileHeader(signed_pubkey, signed_pubkey_size,
                                 &epid_version, NULL);
    if (kEpidNoErr != result || kNumEpidVersions <= epid_version) {
      log_error("EPID version can not be detected");
      ret_value = EXIT_FAILURE;
      break;
    }

    // Configure hashalg based on group
    if (kEpid1x == epid_version) {
      if (!(kSha256 == hashalg || UNPARSED_HASHALG == hashalg)) {
        log_error(
            "unsupported hash algorithm: %s only supported for 2.0 groups",
            HashAlgToString(hashalg));
        ret_value = EXIT_FAILURE;
        break;
      }
    } else {
      if (UNPARSED_HASHALG == hashalg) {
        hashalg = kSha512;
      }
    }

    // Load Verifier pre-computed settings
    if (kEpid1x == epid_version) {
      verifier_precmp_size = sizeof(Epid11VerifierPrecomp);
    } else if (kEpid2x == epid_version) {
      verifier_precmp_size = sizeof(VerifierPrecomp);
    } else {
      log_error("EPID version %s is not supported",
                EpidVersionToString(epid_version));
      ret_value = EXIT_FAILURE;
      break;
    }
    verifier_precmp = AllocBuffer(verifier_precmp_size);
    use_precmp_in = false;
    if (vprecmpi_file) {
      vprecmpi_file_size = GetFileSize(vprecmpi_file);
      if (verifier_precmp_size != vprecmpi_file_size) {
        if (kEpid2x == epid_version &&
            vprecmpi_file_size == verifier_precmp_size - sizeof(GroupId)) {
          log_error(
              "incorrect input precomp size: precomp format may have changed, "
              "try regenerating it");
        } else {
          log_error("incorrect input precomp size");
        }
        ret_value = EXIT_FAILURE;
        break;
      }
      use_precmp_in = true;

      if (0 != ReadLoud(vprecmpi_file, verifier_precmp, verifier_precmp_size)) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Verifying Message:");
      log_msg("");
      log_msg(" [in]  EPID version: %s", EpidVersionToString(epid_version));
      log_msg("");
      log_msg(" [in]  Signature Len: %d", (int)sig_size);
      log_msg(" [in]  Signature: ");
      PrintBuffer(sig, sig_size);
      log_msg("");
      log_msg(" [in]  Message Len: %d", (int)msg_size);
      log_msg(" [in]  Message: ");
      PrintBuffer(msg_str, msg_size);
      log_msg("");
      log_msg(" [in]  BaseName Len: %d", (int)basename_size);
      log_msg(" [in]  BaseName: ");
      PrintBuffer(basename_str, basename_size);
      log_msg("");
      log_msg(" [in]  PrivRl Len: %d", (int)signed_priv_rl_size);
      log_msg(" [in]  PrivRl: ");
      PrintBuffer(signed_priv_rl, signed_priv_rl_size);
      log_msg("");
      log_msg(" [in]  SigRl Len: %d", (int)signed_sig_rl_size);
      log_msg(" [in]  SigRl: ");
      PrintBuffer(signed_sig_rl, signed_sig_rl_size);
      log_msg("");
      log_msg(" [in]  GrpRl Len: %d", (int)signed_grp_rl_size);
      log_msg(" [in]  GrpRl: ");
      PrintBuffer(signed_grp_rl, signed_grp_rl_size);
      log_msg("");
      log_msg(" [in]  VerRl Len: %d", (int)ver_rl_size);
      log_msg(" [in]  VerRl: ");
      PrintBuffer(ver_rl, ver_rl_size);
      log_msg("");
      log_msg(" [in]  Group Public Key: ");
      PrintBuffer(signed_pubkey, sizeof(signed_pubkey_size));
      log_msg("");
      log_msg(" [in]  Hash Algorithm: %s", HashAlgToString(hashalg));
      if (use_precmp_in) {
        log_msg("");
        log_msg(" [in]  Verifier PreComp: ");
        PrintBuffer(verifier_precmp, verifier_precmp_size);
      }
      log_msg("==============================================");
    }

    // Verify
    if (kEpid2x == epid_version) {
      result =
          Verify(sig, sig_size, msg_str, msg_size, basename_str, basename_size,
                 signed_priv_rl, signed_priv_rl_size, signed_sig_rl,
                 signed_sig_rl_size, signed_grp_rl, signed_grp_rl_size, ver_rl,
                 ver_rl_size, signed_pubkey, signed_pubkey_size, &cacert,
                 hashalg, (VerifierPrecomp*)verifier_precmp, use_precmp_in);
    } else if (kEpid1x == epid_version) {
      result = Verify11(sig, sig_size, msg_str, msg_size, basename_str,
                        basename_size, signed_priv_rl, signed_priv_rl_size,
                        signed_sig_rl, signed_sig_rl_size, signed_grp_rl,
                        signed_grp_rl_size, signed_pubkey, signed_pubkey_size,
                        &cacert, (Epid11VerifierPrecomp*)verifier_precmp,
                        use_precmp_in);
    } else {
      log_error("EPID version %s is not supported",
                EpidVersionToString(epid_version));
      ret_value = EXIT_FAILURE;
      break;
    }
    // Report Result
    if (kEpidNoErr == result) {
      log_msg("signature verified successfully");
    } else {
      log_error("signature verification failed: %s",
                EpidStatusToString(result));
      ret_value = result;
      break;
    }

    // Store Verifier pre-computed settings
    if (vprecmpo_file) {
      if (0 !=
          WriteLoud(verifier_precmp, verifier_precmp_size, vprecmpo_file)) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Success
    ret_value = EXIT_SUCCESS;
  } while (0);

  // Free allocated buffers
  if (sig) free(sig);
  if (signed_priv_rl) free(signed_priv_rl);
  if (signed_sig_rl) free(signed_sig_rl);
  if (signed_grp_rl) free(signed_grp_rl);
  if (ver_rl) free(ver_rl);
  if (signed_pubkey) free(signed_pubkey);
  if (verifier_precmp) free(verifier_precmp);

  dropt_free_context(dropt_ctx);

  return ret_value;
}
