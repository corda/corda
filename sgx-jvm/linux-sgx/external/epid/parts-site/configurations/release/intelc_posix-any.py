# INTEL CORPORATION PROPRIETARY INFORMATION
# This software is supplied under the terms of a license agreement or
# nondisclosure agreement with Intel Corporation and may not be copied or
# disclosed except in accordance with the terms of that agreement
# Copyright(c) 2016 Intel Corporation. All Rights Reserved.

""" Defines build configuration for Parts """

from parts.config import *


def map_default_version(env):
    return env['INTELC_VERSION']


config = configuration(map_default_version)

config.VersionRange("7-*",
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
                                ],
                        CXXFLAGS=['',
                                  # modern C++ features support
                                  '-std=c++0x',
                                 ],
                        CPPDEFINES=['NDEBUG']
                        )
                    )
