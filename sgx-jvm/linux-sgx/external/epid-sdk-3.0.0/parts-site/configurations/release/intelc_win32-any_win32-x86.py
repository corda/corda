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

"""Intel win32 compiler configuration for release
"""
from parts.config import ConfigValues, configuration

def map_default_version(env):
    return env['INTELC_VERSION']

config = configuration(map_default_version)

config.VersionRange("7-*",
                    append=ConfigValues(
                        CCFLAGS=['/MP',
                                 '/GS',
                                 '/W4',
                                 '/Gy',
                                 '/Zc:wchar_t',
                                 '/Z7',
                                 '/O2',
                                 '/fp:precise',
                                 '/WX',
                                 '/Zc:forScope',
                                 '/Gd',
                                 '/Oi',
                                 '/MT',
                                 '/nologo'],
                        CXXFLAGS=['/EHsc',
                                  '/GR'],
                        LINKFLAGS=['/LTCG',
                                   '/WX',
                                   '/SAFESEH',
                                   '/NXCOMPAT',
                                   '/DYNAMICBASE',
                                   '/nologo'],
                        CPPDEFINES=['NDEBUG']
                    )
                   )
