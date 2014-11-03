package com.giyeok.moonparser

case class ParseResult(parseNode: ParseTree.ParseNode[Symbols.Symbol])

class Parser(val grammar: Grammar)
        extends SymbolProgresses
        with SymbolsGraph
        with ParsingErrors
        with GrammarChecker {
    import Inputs._

    case class ParsingContext(graph: Graph) {
        def proceedTerminal(next: Input): Either[ParsingContext, ParsingError] = {
            val nextNodes = (graph.nodes collect {
                case s: SymbolProgressTerminal if s accept next =>
                    (s, (s proceedTerminal next).get)
            })
            if (nextNodes isEmpty) Right(ParsingErrors.UnexpectedInput(next)) else {
                val newGraph: (Set[Node], Set[Edge]) = ???
                nextNodes map {
                    case (oldNode, newNode) =>
                        val incoming = graph.incomingEdges(oldNode)
                        incoming filter { _.isInstanceOf[SimpleEdge] } map { _.from } map { i => newNode.asInstanceOf[SymbolProgressNonterminal] lift SimpleLiftingRequest(i) }
                }
                // TODO check newgraph still contains start symbol
                Left(ParsingContext(Graph(newGraph._1, newGraph._2)))
            }
        }
        def toResult: ParseResult = ParseResult(???)
    }

    object ParsingContext {
        def fromSeeds(seeds: Set[Node]): ParsingContext = {
            def expand(queue: List[Node], nodes: Set[Node], edges: Set[Edge]): (Set[Node], Set[Edge]) =
                queue match {
                    case (head: SymbolProgressNonterminal) +: tail =>
                        assert(nodes contains head)
                        val newedges = head.derive
                        val news: Set[SymbolProgress] = newedges flatMap { _.nodes } filterNot { nodes contains _ }
                        expand(news.toList ++ tail, nodes ++ news, edges ++ newedges)
                    case head +: tail =>
                        expand(tail, nodes, edges)
                    case Nil => (nodes, edges)
                }
            val (nodes, edges) = expand(seeds.toList, seeds, Set())
            ParsingContext(Graph(nodes, edges))
        }
    }

    val startingContext = ParsingContext.fromSeeds(Set(SymbolProgress(grammar.startSymbol)))

    def parse(source: Inputs.Source): Either[ParsingContext, ParsingError] =
        source.foldLeft[Either[ParsingContext, ParsingError]](Left(startingContext)) {
            (ctx, terminal) =>
                ctx match {
                    case Left(ctx) => ctx.proceedTerminal(terminal)
                    case error @ Right(_) => error
                }
        }
    def parse(source: String): Either[ParsingContext, ParsingError] =
        parse(source.toCharArray.zipWithIndex map { p => Character(p._1, p._2) })
}
