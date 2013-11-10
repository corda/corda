/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package regex;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Compiles regular expressions into {@link PikeVM}s.
 * 
 * @author Johannes Schindelin
 */
class Compiler implements PikeVMOpcodes {
  private final static CharacterMatcher regularCharacter =
      CharacterMatcher.parse("[^\\\\.*+?|\\[\\]{}()^$]");

  private static class Output {
    private int[] program;
    private int offset;
    private int groupCount = -1;

    public Output(Expression expr) {
      // try-run to determine the code size
      expr.writeCode(this);
      program = new int[offset];
      offset = 0;
      groupCount = -1;
      // write it out!
      expr.writeCode(this);
    }

    public void add(int opcode) {
      if (program != null) {
        program[offset] = opcode;
      }
      offset++;
    }

    public int markJump() {
      return offset++;
    }

    public void setJump(int mark) {
      if (program != null) {
        program[mark] = offset;
      }
    }

    public PikeVM toVM() {
      return new PikeVM(program, groupCount);
    }
  }

  private abstract class Expression {
    protected abstract void writeCode(Output output);
  }

  private class Repeat extends Expression {
    private Expression expr;
    private int minCount, maxCount;
    private boolean greedy;

    public Repeat(Expression expr, int minCount, int maxCount, boolean greedy) {
      if (minCount != 0 && minCount != 1) {
        throw new RuntimeException("Unexpected min count: " + minCount);
      }
      if (maxCount != 1 && maxCount != -1) {
        throw new RuntimeException("Unexpected max count: " + maxCount);
      }
      this.expr = expr;
      this.minCount = minCount;
      this.maxCount = maxCount;
      this.greedy = greedy;
    }

    protected void writeCode(Output output) {
      int start = output.offset;
      int splitJmp = greedy ? SPLIT_JMP : SPLIT;
      int split = greedy ? SPLIT : SPLIT_JMP;
      if (minCount == 1 && maxCount == -1) {
        expr.writeCode(output);
        output.add(splitJmp);
        output.add(start);
      } else if (minCount == 0 && maxCount == -1) {
        output.add(split);
        int jump = output.markJump();
        expr.writeCode(output);
        output.add(splitJmp);
        output.add(start + 2);
        output.setJump(jump);
      } else if (minCount == 0 && maxCount == 1) {
        output.add(split);
        int jump = output.markJump();
        expr.writeCode(output);
        output.setJump(jump);
      } else {
        throw new RuntimeException("Unexpected range: "
          + minCount + ", " + maxCount);
      }
    }
  }

  private class Group extends Expression {
    private ArrayList<Expression> list = new ArrayList<Expression>();

    public void push(Expression expr) {
      list.add(expr);
    }

    public void push(final int c) {
      push(new Expression() {
        public void writeCode(Output output) {
          output.add(c);
        }
      });
    }

    public Expression pop() {
      Expression result = list.remove(list.size() - 1);
      return result;
    }

    protected void writeCode(Output output) {
      int groupIndex = ++ output.groupCount;
      output.add(SAVE_OFFSET);
      output.add(2 * groupIndex);
      for (Expression expr : list) {
        expr.writeCode(output);
      }
      output.add(SAVE_OFFSET);
      output.add(2 * groupIndex + 1);
    }
  }

  private class Group0 extends Expression {
    private final Group group;

    public Group0() {
      group = new Group();
    }

    public void writeCode(Output output) {
      group.writeCode(output);
    }
  }

  private Group0 root;
  private Stack<Group> groups;

  public Compiler() {
    root = new Group0();
    groups = new Stack<Group>();
    groups.add(root.group);
  }

  public Pattern compile(String regex) {
    char[] array = regex.toCharArray();
    for (int index = 0; index < array.length; ++ index) {
      char c = array[index];
      Group current = groups.peek();
      if (regularCharacter.matches(c)) {
        current.push(c);
        continue;
      }
      switch (c) {
      case '.':
        current.push(DOT);
        continue;
      case '?':
      case '*':
      case '+': {
        boolean greedy = true;
        if (index + 1 < array.length && array[index + 1] == '?') {
          greedy = false;
          ++ index;
        }
        current.push(new Repeat(current.pop(),
          c == '+' ? 1 : 0, c == '?' ? 1 : -1, greedy));
        continue;
      }
      case '(':
        if (index + 1 < array.length && array[index + 1] == '?') {
          throw new UnsupportedOperationException("Not yet supported: "
            + regex.substring(index));
        }
        current.push(groups.push(new Group()));
        continue;
      case ')':
        if (groups.size() < 2) {
          throw new RuntimeException("Invalid group close @" + index + ": "
            + regex);
        }
        groups.pop();
        continue;
      default:
        throw new RuntimeException("Parse error @" + index + ": " + regex);
      }
    }
    if (groups.size() != 1) {
      throw new IllegalArgumentException("Unclosed groups: ("
        + (groups.size() - 1) + "): " + regex);
    }
    PikeVM vm = new Output(root).toVM();
    String plain = vm.isPlainString();
    if (plain != null) {
      return new TrivialPattern(regex, plain, 0);
    }
    return new RegexPattern(regex, 0, vm);
  }
}
