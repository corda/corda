#!/usr/bin/env python
#
# Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
#   * Redistributions of source code must retain the above copyright
#     notice, this list of conditions and the following disclaimer.
#   * Redistributions in binary form must reproduce the above copyright
#     notice, this list of conditions and the following disclaimer in
#     the documentation and/or other materials provided with the
#     distribution.
#   * Neither the name of Intel Corporation nor the names of its
#     contributors may be used to endorse or promote products derived
#     from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#
#

import gdb

ENABLE_EMMT = 0
TC_PREFIX = None
TC_PREFIX_DONE = False

class enable_emmt (gdb.Command):
    def __init__ (self):
        gdb.Command.__init__ (self, "enable sgx_emmt", gdb.COMMAND_RUNNING)

    def invoke (self, arg, from_tty):
        global ENABLE_EMMT
        ENABLE_EMMT = 1

class disable_emmt (gdb.Command):
    def __init__ (self):
        gdb.Command.__init__ (self, "disable sgx_emmt", gdb.COMMAND_RUNNING)

    def invoke (self, arg, from_tty):
        global ENABLE_EMMT
        ENABLE_EMMT = 0

class show_emmt (gdb.Command):
    def __init__ (self):
        gdb.Command.__init__ (self, "show sgx_emmt", gdb.COMMAND_RUNNING)

    def invoke (self, arg, from_tty):
        global ENABLE_EMMT
        if ENABLE_EMMT == 1:
            print ("sgx_emmt enabled")
        if ENABLE_EMMT == 0:
            print ("sgx_emmt disabled")

class set_tc_prefix(gdb.Command):
    def __init__ (self):
        gdb.Command.__init__ (self, "set_tc_prefix", gdb.COMMAND_NONE)

    def invoke (self, arg, from_tty):
        global TC_PREFIX, TC_PREFIX_DONE
        #For internal use, and don't allow input by gdb user
        if TC_PREFIX_DONE == True:
            return
        TC_PREFIX = arg
        TC_PREFIX_DONE = True

class get_tc_prefix(gdb.Command):
    def __init__ (self):
        gdb.Command.__init__ (self, "get_tc_prefix", gdb.COMMAND_NONE)

    def invoke (self, arg, from_tty):
        global TC_PREFIX
        #For internal use, and don't allow output to tty
        if from_tty == True:
            return
        print (TC_PREFIX)

def init_emmt():
    enable_emmt()
    disable_emmt()
    show_emmt()
    set_tc_prefix()
    get_tc_prefix()
