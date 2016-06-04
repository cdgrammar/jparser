package com.giyeok.jparser.visualize

import com.giyeok.jparser.NewParser
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Composite
import org.eclipse.zest.core.widgets.Graph
import org.eclipse.swt.SWT
import org.eclipse.zest.core.widgets.GraphConnection
import org.eclipse.zest.core.widgets.CGraphNode
import org.eclipse.draw2d.LineBorder
import org.eclipse.draw2d.ColorConstants
import org.eclipse.draw2d.MarginBorder
import org.eclipse.draw2d.Figure
import org.eclipse.draw2d
import org.eclipse.swt.graphics.Font
import com.giyeok.jparser.visualize.FigureGenerator.Spacing
import org.eclipse.zest.layouts.LayoutStyles
import org.eclipse.swt.layout.FillLayout
import org.eclipse.zest.core.widgets.ZestStyles
import com.giyeok.jparser.Grammar
import org.eclipse.swt.widgets.Shell
import com.giyeok.jparser.DGraph
import org.eclipse.swt.widgets.Display
import com.giyeok.jparser.Symbols.Nonterm
import com.giyeok.jparser.Symbols.AtomicSymbol
import org.eclipse.zest.core.widgets.GraphNode
import org.eclipse.swt.events.MouseAdapter
import org.eclipse.swt.events.MouseEvent
import org.eclipse.swt.events.KeyListener
import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent
import com.giyeok.jparser.ParseResultTree.Node
import com.giyeok.jparser.Symbols.Symbol
import com.giyeok.jparser.NewParser
import com.giyeok.jparser.ParseResult
import com.giyeok.jparser.CtxGraph
import com.giyeok.jparser.ParsingGraph
import com.giyeok.jparser.ParseForest
import com.giyeok.jparser.ParseForestFunc
import com.giyeok.jparser.DerivationFunc
import com.giyeok.jparser.ParsingGraph.AtomicNode
import com.giyeok.jparser.ParseResultTree
import com.giyeok.jparser.Symbols.Sequence
import com.giyeok.jparser.Symbols.Empty
import com.giyeok.jparser.Symbols.AtomicNonterm
import org.eclipse.draw2d.ToolbarLayout
import com.giyeok.jparser.Results
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.events.MouseListener
import org.eclipse.zest.core.viewers.GraphViewer
import com.giyeok.jparser.ParseForest
import com.giyeok.jparser.ParseResultTree
import org.eclipse.draw2d.FigureCanvas

trait BasicGenerators {
    val figureGenerator: FigureGenerator.Generator[Figure] = FigureGenerator.draw2d.Generator

