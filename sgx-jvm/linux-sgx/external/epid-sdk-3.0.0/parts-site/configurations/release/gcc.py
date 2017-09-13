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
# pylint: disable=locally-disabled, invalid-name, missing-docstring

"""gcc compiler configuration for release
"""
from parts.config import ConfigValues, configuration

def map_default_version(env):
    return env['GCC_VERSION']

config = configuration(map_default_version)

config.VersionRange("3-*",
                    append=ConfigValues(
                        CCFLAGS=['',
                                 # second level optimization
                                 '-O2',
                                 # treat warnings as errors
                                 '-Werror',
                                 # enable all warnings
                                 '-Wall',
                                 # extra warnings
                                 '-Wextra',
                                 # pedantic warnings
                                 # '-Wpedantic',
                                 # disable warnings due to gcc 4.8.5 issues
                                 '-Wno-missing-braces',
                                 '-Wno-missing-field-initializers',
                                 '-Wno-unknown-pragmas',
                                 '-Wno-unused-function',
                                 # do not assume strict aliasing
                                 '-fno-strict-aliasing',
                                 # do not warn about unused but set variables
                                 '-Wno-unused-but-set-variable',
                                 # do not warn about multiline comments
                                 '-Wno-comment',
                                ],
                        CPPDEFINES=['NDEBUG'],
                    )
                   )
