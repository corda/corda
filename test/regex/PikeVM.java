/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package regex;

/**
 * A minimal implementation of a regular expression engine.
 * 
 * @author Johannes Schindelin
 */
class PikeVM implements PikeVMOpcodes {
  private final int[] program;
  private final int groupCount;
  private final int offsetsCount;

  public interface Result {
    void set(int[] start, int[] end);
  }

  protected PikeVM(int[] program, int groupCount) {
    this.program = program;
    this.groupCount = groupCount;
    offsetsCount = 2 * groupCount + 2;
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
      next.offsets[nextPC] =
        currentPC < 0 ? new int[offsetsCount] : offsets[currentPC];
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
    current.queueNext(-1, 0, current);
    if (!anchorStart) {
      // this requires non-greedy matching
      throw new UnsupportedOperationException();
    }

    boolean foundMatch = false;
    for (int i = start; i <= end; ++i) {
      if (current.isEmpty()) {
        // no threads left
        return foundMatch;
      }

      char c = i < end ? characters[i] : 0;
      int pc = -1;
      for (;;) {
        pc = current.next(pc);
        if (pc < 0) {
          break;
        }

        // pc == program.length is a match!
        if (pc == program.length) {
          if (anchorEnd && i < end) {
            continue;
          }
          current.setResult(result);
          foundMatch = true;
          break;
        }

        int opcode = program[pc];
        switch (opcode) {
        /* Possible optimization: make all opcodes <= 0xffff implicit chars */
        case CHAR:
          if (c == (char)program[pc + 1]) {
            current.queueNext(pc, pc + 2, next);
          }
          break;
        case DOT:
          if (c != '\0' && c != '\r' && c != '\n') {
            current.queueNext(pc, pc + 1, next);
          }
          break;
        case DOTALL:
          current.queueNext(pc, pc + 1, next);
          break;
        case SAVE_OFFSET:
          int index = program[pc + 1];
          current.saveOffset(pc, index, i);
          current.queueImmediately(pc, pc + 2, false);
          break;
        case SPLIT:
          current.queueImmediately(pc, program[pc + 1], true);
          current.queueImmediately(pc, pc + 2, false);
          break;
        case JMP:
          current.queueImmediately(pc, program[pc + 1], false);
          break;
        default:
          throw new RuntimeException("Invalid opcode: " + opcode
            + " at pc " + pc);
        }
      }
      // clean linked thread list (and states)
      current.clean();

      // prepare for next step
      ThreadQueue swap = current;
      current = next;
      next = swap;
    }
    return foundMatch;
  }
}
