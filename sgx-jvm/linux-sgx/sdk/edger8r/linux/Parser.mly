/*
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
 */

%{
open Util				(* for failwithf *)

(* Here we defined some helper routines to check attributes.
 *
 * An alternative approach is to code these rules in Lexer/Parser but
 * it has several drawbacks:
 *
 * 1. Bad extensibility;
 * 2. It grows the table size and down-graded the parsing time;
 * 3. It makes error reporting rigid this way.
 *)

let get_string_from_attr (v: Ast.attr_value) (err_func: int -> string) =
  match v with
      Ast.AString s -> s
    | Ast.ANumber n -> err_func n

(* Check whether 'size' or 'sizefunc' is specified. *)
let has_size (sattr: Ast.ptr_size) =
  sattr.Ast.ps_size <> None || sattr.Ast.ps_sizefunc <> None

(* Pointers can have the following attributes:
 *
 * 'size'     - specifies the size of the pointer.
 *              e.g. size = 4, size = val ('val' is a parameter);
 *
 * 'count'    - indicates how many of items is managed by the pointer
 *              e.g. count = 100, count = n ('n' is a parameter);
 *
 * 'sizefunc' - use a function to compute the size of the pointer.
 *              e.g. sizefunc = get_ptr_size
 *
 * 'string'   - indicate the pointer is managing a C string;
 * 'wstring'  - indicate the pointer is managing a wide char string.
 *
 * 'isptr'    - to specify that the foreign type is a pointer.
 * 'isary'    - to specify that the foreign type is an array.
 * 'readonly' - to specify that the foreign type has a 'const' qualifier.
 *
 * 'user_check' - inhibit Edger8r from generating code to check the pointer.
 *
 * 'in'       - the pointer is used as input
 * 'out'      - the pointer is used as output
 *
 * Note that 'size' and 'sizefunc' are mutual exclusive (but they can
 * be used together with 'count'.  'string' and 'wstring' indicates 'isptr',
 * and they cannot use with only an 'out' attribute.
 *)
let get_ptr_attr (attr_list: (string * Ast.attr_value) list) =
  let get_new_dir (cds: string) (cda: Ast.ptr_direction) (old: Ast.ptr_direction) =
    if old = Ast.PtrNoDirection then cda
    else if old = Ast.PtrInOut  then failwithf "duplicated attribute: `%s'" cds
    else if old = cda           then failwithf "duplicated attribute: `%s'" cds
    else Ast.PtrInOut
  in
  let update_attr (key: string) (value: Ast.attr_value) (res: Ast.ptr_attr) =
    match key with
        "size"     ->
        { res with Ast.pa_size = { res.Ast.pa_size with Ast.ps_size  = Some value }}
      | "count"    ->
        { res with Ast.pa_size = { res.Ast.pa_size with Ast.ps_count = Some value }}
      | "sizefunc" ->
        let efn n = failwithf "invalid function name (%d) for `sizefunc'" n in
        let funcname = get_string_from_attr value efn
        in { res with Ast.pa_size =
            { res.Ast.pa_size with Ast.ps_sizefunc = Some funcname }}
      | "string"  -> { res with Ast.pa_isptr = true; Ast.pa_isstr = true; }
      | "wstring" -> { res with Ast.pa_isptr = true; Ast.pa_iswstr = true; }
      | "isptr"   -> { res with Ast.pa_isptr = true }
      | "isary"   -> { res with Ast.pa_isary = true }

      | "readonly" -> { res with Ast.pa_rdonly = true }
      | "user_check" -> { res with Ast.pa_chkptr = false }

      | "in"  ->
        let newdir = get_new_dir "in"  Ast.PtrIn  res.Ast.pa_direction
        in { res with Ast.pa_direction = newdir }
      | "out" ->
        let newdir = get_new_dir "out" Ast.PtrOut res.Ast.pa_direction
        in { res with Ast.pa_direction = newdir }
      | _ -> failwithf "unknown attribute: %s" key
  in
  let rec do_get_ptr_attr alist res_attr =
    match alist with
        [] -> res_attr
      | (k,v) :: xs -> do_get_ptr_attr xs (update_attr k v res_attr)
  in
  let has_str_attr (pattr: Ast.ptr_attr) =
    if pattr.Ast.pa_isstr && pattr.Ast.pa_iswstr
    then failwith "`string' and `wstring' are mutual exclusive"
    else (pattr.Ast.pa_isstr || pattr.Ast.pa_iswstr)
  in
  let check_invalid_ptr_size (pattr: Ast.ptr_attr) =
    let ps = pattr.Ast.pa_size in
      if ps.Ast.ps_size <> None && ps.Ast.ps_sizefunc <> None
      then failwith  "`size' and `sizefunc' cannot be used at the same time"
      else
        if ps <> Ast.empty_ptr_size && has_str_attr pattr
        then failwith "size attributes are mutual exclusive with (w)string attribute"
        else
          if (ps <> Ast.empty_ptr_size || has_str_attr pattr) &&
            pattr.Ast.pa_direction = Ast.PtrNoDirection
          then failwith "size/string attributes must be used with pointer direction"
          else pattr
  in
  let check_ptr_dir (pattr: Ast.ptr_attr) =
    if pattr.Ast.pa_direction <> Ast.PtrNoDirection && pattr.Ast.pa_chkptr = false
    then failwith "pointer direction and `user_check' are mutual exclusive"
    else
      if pattr.Ast.pa_direction = Ast.PtrNoDirection && pattr.Ast.pa_chkptr
      then failwith "pointer/array should have direction attribute or `user_check'"
      else
        if pattr.Ast.pa_direction = Ast.PtrOut && (has_str_attr pattr || pattr.Ast.pa_size.Ast.ps_sizefunc <> None)
        then failwith "string/wstring/sizefunc should be used with an `in' attribute"
        else pattr
  in
  let check_invalid_ary_attr (pattr: Ast.ptr_attr) =
    if pattr.Ast.pa_size <> Ast.empty_ptr_size
    then failwith "Pointer size attributes cannot be used with foreign array"
    else
      if not pattr.Ast.pa_isptr
      then
        (* 'pa_chkptr' is default to true unless user specifies 'user_check' *)
        if pattr.Ast.pa_chkptr && pattr.Ast.pa_direction = Ast.PtrNoDirection
        then failwith "array must have direction attribute or `user_check'"
        else pattr
      else
        if has_str_attr pattr
        then failwith "`isary' cannot be used with `string/wstring' together"
        else failwith "`isary' cannot be used with `isptr' together"
  in
  let pattr = do_get_ptr_attr attr_list { Ast.pa_direction = Ast.PtrNoDirection;
                                          Ast.pa_size = Ast.empty_ptr_size;
                                          Ast.pa_isptr = false;
                                          Ast.pa_isary = false;
                                          Ast.pa_isstr = false;
                                          Ast.pa_iswstr = false;
                                          Ast.pa_rdonly = false;
                                          Ast.pa_chkptr = true;
                                        }
  in
    if pattr.Ast.pa_isary
    then check_invalid_ary_attr pattr
    else check_invalid_ptr_size pattr |> check_ptr_dir

(* Untrusted functions can have these attributes:
 *
 * a. 3 mutual exclusive calling convention specifier:
 *     'stdcall', 'fastcall', 'cdecl'.
 *
 * b. 'dllimport' - to import a public symbol.
 *)
let get_func_attr (attr_list: (string * Ast.attr_value) list) =
  let get_new_callconv (key: string) (cur: Ast.call_conv) (old: Ast.call_conv) =
    if old <> Ast.CC_NONE then
      failwithf "unexpected `%s',  conflict with `%s'." key (Ast.get_call_conv_str old)
    else cur
  in
  let update_attr (key: string) (value: Ast.attr_value) (res: Ast.func_attr) =
    match key with
    | "stdcall"  ->
      let callconv = get_new_callconv key Ast.CC_STDCALL res.Ast.fa_convention
      in { res with Ast.fa_convention = callconv}
    | "fastcall" ->
      let callconv = get_new_callconv key Ast.CC_FASTCALL res.Ast.fa_convention
      in { res with Ast.fa_convention = callconv}
    | "cdecl"    ->
      let callconv = get_new_callconv key Ast.CC_CDECL res.Ast.fa_convention
      in { res with Ast.fa_convention = callconv}
    | "dllimport" ->
      if res.Ast.fa_dllimport then failwith "duplicated attribute: `dllimport'"
      else { res with Ast.fa_dllimport = true }
    | _ -> failwithf "invalid function attribute: %s" key
  in
  let rec do_get_func_attr alist res_attr =
    match alist with
      [] -> res_attr
    | (k,v) :: xs -> do_get_func_attr xs (update_attr k v res_attr)
  in do_get_func_attr attr_list { Ast.fa_dllimport = false;
                                  Ast.fa_convention= Ast.CC_NONE;
                                }

(* Some syntax checking against pointer attributes.
 * range: (Lexing.position * Lexing.position)
 *)
let check_ptr_attr (fd: Ast.func_decl) range =
  let fname = fd.Ast.fname in
  let check_const (pattr: Ast.ptr_attr) (identifier: string) =
    let raise_err_direction (direction:string) =
      failwithf "`%s': `%s' is readonly - cannot be used with `%s'"
        fname identifier direction
    in
      if pattr.Ast.pa_rdonly
      then
        match pattr.Ast.pa_direction with
            Ast.PtrOut | Ast.PtrInOut -> raise_err_direction "out"
          | _ -> ()
      else ()
  in
  let check_void_ptr_size (pattr: Ast.ptr_attr) (identifier: string) =
    if pattr.Ast.pa_chkptr && (not (has_size pattr.Ast.pa_size))
    then failwithf "`%s': void pointer `%s' - buffer size unknown" fname identifier
    else ()
  in
  let checker (pd: Ast.pdecl) =
    let pt, declr = pd in
    let identifier = declr.Ast.identifier in
      match pt with
          Ast.PTVal _ -> ()
        | Ast.PTPtr(atype, pattr) ->
          if atype <> Ast.Ptr(Ast.Void) then check_const pattr identifier
          else (* 'void' pointer, check there is a size or 'user_check' *)
            check_void_ptr_size pattr identifier
  in
    List.iter checker fd.Ast.plist
%}

%token EOF
%token TDot TComma TSemicolon TPtr TEqual
%token TLParen TRParen
%token TLBrace TRBrace
%token TLBrack TRBrack
%token Tpublic
%token Tinclude
%token Tconst
%token <string>Tidentifier
%token <int>Tnumber
%token <string>Tstring
%token Tchar Tshort Tunsigned Tint Tfloat Tdouble
       Tint8 Tint16 Tint32 Tint64
       Tuint8 Tuint16 Tuint32 Tuint64
       Tsizet Twchar Tvoid Tlong Tstruct Tunion Tenum
%token Tenclave Tfrom Timport Ttrusted Tuntrusted Tallow Tpropagate_errno

%start start_parsing
%type <Ast.enclave> start_parsing

/* Grammar follows */
%%

/* Type definitions
 * ------------------------------------------------------------------------
 */
char_type: Tchar { Ast.Char Ast.Signed }
  | Tunsigned Tchar { Ast.Char Ast.Unsigned }
  ;

/* Explicit shortness. */
ex_shortness: Tshort { Ast.IShort }
  | Tlong { Ast.ILong }
  ;

longlong: Tlong Tlong     { Ast.LLong Ast.Signed }
  | Tunsigned Tlong Tlong { Ast.LLong Ast.Unsigned }

shortness: /* empty */ { Ast.INone }
  | ex_shortness { $1 }
  ;

int_type: shortness Tint {
      Ast.Int { Ast.ia_signedness = Ast.Signed; Ast.ia_shortness = $1 }
    }
  | Tunsigned shortness Tint {
      Ast.Int { Ast.ia_signedness = Ast.Unsigned; Ast.ia_shortness = $2 }
    }
  | Tunsigned shortness {
      Ast.Int { Ast.ia_signedness = Ast.Unsigned; Ast.ia_shortness = $2 }
    }
  | longlong { $1 }
  | ex_shortness {
      Ast.Int { Ast.ia_signedness = Ast.Signed; Ast.ia_shortness = $1 }
    }
  ;

type_spec:
    char_type { $1 }
  | int_type  { $1 }

  | Tfloat   { Ast.Float }
  | Tdouble  { Ast.Double }
  | Tlong Tdouble { Ast.LDouble }

  | Tint8    { Ast.Int8 }
  | Tint16   { Ast.Int16 }
  | Tint32   { Ast.Int32 }
  | Tint64   { Ast.Int64 }
  | Tuint8   { Ast.UInt8 }
  | Tuint16  { Ast.UInt16 }
  | Tuint32  { Ast.UInt32 }
  | Tuint64  { Ast.UInt64 }
  | Tsizet   { Ast.SizeT }
  | Twchar   { Ast.WChar }
  | Tvoid    { Ast.Void }

  | struct_specifier { $1 }
  | union_specifier  { $1 }
  | enum_specifier   { $1 }
  | Tidentifier      { Ast.Foreign($1) } /* User defined types in C header */
  ;

pointer: TPtr    { fun ii -> Ast.Ptr(ii) }
  | pointer TPtr { fun ii -> Ast.Ptr($1 ii) }
  ;

empty_dimension: TLBrack TRBrack         { failwith "Flexible array is not supported." }
fixed_dimension: TLBrack Tnumber TRBrack { if $2 <> 0 then [$2]
                                           else failwith "Zero-length array is not supported." }

fixed_size_array: fixed_dimension    { $1 }
  | fixed_size_array fixed_dimension { $1 @ $2 }
  ;

array_size: fixed_size_array         { $1 }
  | empty_dimension                  { $1 }
  | empty_dimension fixed_size_array { $1 @ $2 }
  ;

all_type: type_spec   { $1 }
  | type_spec pointer { $2 $1 }
  ;

declarator: Tidentifier    { { Ast.identifier = $1; Ast.array_dims = []; } }
  | Tidentifier array_size { { Ast.identifier = $1; Ast.array_dims = $2; } }
  ;

/* Available types as parameter.
 *
 * Instead of returning an value of 'Ast.parameter_type', we return
 * a lambda which wraps the actual type since so far there is no way
 * to tell whether the identifier is followed by array dimensions.
*/
param_type: attr_block all_type {
    match $2 with
      Ast.Ptr _ -> fun x -> Ast.PTPtr($2, get_ptr_attr $1)
    | _         ->
      if $1 <> [] then
        let attr = get_ptr_attr $1 in
        match $2 with
          Ast.Foreign s ->
            if attr.Ast.pa_isptr || attr.Ast.pa_isary then fun x -> Ast.PTPtr($2, attr)
            else
              (* thinking about 'user_defined_type var[4]' *)
              fun is_ary ->
                if is_ary then Ast.PTPtr($2, attr)
                else failwithf "`%s' is considered plain type but decorated with pointer attributes" s
        | _ ->
          fun is_ary ->
            if is_ary then Ast.PTPtr($2, attr)
            else failwithf "unexpected pointer attributes for `%s'" (Ast.get_tystr $2)
      else
        fun is_ary ->
          if is_ary then Ast.PTPtr($2, get_ptr_attr [])
          else  Ast.PTVal $2
    }
  | all_type {
    match $1 with
      Ast.Ptr _ -> fun x -> Ast.PTPtr($1, get_ptr_attr [])
    | _         ->
      fun is_ary ->
        if is_ary then Ast.PTPtr($1, get_ptr_attr [])
        else  Ast.PTVal $1
    }
  | attr_block Tconst type_spec pointer {
      let attr = get_ptr_attr $1
      in fun x -> Ast.PTPtr($4 $3, { attr with Ast.pa_rdonly = true })
    }
  | Tconst type_spec pointer {
      let attr = get_ptr_attr []
      in fun x -> Ast.PTPtr($3 $2, { attr with Ast.pa_rdonly = true })
    }
  ;


attr_block: TLBrack TRBrack       { failwith "no attribute specified." }
  | TLBrack key_val_pairs TRBrack { $2 }
  ;

key_val_pairs: key_val_pair           { [$1] }
  | key_val_pairs TComma key_val_pair {  $3 :: $1 }
  ;

key_val_pair: Tidentifier TEqual Tidentifier { ($1, Ast.AString($3)) }
  | Tidentifier TEqual Tnumber               { ($1, Ast.ANumber($3)) }
  | Tidentifier                              { ($1, Ast.AString("")) }
  ;

struct_specifier: Tstruct Tidentifier { Ast.Struct($2) }
union_specifier:  Tunion  Tidentifier { Ast.Union($2) }
enum_specifier:   Tenum   Tidentifier { Ast.Enum($2) }

struct_definition: struct_specifier TLBrace member_list TRBrace {
    let s = { Ast.sname = (match $1 with Ast.Struct s -> s | _ -> "");
              Ast.mlist = List.rev $3; }
    in Ast.StructDef(s)
  }

union_definition: union_specifier TLBrace member_list TRBrace {
    let s = { Ast.sname = (match $1 with Ast.Union s -> s | _ -> "");
              Ast.mlist = List.rev $3; }
    in Ast.UnionDef(s)
  }

/* enum can be anonymous. */
enum_definition: Tenum TLBrace enum_body TRBrace {
      let e = { Ast.enname = ""; Ast.enbody = $3; }
      in Ast.EnumDef(e)
    }
  | enum_specifier TLBrace enum_body TRBrace {
      let e = { Ast.enname = (match $1 with Ast.Enum s -> s | _ -> "");
                Ast.enbody = $3; }
      in Ast.EnumDef(e)
    }
  ;

enum_body: /* empty */ { [] }
  | enum_eles          { List.rev $1 }
  ;

enum_eles: enum_ele           { [$1] }
  | enum_eles TComma enum_ele { $3 :: $1 }
  ;

enum_ele: Tidentifier              { ($1, Ast.EnumValNone) }
  | Tidentifier TEqual Tidentifier { ($1, Ast.EnumVal (Ast.AString $3)) }
  | Tidentifier TEqual Tnumber     { ($1, Ast.EnumVal (Ast.ANumber $3)) }
  ;

composite_defs: struct_definition     { $1 }
  | union_definition                  { $1 }
  | enum_definition                   { $1 }
  ;

member_list: member_def TSemicolon    { [$1] }
  | member_list member_def TSemicolon { $2 :: $1 }
  ;

member_def: all_type declarator { ($1, $2) }

/* Importing declarations.
 * ------------------------------------------------------------------------
 */
func_list: Tidentifier            { [$1] }
  | func_list TComma Tidentifier  { $3 :: $1 }
  ;

module_path: Tstring              { $1 }

import_declaration: Tfrom module_path Timport  func_list {
      { Ast.mname = $2; Ast.flist = List.rev $4; }
    }
  | Tfrom module_path Timport TPtr {
      { Ast.mname = $2; Ast.flist = ["*"]; }
    }
  ;

include_declaration: Tinclude Tstring { $2 }

include_declarations: include_declaration    { [$1] }
  | include_declarations include_declaration { $2 :: $1 }
  ;

/* Enclave function declarations.
 * ------------------------------------------------------------------------
 */
enclave_functions: Ttrusted TLBrace trusted_block TRBrace TSemicolon {
      List.rev $3
    }
  | Tuntrusted TLBrace untrusted_block TRBrace TSemicolon {
      List.rev $3
    }
  ;

trusted_block: trusted_functions             { $1 }
  | include_declarations trusted_functions   {
      trusted_headers := !trusted_headers @ List.rev $1; $2
    }
  ;

untrusted_block: untrusted_functions         { $1 }
  | include_declarations untrusted_functions {
      untrusted_headers := !untrusted_headers @ List.rev $1; $2
    }
  ;

/* is_priv? Default to true. */
access_modifier: /* nothing */ { true }
  | Tpublic                    { false  }
  ;

trusted_functions: /* nothing */          { [] }
  | trusted_functions access_modifier func_def TSemicolon {
      check_ptr_attr $3 (symbol_start_pos(), symbol_end_pos());
      Ast.Trusted { Ast.tf_fdecl = $3; Ast.tf_is_priv = $2 } :: $1
    }
  ;

untrusted_functions: /* nothing */                    { [] }
  | untrusted_functions untrusted_func_def TSemicolon { $2 :: $1 }
  ;

func_def: all_type Tidentifier parameter_list {
      { Ast.fname = $2; Ast.rtype = $1; Ast.plist = List.rev $3 ; }
    }
  | all_type array_size Tidentifier parameter_list {
      failwithf "%s: returning an array is not supported - use pointer instead." $3
    }
  ;

parameter_list: TLParen TRParen    { [] }
  | TLParen Tvoid TRParen          { [] } /* Make C programers comfortable */
  | TLParen parameter_defs TRParen { $2 }
  ;

parameter_defs: parameter_def           { [$1] }
  | parameter_defs TComma parameter_def { $3 :: $1 }
  ;

parameter_def: param_type declarator {
    let pt = $1 (Ast.is_array $2) in
    let is_void =
      match pt with
          Ast.PTVal v -> v = Ast.Void
        | _           -> false
    in
      if is_void then
        failwithf "parameter `%s' has `void' type." $2.Ast.identifier
      else
        (pt, $2)
  }

/* propagate_errno? Default to false. */
propagate_errno: /* nothing */ { false }
  | Tpropagate_errno           { true  }
  ;

untrusted_func_def: attr_block func_def allow_list propagate_errno {
      check_ptr_attr $2 (symbol_start_pos(), symbol_end_pos());
      let fattr = get_func_attr $1 in
      Ast.Untrusted { Ast.uf_fdecl = $2; Ast.uf_fattr = fattr; Ast.uf_allow_list = $3; Ast.uf_propagate_errno = $4 }
    }
  | func_def allow_list propagate_errno {
      check_ptr_attr $1 (symbol_start_pos(), symbol_end_pos());
      let fattr = get_func_attr [] in
      Ast.Untrusted { Ast.uf_fdecl = $1; Ast.uf_fattr = fattr; Ast.uf_allow_list = $2; Ast.uf_propagate_errno = $3 }
    }
  ;

allow_list: /* nothing */            { [] }
  | Tallow TLParen TRParen           { [] }
  | Tallow TLParen func_list TRParen { $3 }
  ;

/* Enclave definition
 * ------------------------------------------------------------------------
 */
expressions: /* nothing */ { [] }
  | expressions include_declaration           { Ast.Include($2)   :: $1 }
  | expressions import_declaration TSemicolon { Ast.Importing($2) :: $1 }
  | expressions composite_defs TSemicolon     { Ast.Composite($2) :: $1 }
  | expressions enclave_functions             { Ast.Interface($2) :: $1 }
  ;

enclave_def: Tenclave TLBrace expressions TRBrace {
      { Ast.ename = "";
        Ast.eexpr = List.rev $3 }
    }
  ;

/* The entry point of parser.
 * ------------------------------------------------------------------------
 */
start_parsing: enclave_def TSemicolon EOF { $1 }

%%