    val figureAppearances = new FigureGenerator.Appearances[Figure] {
        val default = FigureGenerator.draw2d.FontAppearance(new Font(null, "Monospace", 10, SWT.NONE), ColorConstants.black)
        val nonterminal = FigureGenerator.draw2d.FontAppearance(new Font(null, "Monospace", 12, SWT.BOLD), ColorConstants.blue)
        val terminal = FigureGenerator.draw2d.FontAppearance(new Font(null, "Monospace", 12, SWT.NONE), ColorConstants.red)

        override val small = FigureGenerator.draw2d.FontAppearance(new Font(null, "Monospace", 8, SWT.NONE), ColorConstants.gray)
        override val kernelDot = FigureGenerator.draw2d.FontAppearance(new Font(null, "Monospace", 12, SWT.NONE), ColorConstants.green)
        override val symbolBorder = FigureGenerator.draw2d.BorderAppearance(new LineBorder(ColorConstants.lightGray))
    }
    val tooltipAppearances = new FigureGenerator.Appearances[Figure] {
        val default = figureAppearances.default
        val nonterminal = figureAppearances.nonterminal
        val terminal = figureAppearances.terminal

        override val input = FigureGenerator.draw2d.FontAppearance(new Font(null, "Monospace", 10, SWT.NONE), ColorConstants.black)
        override val small = figureAppearances.small
        override val kernelDot = figureAppearances.kernelDot
        override val hSymbolBorder =
            new FigureGenerator.draw2d.ComplexAppearance(
                FigureGenerator.draw2d.BorderAppearance(new MarginBorder(0, 1, 1, 1)),
                FigureGenerator.draw2d.NewFigureAppearance(),
                FigureGenerator.draw2d.BorderAppearance(new FigureGenerator.draw2d.PartialLineBorder(ColorConstants.lightGray, 1, false, true, true, true)))
        override val vSymbolBorder =
            new FigureGenerator.draw2d.ComplexAppearance(
                FigureGenerator.draw2d.BorderAppearance(new MarginBorder(1, 0, 1, 1)),
                FigureGenerator.draw2d.NewFigureAppearance(),
                FigureGenerator.draw2d.BorderAppearance(new FigureGenerator.draw2d.PartialLineBorder(ColorConstants.lightGray, 1, true, false, true, true)))
        override val wsBorder =
            new FigureGenerator.draw2d.ComplexAppearance(
                FigureGenerator.draw2d.BorderAppearance(new MarginBorder(0, 1, 1, 1)),
                FigureGenerator.draw2d.NewFigureAppearance(),
                FigureGenerator.draw2d.BorderAppearance(new FigureGenerator.draw2d.PartialLineBorder(ColorConstants.lightBlue, 1, false, true, true, true)),
                FigureGenerator.draw2d.BackgroundAppearance(ColorConstants.lightGray))
        override val joinHighlightBorder =
            new FigureGenerator.draw2d.ComplexAppearance(
                FigureGenerator.draw2d.BorderAppearance(new MarginBorder(1, 1, 1, 1)),
                FigureGenerator.draw2d.NewFigureAppearance(),
                FigureGenerator.draw2d.BorderAppearance(new LineBorder(ColorConstants.red)))

    }
    val symbolFigureGenerator = new SymbolFigureGenerator(figureGenerator, figureAppearances)
    val parseResultTreeFigureGenerator = new ParseResultTreeFigureGenerator(figureGenerator, tooltipAppearances)
}

trait ParseForestFigureGenerator[Fig] {
    val figureGenerator: FigureGenerator.Generator[Fig]
    val figureAppearances: FigureGenerator.Appearances[Fig]
    val parseResultTreeFigureGenerator: ParseResultTreeFigureGenerator[Fig]

    def parseResultTreesFigureOf(trees: Set[ParseResultTree.Node]): Fig = {
        val (g, ap) = (figureGenerator, figureAppearances)
        g.verticalFig(Spacing.Medium, trees.toSeq map { parseResultTreeFigureGenerator.parseNodeHFig _ })
    }
    def parseResultForestFigureOf(forest: ParseForest): Fig = parseResultTreesFigureOf(forest.trees)
}

trait KernelFigureGenerator[Fig] {
    val figureGenerator: FigureGenerator.Generator[Fig]
    val figureAppearances: FigureGenerator.Appearances[Fig]
    val symbolFigureGenerator: SymbolFigureGenerator[Fig]

    def dot = figureGenerator.textFig("\u2022", figureAppearances.kernelDot)
    def atomicFigure(symbol: AtomicSymbol) = {
        symbolFigureGenerator.symbolFig(symbol)
        // figureGenerator.horizontalFig(Spacing.Small, Seq(dot, symbolFigureGenerator.symbolFig(symbol)))
    }
    def sequenceFigure(symbol: Sequence, pointer: Int) = {
        val (p, f) = symbol.seq.splitAt(pointer)
        val f0 = figureGenerator.horizontalFig(Spacing.Medium, p map { symbolFigureGenerator.symbolFig _ })
        val f1 = figureGenerator.horizontalFig(Spacing.Medium, f map { symbolFigureGenerator.symbolFig _ })
        figureGenerator.horizontalFig(Spacing.Small, Seq(f0, dot, f1))
    }
}

class NodeIdCache {
    private var counter = 0
    private val nodeIdMap = scala.collection.mutable.Map[ParsingGraph.Node, Int]()
    private val idNodeMap = scala.collection.mutable.Map[Int, ParsingGraph.Node]()

    def of(node: ParsingGraph.Node): Int = {
        nodeIdMap get node match {
            case Some(id) => id
            case None =>
                counter += 1
                nodeIdMap(node) = counter
                idNodeMap(counter) = node
                counter
        }
    }
    def of(id: Int): Option[ParsingGraph.Node] = idNodeMap get id
}

