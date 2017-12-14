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

from __future__ import print_function
import gdb
import struct
import os.path
from ctypes import create_string_buffer
import load_symbol_cmd
import sgx_emmt
import ctypes

# Calculate the bit mode of current debuggee project
SIZE = gdb.parse_and_eval("sizeof(long)")

ET_SIM = 0x1
ET_DEBUG = 0x2
PAGE_SIZE = 0x1000
KB_SIZE = 1024
# The following definitions should strictly align with the structure of
# debug_enclave_info_t in uRTS.
# Here we only care about the first 7 items in the structure.
# pointer: next_enclave_info, start_addr, tcs_list, lpFileName,
#          g_peak_heap_used_addr
# int32_t: enclave_type, file_name_size
ENCLAVE_INFO_SIZE = 5 * 8 + 2 * 4
INFO_FMT = 'QQQIIQQ'
ENCLAVES_ADDR = {}

# The following definitions should strictly align with the struct of
# tcs_t
# Here we only care about the first 8 items in the structure
# uint64_t: state, flags, ossa, oentry, aep, ofs_base
# uint32_t: nssa, cssa
ENCLAVE_TCS_INFO_SIZE = 6*8 + 2*4
TCS_INFO_FMT = 'QQQIIQQQ'

def get_inferior():
    """Get current inferior"""
    try:
        if len(gdb.inferiors()) == 0:
            print ("No gdb inferior could be found.")
            return -1
        else:
            inferior = gdb.inferiors()[0]
            return inferior
    except AttributeError:
        print ("This gdb's python support is too old, please update first.")
        exit()

def read_from_memory(addr, size):
    """Read data with specified size  from the specified meomory"""
    inferior = get_inferior()
    # actually we can check the addr more securely
    # ( check the address is inside the enclave)
    if inferior == -1 or addr == 0:
        print ("Error happens in read_from_memory: addr = {0:x}".format(int(addr)))
        return None
    try:
        string = inferior.read_memory(addr, size)
        return string
    except gdb.MemoryError:
        print ("Can't access memory at {0:x}.".format(int(addr)))
        return None

def write_to_memory(addr, buf):
    """Write a specified buffer to the specified memory"""
    inferior = get_inferior()
    if inferior == -1 or addr == 0:
        print ("Error happens in write_to_memory: addr = {0:x}".format(int(addr)))
        return -1
    try:
        inferior.write_memory(addr, buf)
        return 0
    except gdb.MemoryError:
        print ("Can't access memory at {0:x}.".format(int(addr)))
        return -1

def target_path_to_host_path(target_path):
    so_name = os.path.basename(target_path)
    strpath = gdb.execute("show solib-search-path", False, True)
    path = strpath.split()[-1]
    strlen = len(path)
    if strlen != 1:
        path = path[0:strlen-1]
    host_path = path + "/" + so_name
    #strlen = len(host_path)
    #host_path = host_path[0:strlen-7]
    return host_path

