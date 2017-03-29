package com.giyeok.jparser.visualize

import com.giyeok.jparser.Symbols._
import com.giyeok.jparser.nparser.ParsingContext._
import com.giyeok.jparser.nparser.NGrammar
import com.giyeok.jparser.Symbols
import com.giyeok.jparser.nparser.AcceptCondition.Always
import com.giyeok.jparser.nparser.NGrammar.NAtomicSymbol

class DotGraphGenerator(ngrammar: NGrammar) {
    implicit class DotGraphSymbols(sym: Symbol) {
        import com.giyeok.jparser.utils.UnicodeUtil.{toReadable, categoryCodeToName}

        def toDotSymbolName: String = ???
        def toDotLabelName: String = sym match {
            case Any => "any"
            case AnyChar => "anychar"
            case ExactChar(c) =>
                c match {
                    case ' ' => "_"
                    case c => toReadable(c)
                }
            case chars: Terminals.Chars =>
                chars.groups map { range =>
                    if (range._1 == range._2) s"${toReadable(range._1)}"
                    else if (range._1 + 1 == range._2) s"${toReadable(range._1)}-${toReadable(range._2)}"
                    else s"${toReadable(range._1)}-${toReadable(range._2)}"
                } mkString "|"
            case Unicode(c) => s"<unicode ${(c.toSeq.sorted map { categoryCodeToName }) mkString ", "}>"
            case t: Terminal => t.toDotLabelName
            case Start => "<start>"
            case s: Nonterminal => s.name
            case s: Sequence => s.seq map { _.toDotLabelName } mkString " "
            case s: OneOf => s.syms map { _.toDotLabelName } mkString "|"
            case Repeat(sym, lower) =>
                lower match {
                    case 0 => s"${sym.toDotLabelName}*"
                    case 1 => s"${sym.toDotLabelName}+"
                    case lower => s"${sym.toDotLabelName}[$lower-]"
                }
            case s: Except => s"${s.sym.toDotLabelName}-${s.except.toDotLabelName}"
            case LookaheadIs(lookahead) => s"la(${lookahead.toDotLabelName})"
            case LookaheadExcept(except) => s"lx(${except.toDotLabelName})"
            case Proxy(sym) => s"P(${sym.toDotLabelName})"
            case Join(sym, join) => s"${sym.toDotLabelName}&${join.toDotLabelName}"
            case Longest(sym) => s"L(${sym.toDotLabelName})"
            case EagerLongest(sym) => s"EL(${sym.toDotLabelName})"
        }
    }

    trait Props[This <: Props[This]] {
        val properties: scala.collection.mutable.Map[String, String]
        def attr(name: String, value: String): This = {
            properties(name) = value
            this.asInstanceOf[This]
        }
        def addStyle(value: String): This = {
            val newStyle = properties get "style" match {
                case None => value
                case Some(old) => old + "," + value
            }
            attr("style", newStyle)
        }
        def attrString: String = {
            properties map { kv => kv._1 + "=\"" + kv._2 + "\"" } mkString ","
        }
    }
    class DotGraphNode(val name: String, val properties: scala.collection.mutable.Map[String, String]) extends Props[DotGraphNode] {
        def this(name: String) = this(name, scala.collection.mutable.Map[String, String]())
    }

    class DotGraphEdge(val properties: scala.collection.mutable.Map[String, String]) extends Props[DotGraphEdge] {
        def this() = this(scala.collection.mutable.Map[String, String]())
    }

    private var _nodes = Seq[(Node, DotGraphNode)]()
    private val edges = scala.collection.mutable.Map[Edge, DotGraphEdge]()

    private var nodeNamesMap = Map[Node, String]()
    def nodeNameOf(node: Node): String = {
        nodeNamesMap get node match {
            case Some(name) => name
            case None =>
                def isDigitAlpha(c: Char) = ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9')
                val name0: String = node.kernel.symbol match {
                    case _: NAtomicSymbol =>
                        node.kernel.symbol.symbol match {
                            case Nonterminal(name) if name.toSeq forall { isDigitAlpha } => s"${name}_${node.kernel.beginGen}_${node.kernel.endGen}"
                            case Repeat(Nonterminal(name), lower) if (name.toSeq forall { isDigitAlpha }) && (lower == 0 || lower == 1) =>
                                val repeatName = lower match {
                                    case 0 => s"${name}_star"
                                    case 1 => s"${name}_plus"
                                    case lower => s"${name}_repeated_more_than_$lower"
                                }
                                s"${repeatName}_${node.kernel.beginGen}_${node.kernel.endGen}}"
                            case other =>
                                s"node${other.id}_${node.kernel.beginGen}_${node.kernel.endGen}"
                        }
                    case _ =>
                        s"node${node.kernel.symbolId}_${node.kernel.pointer}_${node.kernel.beginGen}_${node.kernel.endGen}"
                }
                val occupiedNodeNames = nodeNamesMap.values.toSet
                val name = if (occupiedNodeNames contains name0) {
                    var i = 1
                    while (occupiedNodeNames contains (name0 + i)) {
                        i += 1
                    }
                    name0 + i
                } else name0
                nodeNamesMap += (node -> name)
                name
        }
    }

