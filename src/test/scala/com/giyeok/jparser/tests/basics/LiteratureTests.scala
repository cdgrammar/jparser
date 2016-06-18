package com.giyeok.jparser.tests.basics

import com.giyeok.jparser.tests.GrammarTestCases
import com.giyeok.jparser.tests.BasicParseTest
import com.giyeok.jparser.Grammar
import com.giyeok.jparser.GrammarHelper._
import com.giyeok.jparser.tests.StringSamples
import scala.collection.immutable.ListMap
import scala.collection.immutable.ListSet
import com.giyeok.jparser.tests.AmbiguousSamples

object MyPaper1 extends Grammar with GrammarTestCases with StringSamples with AmbiguousSamples {
    val name = "MyPaper Grammar 1"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("N").plus),
        "N" -> ListSet(
            n("A"),
            n("B")),
        "A" -> ListSet(
            c('a').plus),
        "B" -> ListSet(
            c('b')))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String]()
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String]("aab")
}

object MyPaper2 extends Grammar with GrammarTestCases with StringSamples with AmbiguousSamples {
    val name = "MyPaper Grammar 2"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            oneof(n("A"), n("Z")).plus),
        "A" -> ListSet(
            chars('a' to 'b').plus),
        "Z" -> ListSet(
            c('z')))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String]()
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String]("abz")
}

object MyPaper2_1 extends Grammar with GrammarTestCases with StringSamples with AmbiguousSamples {
    val name = "MyPaper Grammar 2_1"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            oneof(n("A"), n("Z")).plus),
        "A" -> ListSet(
            seq(chars('a' to 'b').plus, lookahead_is(n("Z")))),
        "Z" -> ListSet(
            c('z')))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String]("abz")
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String]()
}

object MyPaper3 extends Grammar with GrammarTestCases with StringSamples with AmbiguousSamples {
    val name = "MyPaper Grammar 3"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("T"),
            seq(n("S"), n("T"))),
        "T" -> ListSet(
            longest(n("N")),
            longest(n("P"))),
        "N" -> ListSet(
            chars('0' to '9'),
            seq(n("N"), chars('0' to '9'))),
        "P" -> ListSet(
            i("+"),
            i("++")))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String]("12+34")
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String]()
}

object MyPaper4 extends Grammar with GrammarTestCases with StringSamples with AmbiguousSamples {
    val name = "MyPaper Grammar 4"
    val rules: RuleMap = ListMap(
        "A" -> ListSet(
            seq(n("A"), chars('a' to 'z')),
            chars('a' to 'z')))
    val startSymbol = n("A")

    val grammar = this
    val correctSamples = Set[String]("ab")
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String]()
}

object MyPaper5 extends Grammar with GrammarTestCases with StringSamples with AmbiguousSamples {
    val name = "MyPaper Grammar 5"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("T").star),
        "T" -> ListSet(
            n("N"),
            n("W")),
        "N" -> ListSet(
            seq(n("A"), lookahead_except(n("A")))),
        "A" -> ListSet(
            seq(chars('a' to 'z'), n("A")),
            chars('a' to 'z')),
        "W" -> ListSet(
            seq(c(' ').plus, lookahead_except(c(' ')))))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String]("abc  def")
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String]()
}

object Earley1970AE extends Grammar with GrammarTestCases with StringSamples {
    val name = "Earley 1970 Grammar AE"
    val rules: RuleMap = ListMap(
        "E" -> ListSet(
            n("T"),
            seq(n("E"), c('+'), n("T"))),
        "T" -> ListSet(
            n("P"),
            seq(n("T"), c('*'), n("P"))),
        "P" -> ListSet(
            c('a')))
    val startSymbol = n("E")

    val grammar = this
    val correctSamples = Set[String](
        "a+a*a")
    val incorrectSamples = Set[String]()
}

object Knuth1965_24 extends Grammar with GrammarTestCases with StringSamples {
    val name = "Knuth 1965 Grammar 24"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            empty,
            seq(c('a'), n("A"), c('b'), n("S")),
            seq(c('b'), n("B"), c('a'), n("S"))),
        "A" -> ListSet(
            empty,
            seq(c('a'), n("A"), c('b'), n("A"))),
        "B" -> ListSet(
            empty,
            seq(c('b'), n("B"), c('a'), n("B"))))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String](
        "",
        "bbbbbaaaaa",
        "aaabaaabaaabbbbbbb",
        "ababbaba",
        "ababbbabaababababababababbabababaaabab")
    val incorrectSamples = Set[String]()
}

object PaperTests {
    val tests: Set[GrammarTestCases] = Set(
        MyPaper1,
        MyPaper2,
        MyPaper2_1,
        MyPaper3,
        MyPaper4,
        MyPaper5,
        Earley1970AE,
        Knuth1965_24)
}

class PaperTestSuite extends BasicParseTest(PaperTests.tests)

// grammars in Parsing Techniques book (http://dickgrune.com/Books/PTAPG_1st_Edition/BookBody.pdf)

object Fig7_4 extends Grammar with GrammarTestCases with StringSamples with AmbiguousSamples {
    val name = "Parsing Techniques Grammar in Figure 7.4"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            seq(c('a'), n("S"), c('b')),
            seq(n("S"), c('a'), c('b')),
            seq(i("aaa"))))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String]()
    val incorrectSamples = Set[String]()
    val ambiguousSamples = Set[String]("aaaab")
}

object Fig7_8 extends Grammar with GrammarTestCases with StringSamples {
    val name = "Parsing Techniques Grammar in Figure 7.8"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("E")),
        "E" -> ListSet(
            seq(n("E"), n("Q"), n("F")),
            n("F")),
        "F" -> ListSet(
            c('a')),
        "Q" -> ListSet(
            c('+'),
            c('-')))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String](
        "a+a",
        "a-a+a")
    val incorrectSamples = Set[String]()
}

object Fig7_19 extends Grammar with GrammarTestCases with StringSamples {
    val name = "Parsing Techniques Grammar in Figure 7.19"
    val rules: RuleMap = ListMap(
        "S" -> ListSet(
            n("A"),
            seq(n("A"), n("B")),
            n("B")),
        "A" -> ListSet(
            n("C")),
        "B" -> ListSet(
            n("D")),
        "C" -> ListSet(
            c('p')),
        "D" -> ListSet(
            c('q')))
    val startSymbol = n("S")

    val grammar = this
    val correctSamples = Set[String](
        "p",
        "q",
        "pq")
    val incorrectSamples = Set[String]()
}

object ParsingTechniquesTests {
    val tests: Set[GrammarTestCases] = Set(
        Fig7_4,
        Fig7_8,
        Fig7_19)
}

class ParsingTechniquesTestSuite extends BasicParseTest(ParsingTechniquesTests.tests)