class enclave_info(object):
    """Class to contain the enclave inforation,
    such as start address, stack addresses, stack size, etc.
    The enclave information is for one enclave."""
    def __init__(self, _next_ei, _start_addr, _enclave_type, _stack_addr_list, \
            _stack_size, _enclave_path, _heap_addr, _tcs_addr_list):
        self.next_ei         =   _next_ei
        self.start_addr      =   _start_addr
        self.enclave_type    =   _enclave_type
        self.stack_addr_list =   _stack_addr_list
        self.stack_size      =   _stack_size
        self.enclave_path    =   _enclave_path
        self.heap_addr       =   _heap_addr
        self.tcs_addr_list   =   _tcs_addr_list
    def __str__(self):
        print ("stack address list = {0:s}".format(self.stack_addr_list))
        return "start_addr = %#x, enclave_path = \"%s\", stack_size = %d" \
            % (self.start_addr, self.enclave_path, self.stack_size)
    def __eq__(self, other):
        if other == None:
            return False
        if self.start_addr == other.start_addr:
            return True
        else:
            return False
    def init_enclave_debug(self):
        # Only product HW enclave can't be debugged
        if (self.enclave_type & ET_SIM) != ET_SIM and (self.enclave_type & ET_DEBUG) != ET_DEBUG:
            print ('Warning: {0:s} is a product hardware enclave. It can\'t be debugged and sgx_emmt doesn\'t work'.format(self.enclave_path))
            return -1
        # set TCS debug flag
        for tcs_addr in self.tcs_addr_list:
            string = read_from_memory(tcs_addr + 8, 4)
            if string == None:
                return 0
            flag = struct.unpack('I', string)[0]
            flag |= 1
            gdb_cmd = "set *(unsigned int *)%#x = %#x" %(tcs_addr + 8, flag)
            gdb.execute(gdb_cmd, False, True)
        #If it is a product enclave, won't reach here.
        #load enclave symbol
        if os.path.exists(self.enclave_path) == True:
            enclave_path = self.enclave_path
        else:
            enclave_path = target_path_to_host_path(self.enclave_path)
        gdb_cmd = load_symbol_cmd.GetLoadSymbolCommand(enclave_path, str(self.start_addr))
        if gdb_cmd == -1:
            return 0
        print (gdb_cmd)
        gdb.execute(gdb_cmd, False, True)
        global ENCLAVES_ADDR
        ENCLAVES_ADDR[self.start_addr] = gdb_cmd.split()[2]
        return 0

    def get_peak_heap_used(self):
        """Get the peak value of the heap used"""
        if self.heap_addr == 0:
            return -2
        # read the peak_heap_used value
        string = read_from_memory(self.heap_addr, SIZE)
        if string == None:
            return -1
        if SIZE == 4:
            fmt = 'I'
        elif SIZE == 8:
            fmt = "Q"
        peak_heap_used = struct.unpack(fmt, string)[0]
        return peak_heap_used

    def internal_compare (self, a, b):
        return (a > b) - (a < b)

    def find_boundary_page_index(self, stack_addr, stack_size):
        """Find the unused page index of the boundary for the used and unused pages
            with the binary search algorithm"""
        page_index = -1   #record the last unused page index
        low = 0
        high = (stack_size>>12) - 1
        mid = 0
        # Read the mid page and check if it is used or not
        # If the mid page is used, then continue to search [mid+1, high]
        while low <= high:
            #print "low = %x, high = %x, mid = %x" % (low, high, mid)
            mid = (low + high)>>1
            string = read_from_memory(stack_addr + mid*PAGE_SIZE + (PAGE_SIZE>>1), PAGE_SIZE>>1)
            if string == None:
                return -2
            dirty_flag = 0
            for i in range(0, PAGE_SIZE>>4):
                temp = struct.unpack_from("Q", string, ((PAGE_SIZE>>4) - 1 - i)*8)[0]
                if (self.internal_compare(temp, 0xcccccccccccccccc)) != 0:
                    dirty_flag = 1
                    break
            if dirty_flag == 0:
                low = mid + 1
                page_index = mid
            else:
                high = mid -1
        return page_index

    def get_peak_stack_used(self):
        """Get the peak value of the stack used"""
        peak_stack_used = 0
        for tcs_addr in self.tcs_addr_list:
            tcs_str = read_from_memory(tcs_addr, ENCLAVE_TCS_INFO_SIZE)
            if tcs_str == None:
                return -1
            tcs_tuple = struct.unpack_from(TCS_INFO_FMT, tcs_str)
            offset = tcs_tuple[7]
            if SIZE == 4:
                td_fmt = '20I'
            elif SIZE == 8:
                td_fmt = '20Q'
            td_str = read_from_memory(self.start_addr+offset, (20*SIZE))
            if td_str == None:
                return -1
            td_tuple = struct.unpack_from(td_fmt, td_str)

            stack_commit_addr = td_tuple[19]
            stack_base_addr = td_tuple[2]
            stack_limit_addr = td_tuple[3]

            stack_usage = 0
            if stack_commit_addr > stack_limit_addr:
                stack_base_addr_page_align = (stack_base_addr + PAGE_SIZE - 1) & ~(PAGE_SIZE - 1)
                stack_usage = stack_base_addr_page_align - stack_commit_addr
            elif stack_limit_addr != 0:
                page_index = self.find_boundary_page_index(stack_limit_addr, self.stack_size)
                if page_index == (self.stack_size)/PAGE_SIZE - 1:
                    continue
                elif page_index == -2:
                    return -1
                else:
                    string = read_from_memory(stack_limit_addr + (page_index+1) * PAGE_SIZE, PAGE_SIZE)
                    if string == None:
                        return -1
                    for i in range(0, len(string)):
                        temp = struct.unpack_from("B", string, i)[0]
                        if (self.internal_compare(temp, 0xcc)) != 0:
                            stack_usage = self.stack_size - (page_index+1) * PAGE_SIZE - i
                            break

            if peak_stack_used < stack_usage:
                peak_stack_used = stack_usage

        return peak_stack_used

    def show_emmt(self):
        ret = gdb.execute("show sgx_emmt", False, True)
        if ret.strip() == "sgx_emmt enabled":
            print ("Enclave: \"{0:s}\"".format(self.enclave_path))
            peak_stack_used = self.get_peak_stack_used()
            if peak_stack_used == -1:
                print ("Failed to collect the stack usage information for \"{0:s}\"".format(self.enclave_path))
            else:
                peak_stack_used_align = (peak_stack_used + KB_SIZE - 1) & ~(KB_SIZE - 1)
                print ("  [Peak stack used]: {0:d} KB".format(peak_stack_used_align >> 10))
            peak_heap_used = self.get_peak_heap_used()
            if peak_heap_used == -1:
                print ("Failed to collect the heap usage information for \"{0:s}\"".format(self.enclave_path))
            elif peak_heap_used == -2:
                print ("  [Can't get peak heap used]: You may use version script to control symbol export. Please export \'g_peak_heap_used\' in your version script.")
            else:
                peak_heap_used_align = (peak_heap_used + KB_SIZE - 1) & ~(KB_SIZE - 1)
                print ("  [Peak heap used]:  {0:d} KB".format(peak_heap_used_align >> 10))

    def fini_enclave_debug(self):
        # If it is HW product enclave, nothing to do
        if (self.enclave_type & ET_SIM) != ET_SIM and (self.enclave_type & ET_DEBUG) != ET_DEBUG:
            return -2
        self.show_emmt()
        try:
            # clear TCS debug flag
            for tcs_addr in self.tcs_addr_list:
                string = read_from_memory(tcs_addr + 8, 4)
                if string == None:
                    return -2
                flag = struct.unpack('I', string)[0]
                flag &= (~1)
                gdb_cmd = "set *(unsigned int *)%#x = %#x" %(tcs_addr + 8, flag)
                gdb.execute(gdb_cmd, False, True)
            #unload symbol
            if os.path.exists(self.enclave_path) == True:
                enclave_path = self.enclave_path
            else:
                enclave_path = target_path_to_host_path(self.enclave_path)
            gdb_cmd = load_symbol_cmd.GetUnloadSymbolCommand(enclave_path, str(self.start_addr))
            if gdb_cmd == -1:
                return -1
            print (gdb_cmd)
            try:
                gdb.execute(gdb_cmd, False, True)
                global ENCLAVES_ADDR
                del ENCLAVES_ADDR[self.start_addr]
            except gdb.error:
                print ("Old gdb doesn't support remove-file-symbol command")
            return 0
        ##It is possible enclave has been destroyed, so may raise exception on memory access
        except gdb.MemoryError:
            return -1
        except:
            return -1

    def append_tcs_list(self, tcs_addr):
        for tcs_tmp in self.tcs_addr_list:
    	    if tcs_tmp == tcs_addr:
    	        return 0
        self.tcs_addr_list.append(tcs_addr)
        return 0