trait ParsingGraphVisualizeWidget extends ParseForestFigureGenerator[Figure] with KernelFigureGenerator[Figure] {
    type R = ParseForest

    val graphView: Graph
    val grammar: Grammar
    val nodeIdCache: NodeIdCache

    val figureGenerator: FigureGenerator.Generator[Figure]
    val figureAppearances: FigureGenerator.Appearances[Figure]
    val symbolFigureGenerator: SymbolFigureGenerator[Figure]

    val nodesMap = scala.collection.mutable.Map[ParsingGraph.Node, CGraphNode]()
    val edgesMap = scala.collection.mutable.Map[ParsingGraph.Edge, Seq[GraphConnection]]()
    val edgesBetween = scala.collection.mutable.Map[(ParsingGraph.Node, ParsingGraph.Node), Int]()
    val parseResultTreeFigureGenerator: ParseResultTreeFigureGenerator[Figure]

    def nodeFromFigure(fig: Figure): CGraphNode = {
        val nodeFig = figureGenerator.horizontalFig(FigureGenerator.Spacing.Medium, Seq(fig))
        nodeFig.setBorder(new LineBorder(ColorConstants.darkGray))
        nodeFig.setBackgroundColor(ColorConstants.buttonLightest)
        nodeFig.setOpaque(true)
        nodeFig.setSize(nodeFig.getPreferredSize())

        new CGraphNode(graphView, SWT.NONE, nodeFig)
    }

    def nodeFigureOf(node: ParsingGraph.Node): Figure = {
        val nodeId = nodeIdCache.of(node)

        val (g, ap) = (figureGenerator, figureAppearances)
        val fig = node match {
            case ParsingGraph.EmptyNode =>
                g.horizontalFig(Spacing.Big, Seq(
                    g.supFig(g.textFig(s"$nodeId", ap.default)),
                    g.horizontalFig(Spacing.Small, Seq(symbolFigureGenerator.symbolFig(Empty), dot))))
            case ParsingGraph.TermNode(symbol, beginGen) =>
                g.horizontalFig(Spacing.Big, Seq(
                    g.supFig(g.textFig(s"$nodeId", ap.default)),
                    g.horizontalFig(Spacing.Small, Seq(dot, symbolFigureGenerator.symbolFig(symbol))),
                    g.textFig(s"$beginGen", ap.default)))
            case ParsingGraph.AtomicNode(symbol, beginGen) =>
                g.horizontalFig(Spacing.Big, Seq(
                    g.supFig(g.textFig(s"$nodeId", ap.default)),
                    atomicFigure(symbol),
                    g.textFig(s"$beginGen", ap.default)))
            case ParsingGraph.SequenceNode(symbol, pointer, beginGen, endGen) =>
                val f = g.horizontalFig(Spacing.Big, Seq(
                    g.supFig(g.textFig(s"$nodeId", ap.default)),
                    sequenceFigure(symbol, pointer),
                    g.textFig(s"$beginGen-$endGen", ap.default)))
                // TODO progresses 표시
                f
        }
        if (node.isInstanceOf[DGraph.BaseNode]) {
            fig.setOpaque(true)
            fig.setBackgroundColor(ColorConstants.yellow)
            fig
        }
        fig.setBorder(new MarginBorder(1, 2, 1, 2))
        fig
    }

    def nodeOf(node: ParsingGraph.Node): CGraphNode = {
        nodesMap get node match {
            case Some(cgnode) => cgnode
            case None =>
                val fig = nodeFigureOf(node)

                val n = nodeFromFigure(fig)
                n.setData(node)
                nodesMap(node) = n

                node match {
                    case node: ParsingGraph.AtomicNode if node.liftBlockTrigger.isDefined =>
                        // liftBlockTrigger 표시
                        val liftBlockTriggerNode = nodeOf(node.liftBlockTrigger.get)
                        new GraphConnection(graphView, ZestStyles.CONNECTIONS_DIRECTED, liftBlockTriggerNode, n).setText("BlockLiftIfLifted")
                    case _ => // nothing to do
                }

                n
        }
    }

