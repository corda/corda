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
open Util                               (* for failwithf *)

(* --------------------------------------------------------------------
 * We first introduce a `parse_enclave_ast' function (see below) to
 * parse a value of type `Ast.enclave' into a `enclave_content' record.
 * --------------------------------------------------------------------
 *)

(* This record type is used to better organize a value of Ast.enclave *)
type enclave_content = {
  file_shortnm : string; (* the short name of original EDL file *)
  enclave_name : string; (* the normalized C identifier *)

  include_list : string list;
  import_exprs : Ast.import_decl list;
  comp_defs    : Ast.composite_type list;
  tfunc_decls  : Ast.trusted_func   list;
  ufunc_decls  : Ast.untrusted_func list;
}

(* Whether to prefix untrusted proxy with Enclave name *)
let g_use_prefix = ref false
let g_untrusted_dir = ref "."
let g_trusted_dir = ref "."

let empty_ec =
  { file_shortnm = "";
    enclave_name = "";
    include_list = [];
    import_exprs = [];
    comp_defs    = [];
    tfunc_decls  = [];
    ufunc_decls  = []; }

let get_tf_fname (tf: Ast.trusted_func) =
  tf.Ast.tf_fdecl.Ast.fname

let is_priv_ecall (tf: Ast.trusted_func) =
  tf.Ast.tf_is_priv

let get_uf_fname (uf: Ast.untrusted_func) =
  uf.Ast.uf_fdecl.Ast.fname

let get_trusted_func_names (ec: enclave_content) =
  List.map get_tf_fname ec.tfunc_decls

let get_untrusted_func_names (ec: enclave_content) =
  List.map get_uf_fname ec.ufunc_decls

let tf_list_to_fd_list (tfs: Ast.trusted_func list) =
  List.map (fun (tf: Ast.trusted_func) -> tf.Ast.tf_fdecl) tfs

let tf_list_to_priv_list (tfs: Ast.trusted_func list) =
  List.map is_priv_ecall tfs

(* Get a list of names of all private ECALLs *)
let get_priv_ecall_names (tfs: Ast.trusted_func list) =
  List.filter is_priv_ecall tfs |> List.map get_tf_fname

let uf_list_to_fd_list (ufs: Ast.untrusted_func list) =
  List.map (fun (uf: Ast.untrusted_func) -> uf.Ast.uf_fdecl) ufs

