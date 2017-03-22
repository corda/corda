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
 * <p>
 * Intended as a permissively-licensed drop-in replacement for Oracle JDK's
 * regular expression engine, this class uses the Pike VM implemented in
 * {@link PikeVM} to match regular expressions.
 * </p>
 * <p>
 * The Pike VM not only has a nicer runtime performance than Oracle JDK's
 * backtracking approach -- <i>O(n*m)</i> instead of <i>O(2^m)</i> where
 * <i>n</i> is the length of the regular expression pattern (after normalizing
 * {&lt;n&gt;} quantifiers) and <i>m</i> the length of the text to match against
 * the pattern -- but also supports arbitrary-sized look-behinds.
 * </p>
 * <p>
 * The current implementation supports all regular expression constructs
 * supported by Oracle JDK's regular expression engine except for the following
 * ones:
 * <ul>
 * <li>control characters: \cX</li>
 * <li>extended character classes: \p{...}</li>
 * <li>extended boundary matchers: \A,\G,\Z,\z</li>
 * <li>possessive quantifiers: X?+</li>
 * <li>back references: \&lt;n&gt;, \k&lt;name&gt;</li>
 * <li>long escape: \Q, \E</li>
 * <li>named groups: (?&lt;name&gt;X)</li>
 * <li>flags: (?idmsuxU)</li>
 * <li>independent, non-capturing group: (?>X)</li>
 * </ul>
 * </p>
 * 
 * @author Johannes Schindelin
 */
public class RegexPattern extends Pattern {
  private PikeVM vm;

  public RegexMatcher matcher(CharSequence string) {
    return new RegexMatcher(vm, string);
  }

  RegexPattern(String regex, int flags, PikeVM vm) {
    super(regex, flags);
    this.vm = vm;
  }
}