    def setResultsTooltipForNode(node: ParsingGraph.Node, results: Results[ParsingGraph.Node, R]): Unit = {
        results.of(node) foreach {
            _ foreach { entry =>
                val tooltipFig = new Figure()
                tooltipFig.setOpaque(true)
                tooltipFig.setBackgroundColor(ColorConstants.white)
                tooltipFig.setLayoutManager(new ToolbarLayout(false))
                tooltipFig.add(figureGenerator.textFig(revertTriggersString(entry._1), figureAppearances.default))
                tooltipFig.add(parseResultForestFigureOf(entry._2))
                nodeOf(node).getFigure().setToolTip(tooltipFig)
            }
        }
    }
    def setProgressesTooltipForNode(node: ParsingGraph.SequenceNode, progresses: Results[ParsingGraph.SequenceNode, R]): Unit = {
        if (node.pointer > 0) {
            progresses.of(node) foreach {
                _ foreach { entry =>
                    val tooltipFig = new Figure()
                    tooltipFig.setOpaque(true)
                    tooltipFig.setBackgroundColor(ColorConstants.white)
                    tooltipFig.setLayoutManager(new ToolbarLayout(false))
                    tooltipFig.add(figureGenerator.textFig(revertTriggersString(entry._1), figureAppearances.default))
                    tooltipFig.add(parseResultForestFigureOf(entry._2))
                    nodeOf(node).getFigure().setToolTip(tooltipFig)
                }
            }
        }
    }

    def edgeOf(edge: ParsingGraph.Edge): Seq[GraphConnection] = {
        def setCurveTo(conn: GraphConnection, start: ParsingGraph.Node, end: ParsingGraph.Node): GraphConnection = {
            val curve = edgesBetween.getOrElse((start, end), 0) + edgesBetween.getOrElse((end, start), 0)
            conn.setCurveDepth(curve * 15)
            edgesBetween((start, end)) = edgesBetween.getOrElse((start, end), 0) + 1
            conn
        }
        edgesMap get edge match {
            case Some(conns) => conns
            case None =>
                val conns = edge match {
                    case ParsingGraph.SimpleEdge(start, end, revertTriggers) =>
                        val conn = setCurveTo(new GraphConnection(graphView, ZestStyles.CONNECTIONS_DIRECTED, nodesMap(start), nodesMap(end)), start, end)
                        if (!(revertTriggers.isEmpty)) {
                            conn.setText(revertTriggersString(revertTriggers))
                        }
                        Seq(conn)
                    case ParsingGraph.JoinEdge(start, end, join) =>
                        val conn = setCurveTo(new GraphConnection(graphView, ZestStyles.CONNECTIONS_DIRECTED, nodesMap(start), nodesMap(end)), start, end)
                        val connJoin = setCurveTo(new GraphConnection(graphView, ZestStyles.CONNECTIONS_DIRECTED, nodesMap(start), nodesMap(join)), start, end)
                        conn.setText("main")
                        connJoin.setText("join")
                        Seq(conn, connJoin)
                }
                edgesMap(edge) = conns
                conns
        }
    }

    def addResults[N <: ParsingGraph.Node](results: Results[N, R], bind: Boolean, ignoreEmpty: Boolean, lineDecorator: GraphConnection => Unit): Unit = {
        results.asMap foreach { result => resultsOf(result, bind, ignoreEmpty, lineDecorator) }
    }

    val resultMap = scala.collection.mutable.Map[R, CGraphNode]()
    def resultOf(result: R): CGraphNode = {
        resultMap get result match {
            case Some(cached) => cached
            case None =>
                val resultNode = nodeFromFigure(parseResultTreesFigureOf(result.trees))
                resultNode.setData(result)
                resultMap(result) = resultNode
                resultNode
        }
    }

