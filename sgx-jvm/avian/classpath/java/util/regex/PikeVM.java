/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.regex;

/**
 * A minimal implementation of a regular expression engine.
 * 
 * @author Johannes Schindelin
 */
class PikeVM implements PikeVMOpcodes {
  private final int[] program;
  private final int groupCount;
  private final int offsetsCount;
  /*
   * For find(), we do not want to anchor the match at the start offset. Our
   * compiler allows this by prefixing the code with an implicit '(?:.*?)'. For
   * regular matches() calls, we want to skip that code and start at {@code
   * findPrefixLength} instead.
   */
  private final int findPrefixLength;
  private final CharacterMatcher[] classes;
  private final PikeVM[] lookarounds;
  private final static CharacterMatcher wordCharacter =
    CharacterMatcher.parse("\\w");
  private final static CharacterMatcher lineTerminator =
    CharacterMatcher.parse("[\n\r\u0085\u2028\u2029]");
  private boolean multiLine;

  public interface Result {
    void set(int[] start, int[] end);
  }

  protected PikeVM(int[] program, int findPrefixLength, int groupCount,
    CharacterMatcher[] classes, PikeVM[] lookarounds)
  {
    this.program = program;
    this.findPrefixLength = findPrefixLength;
    this.groupCount = groupCount;
    offsetsCount = 2 * groupCount + 2;
    this.classes = classes;
    this.lookarounds = lookarounds;
  }

  /**
   * The current thread states.
   * <p>
   * The threads are identified by their program counter. The rationale: as all
   * threads are executed in lock-step, i.e. for the same character in the
   * string to be matched, it does not make sense for two threads to be at the
   * same program counter -- they would both do exactly the same for the rest of
   * the execution.
   * </p>
   * <p>
   * For efficiency, the threads are kept in a linked list that actually lives
   * in an array indexed by the program counter, pointing to the next thread's
   * program counter, in the order of high to low priority.
   * </p>
   * <p>
   * Program counters which have no thread associated thread are marked as -1.
   * The program counter associated with the least-priority thread (the last one
   * in the linked list) is marked as -2 to be able to tell it apart from
   * unscheduled threads.
   * </p>
   * <p>
   * We actually never need to have an explicit value for the priority, the
   * ordering is sufficient: whenever a new thread is to be scheduled and it is
   * found to be scheduled already, it was already scheduled by a
   * higher-priority thread.
   * </p>
   */
  private class ThreadQueue {
    private int head, tail;
    // next[pc] is 1 + the next thread's pc
    private int[] next;
    // offsets[pc][2 * group] is 1 + start offset
    private int[][] offsets;

    public ThreadQueue() {
      head = tail = -1;
      next = new int[program.length + 1];
      offsets = new int[program.length + 1][];
    }

    public ThreadQueue(int startPC) {
      head = tail = startPC;
      next = new int[program.length + 1];
      offsets = new int[program.length + 1][];
      offsets[head] = new int[offsetsCount];
    }

    public int queueOneImmediately(ThreadQueue into) {
      for (;;) {
        if (head < 0) {
          return -1;
        }
        boolean wasQueued = queueNext(head, head, into);
        int pc = head;
        if (head == tail) {
          head = tail = -1;
        } else {
          head = next[pc] - 1;
          next[pc] = 0;
        }
        offsets[pc] = null;
        if (wasQueued) {
          into.tail = pc;
          return pc;
        }
      }
    }

