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

#include "epid/common/errors.h"
#include "epid/common/types.h"
#include "epid/verifier/api.h"

#include "util/argutil.h"
#include "util/buffutil.h"
#include "util/convutil.h"
#include "util/envutil.h"
#include "src/verifysig.h"

// Defaults
#define PROGRAM_NAME ("verifysig")
#define PUBKEYFILE_DEFAULT ("pubkey.bin")
#define PRIVRL_DEFAULT NULL
#define SIGRL_DEFAULT NULL
#define GRPRL_DEFAULT ("grprl.bin")
#define VERIFIERRL_DEFAULT NULL
#define SIG_DEFAULT ("sig.dat")
#define CACERT_DEFAULT ("cacert.bin")
#define HASHALG_DEFAULT ("SHA-512")
#define VPRECMPI_DEFAULT NULL
#define VPRECMPO_DEFAULT NULL

/// Print usage message
void PrintUsage() {
  log_fmt(
      "Usage: %s [OPTION]...\n"
      "Verify signature was created by group member in good standing\n"
      "\n"
      "Options:\n"
      "\n"
      "--sig=FILE            load signature from FILE (default: %s)\n"
      "--msg=MESSAGE         MESSAGE that was signed (default: empty)\n"
      "--bsn=BASENAME        BASENAME used in signature (default: random)\n"
      "--privrl=FILE         load private key revocation list from FILE\n"
      "--sigrl=FILE          load signature based revocation list from FILE\n"
      "--grprl=FILE          load group revocation list from FILE\n"
      "                        (default: %s)\n"
      "--verifierrl=FILE     load verifier revocation list from FILE\n"
      "--gpubkey=FILE        load group public key from FILE (default: %s)\n"
      "--vprecmpi=FILE       load pre-computed verifier data from FILE\n"
      "--vprecmpo=FILE       write pre-computed verifier data to FILE\n"
      "--hashalg=NAME        SHA-256 | SHA-384 | SHA-512 (default: %s)\n"
      "--capubkey=FILE       load IoT Issuing CA public key from FILE\n"
      "                        (default: %s)\n"
      "-h,--help             display this help and exit\n"
      "-v,--verbose          print status messages to stdout\n"
      "\n",
      PROGRAM_NAME, SIG_DEFAULT, GRPRL_DEFAULT, PUBKEYFILE_DEFAULT,
      HASHALG_DEFAULT, CACERT_DEFAULT);
}

/// Main entrypoint
int main(int argc, char* argv[]) {
  // intermediate return value for C style functions
  int ret_value = EXIT_SUCCESS;
  // intermediate return value for EPID functions
  EpidStatus result = kEpidErr;
  // Temp option pointer
  char const* opt_str = 0;

  // User Settings

  // Signature file name parameter
  char const* sig_file = SIG_DEFAULT;

  // Message string parameter
  char const* msg_str = NULL;
  size_t msg_size = 0;

  // Basename string parameter
  char const* basename_str = NULL;
  size_t basename_size = 0;

  // PrivRl file name parameter
  char const* privrl_file = PRIVRL_DEFAULT;

  // SigRl file name parameter
  char const* sigrl_file = SIGRL_DEFAULT;

  // GrpRl file name parameter
  char const* grprl_file = GRPRL_DEFAULT;

  // VerRl file name parameter
  char const* verrl_file = VERIFIERRL_DEFAULT;

  // Group public key file name parameter
  char const* pubkey_file = PUBKEYFILE_DEFAULT;

  // Verifier pre-computed settings input file name parameter
  char const* vprecmpi_file = VPRECMPI_DEFAULT;

  // Verifier pre-computed settings output file name parameter
  char const* vprecmpo_file = VPRECMPO_DEFAULT;

  // Hash algorithm name parameter
  char const* hashalg_str = HASHALG_DEFAULT;

  // CA certificate file name parameter
  char const* cacert_file_name = CACERT_DEFAULT;

  // Verbose flag parameter
  bool verbose = false;

  // Buffers and computed values

  // Signature buffer
  EpidSignature* sig = NULL;
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
  VerifierPrecomp verifier_precmp = {0};

  // Flag that Verifier pre-computed settings input is valid
  bool use_precmp_in;

  // Hash algorithm
  HashAlg hashalg;

  // CA certificate
  EpidCaCertificate cacert = {0};

  // set program name for logging
  set_prog_name(PROGRAM_NAME);
  do {
    // Read command line args

    if (argc < 1) {
      PrintUsage();
      ret_value = EXIT_FAILURE;
      break;
    }

    if (CmdOptionExists(argc, argv, "--help") ||
        CmdOptionExists(argc, argv, "-h")) {
      PrintUsage();
      ret_value = EXIT_SUCCESS;
      break;
    }

    if (CmdOptionExists(argc, argv, "--verbose") ||
        CmdOptionExists(argc, argv, "-v")) {
      verbose = ToggleVerbosity();
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--sig"))) {
      sig_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--msg"))) {
      msg_str = opt_str;
      msg_size = strlen(msg_str);
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--bsn"))) {
      basename_str = opt_str;
      basename_size = strlen(basename_str);
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--privrl"))) {
      privrl_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--sigrl"))) {
      sigrl_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--grprl"))) {
      grprl_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--verifierrl"))) {
      verrl_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--gpubkey"))) {
      pubkey_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--vprecmpi"))) {
      vprecmpi_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--vprecmpo"))) {
      vprecmpo_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--hashalg"))) {
      hashalg_str = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--capubkey"))) {
      cacert_file_name = opt_str;
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

    // Load Verifier pre-computed settings
    use_precmp_in = false;
    if (vprecmpi_file) {
      if (sizeof(verifier_precmp) != GetFileSize(vprecmpi_file)) {
        log_error("incorrect input precomp size");
        ret_value = EXIT_FAILURE;
        break;
      }
      use_precmp_in = true;

      if (0 !=
          ReadLoud(vprecmpi_file, &verifier_precmp, sizeof(verifier_precmp))) {
        ret_value = EXIT_FAILURE;
        break;
      }
    }

    // Hash algorithm
    if (!StringToHashAlg(hashalg_str, &hashalg)) {
      ret_value = EXIT_FAILURE;
      break;
    }

    if (hashalg != kSha256 && hashalg != kSha384 && hashalg != kSha512) {
      log_error("unsupported hash algorithm %s", HashAlgToString(hashalg));
      ret_value = EXIT_FAILURE;
      break;
    }

    // Report Settings
    if (verbose) {
      log_msg("==============================================");
      log_msg("Verifying Message:");
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
        PrintBuffer(&verifier_precmp, sizeof(verifier_precmp));
      }
      log_msg("==============================================");
    }

    // Verify
    result = Verify(
        sig, sig_size, msg_str, msg_size, basename_str, basename_size,
        signed_priv_rl, signed_priv_rl_size, signed_sig_rl, signed_sig_rl_size,
        signed_grp_rl, signed_grp_rl_size, ver_rl, ver_rl_size, signed_pubkey,
        signed_pubkey_size, &cacert, hashalg, &verifier_precmp, use_precmp_in);

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
          WriteLoud(&verifier_precmp, sizeof(verifier_precmp), vprecmpo_file)) {
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

  return ret_value;
}
