# -*- coding: utf-8 -*-
"""
    Basic JavaLexer Test
    ~~~~~~~~~~~~~~~~~~~~

    :copyright: Copyright 2006-2017 by the Pygments team, see AUTHORS.
    :license: BSD, see LICENSE for details.
"""

import unittest

from pygments.token import Text, Name, Operator, Keyword, Number, Punctuation, String
from pygments.lexers import KotlinLexer

class KotlinTest(unittest.TestCase):

    def setUp(self):
        self.lexer = KotlinLexer()
        self.maxDiff = None

    def testCanCopeWithBackTickNamesInFunctions(self):
        fragment = u'fun `wo bble`'
        tokens = [
            (Keyword, u'fun'),
            (Text, u' '),
            (Name.Function, u'`wo bble`'),
            (Text, u'\n')
        ]
        self.assertEqual(tokens, list(self.lexer.get_tokens(fragment)))

    def testCanCopeWithCommasAndDashesInBackTickNames(self):
        fragment = u'fun `wo,-bble`'
        tokens = [
            (Keyword, u'fun'),
            (Text, u' '),
            (Name.Function, u'`wo,-bble`'),
            (Text, u'\n')
        ]
        self.assertEqual(tokens, list(self.lexer.get_tokens(fragment)))

    def testCanCopeWithDestructuring(self):
        fragment = u'val (a, b) = '
        tokens = [
            (Keyword, u'val'),
            (Text, u' '),
            (Punctuation, u'('),
            (Name.Property, u'a'),
            (Punctuation, u','),
            (Text, u' '),
            (Name.Property, u'b'),
            (Punctuation, u')'),
            (Text, u' '),
            (Punctuation, u'='),
            (Text, u' '),
            (Text, u'\n')
        ]
        self.assertEqual(tokens, list(self.lexer.get_tokens(fragment)))

    def testCanCopeGenericsInDestructuring(self):
        fragment = u'val (a: List<Something>, b: Set<Wobble>) ='
        tokens = [
            (Keyword, u'val'),
            (Text, u' '),
            (Punctuation, u'('),
            (Name.Property, u'a'),
            (Punctuation, u':'),
            (Text, u' '),
            (Name.Property, u'List'),
            (Punctuation, u'<'),
            (Name, u'Something'),
            (Punctuation, u'>'),
            (Punctuation, u','),
            (Text, u' '),
            (Name.Property, u'b'),
            (Punctuation, u':'),
            (Text, u' '),
            (Name.Property, u'Set'),
            (Punctuation, u'<'),
            (Name, u'Wobble'),
            (Punctuation, u'>'),
            (Punctuation, u')'),
            (Text, u' '),
            (Punctuation, u'='),
            (Text, u'\n')
        ]
        self.assertEqual(tokens, list(self.lexer.get_tokens(fragment)))

    def testCanCopeWithGenerics(self):
        fragment = u'inline fun <reified T : ContractState> VaultService.queryBy(): Vault.Page<T> {'
        tokens = [
            (Keyword, u'inline fun'),
            (Text, u' '),
            (Punctuation, u'<'),
            (Keyword, u'reified'),
            (Text, u' '),
            (Name, u'T'),
            (Text, u' '),
            (Punctuation, u':'),
            (Text, u' '),
            (Name, u'ContractState'),
            (Punctuation, u'>'),
            (Text, u' '),
            (Name.Class, u'VaultService'),
            (Punctuation, u'.'),
            (Name.Function, u'queryBy'),
            (Punctuation, u'('),
            (Punctuation, u')'),
            (Punctuation, u':'),
            (Text, u' '),
            (Name, u'Vault'),
            (Punctuation, u'.'),
            (Name, u'Page'),
            (Punctuation, u'<'),
            (Name, u'T'),
            (Punctuation, u'>'),
            (Text, u' '),
            (Punctuation, u'{'),
            (Text, u'\n')
        ]
        self.assertEqual(tokens, list(self.lexer.get_tokens(fragment)))

    def testShouldCopeWithMultilineComments(self):
        fragment = u'"""\nthis\nis\na\ncomment"""'
        tokens = [
            (String, u'"""\nthis\nis\na\ncomment"""'),
            (Text, u'\n')
        ]
        self.assertEqual(tokens, list(self.lexer.get_tokens(fragment)))

if __name__ == '__main__':
    unittest.main()