    /**
     * Schedules the instruction at {@code nextPC} to be executed immediately.
     * <p>
     * For non-matching steps (SPLIT, SAVE_STATE, etc) we need to schedule the
     * corresponding program counter(s) to be handled right after this opcode,
     * before advancing to the next character.
     * </p>
     * <p>
     * To achieve this, we insert the program counter to-be-scheduled in the
     * linked thread list at the current position, but only if it has not been
     * scheduled yet: if it has, a higher-priority thread already reached that
     * state.
     * </p>
     * <p>
     * In contrast to {@link #queueNext(int, int, ThreadQueue)}, this method
     * works on the current step's thread list.
     * </p>
     * 
     * @param currentPC
     *          the current program counter
     * @param nextPC
     *          the program counter to schedule
     * @param copyThreadState
     *          whether to spawn off a new thread
     * @return whether the step was queued (i.e. no thread was queued for the
     *         same {@code nextPC} already)
     */
    public boolean queueImmediately(int currentPC, int nextPC,
        boolean copyThreadState) {
      if (isScheduled(nextPC)) {
        return false;
      }
      int[] offsets = this.offsets[currentPC];
      if (copyThreadState) {
        offsets = java.util.Arrays.copyOf(offsets, offsetsCount);
      }
      if (currentPC == tail) {
        tail = nextPC;
      } else {
        next[nextPC] = next[currentPC];
      }
      this.offsets[nextPC] = offsets;
      next[currentPC] = nextPC + 1;
      return true;
    }

    /**
     * Schedules the instruction at {@code nextPC} to be executed in the next
     * step.
     * <p>
     * This method advances the current thread to the next program counter, to
     * be executed after reading the next character.
     * </p>
     * 
     * @param currentPC
     *          the current program counter
     * @param nextPC
     *          the program counter to schedule
     * @param next
     *          the thread state of the next step
     * @return whether the step was queued (i.e. no thread was queued for the
     *         same {@code nextPC} already)
     */
    private boolean queueNext(int currentPC, int nextPC, ThreadQueue next) {
      if (next.tail < 0) {
        next.head = nextPC;
      } else if (next.isScheduled(nextPC)) {
        return false;
      } else {
        next.next[next.tail] = nextPC + 1;
      }
      next.offsets[nextPC] = offsets[currentPC];
      next.tail = nextPC;
      return true;
    }

    public void saveOffset(int pc, int index, int offset) {
      offsets[pc][index] = offset + 1;
    }

    public void setResult(Result result) {
      // copy offsets
      int[] offsets = this.offsets[program.length];
      int[] groupStart = new int[groupCount + 1];
      int[] groupEnd = new int[groupCount + 1];
      for (int j = 0; j <= groupCount; ++j) {
        groupStart[j] = offsets[2 * j] - 1;
        groupEnd[j] = offsets[2 * j + 1] - 1;
      }
      result.set(groupStart, groupEnd);
    }

    private void mustStartMatchAt(int start) {
      int previous = -1;
      for (int pc = head; pc >= 0; ) {
        int nextPC = next[pc] - 1;
        if (start + 1 == offsets[pc][0]) {
          previous = pc;
        } else {
          next[pc] = 0;
          offsets[pc] = null;
          if (pc == tail) {
            head = tail = -1;
          } else if (previous < 0) {
            head = nextPC;
          } else {
            next[previous] = 1 + nextPC;
          }
        }
        pc = nextPC;
      }
    }

    private int startOffset(int pc) {
      return offsets[pc][0] - 1;
    }

    public boolean isEmpty() {
      return head < 0;
    }

    public boolean isScheduled(int pc) {
      return pc == tail || next[pc] > 0;
    }

    public int next(int pc) {
      return pc < 0 ? head : next[pc] - 1;
    }

    public void clean() {
      for (int pc = head; pc >= 0; ) {
        int nextPC = next[pc] - 1;
        next[pc] = 0;
        offsets[pc] = null;
        pc = nextPC;
      }
      head = tail = -1;
    }
  }