(* Get a list of names of all allowed ECALLs from `allow(...)' *)
let get_allowed_names (ufs: Ast.untrusted_func list) =
  let allow_lists =
    List.map (fun (uf: Ast.untrusted_func) -> uf.Ast.uf_allow_list) ufs
  in
    List.flatten allow_lists |> dedup_list

(* With `parse_enclave_ast', each enclave AST is traversed only once. *)
let parse_enclave_ast (e: Ast.enclave) =
  let ac_include_list = ref [] in
  let ac_import_exprs = ref [] in
  let ac_comp_defs = ref [] in
  let ac_tfunc_decls = ref [] in
  let ac_ufunc_decls = ref [] in
    List.iter (fun ex ->
      match ex with
          Ast.Composite x -> ac_comp_defs := x :: !ac_comp_defs
        | Ast.Include   x -> ac_include_list := x :: !ac_include_list
        | Ast.Importing x -> ac_import_exprs := x :: !ac_import_exprs
        | Ast.Interface xs ->
            List.iter (fun ef ->
              match ef with
                  Ast.Trusted f ->
                    ac_tfunc_decls := f :: !ac_tfunc_decls
                | Ast.Untrusted f ->
                    ac_ufunc_decls := f :: !ac_ufunc_decls) xs
      ) e.Ast.eexpr;
    { file_shortnm = e.Ast.ename;
      enclave_name = Util.to_c_identifier e.Ast.ename;
      include_list = List.rev !ac_include_list;
      import_exprs = List.rev !ac_import_exprs;
      comp_defs    = List.rev !ac_comp_defs;
      tfunc_decls  = List.rev !ac_tfunc_decls;
      ufunc_decls  = List.rev !ac_ufunc_decls; }

let is_foreign_array (pt: Ast.parameter_type) =
  match pt with
      Ast.PTVal _     -> false
    | Ast.PTPtr(t, a) ->
        match t with
            Ast.Foreign _ -> a.Ast.pa_isary
          | _             -> false

(* A naked function has neither parameters nor return value. *)
let is_naked_func (fd: Ast.func_decl) =
  fd.Ast.rtype = Ast.Void && fd.Ast.plist = []

(* 
 * If user only defined a trusted function w/o neither parameter nor
 * return value, the generated trusted bridge will not call any tRTS
 * routines.  If the real trusted function doesn't call tRTS function
 * either (highly possible), then the MSVC linker will not link tRTS
 * into the result enclave.
 *)
let tbridge_gen_dummy_variable (ec: enclave_content) =
  let _dummy_variable =
    sprintf "\n#ifdef _MSC_VER\n\
\t/* In case enclave `%s' doesn't call any tRTS function. */\n\
\tvolatile int force_link_trts = sgx_is_within_enclave(NULL, 0);\n\
\t(void) force_link_trts; /* avoid compiler warning */\n\
#endif\n\n" ec.enclave_name
  in
    if ec.ufunc_decls <> [] then ""
    else
      if List.for_all (fun tfd -> is_naked_func tfd.Ast.tf_fdecl) ec.tfunc_decls
      then _dummy_variable
      else ""

(* This function is used to convert Array form into Pointer form.
 * e.g.: int array[10][20]   =>  [count = 200] int* array
 *
 * This function is called when generating proxy/bridge code and
 * the marshaling structure.
 *)
let conv_array_to_ptr (pd: Ast.pdecl): Ast.pdecl =
  let (pt, declr) = pd in
  let get_count_attr ilist =
    (* XXX: assume the size of each dimension will be > 0. *)
    Ast.ANumber (List.fold_left (fun acc i -> acc*i) 1 ilist)
  in
    match pt with
      Ast.PTVal _        ->  (pt, declr)
    | Ast.PTPtr(aty, pa) ->
      if Ast.is_array declr then
        let tmp_declr = { declr with Ast.array_dims = [] } in
        let tmp_aty = Ast.Ptr aty in
        let tmp_cnt = get_count_attr declr.Ast.array_dims in
        let tmp_pa = { pa with Ast.pa_size = { Ast.empty_ptr_size with Ast.ps_count = Some tmp_cnt } }
        in (Ast.PTPtr(tmp_aty, tmp_pa), tmp_declr)
      else (pt, declr)

(* ------------------------------------------------------------------
 * Code generation for edge-routines.
 * ------------------------------------------------------------------
 *)

(* Little functions for naming of a struct and its members etc *)
let retval_name = "retval"
let retval_declr = { Ast.identifier = retval_name; Ast.array_dims = []; }
let eid_name = "eid"
let ms_ptr_name = "pms"
let ms_struct_val = "ms"
let mk_ms_member_name (pname: string) = "ms_" ^ pname
let mk_ms_struct_name (fname: string) = "ms_" ^ fname ^ "_t"
let ms_retval_name = mk_ms_member_name retval_name
let mk_tbridge_name (fname: string) = "sgx_" ^ fname
let mk_parm_accessor name = sprintf "%s->%s" ms_struct_val (mk_ms_member_name name)
let mk_tmp_var name = "_tmp_" ^ name
let mk_len_var name = "_len_" ^ name
let mk_in_var name = "_in_" ^ name
let mk_ocall_table_name enclave_name = "ocall_table_" ^ enclave_name

(* Un-trusted bridge name is prefixed with enclave file short name. *)
let mk_ubridge_name (file_shortnm: string) (funcname: string) =
  sprintf "%s_%s" file_shortnm funcname

let mk_ubridge_proto (file_shortnm: string) (funcname: string) =
  sprintf "static sgx_status_t SGX_CDECL %s(void* %s)"
          (mk_ubridge_name file_shortnm funcname) ms_ptr_name

(* Common macro definitions. *)
let common_macros = "#include <stdlib.h> /* for size_t */\n\n\
#define SGX_CAST(type, item) ((type)(item))\n\n\
#ifdef __cplusplus\n\
extern \"C\" {\n\
#endif\n"

(* Header footer *)
let header_footer = "\n#ifdef __cplusplus\n}\n#endif /* __cplusplus */\n\n#endif\n"

(* Little functions for generating file names. *)
let get_uheader_short_name (file_shortnm: string) = file_shortnm ^ "_u.h"
let get_uheader_name (file_shortnm: string) =
  !g_untrusted_dir ^ separator_str ^ (get_uheader_short_name file_shortnm)

let get_usource_name (file_shortnm: string) =
  !g_untrusted_dir ^ separator_str ^ file_shortnm ^ "_u.c"

let get_theader_short_name (file_shortnm: string) = file_shortnm ^ "_t.h"
let get_theader_name (file_shortnm: string) =
  !g_trusted_dir ^ separator_str ^ (get_theader_short_name file_shortnm)

let get_tsource_name (file_shortnm: string) =
  !g_trusted_dir ^ separator_str ^ file_shortnm ^ "_t.c"

(* Construct the string of structure definition *)
let mk_struct_decl (fs: string) (name: string) =
  sprintf "typedef struct %s {\n%s} %s;\n" name fs name

(* Construct the string of union definition *)
let mk_union_decl (fs: string) (name: string) =
  sprintf "typedef union %s {\n%s} %s;\n" name fs name

(* Generate a definition of enum *)
let mk_enum_def (e: Ast.enum_def) =
  let gen_enum_ele_str (ele: Ast.enum_ele) =
    let k, v = ele in
      match v with
          Ast.EnumValNone -> k
        | Ast.EnumVal ev  -> sprintf "%s = %s" k (Ast.attr_value_to_string ev)
  in
  let enname = e.Ast.enname in
  let enbody = e.Ast.enbody in
  let enbody_str =
    if enbody = [] then ""
    else List.fold_left (fun acc ele ->
               acc ^ "\t" ^ gen_enum_ele_str ele ^ ",\n") "" enbody
  in
    if enname = "" then sprintf "enum {\n%s};\n" enbody_str
    else sprintf "typedef enum %s {\n%s} %s;\n" enname enbody_str enname

let get_array_dims (ns: int list) =
  (* Get the array declaration from a list of array dimensions.
   * Empty `ns' indicates the corresponding declarator is a simple identifier.
   * Element of value -1 means that user does not specify the dimension size.
   *)
  let get_dim n = if n = -1 then "[]" else sprintf "[%d]" n
  in
    if ns = [] then ""
    else List.fold_left (fun acc n -> acc ^ get_dim n) "" ns

let get_typed_declr_str (ty: Ast.atype) (declr: Ast.declarator) =
  let tystr = Ast.get_tystr  ty in
  let dmstr = get_array_dims declr.Ast.array_dims in
    sprintf "%s %s%s" tystr declr.Ast.identifier dmstr

(* Construct a member declaration string *)
let mk_member_decl (ty: Ast.atype) (declr: Ast.declarator) =
  sprintf "\t%s;\n" (get_typed_declr_str ty declr)

(* Note that, for a foreign array type `foo_array_t' we will generate
 *   foo_array_t* ms_field;
 * in the marshaling data structure to keep the pass-by-address scheme
 * as in the C programming language.
*)
let mk_ms_member_decl (pt: Ast.parameter_type) (declr: Ast.declarator) =
  let aty = Ast.get_param_atype pt in
  let tystr = Ast.get_tystr aty in
  let ptr = if is_foreign_array pt then "* " else "" in
  let field = mk_ms_member_name declr.Ast.identifier in
  let dmstr = get_array_dims declr.Ast.array_dims in
    sprintf "\t%s%s %s%s;\n" tystr ptr field dmstr

(* Generate data structure definition *)
let gen_comp_def (st: Ast.composite_type) =
  let gen_member_list mlist =
    List.fold_left (fun acc (ty, declr) ->
                      acc ^ mk_member_decl ty declr) "" mlist
  in
    match st with
        Ast.StructDef s -> mk_struct_decl (gen_member_list s.Ast.mlist) s.Ast.sname
      | Ast.UnionDef  u -> mk_union_decl  (gen_member_list u.Ast.mlist) u.Ast.sname
      | Ast.EnumDef   e -> mk_enum_def    e

(* Generate a list of '#include' *)
let gen_include_list (xs: string list) =
  List.fold_left (fun acc s -> acc ^ sprintf "#include \"%s\"\n" s) "" xs

(* Get the type string from 'parameter_type' *)
let get_param_tystr (pt: Ast.parameter_type) =
  Ast.get_tystr (Ast.get_param_atype pt)

(* Generate marshaling structure definition *)
let gen_marshal_struct (fd: Ast.func_decl) (errno: string) =
    let member_list_str = errno ^
    let new_param_list = List.map conv_array_to_ptr fd.Ast.plist in
    List.fold_left (fun acc (pt, declr) ->
            acc ^ mk_ms_member_decl pt declr) "" new_param_list in
  let struct_name = mk_ms_struct_name fd.Ast.fname in
    match fd.Ast.rtype with
        (* A function w/o return value and parameters doesn't need
           a marshaling struct. *)
        Ast.Void -> if fd.Ast.plist = [] && errno = "" then ""
                    else mk_struct_decl member_list_str struct_name
      | _ -> let rv_str = mk_ms_member_decl (Ast.PTVal fd.Ast.rtype) retval_declr
             in mk_struct_decl (rv_str ^ member_list_str) struct_name

let gen_ecall_marshal_struct (tf: Ast.trusted_func) =
    gen_marshal_struct tf.Ast.tf_fdecl ""

let gen_ocall_marshal_struct (uf: Ast.untrusted_func) =
    let errno_decl = if uf.Ast.uf_propagate_errno then "\tint ocall_errno;\n" else "" in
    gen_marshal_struct uf.Ast.uf_fdecl errno_decl

(* Check whether given parameter is `const' specified. *)
let is_const_ptr (pt: Ast.parameter_type) =
  let aty = Ast.get_param_atype pt in
    match pt with
      Ast.PTVal _      -> false
    | Ast.PTPtr(_, pa) ->
      if not pa.Ast.pa_rdonly then false
      else
        match aty with
          Ast.Foreign _ -> false
        | _             -> true

(* Generate parameter representation. *)
let gen_parm_str (p: Ast.pdecl) =
  let (pt, (declr : Ast.declarator)) = p in
  let aty = Ast.get_param_atype pt in
  let str = get_typed_declr_str aty declr in
    if is_const_ptr pt then "const " ^ str else str

(* Generate parameter representation of return value. *)
let gen_parm_retval (rt: Ast.atype) =
  if rt = Ast.Void then ""
  else Ast.get_tystr rt ^ "* " ^ retval_name

(* ---------------------------------------------------------------------- *)

(* `gen_ecall_table' is used to generate ECALL table with the following form:
    SGX_EXTERNC const struct {
       size_t nr_ecall;    /* number of ECALLs */
       struct {
           void   *ecall_addr;
           uint8_t is_priv;
       } ecall_table [nr_ecall];
   } g_ecall_table = {
       2, { {sgx_foo, 1}, {sgx_bar, 0} }
   };
*)
let gen_ecall_table (tfs: Ast.trusted_func list) =
  let ecall_table_name = "g_ecall_table" in
  let ecall_table_size = List.length tfs in
  let trusted_fds = tf_list_to_fd_list tfs in
  let priv_bits = tf_list_to_priv_list tfs in
  let tbridge_names = List.map (fun (fd: Ast.func_decl) ->
                                  mk_tbridge_name fd.Ast.fname) trusted_fds in
  let ecall_table =
    let bool_to_int b = if b then 1 else 0 in
    let inner_table =
      List.fold_left2 (fun acc s b ->
        sprintf "%s\t\t{(void*)(uintptr_t)%s, %d},\n" acc s (bool_to_int b)) "" tbridge_names priv_bits
    in "\t{\n" ^ inner_table ^ "\t}\n"
  in
    sprintf "SGX_EXTERNC const struct {\n\
\tsize_t nr_ecall;\n\
\tstruct {void* ecall_addr; uint8_t is_priv;} ecall_table[%d];\n\
} %s = {\n\
\t%d,\n\
%s};\n" ecall_table_size
      ecall_table_name
      ecall_table_size
      (if ecall_table_size = 0 then "" else ecall_table)

(* `gen_entry_table' is used to generate Dynamic Entry Table with the form:
   SGX_EXTERNC const struct {
       /* number of OCALLs (number of ECALLs can be found in ECALL table) */
       size_t nr_ocall;

       /* entry_table[m][n] = 1 iff. ECALL n is allowed in the OCALL m. */
       uint8_t entry_table[NR_OCALL][NR_ECALL];
   } g_dyn_entry_table = {
       3, {{0, 0}, {0, 1}, {1, 0}}
   };
*)
let gen_entry_table (ec: enclave_content) =
  let dyn_entry_table_name = "g_dyn_entry_table" in
  let ocall_table_size = List.length ec.ufunc_decls in
  let trusted_func_names = get_trusted_func_names ec in
  let ecall_table_size = List.length trusted_func_names in
  let get_entry_array (allowed_ecalls: string list) =
    List.fold_left (fun acc name ->
                      acc ^ (if List.exists (fun x -> x=name) allowed_ecalls
                             then "1"
                             else "0") ^ ", ") "" trusted_func_names in
  let entry_table =
    let inner_table =
      List.fold_left (fun acc (uf: Ast.untrusted_func) ->
                        let entry_array = get_entry_array uf.Ast.uf_allow_list
                        in acc ^ "\t\t{" ^ entry_array ^ "},\n") "" ec.ufunc_decls
    in
      "\t{\n" ^ inner_table ^ "\t}\n"
  in
    (* Generate dynamic entry table iff. both sgx_ecall/ocall_table_size > 0 *)
  let gen_table_p = (ecall_table_size > 0) && (ocall_table_size > 0) in
    (* When NR_ECALL is 0, or NR_OCALL is 0, there will be no entry table field. *)
  let entry_table_field =
    if gen_table_p then
      sprintf "\tuint8_t entry_table[%d][%d];\n" ocall_table_size ecall_table_size
    else
      ""
  in
    sprintf "SGX_EXTERNC const struct {\n\
\tsize_t nr_ocall;\n%s\
} %s = {\n\
\t%d,\n\
%s};\n" entry_table_field
        dyn_entry_table_name
        ocall_table_size
        (if gen_table_p then entry_table else "")

(* ---------------------------------------------------------------------- *)

(* Generate the function prototype for untrusted proxy in COM style.
 * For example, un-trusted functions
 *   int foo(double d);
 *   void bar(float f);
 *
 * will have an untrusted proxy like below:
 *   sgx_status_t foo(int* retval, double d);
 *   sgx_status_t bar(float f);
 *)
let gen_tproxy_proto (fd: Ast.func_decl) =
  let parm_list =
    match fd.Ast.plist with
        [] -> ""
      | x :: xs ->
          List.fold_left (fun acc pd ->
                            acc ^ ", " ^ gen_parm_str pd) (gen_parm_str x) xs
  in
  let retval_parm_str = gen_parm_retval fd.Ast.rtype in
    if fd.Ast.plist = [] then
      sprintf "sgx_status_t SGX_CDECL %s(%s)" fd.Ast.fname retval_parm_str
    else if fd.Ast.rtype = Ast.Void then
           sprintf "sgx_status_t SGX_CDECL %s(%s)" fd.Ast.fname parm_list
         else
           sprintf "sgx_status_t SGX_CDECL %s(%s, %s)" fd.Ast.fname retval_parm_str parm_list

(* Generate the function prototype for untrusted proxy in COM style.
 * For example, trusted functions
 *   int foo(double d);
 *   void bar(float f);
 *
 * will have an untrusted proxy like below:
 *   sgx_status_t foo(sgx_enclave_id_t eid, int* retval, double d);
 *   sgx_status_t foo(sgx_enclave_id_t eid, float f);
 *
 * When `g_use_prefix' is true, the untrusted proxy name is prefixed
 * with the `prefix' parameter.
 *
 *)
let gen_uproxy_com_proto (fd: Ast.func_decl) (prefix: string) =
  let retval_parm_str = gen_parm_retval fd.Ast.rtype in

  let eid_parm_str =
    if fd.Ast.rtype = Ast.Void then sprintf "(sgx_enclave_id_t %s" eid_name
    else sprintf "(sgx_enclave_id_t %s, " eid_name in
  let parm_list =
    List.fold_left (fun acc pd -> acc ^ ", " ^ gen_parm_str pd)
      retval_parm_str fd.Ast.plist in
  let fname =
    if !g_use_prefix then sprintf "%s_%s" prefix fd.Ast.fname
    else fd.Ast.fname
  in "sgx_status_t " ^ fname ^ eid_parm_str ^ parm_list ^ ")"

let get_ret_tystr (fd: Ast.func_decl) = Ast.get_tystr fd.Ast.rtype
let get_plist_str (fd: Ast.func_decl) =
  if fd.Ast.plist = [] then ""
  else List.fold_left (fun acc pd -> acc ^ ", " ^ gen_parm_str pd)
                      (gen_parm_str (List.hd fd.Ast.plist))
                      (List.tl fd.Ast.plist)

(* Generate the function prototype as is. *)
let gen_func_proto (fd: Ast.func_decl) =
  let ret_tystr = get_ret_tystr fd in
  let plist_str = get_plist_str fd in
    sprintf "%s %s(%s)" ret_tystr fd.Ast.fname plist_str

(* Generate prototypes for untrusted function. *)
let gen_ufunc_proto (uf: Ast.untrusted_func) =
  let dllimport = if uf.Ast.uf_fattr.Ast.fa_dllimport then "SGX_DLLIMPORT " else "" in
  let ret_tystr = get_ret_tystr uf.Ast.uf_fdecl in
  let cconv_str = "SGX_" ^ Ast.get_call_conv_str uf.Ast.uf_fattr.Ast.fa_convention in
  let func_name = uf.Ast.uf_fdecl.Ast.fname in
  let plist_str = get_plist_str uf.Ast.uf_fdecl in
    sprintf "%s%s SGX_UBRIDGE(%s, %s, (%s))"
            dllimport ret_tystr cconv_str func_name plist_str

(* The preemble contains common include expressions. *)
let gen_uheader_preemble (guard: string) (inclist: string)=
  let grd_hdr = sprintf "#ifndef %s\n#define %s\n\n" guard guard in
  let inc_exp = "#include <stdint.h>\n\
#include <wchar.h>\n\
#include <stddef.h>\n\
#include <string.h>\n\
#include \"sgx_edger8r.h\" /* for sgx_satus_t etc. */\n" in
    grd_hdr ^ inc_exp ^ "\n" ^ inclist ^ "\n" ^ common_macros

let ms_writer out_chan ec =
  let ms_struct_ecall = List.map gen_ecall_marshal_struct ec.tfunc_decls in
  let ms_struct_ocall = List.map gen_ocall_marshal_struct ec.ufunc_decls in
  let output_struct s = 
    match s with
        "" -> s
      | _  -> sprintf "%s\n" s
  in
    List.iter (fun s -> output_string out_chan (output_struct s)) ms_struct_ecall;
    List.iter (fun s -> output_string out_chan (output_struct s)) ms_struct_ocall


(* Generate untrusted header for enclave *)
let gen_untrusted_header (ec: enclave_content) =
  let header_fname = get_uheader_name ec.file_shortnm in
  let guard_macro = sprintf "%s_U_H__" (String.uppercase ec.enclave_name) in
  let preemble_code =
    let include_list = gen_include_list (ec.include_list @ !untrusted_headers) in
      gen_uheader_preemble guard_macro include_list
  in
  let comp_def_list   = List.map gen_comp_def ec.comp_defs in
  let func_proto_ufunc = List.map gen_ufunc_proto ec.ufunc_decls in
  let uproxy_com_proto =
      List.map (fun (tf: Ast.trusted_func) ->
                  gen_uproxy_com_proto tf.Ast.tf_fdecl ec.enclave_name)
        ec.tfunc_decls
  in
  let out_chan = open_out header_fname in
    output_string out_chan (preemble_code ^ "\n");
    List.iter (fun s -> output_string out_chan (s ^ "\n")) comp_def_list;
    List.iter (fun s -> output_string out_chan (s ^ ";\n")) func_proto_ufunc;
    output_string out_chan "\n";
    List.iter (fun s -> output_string out_chan (s ^ ";\n")) uproxy_com_proto;
    output_string out_chan header_footer;
    close_out out_chan

(* It generates preemble for trusted header file. *)
let gen_theader_preemble (guard: string) (inclist: string) =
  let grd_hdr = sprintf "#ifndef %s\n#define %s\n\n" guard guard in
  let inc_exp = "#include <stdint.h>\n\
#include <wchar.h>\n\
#include <stddef.h>\n\
#include \"sgx_edger8r.h\" /* for sgx_ocall etc. */\n\n" in
    grd_hdr ^ inc_exp ^ inclist ^ "\n" ^ common_macros


(* Generate function prototype for functions used by `sizefunc' attribute. *)
let gen_sizefunc_proto out_chan (ec: enclave_content) =
  let tfunc_decls = tf_list_to_fd_list ec.tfunc_decls in
  let ufunc_decls = uf_list_to_fd_list ec.ufunc_decls in

  let dict = Hashtbl.create 4 in
  let get_sizefunc_proto s =
    let (pt, ns) = Hashtbl.find dict s in
    let tmpdeclr = { Ast.identifier = "val"; Ast.array_dims = ns; } in
      sprintf "size_t %s(const %s);\n" s (get_typed_declr_str pt tmpdeclr)
  in

  let add_item (fname: string) (ty: Ast.atype * int list) =
    try
      let v = Hashtbl.find dict fname
      in
        if v <> ty then
          failwithf "`%s' requires different parameter types" fname
    with Not_found -> Hashtbl.add dict fname ty
  in
  let fill_dict (pd: Ast.pdecl) =
    let (pt, declr) = pd in
      match pt with
          Ast.PTVal _           -> ()
        | Ast.PTPtr(aty, pattr) ->
            match pattr.Ast.pa_size.Ast.ps_sizefunc with
                Some s -> add_item s (aty, declr.Ast.array_dims)
              | _ -> ()
  in
    List.iter (fun (fd: Ast.func_decl) ->
                 List.iter fill_dict fd.Ast.plist) (tfunc_decls @ ufunc_decls);
    Hashtbl.iter (fun x y ->
                    output_string out_chan (get_sizefunc_proto x)) dict;
    output_string out_chan "\n"

(* Generate trusted header for enclave *)
let gen_trusted_header (ec: enclave_content) =
  let header_fname = get_theader_name ec.file_shortnm in
  let guard_macro = sprintf "%s_T_H__" (String.uppercase ec.enclave_name) in
  let guard_code =
    let include_list = gen_include_list (ec.include_list @ !trusted_headers) in
      gen_theader_preemble guard_macro include_list in
  let comp_def_list   = List.map gen_comp_def ec.comp_defs in
  let func_proto_list = List.map gen_func_proto (tf_list_to_fd_list ec.tfunc_decls) in
  let func_tproxy_list= List.map gen_tproxy_proto (uf_list_to_fd_list ec.ufunc_decls) in

  let out_chan = open_out header_fname in
    output_string out_chan (guard_code ^ "\n");
    List.iter (fun s -> output_string out_chan (s ^ "\n")) comp_def_list;
    gen_sizefunc_proto out_chan ec;
    List.iter (fun s -> output_string out_chan (s ^ ";\n")) func_proto_list;
    output_string out_chan "\n";
    List.iter (fun s -> output_string out_chan (s ^ ";\n")) func_tproxy_list;
    output_string out_chan header_footer;
    close_out out_chan

(* It generates function invocation expression. *)
let mk_parm_name_raw (pt: Ast.parameter_type) (declr: Ast.declarator) =
  let cast_expr =
    if Ast.is_array declr && List.length declr.Ast.array_dims > 1
    then
      let tystr = get_param_tystr pt in
      let dims = get_array_dims (List.tl declr.Ast.array_dims) in
        sprintf "(%s (*)%s)"  tystr dims
    else ""
  in
    cast_expr ^ mk_parm_accessor declr.Ast.identifier

(* We passed foreign array `foo_array_t foo' as `&foo[0]', thus we
 * need to get back `foo' by '* array_ptr' where
 *   array_ptr = &foo[0]
*)
let add_foreign_array_ptrref
    (f: Ast.parameter_type -> Ast.declarator -> string)
    (pt: Ast.parameter_type)
    (declr: Ast.declarator) =
  let arg = f pt declr in
    if is_foreign_array pt
    then sprintf "(%s != NULL) ? (*%s) : NULL" arg arg
    else arg

let mk_parm_name_ubridge (pt: Ast.parameter_type) (declr: Ast.declarator) =
  add_foreign_array_ptrref mk_parm_name_raw pt declr

let mk_parm_name_ext (pt: Ast.parameter_type) (declr: Ast.declarator) =
  let name = declr.Ast.identifier in
    match pt with
        Ast.PTVal _ -> mk_parm_name_raw pt declr
      | Ast.PTPtr (_, attr) ->
          match attr.Ast.pa_direction with
            | Ast.PtrNoDirection -> mk_parm_name_raw pt declr
            | _ -> mk_in_var name

let gen_func_invoking (fd: Ast.func_decl)
                      (mk_parm_name: Ast.parameter_type -> Ast.declarator -> string) =
  let gen_parm_str pt declr =
    let parm_name = mk_parm_name pt declr in
    let tystr = get_param_tystr pt in
      if is_const_ptr pt then sprintf "(const %s)%s" tystr parm_name else parm_name
  in
    match fd.Ast.plist with
      [] -> sprintf "%s();" fd.Ast.fname
    | (pt, (declr : Ast.declarator)) :: ps ->
        sprintf "%s(%s);"
          fd.Ast.fname
          (let p0 = gen_parm_str pt declr in
             List.fold_left (fun acc (pty, dlr) ->
                               acc ^ ", " ^ gen_parm_str pty dlr) p0 ps)

(* Generate untrusted bridge code for a given untrusted function. *)
let gen_func_ubridge (file_shortnm: string) (ufunc: Ast.untrusted_func) =
  let fd = ufunc.Ast.uf_fdecl in
  let propagate_errno = ufunc.Ast.uf_propagate_errno in
  let func_open = sprintf "%s\n{\n" (mk_ubridge_proto file_shortnm fd.Ast.fname) in
  let func_close = "\treturn SGX_SUCCESS;\n}\n" in
  let set_errno = if propagate_errno then "\tms->ocall_errno = errno;" else "" in
  let ms_struct_name = mk_ms_struct_name fd.Ast.fname in
  let declare_ms_ptr = sprintf "%s* %s = SGX_CAST(%s*, %s);"
                               ms_struct_name
                               ms_struct_val
                               ms_struct_name
                               ms_ptr_name in
  let call_with_pms =
    let invoke_func = gen_func_invoking fd mk_parm_name_ubridge in
      if fd.Ast.rtype = Ast.Void then invoke_func
      else sprintf "%s = %s" (mk_parm_accessor retval_name) invoke_func
  in
    if (is_naked_func fd) && (propagate_errno = false) then
      let check_pms =
        sprintf "if (%s != NULL) return SGX_ERROR_INVALID_PARAMETER;" ms_ptr_name
      in
        sprintf "%s\t%s\n\t%s\n%s" func_open check_pms call_with_pms func_close
      else
        sprintf "%s\t%s\n\t%s\n%s\n%s" func_open declare_ms_ptr call_with_pms set_errno func_close

let fill_ms_field (isptr: bool) (pd: Ast.pdecl) =
  let accessor       = if isptr then "->" else "." in
  let (pt, declr)    = pd in
  let param_name     = declr.Ast.identifier in
  let ms_member_name = mk_ms_member_name param_name in
  let assignment_str (use_cast: bool) (aty: Ast.atype) =
    let cast_str = if use_cast then sprintf "(%s)" (Ast.get_tystr aty) else ""
    in
      sprintf "%s%s%s = %s%s;" ms_struct_val accessor ms_member_name cast_str param_name
  in
  let gen_setup_foreign_array aty =
    sprintf "%s%s%s = (%s *)&%s[0];"
      ms_struct_val accessor ms_member_name (Ast.get_tystr aty) param_name
  in
    if declr.Ast.array_dims = [] then
      match pt with
          Ast.PTVal(aty)        -> assignment_str false aty
        | Ast.PTPtr(aty, pattr) ->
            if pattr.Ast.pa_isary
            then gen_setup_foreign_array aty
            else
              if pattr.Ast.pa_rdonly then assignment_str true aty
              else assignment_str false aty
    else
      (* Arrays are passed by address. *)
      let tystr = Ast.get_tystr (Ast.Ptr (Ast.get_param_atype pt)) in
      sprintf "%s%s%s = (%s)%s;" ms_struct_val accessor ms_member_name tystr param_name

(* Generate untrusted proxy code for a given trusted function. *)
let gen_func_uproxy (fd: Ast.func_decl) (idx: int) (ec: enclave_content) =
  let func_open  =
    gen_uproxy_com_proto fd ec.enclave_name ^
      "\n{\n\tsgx_status_t status;\n"
  in
  let func_close = "\treturn status;\n}\n" in
  let ocall_table_name  = mk_ocall_table_name ec.enclave_name in
  let ms_struct_name  = mk_ms_struct_name fd.Ast.fname in
  let declare_ms_expr = sprintf "%s %s;" ms_struct_name ms_struct_val in
  let ocall_table_ptr =
    sprintf "&%s" ocall_table_name in

  (* Normal case - do ECALL with marshaling structure*)
  let ecall_with_ms = sprintf "status = sgx_ecall(%s, %d, %s, &%s);"
                              eid_name idx ocall_table_ptr ms_struct_val in

  (* Rare case - the trusted function doesn't have parameter nor return value.
   * In this situation, no marshaling structure is required - passing in NULL.
   *)
  let ecall_null = sprintf "status = sgx_ecall(%s, %d, %s, NULL);"
                           eid_name idx ocall_table_ptr
  in
  let update_retval = sprintf "if (status == SGX_SUCCESS && %s) *%s = %s.%s;"
                              retval_name retval_name ms_struct_val ms_retval_name in
  let func_body = ref [] in
    if is_naked_func fd then
      sprintf "%s\t%s\n%s" func_open ecall_null func_close
    else
      begin
        func_body := declare_ms_expr :: !func_body;
        List.iter (fun pd -> func_body := fill_ms_field false pd :: !func_body) fd.Ast.plist;
        func_body := ecall_with_ms :: !func_body;
        if fd.Ast.rtype <> Ast.Void then func_body := update_retval :: !func_body;
          List.fold_left (fun acc s -> acc ^ "\t" ^ s ^ "\n") func_open (List.rev !func_body) ^ func_close
      end

(* Generate an expression to check the pointers. *)
let mk_check_ptr (name: string) (lenvar: string) =
  let checker = "CHECK_UNIQUE_POINTER"
  in sprintf "\t%s(%s, %s);\n" checker name lenvar

(* Pointer to marshaling structure should never be NULL. *)
let mk_check_pms (fname: string) =
  let lenvar = sprintf "sizeof(%s)" (mk_ms_struct_name fname)
  in sprintf "\t%s(%s, %s);\n" "CHECK_REF_POINTER" ms_ptr_name lenvar

(* Generate code to get the size of the pointer. *)
let gen_ptr_size (ty: Ast.atype) (pattr: Ast.ptr_attr) (name: string) (get_parm: string -> string) =
  let len_var = mk_len_var name in
  let parm_name = get_parm name in

  let mk_len_size v =
    match v with
        Ast.AString s -> get_parm s
      | Ast.ANumber n -> sprintf "%d" n in

  let mk_len_count v size_str =
    match v with
        Ast.AString s -> sprintf "%s * %s" (get_parm s) size_str
      | Ast.ANumber n -> sprintf "%d * %s" n size_str in

  let mk_len_sizefunc s = sprintf "((%s) ? %s(%s) : 0)" parm_name s parm_name in

  (* Note, during the parsing stage, we already eliminated the case that
   * user specified both 'size' and 'sizefunc' attribute.
   *)
  let do_attribute (pattr: Ast.ptr_attr) =
    let do_ps_attribute (sattr: Ast.ptr_size) =
      let size_str =
        match sattr.Ast.ps_size with
          Some a -> mk_len_size a
        | None   ->
          match sattr.Ast.ps_sizefunc with
            None   -> sprintf "sizeof(*%s)" parm_name
          | Some a -> mk_len_sizefunc a
      in
        match sattr.Ast.ps_count with
          None   -> size_str
        | Some a -> mk_len_count a size_str
    in
      if pattr.Ast.pa_isstr then
        sprintf "%s ? strlen(%s) + 1 : 0" parm_name parm_name
      else if pattr.Ast.pa_iswstr then
        sprintf "%s ? (wcslen(%s) + 1) * sizeof(wchar_t) : 0" parm_name parm_name
      else
        do_ps_attribute pattr.Ast.pa_size
  in
    sprintf "size_t %s = %s;\n"
      len_var
      (if pattr.Ast.pa_isary
       then sprintf "sizeof(%s)" (Ast.get_tystr ty)
       else do_attribute pattr)

(* Find the data type of a parameter. *)
let find_param_type (name: string) (plist: Ast.pdecl list) =
  try
    let (pt, _) = List.find (fun (pd: Ast.pdecl) ->
                            let (pt, declr) = pd
                            in declr.Ast.identifier = name) plist
    in get_param_tystr pt
  with
     Not_found -> failwithf "parameter `%s' not found." name

(* Generate code to check the length of buffers. *)
let gen_check_tbridge_length_overflow (plist: Ast.pdecl list) =
  let gen_check_length (ty: Ast.atype) (attr: Ast.ptr_attr) (declr: Ast.declarator) =
    let name        = declr.Ast.identifier in
    let tmp_ptr_name= mk_tmp_var name in
 
    let mk_len_size v =
      match v with
        Ast.AString s -> mk_tmp_var s
      | Ast.ANumber n -> sprintf "%d" n in

    let mk_len_sizefunc s = sprintf "((%s) ? %s(%s) : 0)" tmp_ptr_name s tmp_ptr_name in

    let gen_check_overflow cnt size_str =
      let if_statement =
        match cnt with
            Ast.AString s -> sprintf "\tif (%s != 0 &&\n\t\t(size_t)%s > (SIZE_MAX / %s)) {\n" size_str (mk_tmp_var s) size_str
          | Ast.ANumber n -> sprintf "\tif (%s != 0 &&\n\t\t%d > (SIZE_MAX / %s)) {\n" size_str n size_str
      in
        sprintf "%s\t\tstatus = SGX_ERROR_INVALID_PARAMETER;\n\t\tgoto err;\n\t}" if_statement
    in
      let size_str =
        match attr.Ast.pa_size.Ast.ps_size with
          Some a -> mk_len_size a
        | None   ->
          match attr.Ast.pa_size.Ast.ps_sizefunc with
            None   -> sprintf "sizeof(*%s)" tmp_ptr_name
          | Some a -> mk_len_sizefunc a
      in
        match attr.Ast.pa_size.Ast.ps_count with
          None   -> ""
        | Some a -> sprintf "%s\n\n" (gen_check_overflow a size_str) 
  in
    List.fold_left
      (fun acc (pty, declr) ->
         match pty with
             Ast.PTVal _         -> acc
           | Ast.PTPtr(ty, attr) -> acc ^ gen_check_length ty attr declr) "" plist

(* Generate code to check all function parameters which are pointers. *)
let gen_check_tbridge_ptr_parms (plist: Ast.pdecl list) =
  let gen_check_ptr (ty: Ast.atype) (pattr: Ast.ptr_attr) (declr: Ast.declarator) =
    if not pattr.Ast.pa_chkptr then ""
    else
      let name = declr.Ast.identifier in
      let len_var = mk_len_var name in
      let parm_name = mk_tmp_var name in
        if pattr.Ast.pa_chkptr
        then mk_check_ptr parm_name len_var
        else ""
  in
  let new_param_list = List.map conv_array_to_ptr plist
  in
    List.fold_left
      (fun acc (pty, declr) ->
         match pty with
             Ast.PTVal _         -> acc
           | Ast.PTPtr(ty, attr) -> acc ^ gen_check_ptr ty attr declr) "" new_param_list

(* If a foreign type is a readonly pointer, we cast it to 'void*' for memcpy() and free() *)
let mk_in_ptr_dst_name (rdonly: bool) (ptr_name: string) =
  if rdonly then "(void*)" ^ ptr_name
  else ptr_name

(* Generate the code to handle function pointer parameter direction,
 * which is to be inserted before actually calling the trusted function.
 *)
let gen_parm_ptr_direction_pre (plist: Ast.pdecl list) =
  let clone_in_ptr (ty: Ast.atype) (attr: Ast.ptr_attr) (declr: Ast.declarator) =
    let name        = declr.Ast.identifier in
    let is_ary      = (Ast.is_array declr || attr.Ast.pa_isary) in
    let in_ptr_name = mk_in_var name in
    let in_ptr_type = sprintf "%s%s" (Ast.get_tystr ty) (if is_ary then "*"  else "") in
    let len_var     = mk_len_var name in
    let in_ptr_dst_name = mk_in_ptr_dst_name attr.Ast.pa_rdonly in_ptr_name in
    let tmp_ptr_name= mk_tmp_var name in

    let mk_len_count v  =
      match v with
        None -> ""
        |Some a ->
          match a with
            Ast.AString s -> sprintf "_tmp_%s * " s
            | Ast.ANumber n -> sprintf "%d * " n
    in
    let check_sizefunc_with_cnt_ptr v fn =
      sprintf "\t\t/* check whether the pointer is modified. */\n\
\t\tif (%s%s(%s) != %s) {\n\
\t\t\tstatus = SGX_ERROR_INVALID_PARAMETER;\n\
\t\t\tgoto err;\n\
\t\t}" (mk_len_count v) fn in_ptr_name len_var
    in
    let malloc_and_copy pre_indent =
      match attr.Ast.pa_direction with
          Ast.PtrIn | Ast.PtrInOut ->
            let code_template = [
              sprintf "if (%s != NULL && %s != 0) {" tmp_ptr_name len_var;
              sprintf "\t%s = (%s)malloc(%s);" in_ptr_name in_ptr_type len_var;
              sprintf "\tif (%s == NULL) {" in_ptr_name;
              "\t\tstatus = SGX_ERROR_OUT_OF_MEMORY;";
              "\t\tgoto err;";
              "\t}\n";
              sprintf "\tmemcpy(%s, %s, %s);" in_ptr_dst_name tmp_ptr_name len_var;
            ]
            in
            let s1 = List.fold_left (fun acc s -> acc ^ pre_indent ^ s ^ "\n") "" code_template in
            let s2 =
              if attr.Ast.pa_isstr
              then sprintf "%s\t\t%s[%s - 1] = '\\0';\n" s1 in_ptr_name len_var
              else if attr.Ast.pa_iswstr
              then sprintf "%s\t\t%s[(%s - sizeof(wchar_t))/sizeof(wchar_t)] = (wchar_t)0;\n" s1 in_ptr_name len_var
              else s1 in
            let s3 =
              match attr.Ast.pa_size.Ast.ps_sizefunc with
                  None   -> s2
                | Some s -> sprintf "%s\n%s\n" s2 (check_sizefunc_with_cnt_ptr attr.Ast.pa_size.Ast.ps_count s)
            in sprintf "%s\t}\n" s3
        | Ast.PtrOut ->
            let code_template = [
              sprintf "if (%s != NULL && %s != 0) {" tmp_ptr_name len_var;
              sprintf "\tif ((%s = (%s)malloc(%s)) == NULL) {" in_ptr_name in_ptr_type len_var;
              "\t\tstatus = SGX_ERROR_OUT_OF_MEMORY;";
              "\t\tgoto err;";
              "\t}\n";
              sprintf "\tmemset((void*)%s, 0, %s);" in_ptr_name len_var;
              "}"]
            in
              List.fold_left (fun acc s -> acc ^ pre_indent ^ s ^ "\n") "" code_template
        | _ -> ""
    in
      malloc_and_copy "\t"
  in List.fold_left
       (fun acc (pty, declr) ->
          match pty with
              Ast.PTVal _          -> acc
            | Ast.PTPtr (ty, attr) -> acc ^ clone_in_ptr ty attr declr) "" plist

(* Generate the code to handle function pointer parameter direction,
 * which is to be inserted after finishing calling the trusted function.
 *)
let gen_parm_ptr_direction_post (plist: Ast.pdecl list) =
  let copy_and_free (attr: Ast.ptr_attr) (declr: Ast.declarator) =
    let name        = declr.Ast.identifier in
    let in_ptr_name = mk_in_var name in
    let len_var     = mk_len_var name in
    let in_ptr_dst_name = mk_in_ptr_dst_name attr.Ast.pa_rdonly in_ptr_name in
      match attr.Ast.pa_direction with
          Ast.PtrIn -> sprintf "\tif (%s) free(%s);\n" in_ptr_name in_ptr_dst_name
        | Ast.PtrInOut | Ast.PtrOut ->
            sprintf "\tif (%s) {\n\t\tmemcpy(%s, %s, %s);\n\t\tfree(%s);\n\t}\n"
                    in_ptr_name
                    (mk_tmp_var name)
                    in_ptr_name
                    len_var
                    in_ptr_name
        | _ -> ""
  in List.fold_left
       (fun acc (pty, declr) ->
          match pty with
              Ast.PTVal _          -> acc
            | Ast.PTPtr (ty, attr) -> acc ^ copy_and_free attr declr) "" plist


(* Generate an "err:" goto mark if necessary. *)
let gen_err_mark (plist: Ast.pdecl list) =
  let has_inout_p (attr: Ast.ptr_attr): bool =
    attr.Ast.pa_direction <> Ast.PtrNoDirection
  in
    if List.exists (fun (pt, name) ->
                      match pt with
                          Ast.PTVal _        -> false
                        | Ast.PTPtr(_, attr) -> has_inout_p attr) plist
    then "err:"
    else ""

(* It is used to save the parameters used as the value of size/count attribute. *)
let param_cache = Hashtbl.create 1
let is_in_param_cache s = Hashtbl.mem param_cache s

(* Try to generate a temporary value to save the size of the buffer. *)
let gen_tmp_size (pattr: Ast.ptr_attr) (plist: Ast.pdecl list) =
  let do_gen_temp_var (s: string) =
    if is_in_param_cache s then ""
    else
      let param_tystr = find_param_type s plist in
      let tmp_var     = mk_tmp_var s in
      let parm_str    = mk_parm_accessor s in
        Hashtbl.add param_cache s true;
        sprintf "\t%s %s = %s;\n" param_tystr tmp_var parm_str
  in
  let gen_temp_var (v: Ast.attr_value) =
    match v with
        Ast.ANumber _ -> ""
      | Ast.AString s -> do_gen_temp_var s
  in
  let tmp_size_str =
    match pattr.Ast.pa_size.Ast.ps_size with
        Some v -> gen_temp_var v
      | None   -> ""
  in
  let tmp_count_str =
    match pattr.Ast.pa_size.Ast.ps_count with
        Some v -> gen_temp_var v
      | None   -> ""
  in
    sprintf "%s%s" tmp_size_str tmp_count_str

let is_ptr (pt: Ast.parameter_type) =
  match pt with
      Ast.PTVal _ -> false
    | Ast.PTPtr _ -> true

let is_ptr_type (aty: Ast.atype) =
  match aty with
    Ast.Ptr _ -> true
  | _         -> false

let ptr_has_direction (pt: Ast.parameter_type) =
  match pt with
      Ast.PTVal _     -> false
    | Ast.PTPtr(_, a) -> a.Ast.pa_direction <> Ast.PtrNoDirection

let tbridge_mk_parm_name_ext (pt: Ast.parameter_type) (declr: Ast.declarator) =
  if is_in_param_cache declr.Ast.identifier || (is_ptr pt && (not (is_foreign_array pt)))
  then
    if ptr_has_direction pt
    then mk_in_var declr.Ast.identifier
    else mk_tmp_var declr.Ast.identifier
  else mk_parm_name_ext pt declr

let mk_parm_name_tbridge (pt: Ast.parameter_type) (declr: Ast.declarator) =
  add_foreign_array_ptrref tbridge_mk_parm_name_ext pt declr

(* Generate local variables required for the trusted bridge. *)
let gen_tbridge_local_vars (plist: Ast.pdecl list) =
  let status_var = "\tsgx_status_t status = SGX_SUCCESS;\n" in
  let do_gen_local_var (ty: Ast.atype) (attr: Ast.ptr_attr) (name: string) =
    let tmp_var =
      (* Save a copy of pointer in case it might be modified in the marshaling structure. *)
      sprintf "\t%s %s = %s;\n" (Ast.get_tystr ty) (mk_tmp_var name) (mk_parm_accessor name)
    in
    let len_var =
      if not attr.Ast.pa_chkptr then ""
      else gen_tmp_size attr plist ^ "\t" ^ gen_ptr_size ty attr name mk_tmp_var in
    let in_ptr =
      match attr.Ast.pa_direction with
      Ast.PtrNoDirection -> ""
    | _ -> sprintf "\t%s %s = NULL;\n" (Ast.get_tystr ty) (mk_in_var name)
    in
      tmp_var ^ len_var ^ in_ptr
  in
  let gen_local_var_for_foreign_array (ty: Ast.atype) (attr: Ast.ptr_attr) (name: string) =
    let tystr = Ast.get_tystr ty in
    let tmp_var =
      sprintf "\t%s* %s = %s;\n" tystr (mk_tmp_var name) (mk_parm_accessor name)
    in
    let len_var = sprintf "\tsize_t %s = sizeof(%s);\n" (mk_len_var name) tystr
    in
    let in_ptr = sprintf "\t%s* %s = NULL;\n" tystr (mk_in_var name)
    in
      match attr.Ast.pa_direction with
        Ast.PtrNoDirection -> ""
      | _ -> tmp_var ^ len_var ^ in_ptr
  in
  let gen_local_var (pd: Ast.pdecl) =
    let (pty, declr) = pd in
      match pty with
      Ast.PTVal _          -> ""
    | Ast.PTPtr (ty, attr) ->
            if is_foreign_array pty
            then gen_local_var_for_foreign_array ty attr declr.Ast.identifier
            else do_gen_local_var ty attr declr.Ast.identifier
  in
  let new_param_list = List.map conv_array_to_ptr plist
  in
    Hashtbl.clear param_cache;
    List.fold_left (fun acc pd -> acc ^ gen_local_var pd) status_var new_param_list

(* It generates trusted bridge code for a trusted function. *)
let gen_func_tbridge (fd: Ast.func_decl) (dummy_var: string) =
  let func_open = sprintf "static sgx_status_t SGX_CDECL %s(void* %s)\n{\n"
                          (mk_tbridge_name fd.Ast.fname)
                          ms_ptr_name in
  let local_vars = gen_tbridge_local_vars fd.Ast.plist in
  let func_close = "\treturn status;\n}\n" in

  let ms_struct_name = mk_ms_struct_name fd.Ast.fname in
  let declare_ms_ptr = sprintf "%s* %s = SGX_CAST(%s*, %s);"
                               ms_struct_name
                               ms_struct_val
                               ms_struct_name
                               ms_ptr_name in

  let invoke_func   = gen_func_invoking fd mk_parm_name_tbridge in
  let update_retval = sprintf "%s = %s"
                              (mk_parm_accessor retval_name)
                              invoke_func in

    if is_naked_func fd then
      let check_pms =
        sprintf "if (%s != NULL) return SGX_ERROR_INVALID_PARAMETER;" ms_ptr_name
      in
        sprintf "%s%s%s\t%s\n\t%s\n%s" func_open local_vars dummy_var check_pms invoke_func func_close
    else
      sprintf "%s%s\t%s\n%s\n%s%s\n%s\t%s\n%s\n%s\n%s"
        func_open
        (mk_check_pms fd.Ast.fname)
        declare_ms_ptr
        local_vars
        (gen_check_tbridge_length_overflow fd.Ast.plist)
        (gen_check_tbridge_ptr_parms fd.Ast.plist)
        (gen_parm_ptr_direction_pre fd.Ast.plist)
        (if fd.Ast.rtype <> Ast.Void then update_retval else invoke_func)
        (gen_err_mark fd.Ast.plist)
        (gen_parm_ptr_direction_post fd.Ast.plist)
        func_close

let tproxy_fill_ms_field (pd: Ast.pdecl) =
  let (pt, declr)   = pd in
  let name          = declr.Ast.identifier in
  let len_var       = mk_len_var name in
  let parm_accessor = mk_parm_accessor name in
    match pt with
        Ast.PTVal _ -> fill_ms_field true pd
      | Ast.PTPtr(ty, attr) ->
        let is_ary = (Ast.is_array declr || attr.Ast.pa_isary) in
        let tystr = sprintf "%s%s" (get_param_tystr pt) (if is_ary then "*" else "") in
          if is_ary && is_ptr_type ty then
            sprintf "\n#pragma message(\"Pointer array `%s' in trusted proxy `\"\
               __FUNCTION__ \"' is dangerous. No code generated.\")\n" name
          else
            let in_ptr_dst_name = mk_in_ptr_dst_name attr.Ast.pa_rdonly parm_accessor in
              if not attr.Ast.pa_chkptr (* [user_check] specified *)
              then sprintf "%s = SGX_CAST(%s, %s);" parm_accessor tystr name
              else
                match attr.Ast.pa_direction with
                  Ast.PtrOut ->
                    let code_template =
                      [sprintf "if (%s != NULL && sgx_is_within_enclave(%s, %s)) {" name name len_var;
                       sprintf "\t%s = (%s)__tmp;" parm_accessor tystr;
                       sprintf "\t__tmp = (void *)((size_t)__tmp + %s);" len_var;
                       sprintf "\tmemset(%s, 0, %s);" in_ptr_dst_name len_var;
                       sprintf "} else if (%s == NULL) {" name;
                       sprintf "\t%s = NULL;" parm_accessor;
                       "} else {";
                       "\tsgx_ocfree();";
                       "\treturn SGX_ERROR_INVALID_PARAMETER;";
                       "}"
                      ]
                    in List.fold_left (fun acc s -> acc ^ s ^ "\n\t") "" code_template
                | _ ->
                    let code_template =
              [sprintf "if (%s != NULL && sgx_is_within_enclave(%s, %s)) {" name name len_var;
               sprintf "\t%s = (%s)__tmp;" parm_accessor tystr;
               sprintf "\t__tmp = (void *)((size_t)__tmp + %s);" len_var;
               sprintf "\tmemcpy(%s, %s, %s);" in_ptr_dst_name name len_var;
               sprintf "} else if (%s == NULL) {" name;
               sprintf "\t%s = NULL;" parm_accessor;
               "} else {";
               "\tsgx_ocfree();";
               "\treturn SGX_ERROR_INVALID_PARAMETER;";
               "}"
              ]
                    in List.fold_left (fun acc s -> acc ^ s ^ "\n\t") "" code_template

(* Generate local variables required for the trusted proxy. *)
let gen_tproxy_local_vars (plist: Ast.pdecl list) =
  let status_var = "sgx_status_t status = SGX_SUCCESS;\n" in
  let do_gen_local_var (ty: Ast.atype) (attr: Ast.ptr_attr) (name: string) =
    if not attr.Ast.pa_chkptr then ""
    else "\t" ^ gen_ptr_size ty attr name (fun x -> x)
  in
  let gen_local_var (pd: Ast.pdecl) =
    let (pty, declr) = pd in
      match pty with
          Ast.PTVal _          -> ""
        | Ast.PTPtr (ty, attr) -> do_gen_local_var ty attr declr.Ast.identifier
  in
  let new_param_list = List.map conv_array_to_ptr plist
  in
    List.fold_left (fun acc pd -> acc ^ gen_local_var pd) status_var new_param_list

(* Generate only one ocalloc block required for the trusted proxy. *)
let gen_ocalloc_block (fname: string) (plist: Ast.pdecl list) =
  let ms_struct_name = mk_ms_struct_name fname in
  let local_vars_block = sprintf "%s* %s = NULL;\n\tsize_t ocalloc_size = sizeof(%s);\n\tvoid *__tmp = NULL;\n\n" ms_struct_name ms_struct_val ms_struct_name in
  let count_ocalloc_size (ty: Ast.atype) (attr: Ast.ptr_attr) (name: string) =
    if not attr.Ast.pa_chkptr then ""
    else sprintf "\tocalloc_size += (%s != NULL && sgx_is_within_enclave(%s, %s)) ? %s : 0;\n" name name (mk_len_var name) (mk_len_var name)
  in
  let do_count_ocalloc_size (pd: Ast.pdecl) =
    let (pty, declr) = pd in
      match pty with
        Ast.PTVal _          -> ""
      | Ast.PTPtr (ty, attr) -> count_ocalloc_size ty attr declr.Ast.identifier
  in
  let do_gen_ocalloc_block = [
      "\n\t__tmp = sgx_ocalloc(ocalloc_size);\n";
      "\tif (__tmp == NULL) {\n";
      "\t\tsgx_ocfree();\n";
      "\t\treturn SGX_ERROR_UNEXPECTED;\n";
      "\t}\n";
      sprintf "\t%s = (%s*)__tmp;\n" ms_struct_val ms_struct_name;
      sprintf "\t__tmp = (void *)((size_t)__tmp + sizeof(%s));\n" ms_struct_name;
      ]
  in
  let new_param_list = List.map conv_array_to_ptr plist
  in
  let s1 = List.fold_left (fun acc pd -> acc ^ do_count_ocalloc_size pd) local_vars_block new_param_list in
     List.fold_left (fun acc s -> acc ^ s) s1 do_gen_ocalloc_block
  
(* Generate trusted proxy code for a given untrusted function. *)
let gen_func_tproxy (ufunc: Ast.untrusted_func) (idx: int) =
  let fd = ufunc.Ast.uf_fdecl in
  let propagate_errno = ufunc.Ast.uf_propagate_errno in
  let func_open = sprintf "%s\n{\n" (gen_tproxy_proto fd) in
  let local_vars = gen_tproxy_local_vars fd.Ast.plist in
  let ocalloc_ms_struct = gen_ocalloc_block fd.Ast.fname fd.Ast.plist in
  let gen_ocfree rtype plist =
    if rtype = Ast.Void && plist = [] then "" else "\tsgx_ocfree();\n"
  in
  let handle_out_ptr plist =
    let copy_memory (attr: Ast.ptr_attr) (declr: Ast.declarator) =
      let name = declr.Ast.identifier in
        match attr.Ast.pa_direction with
            Ast.PtrInOut | Ast.PtrOut ->
              sprintf "\tif (%s) memcpy((void*)%s, %s, %s);\n" name name (mk_parm_accessor name) (mk_len_var name)
          | _ -> ""
    in List.fold_left (fun acc (pty, declr) ->
             match pty with
                             Ast.PTVal _ -> acc
               | Ast.PTPtr(ty, attr) -> acc ^ copy_memory attr declr) "" plist in

  let set_errno = if propagate_errno then "\terrno = ms->ocall_errno;" else "" in
  let func_close = sprintf "%s%s\n%s%s\n"
                           (handle_out_ptr fd.Ast.plist)
                           set_errno
                           (gen_ocfree fd.Ast.rtype fd.Ast.plist)
                           "\treturn status;\n}" in
  let ocall_null = sprintf "status = sgx_ocall(%d, NULL);\n" idx in
  let ocall_with_ms = sprintf "status = sgx_ocall(%d, %s);\n"
                              idx ms_struct_val in
  let update_retval = sprintf "if (%s) *%s = %s;"
                              retval_name retval_name (mk_parm_accessor retval_name) in
  let func_body = ref [] in
    if (is_naked_func fd) && (propagate_errno = false) then
        sprintf "%s\t%s\t%s%s" func_open local_vars ocall_null func_close
    else
      begin
        func_body := local_vars :: !func_body;
        func_body := ocalloc_ms_struct:: !func_body;
        List.iter (fun pd -> func_body := tproxy_fill_ms_field pd :: !func_body) fd.Ast.plist;
        func_body := ocall_with_ms :: !func_body;
        if fd.Ast.rtype <> Ast.Void then func_body := update_retval :: !func_body;
        List.fold_left (fun acc s -> acc ^ "\t" ^ s ^ "\n") func_open (List.rev !func_body) ^ func_close
      end

(* It generates OCALL table and the untrusted proxy to setup OCALL table. *)
let gen_ocall_table (ec: enclave_content) =
  let func_proto_ubridge = List.map (fun (uf: Ast.untrusted_func) ->
                                       let fd : Ast.func_decl = uf.Ast.uf_fdecl in
                                         mk_ubridge_name ec.file_shortnm fd.Ast.fname)
                                    ec.ufunc_decls in
  let nr_ocall = List.length ec.ufunc_decls in
  let ocall_table_name = mk_ocall_table_name ec.enclave_name in
  let ocall_table =
    let ocall_members =
      List.fold_left
        (fun acc proto -> acc ^ "\t\t(void*)" ^ proto ^ ",\n") "" func_proto_ubridge
    in "\t{\n" ^ ocall_members ^ "\t}\n"
  in
    sprintf "static const struct {\n\
\tsize_t nr_ocall;\n\
\tvoid * table[%d];\n\
} %s = {\n\
\t%d,\n\
%s};\n" (max nr_ocall 1)
      ocall_table_name
      nr_ocall
      (if nr_ocall <> 0 then ocall_table else "\t{ NULL },\n")

(* It generates untrusted code to be saved in a `.c' file. *)
let gen_untrusted_source (ec: enclave_content) =
  let code_fname = get_usource_name ec.file_shortnm in
  let include_hd = "#include \"" ^ get_uheader_short_name ec.file_shortnm ^ "\"\n" in
  let include_errno = "#include <errno.h>\n" in
  let uproxy_list =
    List.map2 (fun fd ecall_idx -> gen_func_uproxy fd ecall_idx ec)
      (tf_list_to_fd_list ec.tfunc_decls)
      (Util.mk_seq 0 (List.length ec.tfunc_decls - 1))
  in
  let ubridge_list =
    List.map (fun fd -> gen_func_ubridge ec.file_shortnm fd)
      (ec.ufunc_decls) in
  let out_chan = open_out code_fname in
    output_string out_chan (include_hd ^ include_errno ^ "\n");
    ms_writer out_chan ec;
    List.iter (fun s -> output_string out_chan (s ^ "\n")) ubridge_list;
    output_string out_chan (gen_ocall_table ec);
    List.iter (fun s -> output_string out_chan (s ^ "\n")) uproxy_list;
    close_out out_chan

(* It generates trusted code to be saved in a `.c' file. *)
let gen_trusted_source (ec: enclave_content) =
  let code_fname = get_tsource_name ec.file_shortnm in
  let include_hd = "#include \"" ^ get_theader_short_name ec.file_shortnm ^ "\"\n\n\
#include \"sgx_trts.h\" /* for sgx_ocalloc, sgx_is_outside_enclave */\n\n\
#include <errno.h>\n\
#include <string.h> /* for memcpy etc */\n\
#include <stdlib.h> /* for malloc/free etc */\n\
\n\
#define CHECK_REF_POINTER(ptr, siz) do {\t\\\n\
\tif (!(ptr) || ! sgx_is_outside_enclave((ptr), (siz)))\t\\\n\
\t\treturn SGX_ERROR_INVALID_PARAMETER;\\\n\
} while (0)\n\
\n\
#define CHECK_UNIQUE_POINTER(ptr, siz) do {\t\\\n\
\tif ((ptr) && ! sgx_is_outside_enclave((ptr), (siz)))\t\\\n\
\t\treturn SGX_ERROR_INVALID_PARAMETER;\\\n\
} while (0)\n\
\n" in
  let trusted_fds = tf_list_to_fd_list ec.tfunc_decls in
  let tbridge_list =
    let dummy_var = tbridge_gen_dummy_variable ec in
    List.map (fun tfd -> gen_func_tbridge tfd dummy_var) trusted_fds in
  let ecall_table = gen_ecall_table ec.tfunc_decls in
  let entry_table = gen_entry_table ec in
  let tproxy_list = List.map2
                      (fun fd idx -> gen_func_tproxy fd idx)
                      (ec.ufunc_decls)
                      (Util.mk_seq 0 (List.length ec.ufunc_decls - 1)) in
  let out_chan = open_out code_fname in
    output_string out_chan (include_hd ^ "\n");
    ms_writer out_chan ec;
    List.iter (fun s -> output_string out_chan (s ^ "\n")) tbridge_list;
    output_string out_chan (ecall_table ^ "\n");
    output_string out_chan (entry_table ^ "\n");
    output_string out_chan "\n";
    List.iter (fun s -> output_string out_chan (s ^ "\n")) tproxy_list;
    close_out out_chan

(* We use a stack to keep record of imported files.
 *
 * A file will be pushed to the stack before we parsing it,
 * and we will pop the stack after each `parse_import_file'.
 *)
let already_read = SimpleStack.create ()
let save_file fullpath =
  if SimpleStack.mem fullpath already_read
  then failwithf "detected circled import for `%s'" fullpath
  else SimpleStack.push fullpath already_read

(* The entry point of the Edger8r parser front-end.
 * ------------------------------------------------
 *)
let start_parsing (fname: string) : Ast.enclave =
  let set_initial_pos lexbuf filename =
    lexbuf.Lexing.lex_curr_p <- {
      lexbuf.Lexing.lex_curr_p with Lexing.pos_fname = fname;
    }
  in
    try
      let fullpath = Util.get_file_path fname in
      let preprocessed =
        save_file fullpath;  Preprocessor.processor_macro(fullpath) in
      let lexbuf = 
        match preprocessed with
          | None -> 
            let chan =  open_in fullpath in
            Lexing.from_channel chan
          | Some(preprocessed_string) -> Lexing.from_string preprocessed_string
      in
        try
          set_initial_pos lexbuf fname;
          let e : Ast.enclave = Parser.start_parsing Lexer.tokenize lexbuf in
          let short_name = Util.get_short_name fname in
            if short_name = ""
            then (eprintf "error: %s: file short name is empty\n" fname; exit 1;)
            else
              let res =  { e with Ast.ename = short_name } in
                if Util.is_c_identifier short_name then res
                else (eprintf "warning: %s: file short name `%s' is not a valid C identifier\n" fname short_name; res)
        with exn ->
          begin match exn with
            | Parsing.Parse_error ->
                let curr = lexbuf.Lexing.lex_curr_p in
                let line = curr.Lexing.pos_lnum in
                let cnum = curr.Lexing.pos_cnum - curr.Lexing.pos_bol in
                let tok = Lexing.lexeme lexbuf in
                  failwithf "%s:%d:%d: unexpected token: %s\n" fname line cnum tok
            | _ -> raise exn
          end
    with Sys_error s -> failwithf "%s\n" s

(* Check duplicated ECALL/OCALL names.
 *
 * This is a pretty simple implementation - to improve it, the
 * location information of each token should be carried to AST.
 *)
let check_duplication (ec: enclave_content) =
  let dict = Hashtbl.create 10 in
  let trusted_fds = tf_list_to_fd_list ec.tfunc_decls in
  let untrusted_fds = uf_list_to_fd_list ec.ufunc_decls in
  let check_and_add fname =
    if Hashtbl.mem dict fname then
      failwithf "Multiple definition of function \"%s\" detected." fname
    else
      Hashtbl.add dict fname true
  in
    List.iter (fun (fd: Ast.func_decl) ->
                 check_and_add fd.Ast.fname) (trusted_fds @ untrusted_fds)

(* For each untrusted functions, check that allowed ECALL does exist. *)
let check_allow_list (ec: enclave_content) =
  let trusted_func_names = get_trusted_func_names ec in
  let do_check_allow_list fname allowed_ecalls =
    List.iter (fun trusted_func ->
                 if List.exists (fun x -> x = trusted_func) trusted_func_names
                 then ()
                 else
                   failwithf "\"%s\" declared to allow unknown function \"%s\"."
                     fname trusted_func) allowed_ecalls
  in
    List.iter (fun (uf: Ast.untrusted_func) ->
                 let fd = uf.Ast.uf_fdecl in
                 let allowed_ecalls = uf.Ast.uf_allow_list in
                   do_check_allow_list fd.Ast.fname allowed_ecalls) ec.ufunc_decls

(* Report private ECALL not used in any "allow(...)" expression. *)
let report_orphaned_priv_ecall (ec: enclave_content) =
  let priv_ecall_names = get_priv_ecall_names ec.tfunc_decls in
  let allowed_names = get_allowed_names ec.ufunc_decls in
  let check_ecall n = if List.mem n allowed_names then ()
                      else eprintf "warning: private ECALL `%s' is not used by any OCALL\n" n
  in
    List.iter check_ecall priv_ecall_names

(* Check that there is at least one public ECALL function. *)
let check_priv_funcs (ec: enclave_content) =
  let priv_bits = tf_list_to_priv_list ec.tfunc_decls in
  if List.for_all (fun is_priv -> is_priv) priv_bits
  then failwithf "the enclave `%s' contains no public root ECALL.\n" ec.file_shortnm
  else report_orphaned_priv_ecall ec

(* When generating edge-routines, it need first to check whether there
 * are `import' expressions inside EDL.  If so, it will parse the given
 * importing file to get an `enclave_content' record, recursively.
 *
 * `ec' is the toplevel `enclave_content' record.

 * Here, a tree reduce algorithm is used. `ec' is the root-node, each
 * `import' expression is considered as a children.
 *)
let reduce_import (ec: enclave_content) =
  let combine (ec1: enclave_content) (ec2: enclave_content) =
    { ec1 with
        include_list = ec1.include_list @ ec2.include_list;
        import_exprs = [];
        comp_defs    = ec1.comp_defs   @ ec2.comp_defs;
        tfunc_decls  = ec1.tfunc_decls @ ec2.tfunc_decls;
        ufunc_decls  = ec1.ufunc_decls @ ec2.ufunc_decls; }
  in
  let parse_import_file fname =
    let ec = parse_enclave_ast (start_parsing fname)
    in
      match ec.import_exprs with
      [] -> (SimpleStack.pop already_read |> ignore; ec )
    | _  -> ec
  in
  let check_funs funcs (ec: enclave_content) =
    (* Check whether `funcs' are listed in `ec'.  It returns a
       production (x, y), where:
         x - functions not listed in `ec';
         y - a new `ec' that contains functions from `funcs' listed in `ec'.
    *)
    let enclave_funcs =
      let trusted_func_names = get_trusted_func_names ec in
      let untrusted_func_names = get_untrusted_func_names ec in
        trusted_func_names @ untrusted_func_names
    in
    let in_ec_def name = List.exists (fun x -> x = name) enclave_funcs in
    let in_import_list name = List.exists (fun x -> x = name) funcs in
    let x = List.filter (fun name -> not (in_ec_def name)) funcs in
    let y =
      { empty_ec with
          tfunc_decls = List.filter (fun tf ->
                                       in_import_list (get_tf_fname tf)) ec.tfunc_decls;
          ufunc_decls = List.filter (fun uf ->
                                       in_import_list (get_uf_fname uf)) ec.ufunc_decls; }
    in (x, y)
  in
  (* Import functions listed in `funcs' from `importee'. *)
  let rec import_funcs (funcs: string list) (importee: enclave_content) =
    (* A `*' means importing all the functions. *)
    if List.exists (fun x -> x = "*") funcs
    then
      List.fold_left (fun acc (ipd: Ast.import_decl) ->
            let next_ec = parse_import_file ipd.Ast.mname
            in combine acc (import_funcs ipd.Ast.flist next_ec)) importee importee.import_exprs
    else
      let (x, y) = check_funs funcs importee
      in
        if x = [] then y                (* Resolved all importings *)
        else
          match importee.import_exprs with
              [] -> failwithf "import failed - functions `%s' not found" (List.hd x)
            | ex -> List.fold_left (fun acc (ipd: Ast.import_decl) ->
                                      let next_ec = parse_import_file ipd.Ast.mname
                                      in combine acc (import_funcs x next_ec)) y ex
  in
    import_funcs ["*"] ec

(* Generate the Enclave code. *)
let gen_enclave_code (e: Ast.enclave) (ep: edger8r_params) =
  let ec = reduce_import (parse_enclave_ast e) in
    g_use_prefix := ep.use_prefix;
    g_untrusted_dir := ep.untrusted_dir;
    g_trusted_dir := ep.trusted_dir;
    create_dir ep.untrusted_dir;
    create_dir ep.trusted_dir;
    check_duplication ec;
    check_allow_list ec;
    (if not ep.header_only then check_priv_funcs ec);
    (if ep.gen_untrusted then (gen_untrusted_header ec; if not ep.header_only then gen_untrusted_source ec));
    (if ep.gen_trusted then (gen_trusted_header ec; if not ep.header_only then gen_trusted_source ec))
