package com.giyeok.jparser.visualize

import org.eclipse.swt.widgets.Composite
import com.giyeok.jparser.nparser.NGrammar
import org.eclipse.draw2d.Figure
import com.giyeok.jparser.ParseResultGraph
import org.eclipse.zest.core.widgets.CGraphNode
import org.eclipse.draw2d.LineBorder
import org.eclipse.draw2d.ColorConstants
import org.eclipse.swt.SWT
import org.eclipse.zest.core.viewers.GraphViewer
import org.eclipse.zest.core.widgets.Graph
import org.eclipse.zest.core.widgets.ZestStyles
import org.eclipse.zest.layouts.LayoutStyles
import org.eclipse.zest.core.widgets.GraphConnection
import com.giyeok.jparser.nparser.ParsingContext._
import com.giyeok.jparser.nparser.EligCondition._
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.events.KeyListener
import org.eclipse.swt.events.MouseListener
import org.eclipse.swt.widgets.Control
import com.giyeok.jparser.visualize.FigureGenerator.Spacing
import com.giyeok.jparser.nparser.ParseTreeConstructor
import com.giyeok.jparser.ParseResultGraphFunc
import org.eclipse.swt.widgets.Shell
import org.eclipse.draw2d.FigureCanvas
import com.giyeok.jparser.nparser.NaiveParser
import com.giyeok.jparser.nparser.Parser.WrappedContext
import com.giyeok.jparser.nparser.Parser.DeriveTipsWrappedContext

trait AbstractZestGraphWidget extends Control {
    val graphViewer: GraphViewer
    val graphCtrl: Graph
    val fig: NodeFigureGenerators[Figure]

    val nodesMap = scala.collection.mutable.Map[Node, CGraphNode]()
    val edgesMap = scala.collection.mutable.Map[Edge, Seq[GraphConnection]]()
    var visibleNodes = Set[Node]()
    var visibleEdges = Set[Edge]()

    def addNode(grammar: NGrammar, node: Node): Unit = {
        if (!(nodesMap contains node)) {
            val nodeFig = fig.nodeFig(grammar, node)
            nodeFig.setBorder(new LineBorder(ColorConstants.darkGray))
            nodeFig.setBackgroundColor(ColorConstants.buttonLightest)
            nodeFig.setOpaque(true)
            nodeFig.setSize(nodeFig.getPreferredSize())

            val gnode = new CGraphNode(graphCtrl, SWT.NONE, nodeFig)
            gnode.setData(node)

            nodesMap(node) = gnode
            visibleNodes += node
        }
    }

    def addEdge(edge: Edge): Unit = {
        if (!(edgesMap contains edge)) {
            edge match {
                case SimpleEdge(start, end) =>
                    assert(nodesMap contains start)
                    assert(nodesMap contains end)
                    val conn = new GraphConnection(graphCtrl, ZestStyles.CONNECTIONS_DIRECTED, nodesMap(start), nodesMap(end))
                    edgesMap(edge) = Seq(conn)
                case JoinEdge(start, end, join) =>
                    assert(nodesMap contains start)
                    assert(nodesMap contains end)
                    assert(nodesMap contains join)
                    val conn = new GraphConnection(graphCtrl, ZestStyles.CONNECTIONS_DIRECTED, nodesMap(start), nodesMap(end))
                    val connJoin = new GraphConnection(graphCtrl, ZestStyles.CONNECTIONS_DIRECTED, nodesMap(start), nodesMap(join))
                    conn.setText("main")
                    connJoin.setText("join")
                    edgesMap(edge) = Seq(conn, connJoin)
            }
            visibleEdges += edge
        }
    }

    def addContext(grammar: NGrammar, context: Context): Unit = {
        context.graph.nodes foreach { node =>
            addNode(grammar, node)
        }
        context.graph.edges foreach { edge =>
            addEdge(edge)
        }
    }

    def applyLayout(animation: Boolean): Unit = {
        if (animation) {
            graphViewer.setNodeStyle(ZestStyles.NONE)
        } else {
            graphViewer.setNodeStyle(ZestStyles.NODES_NO_LAYOUT_ANIMATION)
        }
        import org.eclipse.zest.layouts.algorithms._
        val layoutAlgorithm = new TreeLayoutAlgorithm(LayoutStyles.NO_LAYOUT_NODE_RESIZING | LayoutStyles.ENFORCE_BOUNDS)
        graphCtrl.setLayoutAlgorithm(layoutAlgorithm, true)
    }

    def setVisibleSubgraph(nodes: Set[Node], edges: Set[Edge]): Unit = {
        visibleNodes = nodes
        visibleEdges = edges

        nodesMap foreach { kv =>
            kv._2.setVisible(nodes contains kv._1)
        }
        edgesMap foreach { kv =>
            val visible = edges contains kv._1
            kv._2 foreach { _.setVisible(visible) }
        }
    }
}

trait Highlightable extends AbstractZestGraphWidget {
    val blinkInterval = 100
    val blinkCycle = 10
    val highlightedNodes = scala.collection.mutable.Map[Node, (Long, Int)]()