  /**
   * Executes the Pike VM defined by the program.
   * <p>
   * The idea is to execute threads in parallel, at each step executing them
   * from the highest priority thread to the lowest one. In contrast to most
   * regular expression engines, the Thompson/Pike one gets away with linear
   * complexity because the string is matched from left to right, at each step
   * executing a number of threads bounded by the length of the program: if two
   * threads would execute at the same instruction pointer of the program, we
   * need only consider the higher-priority one.
   * </p>
   * <p>
   * This implementation is based on the description of <a
   * href="http://swtch.com/%7Ersc/regexp/regexp2.html">Russ Cox</a>.
   * </p>
   * 
   * @param characters
   *          the {@link String} to match
   * @param start
   *          the start offset where to match
   * @param length
   *          the end offset
   * @param anchorStart
   *          whether the match must start at {@code start}
   * @param anchorEnd
   *          whether the match must start at {@code end}
   * @param result
   *          the {@link Matcher} to store the groups' offsets in, if successful
   * @return whether a match was found
   */
  public boolean matches(char[] characters, int start, int end,
      boolean anchorStart, boolean anchorEnd, Result result)
  {
    ThreadQueue current = new ThreadQueue();
    ThreadQueue next = new ThreadQueue();

    // initialize the first thread
    int startPC = anchorStart ? findPrefixLength : 0;
    ThreadQueue queued = new ThreadQueue(startPC);

    boolean foundMatch = false;
    int step = end > start ? +1 : -1;
    for (int i = start; i != end + step; i += step) {
      if (queued.isEmpty()) {
        // no threads left
        return foundMatch;
      }

      char c = i != end ? characters[i] : 0;
      int pc = -1;
      for (;;) {
        pc = current.next(pc);
        if (pc < 0) {
          pc = queued.queueOneImmediately(current);
        }
        if (pc < 0) {
          break;
        }

        // pc == program.length is a match!
        if (pc == program.length) {
          if (anchorEnd && i != end) {
            continue;
          }
          if (result == null) {
            // only interested in a match, no need to go on
            return true;
          }
          current.setResult(result);

          // now that we found a match, even higher-priority matches must match
          // at the same start offset
          if (!anchorStart) {
            next.mustStartMatchAt(current.startOffset(pc));
          }
          foundMatch = true;
          break;
        }

        int opcode = program[pc];
        switch (opcode) {
        case DOT:
          if (c != '\0' && c != '\r' && c != '\n') {
            current.queueNext(pc, pc + 1, next);
          }
          break;
        case DOTALL:
          current.queueNext(pc, pc + 1, next);
          break;
        case WORD_BOUNDARY:
        case NON_WORD_BOUNDARY: {
          int i2 = i - step;
          int c2 = i2 < 0 || i2 >= characters.length ? -1 : characters[i2];
          switch (opcode) {
          case WORD_BOUNDARY:
            if ((c2 < 0 || !wordCharacter.matches((char)c2))) {
              if (wordCharacter.matches(c)) {
                current.queueImmediately(pc, pc + 1, false);
              }
            } else if (i >= 0 && i < characters.length &&
                !wordCharacter.matches(c)) {
              current.queueImmediately(pc, pc + 1, false);
            }
            break;
          case NON_WORD_BOUNDARY:
            if ((c2 < 0 || !wordCharacter.matches((char)c2))) {
              if (i >= 0 && i < characters.length &&
                  !wordCharacter.matches(c)) {
                current.queueImmediately(pc, pc + 1, false);
              }
            } else if (wordCharacter.matches(c)) {
              current.queueImmediately(pc, pc + 1, false);
            }
            break;
          }
          break;
        }
        case LINE_START:
          if (i == 0 || (multiLine &&
              lineTerminator.matches(characters[i - 1]))) {
            current.queueImmediately(pc, pc + 1, false);
          }
          break;
        case LINE_END:
          if (i == characters.length || (multiLine &&
              lineTerminator.matches(c))) {
            current.queueImmediately(pc, pc + 1, false);
          }
          break;
        case CHARACTER_CLASS:
          if (classes[program[pc + 1]].matches(c)) {
            current.queueNext(pc, pc + 2, next);
          }
          break;
        case LOOKAHEAD:
          if (lookarounds[program[pc + 1]].matches(characters,
              i, characters.length, true, false, null)) {
            current.queueImmediately(pc, pc + 2, false);
          }
          break;
        case LOOKBEHIND:
          if (lookarounds[program[pc + 1]].matches(characters,
              i - 1, -1, true, false, null)) {
            current.queueImmediately(pc, pc + 2, false);
          }
          break;
        case NEGATIVE_LOOKAHEAD:
          if (!lookarounds[program[pc + 1]].matches(characters,
              i, characters.length, true, false, null)) {
            current.queueImmediately(pc, pc + 2, false);
          }
          break;
        case NEGATIVE_LOOKBEHIND:
          if (!lookarounds[program[pc + 1]].matches(characters,
              i - 1, -1, true, false, null)) {
            current.queueImmediately(pc, pc + 2, false);
          }
          break;
        /* immediate opcodes, i.e. thread continues within the same step */
        case SAVE_OFFSET:
          if (result != null) {
            int index = program[pc + 1];
            current.saveOffset(pc, index, i);
          }
          current.queueImmediately(pc, pc + 2, false);
          break;
        case SPLIT:
          current.queueImmediately(pc, program[pc + 1], true);
          current.queueImmediately(pc, pc + 2, false);
          break;
        case SPLIT_JMP:
          current.queueImmediately(pc, pc + 2, true);
          current.queueImmediately(pc, program[pc + 1], false);
          break;
        case JMP:
          current.queueImmediately(pc, program[pc + 1], false);
          break;
        default:
          if (program[pc] >= 0 && program[pc] <= 0xffff) {
            if (c == (char)program[pc]) {
              current.queueNext(pc, pc + 1, next);
            }
            break;
          }
          throw new RuntimeException("Invalid opcode: " + opcode
            + " at pc " + pc);
        }
      }
      // clean linked thread list (and states)
      current.clean();

      // prepare for next step
      ThreadQueue swap = queued;
      queued = next;
      next = swap;
    }
    return foundMatch;
  }

