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

{
open Parser

let string_to_int32 s = int_of_string s
}

(* These are some regular expression definitions *)
let open_comment  = "/*"
let close_comment = "*/"
let line_comment = "//"

let space = [' ' '\t']
let newline = ('\n' | '\r' '\n')
let digit = ['0' - '9']
let ident = ['_' 'a' - 'z' 'A' - 'Z']
let identifier = ident(ident|digit)*
let number = digit+
let string = '"' [^'"']* '"'            (* No embedded '\"' *)

rule tokenize = parse
  (* space and newline *)
  | space        { tokenize lexbuf }
  | newline      { Lexing.new_line lexbuf; tokenize lexbuf }

  (* data types *)
  | "char"       { Tchar }
  | "short"      { Tshort }
  | "unsigned"   { Tunsigned }
  | "int"        { Tint }
  | "float"      { Tfloat }
  | "double"     { Tdouble }

  | "int8_t"     { Tint8 }
  | "int16_t"    { Tint16 }
  | "int32_t"    { Tint32 }
  | "int64_t"    { Tint64 }

  | "uint8_t"    { Tuint8 }
  | "uint16_t"   { Tuint16 }
  | "uint32_t"   { Tuint32 }
  | "uint64_t"   { Tuint64 }

  | "size_t"     { Tsizet }
  | "wchar_t"    { Twchar }
  | "long"       { Tlong }
  | "void"       { Tvoid }

  | "struct"     { Tstruct }
  | "union"      { Tunion }
  | "enum"       { Tenum }

  (* specifier *)
  | "enclave"    { Tenclave }
  | "trusted"    { Ttrusted }
  | "untrusted"  { Tuntrusted }
  | "from"       { Tfrom }
  | "import"     { Timport }
  | "allow"      { Tallow }
  | "public"     { Tpublic }
  | "include"    { Tinclude }
  | "propagate_errno"      { Tpropagate_errno }

  (* Type qualifier *)
  | "const"      { Tconst }

  (* symbols *)
  | '{'          { TLBrace }
  | '}'          { TRBrace }
  | '('          { TLParen }
  | ')'          { TRParen }
  | '['          { TLBrack }
  | ']'          { TRBrack }
  | '*'          { TPtr }
  | '.'          { TDot }
  | ','          { TComma }
  | ';'          { TSemicolon }
  | '='          { TEqual }
  | identifier   { Tidentifier(Lexing.lexeme lexbuf) }
  | number       { Tnumber(string_to_int32(Lexing.lexeme lexbuf)) }
  | string       { let s = Lexing.lexeme lexbuf in Tstring(String.sub s 1 (String.length s - 2)) }

  (* comments *)
  | line_comment { eat_until_nl lexbuf }
  | open_comment { comment lexbuf }
  | eof          { EOF }
  | _            { failwith ("Invalid token: " ^ Lexing.lexeme(lexbuf)) }
and eat_until_nl = parse
  | newline       { Lexing.new_line lexbuf; tokenize lexbuf }
  | _             { eat_until_nl lexbuf }
and comment = parse             (* comments can't be nested *)
    close_comment { tokenize lexbuf }
  | newline       { Lexing.new_line lexbuf; comment lexbuf }
  | _             { comment lexbuf }
