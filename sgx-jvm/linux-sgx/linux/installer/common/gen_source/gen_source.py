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

#  This script is used to copy file from src to dest as descript in a BOM file
#  example: python gen_source.py --bom=PATH_TO_BOM [--cleanup=false] [--deliverydir=DIR_RELATIVE_TO_gen_source.py]
#


import sys
import os
import shutil
import getopt

def parse_cmd(argc, argv):
	global bom_file
	global cleanup
	global deliverydir

	bom_file = ""
	cleanup = True
	deliverydir = ""

	local_path = os.path.split(os.path.realpath(sys.argv[0]))[0]

	try:
		opts, args = getopt.getopt(sys.argv[1:], "", ["bom=", "cleanup=", "deliverydir="])
	except getopt.GetoptError as err:
		print str(err)
		return False

	for option, value in opts:
		if option == "--bom":
			bom_file = value
			if not os.path.exists(os.path.join(local_path, bom_file)):
				return False

		if option == "--cleanup":
			cleanup = value
			if cleanup.lower() != "true" and cleanup.lower() != "false":
				return False
			if cleanup.lower() == "false":
				cleanup = False

		if option == "--deliverydir":
			deliverydir = value


	if bom_file == "":
		return False

	return True

################################################################################
# Name:   copy_folder_unrecursively()                                          #
# Usage:                                                                       #
# return: True if the operation is successful. False otherwise.                #
################################################################################
def copy_folder_unrecursively(src_dir, dest_dir):
	if os.path.isdir(src_dir) == True :
		print "Warning: The src is a folder ......"
		return False
		
	#print "copy" + src_dir + "->" + dest_dir
	try:
		shutil.copy(src_dir, dest_dir)
		return True
	except:
		print "Error !!!: can't find file:" + src_dir
		return False

################################################################################
# Name:   copy_folder_recursively()                                            #
# Usage:                                                                       #
################################################################################
def copy_folder_recursively(src_dir, dest_dir):
	if os.path.isdir(src_dir) == True:
		#copy the folder
		if os.path.exists(dest_dir) == False:
			os.makedirs(dest_dir)

		for item in os.listdir(src_dir):
			#print (os.path.join(src_dir, item)).replace("\\", "/")
			copy_folder_recursively((os.path.join(src_dir, item)).replace("\\", "/"), (os.path.join(dest_dir, item)).replace("\\", "/"))
			continue
	else:
		#copy the file
		print "copy file from " + src_dir + " to " + dest_dir
		shutil.copyfile(src_dir, dest_dir)	
		
		
	#if os.path.isdir(src_dir) == False or os.path.isdir(dest_dir) == False:
	#	print "Bom file error at copying " + src_dir + " to " + dest_dir
	#	exit(-1)

			
################################################################################
# Name:   read_BOM()                                                           #
# Usage:  read bom_file                                                        #
# return: the contents in bom_file                                             #
################################################################################
def read_BOM(local_path):
	#get the BOM file path that need to open
	file_list = local_path + "/" + bom_file
	f = open(file_list, 'r')
	#read the content in BOM file
	lines = f.readlines()
	f.close()	
	return lines	

################################################################################
# Name:   copy_files()                                                         #
# Usage:  copy file from src to dest                                           #
# return:                                                                      #
################################################################################
def copy_txt_files(local_path):
	home_path = os.path.realpath(os.path.join(local_path, "..", "..", "..", ".."))

	#read BOM file contents
	lines = read_BOM(local_path)
	
	for line in lines[1:]:
		if line == "\n":
			continue
		src = line.split('\t')[0]
		dest = line.split('\t')[1]
		src = src.replace("\\", "/")
		dest = dest.replace("\\", "/")	
		if deliverydir == "":
			src = src.replace("<deliverydir>/", home_path + "/")
		else:
			src = src.replace("<deliverydir>/", deliverydir + "/")
		dest = dest.replace("<installdir>/", local_path + "/output/")	
		#print "copy folder from " + src + " to " + dest

		if os.path.exists(src) == True:
			#check whether the src is a folder or file
			if os.path.isdir(src) == False :
				#the src is a file
				if os.path.exists(os.path.dirname(dest)) == False:
					os.makedirs(os.path.dirname(dest))
				ret = copy_folder_unrecursively(src, dest)
				if ret == False:
					exit(1)
				
			else:
				#the src is a folder
				copy_folder_recursively(src, dest)
		else:
			#although the src file isn't exist, create the dest folder
			if os.path.exists(os.path.dirname(dest)) == False:
				os.makedirs(os.path.dirname(dest))
			if os.path.isdir(src) == False :
				print "Error !!!: src file not exist " + src
			else:
				print "Error !!!: src folder not exist " + src
			exit(1)


if __name__ == "__main__":
	ret = parse_cmd(len(sys.argv), sys.argv)
	if ret == False:
		print "Usage:"
		print "python gen_source.py --bom=PATH_TO_BOM [--cleanup=false] [--deliverydir=DIR_RELATIVE_TO_gen_source.py]"
		exit(1)

	#script locate direction
	local_path = os.path.split(os.path.realpath(sys.argv[0]))[0]
	local_dir = os.path.basename(local_path)
	local_path = local_path.replace("\\", "/")

	ret = os.path.exists(local_path + "/output")
	if ret == True:
		if cleanup == True:
			print "clean the dest dir"
			shutil.rmtree(local_path + "/output")
			os.mkdir(local_path + "/output")
	else:
		print "Create the dest dir"
		os.mkdir(local_path + "/output")

	#cpoy the files
	copy_txt_files(local_path)
	
	print "Copy files finished ......"
	exit(0)
