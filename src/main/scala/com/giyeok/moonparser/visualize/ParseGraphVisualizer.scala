package com.giyeok.moonparser.visualize

import scala.Left
import scala.Right

import org.eclipse.draw2d.ColorConstants
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StackLayout
import org.eclipse.swt.events.KeyListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Shell
import org.eclipse.zest.core.widgets.Graph
import org.eclipse.zest.core.widgets.GraphConnection
import org.eclipse.zest.core.widgets.GraphNode
import org.eclipse.zest.core.widgets.ZestStyles
import org.eclipse.zest.layouts.LayoutStyles
import org.eclipse.zest.layouts.algorithms.TreeLayoutAlgorithm

import com.giyeok.moonparser.Grammar
import com.giyeok.moonparser.Inputs.Input
import com.giyeok.moonparser.Inputs.InputToShortString
import com.giyeok.moonparser.ParseTree
import com.giyeok.moonparser.ParseTree.TreePrintableParseNode
import com.giyeok.moonparser.Parser

class ParsingContextGraph(parent: Composite, resources: ParseGraphVisualizer.Resources, private val context: Parser#ParsingContext, private val src: Option[Input]) extends Composite(parent, SWT.NONE) {
    this.setLayout(new FillLayout)

    private val (nodes, edges) = (context.graph.nodes, context.graph.edges)
    val graph = new Graph(this, SWT.NONE)
    private val proceed1: Set[(Parser#SymbolProgress, Parser#SymbolProgress)] = src match {
        case Some(s) => (context proceedTerminal1 s) map { p => (p._1, p._2) }
        case None => Set()
    }
    private val proceeded = proceed1 map { _._2 }
    private var vnodes: Map[Parser#Node, GraphNode] = ((nodes ++ context.resultCandidates ++ proceeded) map { n =>
        val graphNode = new GraphNode(graph, SWT.NONE, n.toShortString)
        graphNode.setFont(resources.default12Font)
        n match {
            case term: Parser#SymbolProgressTerminal if term.parsed.isEmpty =>
                graphNode.setBackgroundColor(ColorConstants.lightGreen)
            case _ =>
        }
        val tooltipText = n match {
            case rep: Parser#RepeatProgress if !rep.children.isEmpty =>
                val list = ParseTree.HorizontalTreeStringSeqUtil.merge(rep.children map { _.toHorizontalHierarchyStringSeq })
                Some(list._2 mkString "\n")
            case seq: Parser#SequenceProgress if !seq.childrenWS.isEmpty =>
                val list = ParseTree.HorizontalTreeStringSeqUtil.merge(seq.childrenWS map { _.toHorizontalHierarchyStringSeq })
                Some(list._2 mkString "\n")
            case n if n.canFinish =>
                val text = n.parsed.get.toHorizontalHierarchyString
                Some(text)
            case _ => None
        }
        tooltipText match {
            case Some(text) =>
                val f = new org.eclipse.draw2d.Label()
                f.setFont(resources.fixedWidth12Font)
                f.setText(text)
                graphNode.setTooltip(f)
                graph.addSelectionListener(new SelectionAdapter() {
                    override def widgetSelected(e: SelectionEvent): Unit = {
                        if (e.item == graphNode) {
                            println(e.item)
                            println(text)
                        }
                    }
                })
                Some(text)
            case None =>
        }
        if (proceeded contains n) {
            graphNode.setFont(resources.italic14Font)
            graphNode.setBackgroundColor(ColorConstants.yellow)
        }
        (n, graphNode)
    }).toMap
    context.resultCandidates foreach { p =>
        val node = vnodes(p)
        node.setFont(resources.bold14Font)
        node.setBackgroundColor(ColorConstants.orange)
    }
    private var vedges: Set[GraphConnection] = edges flatMap {
        case e: Parser#SimpleEdge =>
            Set(new GraphConnection(graph, ZestStyles.CONNECTIONS_DIRECTED, vnodes(e.from), vnodes(e.to)))
        case e: Parser#AssassinEdge =>
            Set(new GraphConnection(graph, ZestStyles.CONNECTIONS_DIRECTED, vnodes(e.from), vnodes(e.to)))
        case _ => None
    }
    private val nedges = proceed1 map { p =>
        val connection = new GraphConnection(graph, ZestStyles.CONNECTIONS_DIRECTED, vnodes(p._1), vnodes(p._2))
        connection.setLineColor(ColorConstants.blue)
        connection
    }
    graph.setLayoutAlgorithm(new TreeLayoutAlgorithm(LayoutStyles.NO_LAYOUT_NODE_RESIZING), true)
}