def retrieve_enclave_info(info_addr = 0):
    """retrieve one enclave info"""
    # Step 1: find the enclave info address
    if info_addr == 0:
        if SIZE == 4:
            info_addr = gdb.parse_and_eval("$eax")
        elif SIZE == 8:
            info_addr = gdb.parse_and_eval("$rdi")

    # Step 2: retrieve the enclave info
    info_str = read_from_memory(info_addr, ENCLAVE_INFO_SIZE)
    if info_str == None:
        return None
    info_tuple = struct.unpack_from(INFO_FMT, info_str, 0)
    # (next_enclave_info,start_addr,tcs_list,enclave_type,file_name_size,
    #   lpFileName,g_peak_heap_used_addr)
    #print "next_addr: %#x, start_addr: %#x, tcs_list: %#x, enclave_type:%#x,  file_name_size: %#x," \
    #    % (info_tuple[0], info_tuple[1], info_tuple[2], info_tuple[3], info_tuple[4])
    #print "name_addr: %#x, peak_heap_used_addr: %#x" \
    #    % (info_tuple[5], info_tuple[6])
    #get enclave path
    name_str = read_from_memory(info_tuple[5], info_tuple[4])
    if name_str == None:
        return None
    fmt = str(info_tuple[4]) + 's'
    enclave_path = struct.unpack_from(fmt, name_str)[0].decode(encoding='UTF-8')
    # get the stack addr list
    stack_addr_list = []
    tcs_addr_list = []
    if SIZE == 4:
        fmt = '3I'
    elif SIZE == 8:
        fmt = '3Q'
    tcs_info_addr = info_tuple[2]
    if tcs_info_addr == 0:
        print ("Error: tcs info address = {0:x}".format(tcs_info_addr))
        return None

    while tcs_info_addr is not 0:
        tcs_info_str = read_from_memory(tcs_info_addr, 3*SIZE)
        if tcs_info_str == None:
            return None
        tcs_info_tuple = struct.unpack_from(fmt, tcs_info_str)

        #get tcs struct data
        tcs_t_str = read_from_memory(tcs_info_tuple[1], ENCLAVE_TCS_INFO_SIZE)
        if tcs_t_str == None:
            return None
        tcs_t_tuple = struct.unpack_from(TCS_INFO_FMT, tcs_t_str)

        if SIZE == 4:
            td_fmt = '4I'
        elif SIZE == 8:
            td_fmt = '4Q'

        #get thread_data_t address
        td_addr = tcs_t_tuple[7] + info_tuple[1]     #thread_data_t = tcs.of_base + debug_enclave_info.start_addr
        td_str = read_from_memory(td_addr, (4*SIZE))
        if td_str == None:
            return None
        td_tuple = struct.unpack_from(td_fmt, td_str)
        #print ("thread_info:%#x, last_sp:%#x, stack_base_addr:%#x, stack_limit_addr:%#x" \
        #     % (td_tuple[0], td_tuple[1], td_tuple[2], td_tuple[3]));

        #stack size = ROUND_TO_PAGE(stack_base_addr - stack_limit_addr) since we have
        #a static stack whose size is smaller than PAGE_SIZE
        stacksize = (td_tuple[2] - td_tuple[3] + PAGE_SIZE - 1) & ~(PAGE_SIZE - 1)
        stack_addr_list.append(td_tuple[3])     #use stack limit addr as stack base address
        tcs_addr_list.append(tcs_info_tuple[1])
        tcs_info_addr = tcs_info_tuple[0]

        last_ocall_frame = tcs_info_tuple[2]
        last_frame = last_ocall_frame

        #print ("last_ocall_frame = {0:x}".format(last_ocall_frame))

        while last_ocall_frame is not 0:
            if SIZE == 4:
                of_fmt = '4I'
            elif SIZE == 8:
                of_fmt = '4Q'

            ocall_frame = read_from_memory(last_ocall_frame, 4*SIZE)

            if ocall_frame == None:
                return None
            ocall_frame_tuple = struct.unpack_from(of_fmt, ocall_frame)
            last_frame = ocall_frame_tuple[0]

            last_trusted_ocall_frame = td_tuple[1]
            #print ("last_trusted_ocall_frame = {0:x}".format(last_trusted_ocall_frame))
            #print ("td_tuple[2] = {0:x}".format(td_tuple[2]))
            #break

            while last_trusted_ocall_frame != td_tuple[2]:
                if SIZE == 4:
                    oc_fmt = '20I'
                    ret_addr_of_fmt = 'I'
                elif SIZE == 8:
                    oc_fmt = '20Q'
                    ret_addr_of_fmt = 'Q'

                oc_str = read_from_memory(last_trusted_ocall_frame, 20*SIZE)
                if oc_str == None:
                    return None
                oc_tuple = struct.unpack_from(oc_fmt, oc_str)

                last_trusted_ocall_frame = oc_tuple[6]

                #print ("last_trusted_ocall_frame = {0:x}".format(last_trusted_ocall_frame))
                #print ("ocall_frame_tuple[1] = {0:x}".format(ocall_frame_tuple[1]))
                #print ("oc_tuple[18] = {0:x}".format(oc_tuple[18]))
                #break

                if ocall_frame_tuple[1] == oc_tuple[18]:
                    #ocall_frame.pre_last_frame = 0
                    #ocall_frame.ret = ocall_context.ocall_ret
                    #ocall_frame.xbp = ocall_context.xbp
                    xbp = oc_tuple[11]
                    ret_addr_str = read_from_memory(xbp + SIZE, SIZE)
                    if ret_addr_str == None:
                        return None
                    ret_addr_tuple = struct.unpack_from(ret_addr_of_fmt, ret_addr_str)
                    gdb_cmd = "set *(uintptr_t *)%#x = 0" %(int(last_ocall_frame))
                    gdb.execute(gdb_cmd, False, True)
                    gdb_cmd = "set *(uintptr_t *)%#x = %#x" %(int(last_ocall_frame+(2*SIZE)), xbp)
                    gdb.execute(gdb_cmd, False, True)
                    gdb_cmd = "set *(uintptr_t *)%#x = %#x" %(int(last_ocall_frame+(3*SIZE)), ret_addr_tuple[0])
                    gdb.execute(gdb_cmd, False, True)
                    break

            last_ocall_frame = last_frame

    node = enclave_info(info_tuple[0], info_tuple[1], info_tuple[3], stack_addr_list, \
        stacksize, enclave_path, info_tuple[6], tcs_addr_list)
    return node