  /**
   * Determines whether this machine recognizes a pattern without special
   * operators.
   * <p>
   * In case that the regular expression is actually a plain string without any
   * special operators, we can avoid using a full-blown Pike VM and instead fall
   * back to using the much faster {@link TrivialPattern}.
   * </p>
   * 
   * @return the string to match, or null if the machine recognizes a
   *         non-trivial pattern
   */
  public String isPlainString() {
    // we expect the machine to start with the find preamble and SAVE_OFFSET 0
    // end with SAVE_OFFSET 1
    int start = findPrefixLength;
    if (start + 1 < program.length &&
        program[start] == SAVE_OFFSET && program[start + 1] == 0) {
      start += 2;
    }
    int end = program.length;
    if (end > start + 1 &&
        program[end - 2] == SAVE_OFFSET && program[end - 1] == 1) {
      end -= 2;
    }
    for (int i = start; i < end; ++ i) {
      if (program[i] < 0) {
        return null;
      }
    }
    char[] array = new char[end - start];
    for (int i = start; i < end; ++ i) {
      array[i - start] = (char)program[i];
    }
    return new String(array);
  }

  private static int length(int opcode) {
    return opcode <= SINGLE_ARG_START && opcode >= SINGLE_ARG_END ? 2 : 1;
  }

  private static boolean isJump(int opcode) {
    return opcode <= SPLIT && opcode >= JMP;
  }

  /**
   * Reverses the program (effectively matching the reverse pattern).
   * <p>
   * It is a well-known fact that any regular expression can be reordered
   * trivially into an equivalent regular expression to be applied in backward
   * direction (coming in real handy for look-behind expressions).
   * </p>
   * <p>
   * Example: instead of matching the sequence "aaaabb" with the pattern "a+b+",
   * we can match the reverse sequence "bbaaaa" with the pattern "b+a+".
   * </p>
   * <p>
   * One caveat: while the reverse pattern is equivalent in the sense that it
   * matches if, and only if, the original pattern matches the forward
   * direction, the same is not true for submatches. Consider the input "a" and
   * the pattern "(a?)a?": when matching in forward direction the captured group
   * is "a", while the backward direction will yield the empty string. For that
   * reason, Java dictates that capturing groups in look-behind patterns are
   * ignored.
   * </p>
   */
  public void reverse() {
    reverse(findPrefixLength, program.length);
  }

