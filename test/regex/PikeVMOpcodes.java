/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package regex;

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

  final static int CHARACTER_CLASS = -20;

  final static int LOOKAHEAD = -30;

  final static int SAVE_OFFSET = -40;

  final static int SPLIT = -50;
  final static int SPLIT_JMP = -51; // this split prefers to jump
  final static int JMP = -52;

  final static int SINGLE_ARG_START = CHARACTER_CLASS;
  final static int SINGLE_ARG_END = JMP;
}