    def resultsOf[N <: ParsingGraph.Node](results: (N, Map[Set[ParsingGraph.Trigger], R]), bind: Boolean, ignoreEmpty: Boolean, lineDecorator: GraphConnection => Unit): Unit = {
        val (node, matches) = results
        matches foreach { m =>
            val (triggers, result) = m

            val trees = if (bind) ParseForestFunc.bind(node.symbol, result).trees else result.trees
            val trees1 =
                if (!ignoreEmpty) trees else {
                    trees filter {
                        case n: ParseResultTree.SequenceNode => !n.children.isEmpty
                        case _ => true
                    }
                }

            if (!trees1.isEmpty && (nodesMap contains node)) {
                val resultNode = resultOf(result)
                val connection = new GraphConnection(graphView, ZestStyles.CONNECTIONS_SOLID, nodesMap(node), resultNode)
                lineDecorator(connection)
                if (!triggers.isEmpty) {
                    connection.setText(revertTriggersString(triggers))
                }
            }
        }
    }

    def addGraph(graph: ParsingGraph[R]): Unit = {
        graph.nodes foreach { nodeOf(_) }
        graph.edges foreach { edgeOf(_) }

        // 현재 그래프의 progress를 툴팁으로 추가
        graph.nodes foreach {
            case node: ParsingGraph.SequenceNode =>
                setProgressesTooltipForNode(node, graph.progresses)
            case _ =>
        }
    }

    def revertTriggersString(revertTriggers: Set[ParsingGraph.Trigger]): String =
        revertTriggers map {
            case ParsingGraph.Trigger(node, ttype) =>
                // s"$ttype($node)"
                s"$ttype(${nodeIdCache.of(node)})"
        } mkString " or "

    def nodesAt(ex: Int, ey: Int): Seq[Any] = {
        import scala.collection.JavaConversions._

        val (x, y) = (ex + graphView.getHorizontalBar().getSelection(), ey + graphView.getVerticalBar().getSelection())

        graphView.getNodes.toSeq collect {
            case n: GraphNode if n != null && n.getNodeFigure() != null && n.getNodeFigure().containsPoint(x, y) && n.getData() != null =>
                println(n)
                n.getData
        }
    }
}

trait InteractionSupport extends Composite with ParsingGraphVisualizeWidget {
    var visibleNodes = Seq[CGraphNode]()
    var visibleEdges = Seq[GraphConnection]()

    def setSubgraph(nodes: Seq[CGraphNode], edges: Seq[GraphConnection]): Unit = {
        nodesMap.values foreach { _.setVisible(false) }
        edgesMap.values foreach { _ foreach { _.setVisible(false) } }

        visibleNodes = nodes
        visibleEdges = edges

        nodes foreach { _.setVisible(true) }
        edges foreach { _.setVisible(true) }
    }

    override def nodeOf(node: ParsingGraph.Node): CGraphNode = {
        val n = super.nodeOf(node)
        visibleNodes +:= n
        n
    }
    override def edgeOf(edge: ParsingGraph.Edge): Seq[GraphConnection] = {
        val e = super.edgeOf(edge)
        visibleEdges ++:= e
        e
    }

    val blinkInterval = 100
    val blinkCycle = 10
    val highlightedNodes = scala.collection.mutable.Map[CGraphNode, Int]()

    def highlightNode(cgnode: CGraphNode): Unit = {
        val display = getDisplay()
        if (!(highlightedNodes contains cgnode) && (visibleNodes contains cgnode)) {
            cgnode.highlight()
            highlightedNodes(cgnode) = 0
            display.timerExec(100, new Runnable() {
                def run(): Unit = {
                    highlightedNodes get cgnode match {
                        case Some(count) =>
                            if (count % 2 == 0) {
                                cgnode.setVisible(true)
                            } else {
                                cgnode.setVisible(false)
                            }
                            highlightedNodes(cgnode) = count + 1
                            if (count < blinkCycle) {
                                display.timerExec(blinkInterval, this)
                            } else {
                                cgnode.setVisible(true)
                            }
                        case None => // nothing to do
                    }
                }
            })
        }
    }
    def unhighlightAllNodes(): Unit = {
        highlightedNodes foreach { kv =>
            val (cgnode, _) = kv
            cgnode.unhighlight()
            cgnode.setVisible(visibleNodes contains cgnode)
        }
        highlightedNodes.clear()
    }
}

