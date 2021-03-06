package com.giyeok.jparser.tests

import com.giyeok.jparser.studio.GrammarExample
import com.giyeok.jparser.studio.ParserStudio
import com.giyeok.jparser.tests.basics.FollowedByGrammar3
import com.giyeok.jparser.tests.basics.JoinGrammar3_1
import com.giyeok.jparser.tests.gramgram.ExpressionGrammar0Tests
import com.giyeok.jparser.tests.gramgram.LexicalGrammar2Tests
import com.giyeok.jparser.tests.gramgram.MetaGrammarTests

object ParserStudioMain {
    def main(args: Array[String]): Unit = {
        def grammar(name: String, test: GrammarTestCases): GrammarExample = {
            val (correctSamples, incorrectSamples) = test match {
                case t: StringSamples =>
                    (t.correctSamples.toSeq, t.incorrectSamples.toSeq)
                case _ => (Seq(), Seq())
            }
            val ambiguousSamples = test match {
                case t: AmbiguousSamples =>
                    t.ambiguousSamples.toSeq
                case _ => Seq()
            }
            GrammarExample(name, test.grammar, correctSamples, incorrectSamples, ambiguousSamples)
        }
        val examples = AllTestGrammars.allTestGrammars.toSeq map { test => grammar(test.grammar.name, test) } sortBy { _.name }
        val specials = Seq(
            grammar("(Fig 1) Expression Grammar", ExpressionGrammar0Tests),
            grammar("(Fig 2) Lexical Grammar", LexicalGrammar2Tests),
            grammar("(Fig 3) CDG Grammar", MetaGrammarTests),
            grammar("(Fig 7) a^n b^n c^n Grammar", JoinGrammar3_1),
            grammar("(Fig 8) a^n b^n c^n Grammar", FollowedByGrammar3)
        )
        ParserStudio.start(ExpressionGrammar0Tests.expressionGrammar0Text, ExpressionGrammar0Tests.correctSamples.head, specials ++ examples)
    }
}