    def labelOf(node: Node): String = {
        val kernelLabel = node.kernel match {
            case kernel if kernel.symbol.isInstanceOf[NAtomicSymbol] =>
                if (kernel.pointer == 0) {
                    s"&bull;${kernel.symbol.symbol.toDotLabelName},${kernel.beginGen}&#x2025;${kernel.endGen}"
                } else {
                    s"${kernel.symbol.symbol.toDotLabelName}&bull;,${kernel.beginGen}&#x2025;${kernel.endGen}"
                }
            case kernel =>
                val Symbols.Sequence(seq, whitespace) = ngrammar.nsequences(kernel.symbolId).symbol
                val split = seq.splitAt(kernel.pointer)
                (split._1 map { _.toDotLabelName } mkString " ") + "&bull;" + (split._2 map { _.toDotLabelName } mkString " ") + "," + s"${kernel.beginGen}&#x2025;${kernel.endGen}"
        }
        kernelLabel // TODO condition 추가
    }

    def getNode(node: Node): Option[DotGraphNode] = (_nodes find { _._1 == node } map { _._2 })
    def addNode(node: Node): DotGraphNode = {
        getNode(node) match {
            case Some(dotnode) => dotnode
            case None =>
                val dotnode = new DotGraphNode(nodeNameOf(node)).attr("label", labelOf(node))
                node.kernel.symbol match {
                    case _: NGrammar.Sequence =>
                        dotnode.attr("shape", "rectangle")
                    case _ =>
                        dotnode.attr("shape", "rectangle").addStyle("rounded")
                }
                _nodes :+= (node, dotnode)
                dotnode
        }
    }

    def addGraph(graph: Graph): DotGraphGenerator = {
        var visited = Set[Node]()
        var queue = List[Node]()
        def traverseNode(): Unit = {
            val node = queue.head
            queue = queue.tail
            addNode(node)
            graph.edgesByStart(node).toSeq sortWith { (x, y) => x.end.kernel.symbolId < y.end.kernel.symbolId } foreach {
                case edge @ SimpleEdge(start, end) =>
                    edges(edge) = new DotGraphEdge()
                    if (!(visited contains end)) queue +:= end
                    visited += end
                case edge @ JoinEdge(start, end, join) =>
                    edges(edge) = new DotGraphEdge()
                    if (!(visited contains end)) queue +:= end
                    if (!(visited contains join)) queue +:= join
                    queue :+ join
                    visited += end
                    visited += join
            }
            if (queue.nonEmpty) {
                traverseNode()
            }
        }
        val startNode = Node(Kernel(ngrammar.startSymbol, 0, 0, 0)(ngrammar.nsymbols(ngrammar.startSymbol)), Always)
        visited += startNode
        queue +:= startNode
        traverseNode()

        graph.nodes foreach { node =>
            addNode(node)
        }
        graph.edges foreach { edge =>
            edges(edge) = new DotGraphEdge()
        }
        this
    }

    def addTransition(baseGraph: Graph, afterGraph: Graph): DotGraphGenerator = {
        addGraph(baseGraph)
        // baseGraph -> afterGraph 과정에서 없어진 노드/엣지 스타일에 dotted 추가
        (baseGraph.nodes -- afterGraph.nodes) foreach { removedNode =>
            getNode(removedNode) match {
                case Some(node) => node.addStyle("dotted")
                case _ => // nothing to do
            }
        }
        (baseGraph.edges -- afterGraph.edges) foreach { removedEdge =>
            if (edges contains removedEdge) {
                edges(removedEdge).addStyle("dotted")
            }
        }
        // 새로 추가된 노드 스타일에 filled 추가
        (afterGraph.nodes -- baseGraph.nodes) foreach { node =>
            addNode(node).addStyle("filled")
        }
        (afterGraph.edges -- baseGraph.edges) foreach { edge =>
            if (!(edges contains edge)) {
                edges(edge) = new DotGraphEdge()
            }
        }
        this
    }

    def printDotGraph(): Unit = {
        println("digraph G {")
        println("    node[fontname=\"monospace\", height=.1];")
        _nodes foreach { kv =>
            if (kv._1.kernel.symbolId != ngrammar.startSymbol) {
                println(s"    ${nodeNameOf(kv._1)}[${kv._2.attrString}];")
            }
        }
        println()
        edges foreach { kv =>
            kv._1 match {
                case edge @ SimpleEdge(start, end) =>
                    if (start.kernel.symbolId != ngrammar.startSymbol) {
                        println(s"    ${nodeNameOf(start)} -> ${nodeNameOf(end)}[${kv._2.attrString}];")
                    }
                case edge @ JoinEdge(start, end, join) =>
                    println(s"    ${nodeNameOf(start)}proxy" + "[shape=\"point\", width=\"0.1\", height=\"0\"];")
                    println(s"    ${nodeNameOf(start)} -> ${nodeNameOf(start)}proxy[dir=none,${kv._2.attrString}];")
                    println(s"    ${nodeNameOf(start)}proxy -> ${nodeNameOf(end)}[${kv._2.attrString}];")
                    println(s"    ${nodeNameOf(start)}proxy -> ${nodeNameOf(join)}[${kv._2.attrString}];")
            }
        }
        println("}")
    }

}