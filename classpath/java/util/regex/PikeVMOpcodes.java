/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.regex;

/**
 * Opcodes for the Pike VM.
 * <p>
 * See {@link PikeVM}.
 * </p>
 * 
 * @author Johannes Schindelin
 */
interface PikeVMOpcodes {
  final static int DOT = -1;
  final static int DOTALL = -2;

  final static int WORD_BOUNDARY = -10;
  final static int NON_WORD_BOUNDARY = -11;
  final static int LINE_START = -12;
  final static int LINE_END = -13;

  final static int CHARACTER_CLASS = -20;

  final static int LOOKAHEAD = -30;
  final static int LOOKBEHIND = -31;
  final static int NEGATIVE_LOOKAHEAD = -32;
  final static int NEGATIVE_LOOKBEHIND = -33;

  final static int SAVE_OFFSET = -40;

  final static int SPLIT = -50;
  final static int SPLIT_JMP = -51; // this split prefers to jump
  final static int JMP = -52;

  final static int SINGLE_ARG_START = CHARACTER_CLASS;
  final static int SINGLE_ARG_END = JMP;
}
