(*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *)

open Unix
open Printf

open Util

(* for compat of OCaml before version 4.02.0 *)
module Bytes = String

(* Run a command and return its results as a process_status*string. *)
let read_process (command : string) : Unix.process_status * string =
  let buffer_size = 2048 in
  let buffer = Buffer.create buffer_size in
  let str = Bytes.create buffer_size in
  let in_channel = Unix.open_process_in command in
  let chars_read = ref 1 in
  while !chars_read <> 0 do
    chars_read := input in_channel str 0 buffer_size;
    Buffer.add_substring buffer str 0 !chars_read
  done;
  let status = Unix.close_process_in in_channel in
  let output = Buffer.contents buffer in
  ( status, output )

(*Return None if gcc not found, caller should handle it*)
let processor_macro ( full_path : string) : string option=
  let gcc_path = snd (read_process "which gcc") in
  if not (String.contains gcc_path  '/' ) then
    (eprintf "warning: preprocessor is not found\n"; None)
  else
    let command = sprintf "gcc -x c -E -P \"%s\" 2>/dev/null" full_path in
    let output = read_process command in
    match fst output with
      | WEXITED exit_status -> 
        if exit_status < 0 then
          failwithf "gcc exited with error code 0x%d\n" exit_status
        else if exit_status > 0 then
          failwithf "Preprocessor failed\n"
        else
          Some(snd output)
      | _ -> failwithf "Preprocessor stopped by signal\n"  
