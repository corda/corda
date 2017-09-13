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

try:
    from cStringIO import StringIO
except ImportError:
    from io import StringIO

import traceback, errno, string, re, sys, time, readelf;

def GetLoadSymbolCommand(EnclaveFile, Base):
    text = readelf.ReadElf(EnclaveFile)
    if text == None:
        return -1
    SegsFile = StringIO(text)

    try:
        FileList = SegsFile.readlines()
        n=4;
        m=100;
        Out = [[[] for ni in range(n)] for mi in range(m)]
        i=0;
        Out[99][2] = '0';
        # Parse the readelf output file to extract the section names and
        # their offsets and add the Proj base address.
        for line in FileList:
            list = line.split();
            if(len(list) > 0):
                SegOffset = -1;
                # The readelf will put a space after the open bracket for single
                # digit section numbers.  This causes the line.split to create
                # an extra element in the array for these lines.
                if(re.match('\[\s*[0-9]+\]',list[0])):
                    SegOffset = 0;
                if(re.match('\s*[0-9]+\]',list[1])):
                    SegOffset = 1;

                if(SegOffset != -1):
                    if (list[SegOffset+1][0] == '.'):
                        # If it is the .text section, put it in a special place in the array
                        # because the 'add-symbol-file' command treats it differently.
                        #print "%#08x" % (int(list[SegOffset+3], 16))
                        if(list[SegOffset+1].find(".text") != -1):
                            Out[99][0] = "-s";
                            Out[99][1] = list[SegOffset+1];
                            Out[99][2] = str(int(list[SegOffset+3], 16) + int(Base, 10));
                            Out[99][3] = " ";
                        elif(int(list[SegOffset+3], 16) != 0):
                            Out[i][0] = "-s";
                            Out[i][1] = list[SegOffset+1];
                            Out[i][2] = str(int(list[SegOffset+3], 16) + int(Base, 10));
                            Out[i][3] = " ";
                            i = i+1;
        if('0' != Out[99][2]):
            # The last section must not have the '\' line continuation character.
            Out[i-1][3] = '';

            # Write the GDB 'add-symbol-file' command with all the arguments to the setup GDB command file.
            # Note: The mandatory argument for the 'add-symbol-file' command is the .text section without a
            # '-s .SectionName'.  All other sections need the '-s .SectionName'.
            gdbcmd = "add-symbol-file '" + EnclaveFile + "' " + '%(Location)#08x' % {'Location':int(Out[99][2])} + " -readnow "
            for j in range(i):
                gdbcmd += Out[j][0] + " " + Out[j][1] + " " + '%(Location)#08x' % {'Location' : int(Out[j][2])} + " " + Out[j][3]
        else:
            return -1

        return gdbcmd

    except:
        print ("Error parsing enclave file.  Check format of file.")
        return -1

def GetUnloadSymbolCommand(EnclaveFile, Base):
    text = readelf.ReadElf(EnclaveFile)
    if text == None:
        return -1
    SegsFile = StringIO(text)

    try:
        FileList = SegsFile.readlines()
        # Parse the readelf output file to extract the section names and
        # their offsets and add the Proj base address.
        for line in FileList:
            list = line.split();
            if(len(list) > 0):
                SegOffset = -1;
                # The readelf will put a space after the open bracket for single
                # digit section numbers.  This causes the line.split to create
                # an extra element in the array for these lines.
                if(re.match('\[\s*[0-9]+\]',list[0])):
                    SegOffset = 0;
                if(re.match('\s*[0-9]+\]',list[1])):
                    SegOffset = 1;

                if(SegOffset != -1):
                    if (list[SegOffset+1][0] == '.'):
                        # If it is the .text section, get the .text start address and plus enclave start address
                        if(list[SegOffset+1].find(".text") != -1):
                            return "remove-symbol-file -a " + str(int(list[SegOffset+3], 16) + int(Base, 10))

    except:
        print ("Error parsing enclave file.  Check format of file.")
        return -1
