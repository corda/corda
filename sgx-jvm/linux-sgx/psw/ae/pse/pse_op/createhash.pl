#!/usr/bin/env perl
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

#generate default hash values for VMC hash tree
use Digest::SHA qw(sha256_hex);

open (OUT,">hashtable.txt");
select OUT;
#create an empty array whose size is sizeof(vmc_entry) x 2
#sizeof(vmc_entry) == 70
@data = chr(0x0) x 140;
$digest = sha256_hex(@data);
@arr = unpack("a2"x32,$digest);
$len=@arr;
$line = "{0x".join(",0x",@arr)."},\n";
print($line);
for($i=0;$i<22;$i++) {
  @data=();
  for($k=0;$k<2;$k++) {
    for($j=0;$j<32;$j++) {
      push(@data,chr(hex($arr[$j])));
    }
  }
  #print(@data);
  $digest = sha256_hex(@data);
  @arr = unpack("a2"x32,$digest);
  $line = "{0x".join(",0x",@arr)."},\n";
  print($line);
}
select STDOUT;
