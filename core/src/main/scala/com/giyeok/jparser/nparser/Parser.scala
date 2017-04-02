package com.giyeok.jparser.nparser

import com.giyeok.jparser.ParsingErrors.ParsingError
import com.giyeok.jparser.nparser.ParsingContext._
import com.giyeok.jparser.nparser.AcceptCondition._
import com.giyeok.jparser.Inputs.Input
import com.giyeok.jparser.Inputs
import Parser._

trait Parser[T <: Context] {
    val grammar: NGrammar

    val startNode = Node(Kernel(grammar.startSymbol, 0, 0, 0)(grammar.nsymbols(grammar.startSymbol)), Always)

    val initialContext: T

    def proceedDetail(wctx: T, input: Input): Either[(ProceedDetail, T), ParsingError]

    def proceed(wctx: T, input: Input): Either[T, ParsingError] =
        proceedDetail(wctx, input) match {
            case Left((detail, nextCtx)) => Left(nextCtx)
            case Right(error) => Right(error)
        }

    def parse(source: Inputs.Source): Either[T, ParsingError] =
        source.foldLeft[Either[T, ParsingError]](Left(initialContext)) {
            (cc, input) =>
                cc match {
                    case Left(ctx) => proceed(ctx, input)
                    case error @ Right(_) => error
                }
        }
    def parse(source: String): Either[T, ParsingError] =
        parse(Inputs.fromString(source))
}

object Parser {
    class ConditionFate(val trueFixed: Set[AcceptCondition], val falseFixed: Set[AcceptCondition], val unfixed: Map[AcceptCondition, AcceptCondition]) {
        def of(condition: AcceptCondition): Boolean = {
            if (trueFixed contains condition) true
            else if (falseFixed contains condition) false
            else ??? // unfixed(condition)
        }

        def update(evaluation: Map[AcceptCondition, AcceptCondition]): ConditionFate = {
            var trueConditions = trueFixed
            var falseConditions = falseFixed
            var unfixedConditions = Map[AcceptCondition, AcceptCondition]()

            evaluation foreach { kv =>
                kv._2 match {
                    case Always => trueConditions += kv._1
                    case Never => falseConditions += kv._1
                    case _ => unfixedConditions += kv
                }
            }
            new ConditionFate(trueConditions, falseConditions, unfixedConditions)
        }

        // TODO unfixed에 대한 acceptable값 계산해서 저장 - 근데 이건 마지막에 한번만 하면 되는데
    }
    object ConditionFate {
        def apply(conditionFate: Map[AcceptCondition, AcceptCondition]): ConditionFate = {
            var trueConditions = Set[AcceptCondition]()
            var falseConditions = Set[AcceptCondition]()
            var unfixedConditions = Map[AcceptCondition, AcceptCondition]()

            conditionFate foreach { kv =>
                kv._2 match {
                    case Always => trueConditions += kv._1
                    case Never => falseConditions += kv._1
                    case _ => unfixedConditions += kv
                }
            }
            new ConditionFate(trueConditions, falseConditions, unfixedConditions)
        }
    }

    abstract class Context(val gen: Int, val nextGraph: Graph, _inputs: List[Input], _history: List[Graph], val conditionFate: ConditionFate) {
        def nextGen: Int = gen + 1
        def inputs: Seq[Input] = _inputs.reverse
        def history: Seq[Graph] = _history.reverse
        def resultGraph: Graph = _history.head
    }

    class NaiveContext(gen: Int, nextGraph: Graph, _inputs: List[Input], _history: List[Graph], conditionFate: ConditionFate)
            extends Context(gen, nextGraph, _inputs, _history, conditionFate) {
        def proceed(nextGen: Int, resultGraph: Graph, nextGraph: Graph, newInput: Input, newConditionFate: ConditionFate): NaiveContext = {
            new NaiveContext(nextGen, nextGraph, newInput +: _inputs, resultGraph +: _history, newConditionFate)
        }
    }

    class DeriveTipsContext(gen: Int, nextGraph: Graph, val deriveTips: Set[Node], _inputs: List[Input], _history: List[Graph], conditionFate: ConditionFate)
            extends Context(gen, nextGraph, _inputs, _history, conditionFate) {
        // assert(deriveTips subsetOf ctx.graph.nodes)
        def proceed(nextGen: Int, resultGraph: Graph, nextGraph: Graph, deriveTips: Set[Node], newInput: Input, newConditionFate: ConditionFate): DeriveTipsContext = {
            new DeriveTipsContext(nextGen, nextGraph, deriveTips, newInput +: _inputs, resultGraph +: _history, newConditionFate)
        }
    }

    case class Transition(name: String, result: Graph)
    case class ProceedDetail(baseGraph: Graph, transitions: Transition*) {
        def graphAt(idx: Int): Graph =
            if (idx == 0) baseGraph else transitions(idx - 1).result
        def nameOf(idx: Int): String =
            transitions(idx - 1).name
    }

    def evaluateAcceptConditions(nextGen: Int, conditions: Set[AcceptCondition], graph: Graph, updatedNodes: Map[Node, Set[Node]]): Map[AcceptCondition, AcceptCondition] = {
        (conditions map { condition =>
            condition -> condition.evaluate(nextGen, graph, updatedNodes)
        }).toMap
    }
}