    var _uniqueId = 0L
    def uniqueId = { _uniqueId += 1; _uniqueId }

    def highlightNode(node: Node): Unit = {
        val display = getDisplay()
        if (!(highlightedNodes contains node) && (visibleNodes contains node)) {
            nodesMap(node).highlight()
            val highlightId = uniqueId
            highlightedNodes(node) = (highlightId, 0)
            display.timerExec(100, new Runnable() {
                def run(): Unit = {
                    highlightedNodes get node match {
                        case Some((`highlightId`, count)) =>
                            val shownNode = nodesMap(node)
                            if (count < blinkCycle) {
                                shownNode.setVisible(count % 2 == 0)
                                highlightedNodes(node) = (highlightId, count + 1)
                                display.timerExec(blinkInterval, this)
                            } else {
                                shownNode.setVisible(true)
                            }
                        case _ => // nothing to do
                    }
                }
            })
        }
    }
    def unhighlightAllNodes(): Unit = {
        highlightedNodes.keys foreach { node =>
            val shownNode = nodesMap(node)
            shownNode.unhighlight()
            shownNode.setVisible(visibleNodes contains node)
        }
        highlightedNodes.clear()
    }
}

trait Interactable extends AbstractZestGraphWidget {
    def nodesAt(ex: Int, ey: Int): Seq[Any] = {
        import scala.collection.JavaConversions._

        val (x, y) = (ex + graphCtrl.getHorizontalBar().getSelection(), ey + graphCtrl.getVerticalBar().getSelection())

        graphCtrl.getNodes.toSeq collect {
            case n: CGraphNode if n != null && n.getNodeFigure() != null && n.getNodeFigure().containsPoint(x, y) && n.getData() != null =>
                println(n)
                n.getData
        }
    }
}

class ZestGraphWidget(parent: Composite, style: Int, val fig: NodeFigureGenerators[Figure], grammar: NGrammar, context: Context)
        extends Composite(parent, style) with AbstractZestGraphWidget with Highlightable with Interactable {
    val graphViewer = new GraphViewer(this, style)
    val graphCtrl = graphViewer.getGraphControl()

    setLayout(new FillLayout())

    def initialize(): Unit = {
        addContext(grammar, context)
        applyLayout(false)
    }
    initialize()

    // finishable node highlight
    val tooltips = scala.collection.mutable.Map[Node, Seq[Figure]]()

    // finish, progress 조건 툴팁으로 표시
    context.finishes.nodeConditions foreach { kv =>
        val (node, conditions) = kv
        if (!(nodesMap contains node)) {
            addNode(grammar, node)
        }

        nodesMap(node).setBackgroundColor(ColorConstants.yellow)

        val conditionsFig = conditions.toSeq map { fig.conditionFig(grammar, _) }
        tooltips(node) = tooltips.getOrElse(node, Seq()) :+ fig.fig.verticalFig(Spacing.Medium, fig.fig.textFig("Finishes", fig.appear.default) +: conditionsFig)
    }
    context.progresses.nodeConditions foreach { kv =>
        val (node, conditions) = kv

        val conditionsFig = conditions.toSeq map { fig.conditionFig(grammar, _) }
        tooltips(node) = tooltips.getOrElse(node, Seq()) :+ fig.fig.verticalFig(Spacing.Medium, fig.fig.textFig("Progresses", fig.appear.default) +: conditionsFig)
    }
    tooltips foreach { kv =>
        val (node, figs) = kv
        val tooltipFig = fig.fig.verticalFig(Spacing.Big, figs)
        tooltipFig.setOpaque(true)
        tooltipFig.setBackgroundColor(ColorConstants.white)
        nodesMap(node).getFigure().setToolTip(tooltipFig)
    }

    // Interactions
    override def addKeyListener(keyListener: KeyListener): Unit = graphCtrl.addKeyListener(keyListener)
    override def addMouseListener(mouseListener: MouseListener): Unit = graphCtrl.addMouseListener(mouseListener)

    val inputMaxInterval = 2000
    case class InputAccumulator(textSoFar: String, lastTime: Long) {
        def accumulate(char: Char, thisTime: Long) =
            if (thisTime - lastTime < inputMaxInterval) InputAccumulator(textSoFar + char, thisTime) else InputAccumulator("" + char, thisTime)
        def textAsInt: Option[Int] = try { Some(textSoFar.toInt) } catch { case _: Throwable => None }
        def textAsInts: Seq[Int] = {
            var seq = List[Int]()
            var buffer = ""
            val text = textSoFar + " "
            for (i <- 0 until text.length()) {
                val c = text.charAt(i)
                if ('0' <= c && c <= '9') {
                    buffer += c
                } else {
                    if (buffer != "") {
                        seq = buffer.toInt +: seq
                    }
                    buffer = ""
                }
            }
            seq.reverse
        }
    }
    var inputAccumulator = InputAccumulator("", 0)

    addKeyListener(new KeyListener() {
        def keyPressed(e: org.eclipse.swt.events.KeyEvent): Unit = {
            e.keyCode match {
                case 'R' | 'r' =>
                    applyLayout(true)
                case c if ('0' <= c && c <= '9') || (c == ' ' || c == ',' || c == '.') =>
                    unhighlightAllNodes()
                    inputAccumulator = inputAccumulator.accumulate(c.toChar, System.currentTimeMillis())
                    val textAsInts = inputAccumulator.textAsInts
                    println(textAsInts)
                    textAsInts match {
                        case Seq(symbolId) =>
                            nodesMap.keySet filter { node => node.symbolId == symbolId } foreach { highlightNode(_) }
                        case Seq(symbolId, beginGen) =>
                            nodesMap.keySet filter { node => node.symbolId == symbolId && node.beginGen == beginGen } foreach { highlightNode(_) }
                        case Seq(symbolId, pointer, beginGen, endGen) =>
                            highlightNode(SequenceNode(symbolId, pointer, beginGen, endGen))
                        case _ => // nothing to do
                    }
                case SWT.ESC =>
                    inputAccumulator = InputAccumulator("", 0)
                case _ =>
            }
        }
        def keyReleased(e: org.eclipse.swt.events.KeyEvent): Unit = {}
    })
}