def handle_load_event():
    """Handle the enclave loading event.
    Firstly, retrieve the enclave info node from register
    """
    node = retrieve_enclave_info()
    if node != None:
        node.init_enclave_debug()
    else:
        return

def handle_unload_event():
    node = retrieve_enclave_info()
    if node != None:
        node.fini_enclave_debug()
    else:
        return

def is_bp_in_urts():
    try:
        ip = gdb.parse_and_eval("$pc")
        solib_name = gdb.solib_name(int(str(ip).split()[0], 16))
        if(solib_name.find("libsgx_urts.so") == -1 and solib_name.find("libsgx_urts_sim.so") == -1 and solib_name.find("libsgx_aesm_service.so") == -1):
            return False
        else:
            return True
    #If exception happens, just assume it is bp in uRTS.
    except:
        return True

def init_enclaves_debug():
    #execute "set displaced-stepping off" to workaround the gdb 7.11 issue
    gdb.execute("set displaced-stepping off", False, True)
    enclave_info_addr = gdb.parse_and_eval("*(void**)&g_debug_enclave_info_list")
    while enclave_info_addr != 0:
        node = retrieve_enclave_info(enclave_info_addr)
        if node != None:
            node.init_enclave_debug()
        else:
            return
        enclave_info_addr = node.next_ei
    return

