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

#include "util/argutil.h"
#include "util/buffutil.h"
#include "util/convutil.h"
#include "util/envutil.h"
#include "util/stdtypes.h"
#include "src/signmsg.h"

// Defaults

#define PROGRAM_NAME ("signmsg")
#define MPRIVKEYFILE_DEFAULT ("mprivkey.dat")
#define PUBKEYFILE_DEFAULT ("pubkey.bin")
#define SIGRL_DEFAULT (NULL)
#define SIG_DEFAULT ("sig.dat")
#define CACERT_DEFAULT ("cacert.bin")
#define HASHALG_DEFAULT ("SHA-512")
#define MPRECMPI_DEFAULT NULL
#define MPRECMPO_DEFAULT NULL

/// Print usage message
void PrintUsage() {
  log_fmt(
      "Usage: %s [OPTION]...\n"
      "Create Intel(R) EPID signature of message\n"
      "\n"
      "Options:\n"
      "\n"
      "--sig=FILE            write signature to FILE (default: %s)\n"
      "--msg=MESSAGE         MESSAGE to sign\n"
      "--bsn=BASENAME        BASENAME to sign with (default: random)\n"
      "--sigrl=FILE          load signature based revocation list from FILE\n"
      "--gpubkey=FILE        load group public key from FILE\n"
      "                        (default: %s)\n"
      "--mprivkey=FILE       load member private key from FILE\n"
      "                        (default: %s)\n"
      "--mprecmpi=FILE       load pre-computed member data from FILE\n"
      "--mprecmpo=FILE       write pre-computed member data to FILE\n"
      "--hashalg=NAME        SHA-256 | SHA-384 | SHA-512 (default: %s)\n"
      "--capubkey=FILE       load IoT Issuing CA public key from FILE\n"
      "                        (default: %s)\n"
      "-h,--help             display this help and exit\n"
      "-v,--verbose          print status messages to stdout\n"
      "\n",
      PROGRAM_NAME, SIG_DEFAULT, PUBKEYFILE_DEFAULT, MPRIVKEYFILE_DEFAULT,
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

  // SigRl file name parameter
  char const* sigrl_file = SIGRL_DEFAULT;

  // Group public key file name parameter
  char const* pubkey_file = PUBKEYFILE_DEFAULT;

  // Member private key file name parameter
  char const* mprivkey_file = MPRIVKEYFILE_DEFAULT;

  // Member pre-computed settings input file name parameter
  char const* mprecmpi_file = MPRECMPI_DEFAULT;

  // Member pre-computed settings output file name parameter
  char const* mprecmpo_file = MPRECMPO_DEFAULT;

  // Hash algorithm name parameter
  char const* hashalg_str = HASHALG_DEFAULT;

  // CA certificate file name parameter
  char const* cacert_file = CACERT_DEFAULT;

  // Verbose flag parameter
  bool verbose = false;

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
  HashAlg hashalg;

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

    if (0 != (opt_str = GetCmdOption(argc, argv, "--sigrl"))) {
      sigrl_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--gpubkey"))) {
      pubkey_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--mprivkey"))) {
      mprivkey_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--mprecmpi"))) {
      mprecmpi_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--mprecmpo"))) {
      mprecmpo_file = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--hashalg"))) {
      hashalg_str = opt_str;
    }

    if (0 != (opt_str = GetCmdOption(argc, argv, "--capubkey"))) {
      cacert_file = opt_str;
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
      if (kEpidSigRevokedinSigRl == result) {
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

  return ret_value;
}