  /**
   * Reverses a specific part of the program (to match in reverse direction).
   * <p>
   * This is the work-horse of {@link #reverse()}.
   * </p>
   * <p>
   * To visualize the process of reversing a program, let's look at it as a
   * directed graph (each jump is represented by an "<tt>X</tt>
   * ", non-jumping steps are represented by a "<tt>o</tt>"s, arrows show the
   * direction of the flow, <code>SPLIT</code>s spawn two arrows):
   * 
   * <pre>
   * o -> X -> X -> o -> X    o -> o
   * ^    |     \         \___^____^
   *  \__/       \____________|
   * </pre>
   * 
   * The concept of reversing the program is easiest explained as following: if
   * we insert auxiliary nodes "<tt>Y</tt>" for jump targets, the graph looks
   * like this instead:
   * 
   * <pre>
   * Y -> o -> X -> X -> o -> X    Y -> o -> Y -> o
   * ^         |     \         \___^_________^
   *  \_______/       \____________|
   * </pre>
   * 
   * It is now obvious that reversing the program is equivalent to reversing all
   * arrows, simply deleting all <tt>X</tt>s and substituting each <tt>Y</tt>
   * with a jump. Note that the reverse program will have the same number of
   * <tt>JMP</tt>, but they will not be associated with the same arrows!:
   * 
   * <pre>
   * X <- o <- o    X <- o <- X <- o
   * |    ^    ^____|________/
   *  \__/ \_______/
   * </pre>
   * 
   * </p>
   * @param start
   *          start reversing the program with this instruction
   * @param end
   *          stop reversing at this instruction (this must be either an index
   *          aligned exactly with an instruction, or exactly
   *          {@code program.length}.
   */
  private void reverse(int start, int end) {
    // Pass 1: build the list of jump targets
    int[] newJumps = new int[end + 1];
    boolean[] brokenArrows = new boolean[end + 1];
    for (int pc = start; pc < end; pc += length(program[pc])) {
      if (isJump(program[pc])) {
        int target = program[pc + 1];
        newJumps[pc + 1] = newJumps[target];
        newJumps[target] = pc + 1;
        if (program[pc] == JMP) {
          brokenArrows[pc + 2] = true;
        }
      }
    }

    // Pass 2: determine mapped program counters
    int[] mapping = new int[end];
    for (int pc = start, mappedPC = end; mappedPC > 0
        && pc < end; pc += length(program[pc])) {
      for (int jump = newJumps[pc]; jump > 0; jump = newJumps[jump]) {
        mappedPC -= 2;
      }
      if (!isJump(program[pc])) {
        mappedPC -= length(program[pc]);
      }
      mapping[pc] = mappedPC;
    }

    // Pass 3: write the new program
    int[] reverse =  new int[end];
    for (int pc = start, mappedPC = end; mappedPC > 0;
        pc += length(program[pc])) {
      boolean brokenArrow = brokenArrows[pc];
      for (int jump = newJumps[pc]; jump > 0; jump = newJumps[jump]) {
        reverse[--mappedPC] = mapping[jump - 1];
        if (brokenArrow) {
          reverse[--mappedPC] = JMP;
          brokenArrow = false;
        } else {
          reverse[--mappedPC] =
              program[jump - 1] == SPLIT_JMP ? SPLIT_JMP : SPLIT;
        }
      }
      if (pc == end) {
        break;
      }
      if (!isJump(program[pc])) {
        for (int i = length(program[pc]); i-- > 0; ) {
          reverse[--mappedPC] = program[pc + i];
        }
      }
    }
    System.arraycopy(reverse, start, program, start, end - start);
  }
}
