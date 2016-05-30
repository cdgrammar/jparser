package com.giyeok.jparser

import ParsingGraph._
import Symbols._

trait ParsingTasks[R <: ParseResult, Graph <: ParsingGraph[R]] {
    val resultFunc: ParseResultFunc[R]

    sealed trait Task
    case class DeriveTask(nextGen: Int, baseNode: NontermNode) extends Task
    // node가 finishable해져서 lift하면 afterKernel의 커널과 parsed의 노드를 갖게 된다는 의미
    case class FinishingTask(nextGen: Int, node: Node, result: R, revertTriggers: Set[Trigger]) extends Task
    case class SequenceProgressTask(nextGen: Int, node: SequenceNode, child: R, revertTriggers: Set[Trigger]) extends Task
}

trait LiftTasks[R <: ParseResult, Graph <: ParsingGraph[R]] extends ParsingTasks[R, Graph] {
    def finishingTask(task: FinishingTask, cc: Graph): (Graph, Seq[Task]) = {
        val FinishingTask(nextGen, node, result, revertTriggers) = task
        // 1. cc의 기존 result가 있는지 확인하고 기존 result가 있으면 merge하고 없으면 새로 생성
        val updatedResult: Option[R] = {
            cc.resultOf(node, revertTriggers) match {
                case Some(baseResult) =>
                    val merged = resultFunc.merge(baseResult, result)
                    if (merged != baseResult) Some(merged) else None
                case None => Some(result)
            }
        }

        if (updatedResult.isEmpty) {
            // 기존 결과가 있는데 기존 결과에 merge를 해도 변하는 게 없으면 더이상 진행하지 않는다(그런 경우 이 task는 없는 것처럼 취급)
            (cc, Seq())
        } else {
            // 2. cc에 새로운 result를 업데이트하고
            val ncc = cc.updateResultOf(node, revertTriggers, updatedResult.get)

            // 3. chain lift task 만들어서 추가
            // - 노드에 붙어 있는 reservedReverter, 타고 가는 엣지에 붙어 있는 revertTriggers 주의해서 처리
            // - join 처리
            // - incomingEdge의 대상이 sequence인 경우 SequenceProgressTask가 생성됨
            // - result로 들어온 내용은 node의 symbol로 bind가 안 되어 있으므로 여기서 만드는 태스크에서는 resultFunc.bind(node.symbol, result) 로 줘야함 
            val newTasks: Seq[Task] = {
                val incomingSimpleEdges = cc.incomingSimpleEdgesTo(node)
                val incomingJoinEdges = cc.incomingJoinEdgesTo(node)

                val finishedResult = resultFunc.bind(node.symbol, result)

                val simpleEdgeTasks: Set[Task] = incomingSimpleEdges map {
                    case SimpleEdge(start, end, edgeRevertTriggers) =>
                        assert(end == node)
                        start match {
                            case incoming: AtomicNode =>
                                FinishingTask(nextGen, incoming, finishedResult, revertTriggers ++ edgeRevertTriggers)
                            case incoming: SequenceNode =>
                                SequenceProgressTask(nextGen, incoming, finishedResult, revertTriggers ++ edgeRevertTriggers)
                        }
                }

                val joinEdgeTasks: Set[Task] = incomingJoinEdges flatMap {
                    case JoinEdge(start, end, join) =>
                        if (node == end) {
                            cc.resultOf(join) map { r =>
                                FinishingTask(nextGen, start, resultFunc.join(finishedResult, r._2), revertTriggers ++ r._1)
                            }
                        } else {
                            assert(node == join)
                            cc.resultOf(end) map { r =>
                                FinishingTask(nextGen, start, resultFunc.join(r._2, finishedResult), revertTriggers ++ r._1)
                            }
                        }
                }

                simpleEdgeTasks.toSeq ++ joinEdgeTasks.toSeq
            }

            (ncc, newTasks)
        }
    }