class detach_enclaves (gdb.Command):
    def __init__ (self):
        gdb.Command.__init__ (self, "detach_enclaves", gdb.COMMAND_NONE)

    def invoke (self, arg, from_tty):
        #We reject the command from the input of terminal
        if from_tty == True:
            return
        try:
            enclave_info_addr = gdb.parse_and_eval("*(void**)&g_debug_enclave_info_list")
        except:
            return
        while enclave_info_addr != 0:
            node = retrieve_enclave_info(enclave_info_addr)
            if node != None:
                node.fini_enclave_debug()
            else:
                return
            enclave_info_addr = node.next_ei

class UpdateOcallFrame(gdb.Breakpoint):
    def __init__(self):
        gdb.Breakpoint.__init__ (self, spec="notify_gdb_to_update", internal=1)

    def stop(self):
        bp_in_urts = is_bp_in_urts()

        if bp_in_urts == True:

            if SIZE == 4:
                base_addr = gdb.parse_and_eval("$eax")
                tcs_addr = gdb.parse_and_eval("$edx")
                ocall_frame = gdb.parse_and_eval("$ecx")
            elif SIZE == 8:
                base_addr = gdb.parse_and_eval("$rdi")
                tcs_addr = gdb.parse_and_eval("$rsi")
                ocall_frame = gdb.parse_and_eval("$rdx")

            #print ("base_addr = {0:x}".format(int(base_addr)))
            #print ("tcs_addr = {0:x}".format(int(tcs_addr)))
            #print ("ocall_frame = {0:x}".format(int(ocall_frame)))

            tcs_str = read_from_memory(tcs_addr, ENCLAVE_TCS_INFO_SIZE)
            if tcs_str == None:
                return False
            tcs_tuple = struct.unpack_from(TCS_INFO_FMT, tcs_str)
            offset = tcs_tuple[7]

            if SIZE == 4:
                td_fmt = '4I'
            elif SIZE == 8:
                td_fmt = '4Q'

            td_str = read_from_memory(base_addr+offset, (4*SIZE))
            if td_str == None:
                return False
            td_tuple = struct.unpack_from(td_fmt, td_str)

            if SIZE == 4:
                trusted_of_fmt = '20I'
                ret_addr_of_fmt = 'I'
            elif SIZE == 8:
                trusted_of_fmt = '20Q'
                ret_addr_of_fmt = 'Q'

            last_sp = td_tuple[1]

            trusted_ocall_frame = read_from_memory(last_sp, (20*SIZE))
            if trusted_ocall_frame == None:
                return False
            trusted_ocall_frame_tuple = struct.unpack_from(trusted_of_fmt, trusted_ocall_frame)

            xbp = trusted_ocall_frame_tuple[11]

            ret_addr_str = read_from_memory(xbp + SIZE, SIZE)
            if ret_addr_str == None:
                return False
            ret_addr_tuple = struct.unpack_from(ret_addr_of_fmt, ret_addr_str)

            gdb_cmd = "set *(uintptr_t *)%#x = 0" %(int(ocall_frame))
            gdb.execute(gdb_cmd, False, True)
            gdb_cmd = "set *(uintptr_t *)%#x = %#x" %(int(ocall_frame+(2*SIZE)), xbp)
            gdb.execute(gdb_cmd, False, True)
            gdb_cmd = "set *(uintptr_t *)%#x = %#x" %(int(ocall_frame+(3*SIZE)), ret_addr_tuple[0])
            gdb.execute(gdb_cmd, False, True)

        return False

