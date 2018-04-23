############################################################################
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
############################################################################

"""use scons -k to invoke all builds regardless of unit test failures
"""
import string
import sys
import SCons.Script
import os.path
from parts import *

print "**************** TOOLS ****************"
print '* Python Version:', string.split(sys.version, " ", 1)[0]
print '* SCons  Version:', SCons.__version__
print '* Parts  Version:', PartsExtensionVersion()
print "***************************************"

def PrintCompilerVersion(env):
    """
    Function to print version of compilers used for build
    Args:
      env: Environment to get compilers version
    """
    res = ''
    if 'INTELC_VERSION' in env:
        res += 'ICC ' +  env['INTELC_VERSION'] + ';'
    if 'MSVC_VERSION' in env:
        res += 'MS ' + env['MSVC_VERSION'] + ';'
    if 'GXX_VERSION' in env:
        res += 'GXX ' + env['GXX_VERSION'] + ';'
    if 'GCC_VERSION' in env:
        res += 'GCC ' + env['GCC_VERSION'] + ';'
    print 'Compiler Version: ', res

def include_parts(part_list, **kwargs):
    for parts_file in part_list:
        if os.path.isfile(DefaultEnvironment().subst(parts_file)):
            Part(parts_file=parts_file, **kwargs)

######## Part groups ####################################################
ipp_parts = ['ext/ipp/ippcp.parts',
             'ext/ipp/ippcpepid.parts',
             'ext/ipp/ippcommon.parts']
utest_parts = ['ext/gtest/gtest.parts',
               'epid/common-testhelper/common-testhelper.parts']
common_parts = ['epid/common/common.parts']
member_parts = ['epid/member/member.parts']
verifier_parts = ['epid/verifier/verifier.parts']
util_parts = ['example/util/util.parts']
example_parts = ['ext/dropt/dropt.parts',
                 'example/verifysig/verifysig.parts',
                 'example/signmsg/signmsg.parts',
                 'example/data/data.parts',
                 'example/compressed_data/compressed_data.parts']
tools_parts = ['tools/revokegrp/revokegrp.parts',
               'tools/revokekey/revokekey.parts',
               'tools/revokesig/revokesig.parts',
               'tools/extractkeys/extractkeys.parts',
               'tools/extractgrps/extractgrps.parts']
testbot_test_parts = ['test/testbot/testbot.parts',
                      'test/testbot/signmsg/signmsg_testbot.parts',
                      'test/testbot/verifysig/verifysig_testbot.parts',
                      'test/testbot/integration/integration_testbot.parts',
                      'test/testbot/ssh_remote/ssh_remote_testbot.parts',
                      'test/testbot/revokegrp/revokegrp_testbot.parts',
                      'test/testbot/revokekey/revokekey_testbot.parts',
                      'test/testbot/revokesig/revokesig_testbot.parts',
                      'test/testbot/extractkeys/extractkeys_testbot.parts',
                      'test/testbot/extractgrps/extractgrps_testbot.parts']
package_parts = ['ext/gtest/gtest.parts',
                 'ext/ipp/ippcommon.parts',
                 'ext/ipp/ippcp.parts',
                 'ext/ipp/ippcpepid.parts',
                 'package.parts']
internal_tools_parts = ['ext/dropt/dropt.parts',
                        'tools/ikgfwrapper/ikgfwrapper.parts']
######## End Part groups ###############################################
######## Commandline option setup #######################################
product_variants = [
    'production',
    'internal-test',
    'package-epid-sdk',
    'internal-tools'
]

default_variant = 'production'

def is_production():
    return GetOption("product-variant") == 'production'

def is_internal_test():
    return GetOption("product-variant") == 'internal-test'

def is_internal_tools():
    return GetOption("product-variant") == 'internal-tools'

def is_package():
    return GetOption("product-variant") == 'package-epid-sdk'

def use_commercial_ipp():
    return GetOption("use-commercial-ipp")

def variant_dirname():
    s = GetOption("product-variant")
    if s == 'production':
        return 'epid-sdk'
    elif s == 'package-epid-sdk':
        return 'epid-sdk'
    else:
        return s

AddOption("--product-variant", "--prod-var", nargs=1,
          help=("Select product variant to build. Possible "
                "options are: {0}. The default is {1} if no option "
                "is specified").format(", ".join(product_variants),
                                       default_variant),
          action='store', dest='product-variant', type='choice',
          choices=product_variants, default=default_variant)