case class InputAccum(textSoFar: String, lastTime: Long) {
    def accum(char: Char, thisTime: Long) =
        if (thisTime - lastTime < 500) InputAccum(textSoFar + char, thisTime) else InputAccum("" + char, thisTime)
    def textAsInt: Option[Int] = try { Some(textSoFar.toInt) } catch { case _: Throwable => None }
}

abstract class GraphControl(parent: Composite, style: Int, graph: ParsingGraph[ParseForest])
        extends Composite(parent, style) with ParsingGraphVisualizeWidget with BasicGenerators with KernelFigureGenerator[Figure] with ParseForestFigureGenerator[Figure] with InteractionSupport {
    setLayout(new FillLayout())
    val graphViewer = new GraphViewer(this, style)
    val graphView = graphViewer.getGraphControl()

    def applyLayout(animation: Boolean): Unit = {
        if (animation) {
            graphViewer.setNodeStyle(ZestStyles.NONE)
        } else {
            graphViewer.setNodeStyle(ZestStyles.NODES_NO_LAYOUT_ANIMATION)
        }
        import org.eclipse.zest.layouts.algorithms._
        val layoutAlgorithm = new TreeLayoutAlgorithm(LayoutStyles.NO_LAYOUT_NODE_RESIZING | LayoutStyles.ENFORCE_BOUNDS)
        graphView.setLayoutAlgorithm(layoutAlgorithm, true)
    }

    var inputAccum: InputAccum = InputAccum("", 0)
    graphView.addKeyListener(new KeyListener() {
        def keyPressed(e: org.eclipse.swt.events.KeyEvent): Unit = {
            e.keyCode match {
                case 'R' | 'r' => applyLayout(true)
                case c if '0' <= c && c <= '9' =>
                    unhighlightAllNodes()
                    inputAccum = inputAccum.accum(c.toChar, System.currentTimeMillis())
                    val highlightingNode = inputAccum.textAsInt flatMap { nodeIdCache.of(_) }
                    val highlightingCGNode = highlightingNode flatMap { nodesMap.get(_) }
                    highlightingCGNode match {
                        case Some(cgnode) =>
                            highlightNode(cgnode)
                        case None => // nothing to do
                    }
                case _ =>
            }
        }
        def keyReleased(e: org.eclipse.swt.events.KeyEvent): Unit = {}
    })

    def resultsOf(node: ParsingGraph.NontermNode): Option[Map[Set[ParsingGraph.Trigger], ParseForest]] = graph.results.of(node)
    def progressesOf(node: ParsingGraph.SequenceNode): Option[Map[Set[ParsingGraph.Trigger], ParseForest]] = graph.progresses.of(node)

    def resultAndProgressFigureOf(node: ParsingGraph.NontermNode): Figure = {
        val progressFig = new Figure()
        progressFig.setLayoutManager(new ToolbarLayout(false))
        node match {
            case node: ParsingGraph.SequenceNode =>
                progressesOf(node) match {
                    case Some(progresses) =>
                        progressFig.add(figureGenerator.textFig("Progresses --", figureAppearances.default))
                        progresses foreach { p =>
                            progressFig.add(figureGenerator.textFig(revertTriggersString(p._1), figureAppearances.default))
                            progressFig.add(parseResultForestFigureOf(p._2))
                        }
                    case None => // nothing to do
                }
            case _ => // nothing to do
        }

        val resultFig = new Figure()
        resultFig.setLayoutManager(new ToolbarLayout(false))
        resultsOf(node) match {
            case Some(results) =>
                resultFig.add(figureGenerator.textFig("Results --", figureAppearances.default))
                results foreach { p =>
                    resultFig.add(figureGenerator.textFig(revertTriggersString(p._1), figureAppearances.default))
                    resultFig.add(parseResultForestFigureOf(p._2))
                }
            case _ => // nothing to do
        }

        val allFig = new Figure()
        allFig.setLayoutManager(new ToolbarLayout(true))
        allFig.add(nodeFigureOf(node))
        allFig.add(progressFig)
        allFig.add(resultFig)

        allFig
    }

    graphView.addMouseListener(new MouseListener() {
        def mouseDown(e: org.eclipse.swt.events.MouseEvent): Unit = {}
        def mouseUp(e: org.eclipse.swt.events.MouseEvent): Unit = {}
        def mouseDoubleClick(e: org.eclipse.swt.events.MouseEvent): Unit = {
            nodesAt(e.x, e.y) foreach {
                case ParseForest(trees) =>
                    trees foreach { tree =>
                        new ParseResultTreeViewer(tree, figureGenerator, figureAppearances, parseResultTreeFigureGenerator).start()
                    }
                case node: ParsingGraph.NontermNode =>
                    if ((e.stateMask & SWT.SHIFT) != 0) {
                        DerivationGraphVisualizer.start(grammar, getDisplay(), new Shell(getDisplay()), DerivationGraphVisualizer.kernelOf(node))
                    } else {
                        val figureShell = new Shell(getDisplay())
                        figureShell.setLayout(new FillLayout)
                        val figureCanv = new FigureCanvas(figureShell, SWT.NONE)
                        figureCanv.setContents(resultAndProgressFigureOf(node))
                        figureShell.open()
                    }
                case data =>
                    println(data)
            }
        }
    })

    override def addKeyListener(keyListener: KeyListener): Unit = graphView.addKeyListener(keyListener)
    override def addMouseListener(mouseListener: MouseListener): Unit = graphView.addMouseListener(mouseListener)

    addGraph(graph)
    applyLayout(false)
}

