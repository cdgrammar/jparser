package com.giyeok.jparser

import com.giyeok.jparser.utils.UnicodeUtil

object Inputs {
    type Location = Int

    sealed trait Input
    sealed trait ConcreteInput extends Input
    case class Character(char: Char) extends ConcreteInput
    case class Virtual(name: String) extends ConcreteInput
    case class AbstractInput(termGroup: TermGroupDesc) extends Input

    sealed trait TermGroupDesc {
        def toShortString: String
        def isEmpty: Boolean

        def contains(input: Input): Boolean
    }
    sealed trait CharacterTermGroupDesc extends TermGroupDesc {
        def +(other: CharacterTermGroupDesc): CharacterTermGroupDesc
        def -(other: CharacterTermGroupDesc): CharacterTermGroupDesc
        def intersect(other: CharacterTermGroupDesc): CharacterTermGroupDesc

        def contains(input: Input): Boolean = input match {
            case Character(char) => contains(char)
            case Virtual(_) => false
            case _ => ???
        }
        def contains(char: Char): Boolean
    }
    sealed trait VirtualTermGroupDesc extends TermGroupDesc {
        def -(other: VirtualTermGroupDesc): VirtualTermGroupDesc
        def intersect(other: VirtualTermGroupDesc): VirtualTermGroupDesc
    }
    case class AllCharsExcluding(excluding: CharsGroup) extends CharacterTermGroupDesc {
        def +(other: CharacterTermGroupDesc): CharacterTermGroupDesc = other match {
            case AllCharsExcluding(otherExcluding) =>
                AllCharsExcluding((excluding intersect otherExcluding).asInstanceOf[CharsGroup])
            case other: CharsGroup =>
                AllCharsExcluding((excluding - other).asInstanceOf[CharsGroup])
        }
        def -(other: CharacterTermGroupDesc): CharacterTermGroupDesc = other match {
            case other: AllCharsExcluding =>
                other - excluding
            case other: CharsGroup =>
                AllCharsExcluding((excluding + other).asInstanceOf[CharsGroup])
        }
        def intersect(other: CharacterTermGroupDesc): CharacterTermGroupDesc = other match {
            case other: AllCharsExcluding =>
                AllCharsExcluding((excluding + other).asInstanceOf[CharsGroup])
            case other: CharsGroup =>
                other - excluding
        }

        def contains(char: Char) = !(excluding contains char)

        def toShortString: String = "AllCharsExcluding(" + (excluding.toShortString) + ")"
        def isEmpty = false
    }

    case class CharsGroup(unicodeCategories: Set[Int], excludingChars: Set[Char], val chars: Set[Char]) extends CharacterTermGroupDesc {
        assert((excludingChars intersect chars).isEmpty)
        assert(excludingChars forall { c => (unicodeCategories contains c.getType) })
        assert(chars forall { c => !(unicodeCategories contains c.getType) })

        def +(other: CharacterTermGroupDesc): CharacterTermGroupDesc = other match {
            case other: AllCharsExcluding =>
                other + this
            case other: CharsGroup =>
                val baseUnicodes = unicodeCategories ++ other.unicodeCategories
                val excludings = (excludingChars filterNot { other contains _ }) ++ (other.excludingChars filterNot { this contains _ })
                val chars = this.chars ++ other.chars
                CharsGroup(baseUnicodes, excludings -- chars, chars filterNot { baseUnicodes contains _.getType })
        }
        def -(other: CharacterTermGroupDesc): CharacterTermGroupDesc = other match {
            case AllCharsExcluding(excluding) =>
                this intersect excluding
            case other: CharsGroup =>
                val baseUnicodes = unicodeCategories -- other.unicodeCategories
                val excludings = (excludingChars ++ other.chars) filter { baseUnicodes contains _.getType }
                val chars = (other.excludingChars filter { this contains _ }) ++ (this.chars filterNot { other contains _ })
                CharsGroup(baseUnicodes, excludings, chars)
        }
        def intersect(other: CharacterTermGroupDesc): CharacterTermGroupDesc = other match {
            case other: AllCharsExcluding =>
                other intersect this
            case other: CharsGroup =>
                val baseUnicodes = unicodeCategories intersect other.unicodeCategories
                val excludings = (excludingChars ++ other.excludingChars) filter { baseUnicodes contains _.getType }
                val chars = (this.chars filter { other contains _ }) ++ (other.chars filter { this contains _ })
                CharsGroup(baseUnicodes, excludings, chars)
        }

        def contains(char: Char) = ((unicodeCategories contains char.getType) && !(excludingChars contains char)) || (chars contains char)

        def toShortString: String = {
            var string = "CharsGroup("
            if (!unicodeCategories.isEmpty) {
                string += "{" + (unicodeCategories.toSeq.sorted map { UnicodeUtil.categoryCodeToName _ } mkString ",") + "}"
                if (!excludingChars.isEmpty) {
                    string += "-{" + (excludingChars.toSeq.sorted map { UnicodeUtil.toReadable _ } mkString "") + "}"
                }
                if (!chars.isEmpty) {
                    string += "+{" + (chars.toSeq.sorted map { UnicodeUtil.toReadable _ } mkString "") + "}"
                }
            } else {
                string += (chars.toSeq.sorted map { UnicodeUtil.toReadable _ } mkString "")
            }
            string += ")"
            string
        }
        def isEmpty = unicodeCategories.isEmpty && chars.isEmpty
    }

