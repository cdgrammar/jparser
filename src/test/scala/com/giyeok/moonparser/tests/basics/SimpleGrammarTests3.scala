package com.giyeok.moonparser.tests.basics

import com.giyeok.moonparser.Grammar
import com.giyeok.moonparser.GrammarHelper._
import scala.collection.immutable.ListMap
import com.giyeok.moonparser.Parser
import com.giyeok.moonparser.Inputs._
import scala.collection.immutable.ListSet
import com.giyeok.moonparser.Parser
import com.giyeok.moonparser.tests.BasicParseTest
import com.giyeok.moonparser.tests.Samples
import com.giyeok.moonparser.tests.StringSamples

object SimpleGrammar5 extends Grammar with StringSamples {
    val name = "Simple Grammar 5"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(seq(n("A"), n("B"))),
        "A" -> ListSet(i("a"), e),
        "B" -> ListSet(i("b"), e))
    val startSymbol = n("S")

    val correctSamples = Set("", "a", "b", "ab")
    val incorrectSamples = Set("aa")

    def main(args: Array[String]): Unit = {
        val parser = new Parser(this)
        val ctx = parser.startingContext
        ctx.graph.edges foreach { e => println(e.toShortString) }
        println("=== End ===")
    }
}

object SimpleGrammar6 extends Grammar with StringSamples {
    val name = "Simple Grammar 6"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(seq(n("A"), n("C"))),
        "A" -> ListSet(seq(n("B"), i("a").star)),
        "B" -> ListSet(i("b"), e),
        "C" -> ListSet(seq(n("B"), i("c").star)))
    val startSymbol = n("S")

    val correctSamples = Set("", "ab", "c", "ccc", "abc", "aa", "aaabccc")
    val incorrectSamples = Set("cb")
}

object SimpleGrammarSet3 {
    val grammars: Set[Grammar with Samples] = Set(
        SimpleGrammar5, // fromSeeds failed
        SimpleGrammar6 // Assertion failed
        )
}

class SimpleGrammarTestSuite3 extends BasicParseTest(SimpleGrammarSet3.grammars)