abstract class GraphTransitionControl(parent: Composite, style: Int, baseGraph: ParsingGraph[ParseForest], afterGraph: ParsingGraph[ParseForest]) extends GraphControl(parent, style, baseGraph) {
    addGraph(baseGraph)
    addGraph(afterGraph)

    override def resultsOf(node: ParsingGraph.NontermNode): Option[Map[Set[ParsingGraph.Trigger], ParseForest]] = afterGraph.results.of(node)
    override def progressesOf(node: ParsingGraph.SequenceNode): Option[Map[Set[ParsingGraph.Trigger], ParseForest]] = afterGraph.progresses.of(node)

    // baseGraph -> afterGraph 과정에서 없어진 노드/엣지 빨간색으로 표시
    (baseGraph.nodes -- afterGraph.nodes) foreach { removedNode =>
        nodeOf(removedNode).getFigure().setBorder(new LineBorder(ColorConstants.red))
    }
    (baseGraph.edges -- afterGraph.edges) foreach { removedEdge =>
        edgeOf(removedEdge) foreach { conn => conn.setLineColor(ColorConstants.red) }
    }

    // 새로 추가된 노드/엣지 파란색으로 표시
    (afterGraph.nodes -- baseGraph.nodes) foreach { newNode =>
        nodeOf(newNode).getFigure().setBorder(new LineBorder(ColorConstants.blue))
    }
    (afterGraph.edges -- baseGraph.edges) foreach { newEdge =>
        edgeOf(newEdge) foreach { conn => conn.setLineColor(ColorConstants.blue) }
    }

    // 이전 세대 그래프의 results를 툴팁으로 추가
    baseGraph.nodes foreach {
        case node =>
            setResultsTooltipForNode(node, afterGraph.results)
    }

    val (allNodes, allEdges) = (baseGraph.nodes ++ afterGraph.nodes, baseGraph.edges ++ afterGraph.edges)
    addKeyListener(new KeyListener() {
        def keyPressed(e: org.eclipse.swt.events.KeyEvent): Unit = {
            e.keyCode match {
                case 'q' | 'Q' =>
                    // 전 그래프만 보여주기
                    setSubgraph(baseGraph.nodes.toSeq map { nodesMap(_) }, baseGraph.edges.toSeq flatMap { edgesMap(_) })
                case 'w' | 'W' =>
                    // 후 그래프만 보여주기
                    // - 후그래프 보이기
                    setSubgraph(afterGraph.nodes.toSeq map { nodesMap(_) }, afterGraph.edges.toSeq flatMap { edgesMap(_) })
                case 'e' | 'E' =>
                    // 전후 그래프 모두 보여주기
                    setSubgraph(allNodes.toSeq map { nodesMap(_) }, allEdges.toSeq flatMap { edgesMap(_) })
                case _ =>
            }
        }
        def keyReleased(e: org.eclipse.swt.events.KeyEvent): Unit = {}
    })
}