object ParseGraphVisualizer {
    trait Resources {
        val default12Font: Font
        val fixedWidth12Font: Font
        val italic14Font: Font
        val bold14Font: Font
    }

    def start(grammar: Grammar, input: Seq[Input], display: Display, shell: Shell): Unit = {
        val resources = new Resources {
            val default12Font = new Font(null, "Monaco", 12, SWT.NONE)
            val fixedWidth12Font = new Font(null, "Monaco", 12, SWT.NONE)
            val italic14Font = new Font(null, "Monaco", 14, SWT.ITALIC)
            val bold14Font = new Font(null, "Monaco", 14, SWT.BOLD)
        }

        val layout = new StackLayout

        shell.setText("Parsing Graph")
        shell.setLayout(layout)

        val parser = new Parser(grammar)
        val source = input

        val fin = source.scanLeft[Either[Parser#ParsingContext, Parser#ParsingError], Seq[Either[Parser#ParsingContext, Parser#ParsingError]]](Left[Parser#ParsingContext, Parser#ParsingError](parser.startingContext)) {
            (ctx, terminal) =>
                ctx match {
                    case Left(ctx) =>
                        //Try(ctx proceedTerminal terminal).getOrElse(Right(parser.ParsingErrors.UnexpectedInput(terminal)))
                        (ctx proceedTerminal terminal) match {
                            case Left(next) => Left(next)
                            case Right(error) => Right(error.asInstanceOf[Parser#ParsingError])
                        }
                    case error @ Right(_) => error
                }
        }
        assert(fin.length == (source.length + 1))
        val views: Seq[Control] = (fin zip ((source map { Some(_) }) :+ None)) map {
            case (Left(ctx), src) =>
                new ParsingContextGraph(shell, resources, ctx, src)
            case (Right(error), _) =>
                val label = new Label(shell, SWT.NONE)
                label.setAlignment(SWT.CENTER)
                label.setText(error.msg)
                label
        }

        var currentLocation = 0

        def updateLocation(newLocation: Int): Unit = {
            if (newLocation >= 0 && newLocation <= source.size) {
                currentLocation = newLocation
                val sourceStr = source map { _.toCleanString }
                shell.setText((sourceStr take newLocation).mkString + "*" + (sourceStr drop newLocation).mkString)
                layout.topControl = views(newLocation)
                shell.layout()
            }
        }
        updateLocation(currentLocation)

        val keyListener = new KeyListener() {
            def keyPressed(x: org.eclipse.swt.events.KeyEvent): Unit = {
                x.keyCode match {
                    case SWT.ARROW_LEFT => updateLocation(currentLocation - 1)
                    case SWT.ARROW_RIGHT => updateLocation(currentLocation + 1)
                    case code =>
                }
            }

            def keyReleased(x: org.eclipse.swt.events.KeyEvent): Unit = {}
        }
        shell.addKeyListener(keyListener)
        views foreach { v => v.addKeyListener(keyListener) }
        views collect { case v: ParsingContextGraph => v.graph.addKeyListener(keyListener) }

        shell.open()
    }

    def start(grammar: Grammar, input: Seq[Input]): Unit = {
        val display = new Display
        val shell = new Shell(display)

        start(grammar, input, display, shell)

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep()
            }
        }
        display.dispose()
    }
}