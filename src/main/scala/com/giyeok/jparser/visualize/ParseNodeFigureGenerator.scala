package com.giyeok.jparser.visualize

import com.giyeok.jparser.ParseTree.ParseNode
import com.giyeok.jparser.Symbols._
import com.giyeok.jparser.ParseTree.TreePrint
import com.giyeok.jparser.visualize.FigureGenerator.Appearance

object ParseNodeFigureGenerator {
    case class RenderingConfiguration(renderJoin: Boolean, renderWS: Boolean, renderLookaheadExcept: Boolean)
    val cleanestConfiguration = RenderingConfiguration(false, false, false)
}

class ParseNodeFigureGenerator[Fig](g: FigureGenerator.Generator[Fig], ap: FigureGenerator.Appearances[Fig]) {
    import com.giyeok.jparser.ParseTree._
    import com.giyeok.jparser.visualize.FigureGenerator.Spacing

    val symbolFigureGenerator = new SymbolFigureGenerator(g, ap)

    def parseNodeHFig(n: ParseNode[Symbol]): Fig =
        parseNodeHFig(n: ParseNode[Symbol], ParseNodeFigureGenerator.cleanestConfiguration)

    def parseNodeHFig(n: ParseNode[Symbol], renderConf: ParseNodeFigureGenerator.RenderingConfiguration): Fig = {
        parseNodeFig(ap.hSymbolBorder, g.verticalFig _, g.horizontalFig _, renderConf)(n)
    }

    def parseNodeVFig(n: ParseNode[Symbol]): Fig =
        parseNodeVFig(n: ParseNode[Symbol], ParseNodeFigureGenerator.cleanestConfiguration)

    def parseNodeVFig(n: ParseNode[Symbol], renderConf: ParseNodeFigureGenerator.RenderingConfiguration): Fig = {
        parseNodeFig(ap.vSymbolBorder, g.horizontalFig _, g.verticalFig _, renderConf)(n)
    }

    private def parseNodeFig(symbolBorder: FigureGenerator.Appearance[Fig], vfig: (Spacing.Value, Seq[Fig]) => Fig, hfig: (Spacing.Value, Seq[Fig]) => Fig, renderConf: ParseNodeFigureGenerator.RenderingConfiguration)(n: ParseNode[Symbol]): Fig = {
        def parseNodeFig(n: ParseNode[Symbol]): Fig = n match {
            case ParsedEmpty(sym) =>
                vfig(Spacing.Small, Seq(
                    g.textFig("", ap.default),
                    symbolFigureGenerator.symbolFig(sym)))
            case ParsedTerminal(sym, child) =>
                vfig(Spacing.Small, Seq(
                    g.textFig(child.toShortString, ap.input),
                    symbolFigureGenerator.symbolFig(sym)))
            case ParsedSymbolJoin(sym, body, join) =>
                var content = Seq(symbolBorder.applyToFigure(parseNodeFig(body)))
                if (renderConf.renderJoin) {
                    content :+= ap.joinHighlightBorder.applyToFigure(hfig(Spacing.Small, Seq(g.textFig("&", ap.default), parseNodeFig(join))))
                }
                content :+= symbolFigureGenerator.symbolFig(sym)
                vfig(Spacing.Small, content)
            case ParsedSymbol(sym, body) =>
                vfig(Spacing.Small, Seq(
                    symbolBorder.applyToFigure(parseNodeFig(body)),
                    symbolFigureGenerator.symbolFig(sym)))
            case s @ ParsedSymbolsSeq(sym, _, _) =>
                if (s.children.isEmpty) {
                    vfig(Spacing.Small, Seq(
                        hfig(Spacing.Medium, Seq()),
                        symbolFigureGenerator.symbolFig(sym)))
                } else {
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
                                val wsFigs = (lastIdx until idx) filterNot { childrenWS(_).symbol.isInstanceOf[LookaheadExcept] } map { wsIdx => ap.wsBorder.applyToFigure(parseNodeFig(childrenWS(wsIdx))) }
                                val symFig = if (childrenWS(idx).symbol.isInstanceOf[LookaheadExcept]) None else Some(symbolBorder.applyToFigure(parseNodeFig(childrenWS(idx))))
                                val newSeq = (seq ++ wsFigs) ++ symFig
                                (idx + 1, newSeq)
                            }
                        })._2
                    } else {
                        if (renderConf.renderLookaheadExcept) {
                            s.children map { b => symbolBorder.applyToFigure(parseNodeFig(b)) }
                        } else {
                            s.children filterNot { _.symbol.isInstanceOf[LookaheadExcept] } map { b => symbolBorder.applyToFigure(parseNodeFig(b)) }
                        }
                    }
                    vfig(Spacing.Small, Seq(
                        hfig(Spacing.Medium, seq),
                        symbolFigureGenerator.symbolFig(sym)))
                }
        }
        parseNodeFig(n)
    }
}