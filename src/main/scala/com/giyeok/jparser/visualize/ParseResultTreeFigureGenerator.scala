package com.giyeok.jparser.visualize

import com.giyeok.jparser.ParseResultTree.Node
import com.giyeok.jparser.ParseResultTree.TreePrint
import com.giyeok.jparser.Symbols._
import com.giyeok.jparser.visualize.FigureGenerator.Appearance
import com.giyeok.jparser.Symbols

object ParseResultTreeFigureGenerator {
    case class RenderingConfiguration(renderJoin: Boolean, renderWS: Boolean, renderLookaheadExcept: Boolean)
    val cleanestConfiguration = RenderingConfiguration(false, false, false)
}

class ParseResultTreeFigureGenerator[Fig](g: FigureGenerator.Generator[Fig], ap: FigureGenerator.Appearances[Fig]) {
    import com.giyeok.jparser.ParseResultTree._
    import com.giyeok.jparser.visualize.FigureGenerator.Spacing

    val symbolFigureGenerator = new SymbolFigureGenerator(g, ap)

    def parseNodeHFig(n: Node): Fig =
        parseNodeHFig(n: Node, ParseResultTreeFigureGenerator.cleanestConfiguration)

    def parseNodeHFig(n: Node, renderConf: ParseResultTreeFigureGenerator.RenderingConfiguration): Fig = {
        parseNodeFig(ap.hSymbolBorder, g.verticalFig _, g.horizontalFig _, renderConf)(n)
    }

    def parseNodeVFig(n: Node): Fig =
        parseNodeVFig(n: Node, ParseResultTreeFigureGenerator.cleanestConfiguration)

    def parseNodeVFig(n: Node, renderConf: ParseResultTreeFigureGenerator.RenderingConfiguration): Fig = {
        parseNodeFig(ap.vSymbolBorder, g.horizontalFig _, g.verticalFig _, renderConf)(n)
    }

    private def parseNodeFig(symbolBorder: FigureGenerator.Appearance[Fig], vfig: (Spacing.Value, Seq[Fig]) => Fig, hfig: (Spacing.Value, Seq[Fig]) => Fig, renderConf: ParseResultTreeFigureGenerator.RenderingConfiguration)(n: Node): Fig = {
        def parseNodeFig(n: Node): Fig = n match {
            case EmptyNode =>
                g.textFig("", ap.default)
            case TerminalNode(input) =>
                g.textFig(input.toShortString, ap.input)
            case BindedNode(sym, body) =>
                vfig(Spacing.Small, Seq(
                    symbolBorder.applyToFigure(parseNodeFig(body)),
                    symbolFigureGenerator.symbolFig(sym)))
            case JoinNode(body, join) =>
                var content = Seq(symbolBorder.applyToFigure(parseNodeFig(body)))
                if (renderConf.renderJoin) {
                    content :+= ap.joinHighlightBorder.applyToFigure(hfig(Spacing.Small, Seq(g.textFig("&", ap.default), parseNodeFig(join))))
                }
                vfig(Spacing.Small, content)
            case s: SequenceNode =>
                if (s.children.isEmpty) {
                    hfig(Spacing.Medium, Seq(
                        g.textFig("", ap.default)))
                } else {
                    def isLookaheadNode(node: Node): Boolean = node match {
                        case BindedNode(_: Symbols.LookaheadExcept, _) => true
                        case BindedNode(_: Symbols.LookaheadIs, _) => true
                        case _ => false
                    }
                    val seq: Seq[Fig] = if (renderConf.renderWS) {
                        val (childrenWS, idx0) = (s.childrenWS, s.childrenIdx)
                        val idx = if (idx0.last == childrenWS.size - 1) idx0 else (idx0 :+ childrenWS.size)
                        (idx.foldLeft(0, Seq[Fig]()) { (m, idx) =>
                            val (lastIdx, seq) = m
                            if (renderConf.renderLookaheadExcept) {
                                val wsFigs = (lastIdx until idx) map { wsIdx => ap.wsBorder.applyToFigure(parseNodeFig(childrenWS(wsIdx))) }
                                val symFig = symbolBorder.applyToFigure(parseNodeFig(childrenWS(idx)))
                                val newSeq = (seq ++ wsFigs) :+ symFig
                                (idx + 1, newSeq)
                            } else {
                                val wsFigs = (lastIdx until idx) filterNot { idx => isLookaheadNode(childrenWS(idx)) } map { wsIdx => ap.wsBorder.applyToFigure(parseNodeFig(childrenWS(wsIdx))) }
                                val symFig = if (isLookaheadNode(childrenWS(idx))) None else Some(symbolBorder.applyToFigure(parseNodeFig(childrenWS(idx))))
                                val newSeq = (seq ++ wsFigs) ++ symFig
                                (idx + 1, newSeq)
                            }
                        })._2
                    } else {
                        if (renderConf.renderLookaheadExcept) {
                            s.children map { b => symbolBorder.applyToFigure(parseNodeFig(b)) }
                        } else {
                            s.children filterNot { isLookaheadNode _ } map { b => symbolBorder.applyToFigure(parseNodeFig(b)) }
                        }
                    }
                    hfig(Spacing.Medium, seq)
                }
        }
        parseNodeFig(n)
    }
}