    case class VirtualsGroup(virtualNames: Set[String]) extends VirtualTermGroupDesc {
        def -(other: VirtualTermGroupDesc): VirtualsGroup = other match {
            case VirtualsGroup(otherVirtualNames) => VirtualsGroup(virtualNames -- otherVirtualNames)
        }
        def intersect(other: VirtualTermGroupDesc): VirtualsGroup = other match {
            case VirtualsGroup(otherVirtualNames) => VirtualsGroup(virtualNames intersect otherVirtualNames)
        }

        def toShortString: String = virtualNames.toSeq.sorted mkString ","
        def isEmpty = virtualNames.isEmpty

        def contains(input: Input) = input match {
            case Character(_) => false
            case Virtual(name) => virtualNames contains name
            case _ => ???
        }
    }

    object TermGroupDesc {
        import Symbols.Terminals._

        def descOf(term: CharacterTerminal): CharacterTermGroupDesc = term match {
            case AnyChar => new AllCharsExcluding(CharsGroup(Set(), Set(), Set()))
            case ExactChar(char) => CharsGroup(Set(), Set(), Set(char))
            case Chars(chars) => CharsGroup(Set(), Set(), chars)
            case Unicode(categories) => CharsGroup(categories, Set(), Set())
        }
        def descOf(term: VirtualTerminal): VirtualTermGroupDesc = term match {
            case ExactVirtual(name) => VirtualsGroup(Set(name))
            case Virtuals(names) => VirtualsGroup(names)
        }
    }

    type Source = Iterable[Input]
    type ConcreteSource = Iterable[ConcreteInput]

    implicit class InputToShortString(input: Input) {
        def toShortString: String = input match {
            case Character(char) =>
                char match {
                    case '\n' => "\\n"
                    case '\t' => "\\t"
                    case '\\' => "\\\\"
                    case _ => s"$char"
                }
            case Virtual(name) => s"<$name>"
            case AbstractInput(chars) => s"{${chars.toShortString}}"
        }

        def toCleanString: String = input match {
            case Character(char) =>
                char match {
                    case '\n' => "\\n"
                    case '\r' => "\\r"
                    case '\t' => "\\t"
                    case c => c.toString
                }
            case Virtual(name) => s"{$name}"
            case AbstractInput(chars) => s"{${chars.toShortString}}"
        }
    }
    implicit class SourceToCleanString(source: Source) {
        def toCleanString: String = (source map { _.toCleanString }).mkString
    }

    def fromString(source: String): Seq[ConcreteInput] =
        source.toCharArray map Character
}