AddOption("--use-commercial-ipp",
          help=("Link with commercial IPP. The IPPROOT environment variable "
                "must be set."),
          action='store_true', dest='use-commercial-ipp',
          default=False)

SetOptionDefault("PRODUCT_VARIANT", variant_dirname())

######## End Commandline option setup ###################################


# fix for parts 0.10.8 until we get better logic to extract ${CC}
SetOptionDefault('PARTS_USE_SHORT_TOOL_NAMES', 1)

def set_default_production_options():
    SetOptionDefault('TARGET_PLATFORM', 'x86_64')
    SetOptionDefault('CONFIG', 'release')

    SetOptionDefault('TARGET_VARIANT', '${TARGET_OS}-${TARGET_ARCH}')

    SetOptionDefault('INSTALL_ROOT',
                     '#_install/${PRODUCT_VARIANT}')

    SetOptionDefault('INSTALL_TOOLS_BIN',
                     '$INSTALL_ROOT/tools')

    SetOptionDefault('INSTALL_SAMPLE_BIN',
                     '$INSTALL_ROOT/example')

    SetOptionDefault('INSTALL_EPID_INCLUDE',
                     '$INSTALL_ROOT/include/epid')

    SetOptionDefault('INSTALL_IPP_INCLUDE',
                     '$INSTALL_ROOT/include/ext/ipp/include')

    SetOptionDefault('INSTALL_TEST_BIN',
                     '$INSTALL_ROOT/test')

    SetOptionDefault('INSTALL_LIB',
                     '$INSTALL_ROOT/lib/${TARGET_VARIANT}')

    SetOptionDefault('INSTALL_SAMPLE_DATA',
                     '$INSTALL_ROOT/example')

    SetOptionDefault('INSTALL_TOOLS_DATA',
                     '$INSTALL_ROOT/tools')

    SetOptionDefault('PACKAGE_DIR',
                     '#_package')

    SetOptionDefault('PACKAGE_ROOT',
                     '#_package/${PRODUCT_VARIANT}')

    SetOptionDefault('ROOT',
                     '#')

    SetOptionDefault('PACKAGE_NAME',
                     '{PRODUCT_VARIANT}')

if is_production():
    set_default_production_options()
    ipp_mode = ['install_lib']
    if use_commercial_ipp():
        ipp_mode.append('use_commercial_ipp')
    include_parts(ipp_parts, mode=ipp_mode,
                  INSTALL_INCLUDE='${INSTALL_IPP_INCLUDE}')
    include_parts(utest_parts + common_parts +
                  member_parts + verifier_parts,
                  mode=['install_lib'],
                  INSTALL_INCLUDE='${INSTALL_EPID_INCLUDE}')
    include_parts(util_parts + example_parts,
                  INSTALL_INCLUDE='${INSTALL_EPID_INCLUDE}',
                  INSTALL_BIN='${INSTALL_SAMPLE_BIN}',
                  INSTALL_DATA='${INSTALL_SAMPLE_DATA}')
    include_parts(tools_parts,
                  INSTALL_BIN='${INSTALL_TOOLS_BIN}',
                  INSTALL_DATA='${INSTALL_TOOLS_DATA}')
    PrintCompilerVersion(DefaultEnvironment())
    Default('all')
    Default('run_utest::')

if is_internal_test():
    set_default_production_options()
    include_parts(ipp_parts)
    include_parts(utest_parts + common_parts +
                  member_parts + verifier_parts)
    include_parts(util_parts + example_parts,
                  INSTALL_BIN='${INSTALL_SAMPLE_BIN}',
                  INSTALL_DATA='${INSTALL_SAMPLE_DATA}')
    include_parts(tools_parts, INSTALL_BIN='${INSTALL_TOOLS_BIN}')
    include_parts(testbot_test_parts)
    Default('all')

if is_internal_tools():
    set_default_production_options()
    include_parts(ipp_parts + utest_parts + common_parts + util_parts)
    include_parts(internal_tools_parts, INSTALL_BIN='${INSTALL_TOOLS_BIN}')
    Default('ikgfwrapper')

if is_package():
    set_default_production_options()
    include_parts(package_parts,
                  mode=['install_package'],
                  INSTALL_TOP_LEVEL='${PACKAGE_ROOT}')
    Default('package')