    // 나중에 parser 코드랑 합칠 때 정리해야될듯
    def sequenceProgressTask(task: SequenceProgressTask, cc: Graph): (Graph, Seq[Task]) = {
        val SequenceProgressTask(nextGen, node, child, revertTriggers) = task
        // 1. cc에
        // - 기존의 progress에 append
        // - pointer + 1된 SequenceNode 만들어서
        //   - 이 노드가 finishable이 되면 FinishingTask를 만들고
        //   - 아닌 경우
        //     - 이 노드가 
        //       - cc에 없으면 새로 추가하고 해당 노드의 progress로 appendedResult를 넣어주고(a)
        //       - 이미 같은 노드가 cc에 있으면 기존 progress에 merge해서 업데이트해주고
        //         - merge된 result가 기존의 progress와 다르면(b)
        //     - a나 b의 경우 추가된 노드에 대한 DeriveTask를 만들어 준다
        val appendedSequence = resultFunc.append(cc.progressOf(node, revertTriggers), child)

        if (node.pointer + 1 < node.symbol.seq.length) {
            // append된 뒤에도 아직 finishable이 되지 않는 상태
            val appendedNode = SequenceNode(node.symbol, node.pointer + 1, node.beginGen, nextGen)
            if (cc.nodes contains appendedNode) {
                val baseResult = cc.progressOf(node, revertTriggers)
                val mergedResult = resultFunc.merge(baseResult, appendedSequence)
                if (mergedResult == baseResult) {
                    // append를 했는데 기존 결과에서 달라지지 않아서 더이상의 작업이 필요 없는 경우
                    (cc, Seq())
                } else {
                    // append를 했더니 기존 결과에서 달라진 경우
                    // - 끝인가..? TODO 검토해보기
                    (cc.updateProgressOf(node, revertTriggers, mergedResult), Seq())
                }
            } else {
                // append한 노드가 아직 cc에 없는 경우
                // node로 들어오는 모든 엣지(모두 SimpleEdge여야 함)의 start -> appendedNode로 가는 엣지를 추가한다
                val newEdges: Set[Edge] = cc.incomingSimpleEdgesTo(appendedNode) map {
                    case SimpleEdge(start, end, edgeRevertTriggers) =>
                        assert(end == node)
                        // TODO 여기서 revertTrigger를 이렇게 주는게 맞나?
                        SimpleEdge(start, appendedNode, revertTriggers ++ edgeRevertTriggers)
                }
                assert(cc.incomingJoinEdgesTo(appendedNode).isEmpty)
                (cc.withNodeEdgesProgresses(appendedNode, newEdges, Map(appendedNode -> Map(revertTriggers -> appendedSequence))), Seq(DeriveTask(nextGen, appendedNode)))
            }
        } else {
            // append되면 finish할 수 있는 상태
            // - FinishingTask만 만들어 주면 될듯
            (cc, Seq(FinishingTask(nextGen, node, appendedSequence, revertTriggers)))
        }
    }
}

trait DeriveTasks[R <: ParseResult, Graph <: ParsingGraph[R]] extends ParsingTasks[R, Graph] {
    val grammar: Grammar

    def newNode(symbol: Symbol): Node = symbol match {
        case Empty => EmptyNode

        case s: Terminal => TermNode(s)

        case s: Except => AtomicNode(s, 0)(Some(newNode(s.except)), None)
        case s: Longest => AtomicNode(s, 0)(None, Some(Trigger.Type.Lift))
        case s: EagerLongest => AtomicNode(s, 0)(None, Some(Trigger.Type.Alive))

        case s: AtomicNonterm => AtomicNode(s, 0)(None, None)
        case s: Sequence => SequenceNode(s, 0, 0, 0)
    }

    def deriveNode(baseNode: NontermNode): Set[Edge] = {
        def deriveAtomic(symbol: AtomicNonterm): Set[Edge] = symbol match {
            case Start =>
                Set(SimpleEdge(baseNode, newNode(grammar.startSymbol), Set()))
            case Nonterminal(nonterminalName) =>
                grammar.rules(nonterminalName) map { s => SimpleEdge(baseNode, newNode(s), Set()) }
            case OneOf(syms) =>
                syms map { s => SimpleEdge(baseNode, newNode(s), Set()) }
            case Repeat(sym, lower) =>
                val baseSeq = Sequence(((0 until lower) map { _ => sym }).toSeq, Set())
                val repeatSeq = Sequence(Seq(symbol, sym), Set())
                Set(SimpleEdge(baseNode, newNode(baseSeq), Set()),
                    SimpleEdge(baseNode, newNode(repeatSeq), Set()))
            case Except(sym, except) =>
                // baseNode가 BaseNode인 경우는 실제 파싱에선 생길 수 없고 테스트 중에만 발생 가능
                // 일반적인 경우에는 baseNode가 AtomicNode이고 liftBlockTrigger가 k.symbol.except 가 들어있어야 함
                Set(SimpleEdge(baseNode, newNode(sym), Set()))
            case LookaheadIs(lookahead) =>
                Set(SimpleEdge(baseNode, newNode(Empty), Set(Trigger(newNode(lookahead), Trigger.Type.Wait))))
            case LookaheadExcept(except) =>
                Set(SimpleEdge(baseNode, newNode(Empty), Set(Trigger(newNode(except), Trigger.Type.Lift))))
            case Proxy(sym) =>
                Set(SimpleEdge(baseNode, newNode(sym), Set()))
            case Backup(sym, backup) =>
                val preferNode = newNode(sym)
                Set(SimpleEdge(baseNode, preferNode, Set()),
                    SimpleEdge(baseNode, newNode(backup), Set(Trigger(preferNode, Trigger.Type.Lift))))
            case Join(sym, join) =>
                Set(JoinEdge(baseNode, newNode(sym), newNode(join)))
            case Longest(sym) =>
                // baseNode가 NewAtomicNode이고 reservedRevertter가 Some(Trigger.Type.Lift)여야 함
                Set(SimpleEdge(baseNode, newNode(sym), Set()))
            case EagerLongest(sym) =>
                // baseNode가 NewAtomicNode이고 reservedRevertter가 Some(Trigger.Type.Alive)여야 함
                Set(SimpleEdge(baseNode, newNode(sym), Set()))
            // Except, Longest, EagerLongest의 경우를 제외하고는 모두 liftBlockTrigger와 reservedReverter가 비어있어야 함
        }
        def deriveSequence(symbol: Sequence, pointer: Int): Set[Edge] = {
            assert(pointer < symbol.seq.size)
            val sym = symbol.seq(pointer)
            if (pointer > 0 && pointer < symbol.seq.size) {
                // whitespace only between symbols
                (symbol.whitespace + sym) map { newNode _ } map { SimpleEdge(baseNode, _, Set()) }
            } else {
                Set(SimpleEdge(baseNode, newNode(sym), Set()))
            }
        }

        baseNode match {
            case DGraph.BaseNode(symbol: AtomicNonterm, pointer) => deriveAtomic(symbol)
            case AtomicNode(symbol, _) => deriveAtomic(symbol)
            case SequenceNode(symbol, pointer, _, _) => deriveSequence(symbol, pointer)
        }
    }