class LoadEventBreakpoint(gdb.Breakpoint):
    def __init__(self):
        gdb.Breakpoint.__init__ (self, spec="sgx_debug_load_state_add_element", internal=1)

    def stop(self):
        bp_in_urts = is_bp_in_urts()

        if bp_in_urts == True:
            handle_load_event()
        return False

class UnloadEventBreakpoint(gdb.Breakpoint):
    def __init__(self):
        gdb.Breakpoint.__init__ (self, spec="sgx_debug_unload_state_remove_element", internal=1)

    def stop(self):
        bp_in_urts = is_bp_in_urts()

        if bp_in_urts == True:
            handle_unload_event()
        return False
        
class GetTCSBreakpoint(gdb.Breakpoint):
    def __init__(self):
        gdb.Breakpoint.__init__ (self, spec="urts_add_tcs", internal=1) # sgx_add_tcs should be fastcall

    def stop(self):
        bp_in_urts = is_bp_in_urts()

        if bp_in_urts == True:
            if SIZE == 4:
                tcs_addr_1 = gdb.parse_and_eval("$eax")
                tcs_addr = ctypes.c_uint32(tcs_addr_1).value
            elif SIZE == 8:
                tcs_addr_1 = gdb.parse_and_eval("$rdi")
                tcs_addr = ctypes.c_uint64(tcs_addr_1).value
            enclave_info_addr = gdb.parse_and_eval("*(void **)&g_debug_enclave_info_list")
            if enclave_info_addr != 0:
                node = retrieve_enclave_info(enclave_info_addr)
            else:
                return False
            if node != None:
                node.append_tcs_list(tcs_addr)
            string = read_from_memory(tcs_addr + 8, 4)
            if string == None:
                return False
            flag = struct.unpack('I', string)[0]
            flag |= 1
            gdb_cmd = "set *(unsigned int *)%#x = %#x" %(tcs_addr + 8, flag)
            gdb.execute(gdb_cmd, False, True)
        return False
        
def sgx_debugger_init():
    print ("detect urts is loaded, initializing")
    global SIZE
    SIZE = gdb.parse_and_eval("sizeof(long)")
    inited = 0
    bps = gdb.breakpoints()
    if None != bps:
        for bp in bps:
            if bp.location == "sgx_debug_load_state_add_element":
                inited = 1
                break
    if inited == 0:
        detach_enclaves()
        gdb.execute("source gdb_sgx_cmd", False, True)
        UpdateOcallFrame()
        LoadEventBreakpoint()
        UnloadEventBreakpoint()
        GetTCSBreakpoint()
        gdb.events.exited.connect(exit_handler)
    init_enclaves_debug()


def exit_handler(event):
    # When the inferior exited, remove all enclave symbol
    for key in list(ENCLAVES_ADDR.keys()):
        gdb.execute("remove-symbol-file -a %s" % (ENCLAVES_ADDR[key]), False, True)
    ENCLAVES_ADDR.clear()

def newobj_handler(event):
    solib_name = os.path.basename(event.new_objfile.filename)
    if solib_name == 'libsgx_urts.so' or solib_name == 'libsgx_urts_sim.so' or solib_name == 'libsgx_aesm_service.so':
        sgx_debugger_init()
    return

if __name__ == "__main__":
    gdb.events.new_objfile.connect(newobj_handler)
    sgx_emmt.init_emmt()
