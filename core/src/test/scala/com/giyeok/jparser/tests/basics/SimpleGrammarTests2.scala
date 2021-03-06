package com.giyeok.jparser.tests.basics

import com.giyeok.jparser.Grammar
import com.giyeok.jparser.GrammarHelper._
import com.giyeok.jparser.Inputs._
import org.junit.Assert._
import scala.collection.immutable.ListSet
import scala.collection.immutable.ListMap
import com.giyeok.jparser.tests.BasicParseTest
import com.giyeok.jparser.tests.Samples
import com.giyeok.jparser.tests.StringSamples
import com.giyeok.jparser.tests.GrammarTestCases

object SimpleGrammar2 extends Grammar with GrammarTestCases with StringSamples {
    val name = "Simple Grammar 2"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("Decimal"),
            n("HexDecimal")),
        "Decimal" -> ListSet(
            seq(c('-').opt, oneof(c('0'), seq(n("D1"), n("D0").star)), seq(c('.'), n("D0").star).opt, seq(chars("eE"), c('-').opt, n("D0").plus).opt)),
        "D0" -> ListSet(chars('0' to '9')),
        "D1" -> ListSet(chars('1' to '9')),
        "HexDecimal" -> ListSet(
            seq(c('-').opt, c('0'), chars("xX"), n("HD1"), n("HD0").star)),
        "HD0" -> ListSet(chars('0' to '9', 'a' to 'f', 'A' to 'F')),
        "HD1" -> ListSet(chars('1' to '9', 'a' to 'f', 'A' to 'F')))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set("10.1e2", "10.1e-00002")
    val incorrectSamples = Set("--10.1e2")
}

object SimpleGrammar3 extends Grammar with GrammarTestCases with StringSamples {
    val name = "Simple Grammar 3 - Donald Knuth 63"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(seq(n("A"), n("D"))),
        "A" -> ListSet(seq(c('a'), n("C"))),
        "B" -> ListSet(i("bcd")),
        "C" -> ListSet(seq(n("B"), n("E"))),
        "D" -> ListSet(empty),
        "E" -> ListSet(i("e")))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set("abcde")
    val incorrectSamples = Set("f")
}

object SimpleGrammar4 extends Grammar with GrammarTestCases with StringSamples {
    val name = "Simple Grammar 4 - StackOverflow"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(seq(chars("ab").star, c('a'), chars("ab").star)))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set("abb", "bbbabbb")
    val incorrectSamples = Set("c")
}

object SimpleGrammarSet2 {
    val tests: Set[GrammarTestCases] = Set(
        SimpleGrammar2,
        SimpleGrammar3,
        SimpleGrammar4)
}

class SimpleGrammarTestSuite2 extends BasicParseTest(SimpleGrammarSet2.tests)