class DerivationGraphVisualizeWidget(parent: Composite, style: Int, val grammar: Grammar, val nodeIdCache: NodeIdCache, dgraph: DGraph[ParseForest])
    extends GraphControl(parent, style, dgraph)
class DerivationSliceGraphVisualizeWidget(parent: Composite, style: Int, val grammar: Grammar, val nodeIdCache: NodeIdCache, baseDGraph: DGraph[ParseForest], sliceDGraph: DGraph[ParseForest])
    extends GraphTransitionControl(parent, style, baseDGraph, sliceDGraph)

class ParsingContextGraphVisualizeWidget(parent: Composite, style: Int, val grammar: Grammar, val nodeIdCache: NodeIdCache, context: NewParser[ParseForest]#ParsingCtx)
        extends GraphControl(parent, style, context.graph) {
    // ParsingContext 그래프에서는 파싱 결과만 별도로 표시
    val resultNode = context.result match {
        case Some(result) =>
            val resultNode = resultOf(result)
            resultNode.getFigure.setBorder(new LineBorder(ColorConstants.green, 1))
            if (context.graph.nodes contains context.startNode) {
                new GraphConnection(graphView, ZestStyles.CONNECTIONS_DIRECTED, nodeOf(context.startNode), resultNode).setLineColor(ColorConstants.green)
            }
        case None =>
    }

    // derivation tip node는 노란 배경으로
    context.derivables foreach { nodeOf(_).setBackgroundColor(ColorConstants.yellow) }

    // addResults(context.graph.results, true, false, { conn => conn.setLineColor(ColorConstants.lightGreen); })
    // addResults(context.graph.progresses, false, true, { conn => conn.setLineColor(ColorConstants.lightGreen); conn.setLineStyle(SWT.LINE_DASH) })

    applyLayout(false)
}

class ExpandTransitionVisualize(parent: Composite, style: Int, val grammar: Grammar, val nodeIdCache: NodeIdCache, transition: NewParser[ParseForest]#ExpandTransition)
        extends GraphTransitionControl(parent, style, transition.baseGraph, transition.nextGraph) {
    // 적용 가능한 터미널 노드는 오렌지색 배경으로
    (transition.expandedTermNodes ++ transition.pendedTermNodes) foreach { nodeOf(_).setBackgroundColor(ColorConstants.orange) }
}

class LiftTransitionVisualize(parent: Composite, style: Int, val grammar: Grammar, val nodeIdCache: NodeIdCache, transition: NewParser[ParseForest]#LiftTransition)
        extends GraphTransitionControl(parent, style, transition.baseGraph, transition.nextGraph) {
    // (lift된 results는 초록색 실선으로, progresses는 옅은 초록색 점선으로)
    // addResults(transition.nextGraph.results, true, false, { conn => conn.setLineColor(ColorConstants.green) })
    // addResults(transition.nextGraph.progresses, false, true, { conn => conn.setLineColor(ColorConstants.lightGreen); conn.setLineStyle(SWT.LINE_DASH) })

    // 리프트 시작 노드는 오렌지색 배경으로
    (transition.startingNodes) foreach { p => nodeOf(p._1).setBackgroundColor(ColorConstants.orange) }

    // derivation tip node는 노란 배경으로
    transition.nextDerivables foreach { nodeOf(_).setBackgroundColor(ColorConstants.yellow) }
}

class TrimmingTransitionVisualize(parent: Composite, style: Int, val grammar: Grammar, val nodeIdCache: NodeIdCache, transition: NewParser[ParseForest]#TrimmingTransition)
        extends GraphTransitionControl(parent, style, transition.baseGraph, transition.nextGraph) {
    nodeOf(transition.startNode).setBackgroundColor(ColorConstants.yellow)
    transition.endNodes foreach { nodeOf(_).setBackgroundColor(ColorConstants.orange) }
}

class RevertTransitionVisualize(parent: Composite, style: Int, val grammar: Grammar, val nodeIdCache: NodeIdCache, transition: NewParser[ParseForest]#RevertTransition)
    extends GraphTransitionControl(parent, style, transition.baseGraph, transition.nextGraph)