class ZestGraphTransitionWidget(parent: Composite, style: Int, fig: NodeFigureGenerators[Figure], grammar: NGrammar, base: Context, trans: Context)
        extends ZestGraphWidget(parent, style, fig, grammar, trans) {
    override def initialize(): Unit = {
        addContext(grammar, base)
        addContext(grammar, trans)
        (base.graph.nodes -- trans.graph.nodes) foreach { removedNode =>
            val shownNode = nodesMap(removedNode)
            shownNode.getFigure().setBorder(new LineBorder(ColorConstants.lightGray))
        }
        (base.graph.edges -- trans.graph.edges) foreach { removedEdge =>
            edgesMap(removedEdge) foreach { _.setLineStyle(SWT.LINE_DASH) }
        }
        (trans.graph.nodes -- base.graph.nodes) foreach { newNode =>
            val shownNode = nodesMap(newNode)
            shownNode.getFigure().setBorder(new LineBorder(3))
            val preferredSize = shownNode.getFigure().getPreferredSize()
            shownNode.setSize(preferredSize.width, preferredSize.height)
        }
        (trans.graph.edges -- base.graph.edges) foreach { newEdge =>
            edgesMap(newEdge) foreach { _.setLineWidth(3) }
        }
        applyLayout(false)
    }
}

class ZestParsingContextWidget(parent: Composite, style: Int, fig: NodeFigureGenerators[Figure], grammar: NGrammar, context: WrappedContext)
        extends ZestGraphWidget(parent, style, fig, grammar, context.ctx) {
    addKeyListener(new KeyListener() {
        def keyPressed(e: org.eclipse.swt.events.KeyEvent): Unit = {
            e.keyCode match {
                case 'F' | 'f' =>
                    context.conditionFate foreach { kv =>
                        println(s"${kv._1} -> ${kv._2}")
                    }
                case _ =>
            }
        }
        def keyReleased(e: org.eclipse.swt.events.KeyEvent): Unit = {}
    })

    addMouseListener(new MouseListener() {
        def mouseDown(e: org.eclipse.swt.events.MouseEvent): Unit = {}
        def mouseUp(e: org.eclipse.swt.events.MouseEvent): Unit = {}
        def mouseDoubleClick(e: org.eclipse.swt.events.MouseEvent): Unit = {
            nodesAt(e.x, e.y) foreach {
                case node: SequenceNode =>
                // TODO show preprocessed derivation graph
                case node: SymbolNode =>
                    val parseResultGraphOpt = new ParseTreeConstructor(ParseResultGraphFunc)(grammar)(context.inputs, context.history, context.conditionFate).reconstruct(node, context.gen)
                    parseResultGraphOpt match {
                        case Some(parseResultGraph) =>
                            new ParseResultGraphViewer(parseResultGraph, fig.fig, fig.appear, fig.symbol).start()
                        case None =>
                    }
                case data =>
                    println(data)
            }
        }
    })
}

class ZestDeriveTipParsingContextWidget(parent: Composite, style: Int, fig: NodeFigureGenerators[Figure], grammar: NGrammar, context: DeriveTipsWrappedContext)
        extends ZestParsingContextWidget(parent, style, fig, grammar, context) {
    override def initialize(): Unit = {
        super.initialize()
        context.deriveTips foreach { deriveTip =>
            if (!(nodesMap contains deriveTip)) {
                println(s"Error! $deriveTip @ ${context.gen}")
            } else {
                val shownDeriveTip = nodesMap(deriveTip)
                shownDeriveTip.getFigure().setBorder(new LineBorder(ColorConstants.orange, 3))
                val size = shownDeriveTip.getFigure().getPreferredSize()
                shownDeriveTip.setSize(size.width, size.height)
            }
        }
    }
}