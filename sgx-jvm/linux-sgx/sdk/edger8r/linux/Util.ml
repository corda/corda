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

open Printf

(* It contains some utility functions. *)
let failwithf fmt = kprintf failwith fmt;;
let splitwith chr = Str.split (Str.regexp_string (Char.escaped chr))

(* For compatibility with F#. *)
let (|>) a f = f a

(* Generate a list of sequent number.
 * ----------------------------------
 *
 * mk_seq 1 0 -> []
 * mk_seq 1 1 -> [1]
 * mk_seq 1 5 -> [1; 2; 3; 4; 5]
 *)
let rec mk_seq from_num to_num =
  if from_num > to_num then []
  else from_num :: mk_seq (from_num + 1) to_num

(* Return a list without duplicated elements. *)
let dedup_list lst =
  let rec do_dedup acc rst =
    match rst with
        []   -> acc
      | h::t -> if List.mem h acc then do_dedup acc t
                else do_dedup (h :: acc) t
  in do_dedup [] lst

(* Print the usage of this program. *)
let usage (progname: string) =
  eprintf "usage: %s [options] <file> ...\n" progname;
  eprintf "\n[options]\n\
--search-path <path>  Specify the search path of EDL files\n\
--use-prefix          Prefix untrusted proxy with Enclave name\n\
--header-only         Only generate header files\n\
--untrusted           Generate untrusted proxy and bridge\n\
--trusted             Generate trusted proxy and bridge\n\
--untrusted-dir <dir> Specify the directory for saving untrusted code\n\
--trusted-dir   <dir> Specify the directory for saving trusted code\n\
--help                Print this help message\n";
  eprintf "\n\
If neither `--untrusted' nor `--trusted' is specified, generate both.\n";
  exit 1


(* Command line parsing facilities. *)
type edger8r_params = {
  input_files   : string list;
  use_prefix    : bool;
  header_only   : bool;
  gen_untrusted : bool;         (* User specified `--untrusted' *)
  gen_trusted   : bool;         (* User specified `--trusted' *)
  untrusted_dir : string;       (* Directory to save untrusted code *)
  trusted_dir   : string;       (* Directory to save trusted code *)
}

(* The search paths are recored in the array below.
 * W/o extra search paths specified, edger8r searchs from current directory.
 *)
let search_paths = ref [|"."|]

(* The path separator is usually ':' on Linux and ';' on Windows.
 * Concerning that we might compile this code with OCaml on Windows,
 * we'd better don't assume that ':' is always used.
 *)
let path_separator : char =
  match Sys.os_type with
      "Win32" -> ';'
    | _       -> ':'			(* "Unix" or "Cygwin" *)

(* Parse the command line and return a record of `edger8r_params'. *)
let rec parse_cmdline (progname: string) (cmdargs: string list) =
  let use_pref = ref false in
  let hd_only  = ref false in
  let untrusted= ref false in
  let trusted  = ref false in
  let u_dir    = ref "." in
  let t_dir    = ref "." in
  let files    = ref [] in

  let rec local_parser (args: string list) =
    match args with
        [] -> ()
      | op :: ops ->
          match String.lowercase op with
              "--use-prefix" -> use_pref := true; local_parser ops
            | "--header-only"-> hd_only := true; local_parser ops
            | "--untrusted"  -> untrusted := true; local_parser ops
            | "--trusted"    -> trusted := true; local_parser ops
            | "--untrusted-dir" ->
              (match ops with
                []    -> usage progname
              | x::xs -> u_dir := x; local_parser xs)
            | "--trusted-dir" ->
              (match ops with
                []    -> usage progname
              | x::xs -> t_dir := x; local_parser xs)
            | "--help" -> usage progname
            | "--search-path" ->
                if ops = [] then usage progname
                else
                  let search_path_str = List.hd ops in
                  let extra_paths = splitwith path_separator search_path_str in
		  let extra_path_arry = Array.of_list extra_paths in
                    search_paths := Array.append extra_path_arry !search_paths;
                    local_parser (List.tl ops)
            | _ -> files := op :: !files; local_parser ops
  in
    local_parser cmdargs;
    let opt =
      { input_files = List.rev !files; use_prefix = !use_pref;
        header_only = !hd_only; gen_untrusted = true; gen_trusted = true;
        untrusted_dir = !u_dir; trusted_dir = !t_dir;
      }
    in
      if !untrusted || !trusted (* User specified '--untrusted' or '--trusted' *)
      then { opt with gen_trusted = !trusted; gen_untrusted = !untrusted }
      else opt

let separator_str : string = Filename.dir_sep

(* Search the file within given search pathes.
 * -------------------------------------------
 *
 * The second parameter is read from the global variable `search_paths'.
 *
 * get_file_path "Util.fs" [|"."|] -> "./Ast.fs"
 * get_file_path "Util.fs" [|"misc/dir"; "../Edger8r"|] -> "../Edger8r/Ast.fs"
 * get_file_path "Util.fs" [|"misc/dir"; "another/dir"|] -> Not_found
 *)
let get_file_path (fname: string) =
  let get_full_name path =
    if Filename.is_relative fname then path ^ separator_str ^ fname
    else fname
  in
  let targets = Array.map get_full_name !search_paths in
  let fn_list = Array.to_list targets in
    try
      List.find Sys.file_exists fn_list
    with
      Not_found -> failwithf "File not found within search paths: %s\n" fname

(* Get the short name of the given file name.
 * ------------------------------------------
 *
 * get_short_name "Util.fs"      -> "Util"
 * get_short_name "./Util.fs"    -> "Util"
 * get_short_name "misc/Util.fs" -> "Util"
 *)
let get_short_name (fname: string) =
  let bn = Filename.basename fname in
    try Filename.chop_extension bn
    with Invalid_argument _ -> bn

(* Helper functions that are not contained in OCaml standard library *)
let isdigit = function '0' | '1' .. '9' -> true | _ -> false
let isalpha = function 'a' .. 'z' | 'A' .. 'Z' -> true | _ -> false
let isalnum c = isdigit c || isalpha c

let str_map f s =
  let len = String.length s in
  let res = String.create len in
    for i = 0 to len - 1 do
      String.set res i (f (String.get s i))
    done;
  res

let str_to_list s =
  let rec iter i lst =
    if i < 0 then lst else iter (i - 1) (s.[i] :: lst)
  in
    iter (String.length s - 1) []

let str_forall p s = List.for_all p (str_to_list s)

(* Compute a string that conforms to C identifier.
 *
 * to_c_identifier "this foo" => "this_foo"
 * to_c_identifier "3this"    => "_this"
 *
 * The algorithm is simple, filter invalid characters to `_'.
*)
let to_c_identifier (s: string) =
  let convert_char ch =
    if isalnum ch then ch else '_'
  in
  let first_ch =
    let ch = s.[0] in
      if isalnum ch then ch else '_'
  in
  let rest_str =
    String.sub s 1 (String.length s - 1)
  in
    Char.escaped first_ch ^ str_map convert_char rest_str


(* Check whether given string is a valid C identifier.
 *
 * is_c_identifier "this foo" => false
 * is_c_identifier "3this"    => false
 * is_c_identifier "_this"    => true
*)
let is_c_identifier(s: string) =
  let first_ch = s.[0] in
  let rest_str = String.sub s 1 (String.length s - 1) in
    if isalpha first_ch || first_ch = '_'
    then str_forall (fun ch -> isalnum ch || ch = '_') rest_str
    else false

(* ocamlyacc doesn't expose definitions in header section,
 * as a quick work-around, we put them here.
 *)
let trusted_headers  : string list ref = ref []
let untrusted_headers: string list ref = ref []

(* Create directory specified by `d'. *)
let create_dir (d: string) =
  let curr_dir  = Unix.getcwd () in

  (* `get_root_dir' will be called with the head element of a list of
   * sub-directories starting from root directory.
   *  The list will look like -
   *   ["home", "guest", ...] on Linux, while
   *   ["c:\\", "Users", ...] on Windows.
   *)
  let get_root_dir (dirs: string list) =
    match Sys.os_type with
        "Win32" -> List.hd dirs
      | _       -> Filename.dir_sep
  in
  (* If we have a directory list like ["c:", ...], change the first element
   * to "c:\\".  Due to the fact that:
   *   Sys.file_exists "c:"   => false
   *   Sys.file_exists "c:\\" => true
   *)
  let normalize (ds: string list) =
    if Sys.os_type <> "Win32" then ds
    else
      let d = List.hd ds in
        if String.length d = 2  && d.[1] = ':'
        then (d ^ Filename.dir_sep) :: List.tl ds
        else ds
  in
  let dir_exist_p dir =
    if Sys.file_exists dir then
      let stats = Unix.stat dir in
        match stats.Unix.st_kind with
        | Unix.S_DIR -> true
        (* No need handle S_LNK because 'stat' will follow link. *)
        | _          -> false
    else false
  in
  let __do_create_and_goto_dir dir =
    (if dir_exist_p dir then () else Unix.mkdir dir 0o755);
    Unix.chdir dir
  in
  let do_create_dir () =
    let rec do_create_dir_recursively dirs =
      match dirs with
        []    -> ()
      | x::xs ->
        __do_create_and_goto_dir x; do_create_dir_recursively xs
    in
    (* After splitting, we will get a list of all sub-directories.
     * "/home/guest/some/path"    -> ["home", "guest", "some", "path"];
     * "c:\Users\guest\some\path" -> ["c:", "Users", "guest", "some", "path"].
     *)
    let dirs = normalize (Str.split (Str.regexp separator_str) d) in
    let start_dir = if Filename.is_relative d then curr_dir else get_root_dir dirs in
      Unix.chdir start_dir;
      (* In case of continuous dir_sep in path string, we filter out empty strings. *)
      do_create_dir_recursively (List.filter (fun s -> s <> "") dirs);
      Unix.chdir curr_dir;
  in
    try do_create_dir ()
    with exn -> (eprintf "error: failed to create directory: `%s'\n" d; exit 1)