    def deriveTask(task: DeriveTask, cc: Graph): (Graph, Seq[Task]) = {
        val DeriveTask(nextGen, baseNode) = task
        // 1. Derivation
        val newEdges = deriveNode(baseNode)
        val newNodes = {
            assert(newEdges forall { _.start == baseNode })
            // derive된 엣지의 타겟들만 모으고
            val newNodes0 = newEdges flatMap {
                case SimpleEdge(start, end, revertTriggers) => Set(end) ++ (revertTriggers map { _.node })
                case JoinEdge(start, end, join) => Set(end, join)
            }
            // 그 중 liftBlockTrigger가 있으면 그것들도 모아서
            val newNodes1 = newNodes0 flatMap {
                case n: AtomicNode => Set(n) ++ n.liftBlockTrigger
                case n => Set(n)
            }
            // 기존에 있던 것들을 제외
            newNodes1 -- cc.nodes
        }

        // 2. newNodes에 대한 DeriveTask 만들고 derive된 edge/node를 cc에 넣기
        // 이 때, 새로 생성된 노드 중
        // - 바로 finishable한 노드(empty node, 빈 sequence node)들은 results에 추가해주고,
        // - 비어있지 않은 sequence는 progresses에 추가해준다
        val newDeriveTasks: Set[DeriveTask] = newNodes collect {
            case n: NontermNode => DeriveTask(nextGen, n)
        }
        val ncc: Graph = {
            val newResults: Map[Node, Map[Set[Trigger], R]] = (newNodes collect {
                case node @ EmptyNode =>
                    node -> Map(Set[Trigger]() -> resultFunc.empty(0))
                case node @ SequenceNode(Sequence(seq, _), 0, _, _) if seq.isEmpty => // 이 시점에서 SequenceNode의 pointer는 반드시 0
                    node -> Map(Set[Trigger]() -> resultFunc.sequence())
            }).toMap
            val newProgresses: Map[SequenceNode, Map[Set[Trigger], R]] = (newNodes collect {
                case node @ SequenceNode(Sequence(seq, _), 0, _, _) if !seq.isEmpty =>
                    node -> Map(Set[Trigger]() -> resultFunc.sequence())
            }).toMap
            cc.withNodesEdgesResultsProgresses(newNodes, newEdges, newResults, newProgresses)
        }

        // 3. 새로 derive된 노드 중 바로 끝낼 수 있는 노드들에 대해 FinishingTask를 만든다
        // - 바로 끝낼 수 있는 empty node나 빈 sequence node가 이미 ncc의 results에 들어있으므로 ncc의 results에 있는 것들만 추가해주면 된다
        val newFinishingTasks: Set[FinishingTask] = (newNodes collect {
            case node if ncc.results contains node =>
                ncc.results(node) map { r => FinishingTask(nextGen, node, r._2, r._1) }
        }).flatten

        (ncc, newFinishingTasks.toSeq ++ newDeriveTasks.toSeq)
    }
}
