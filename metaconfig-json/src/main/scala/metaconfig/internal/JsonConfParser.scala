package metaconfig.internal

import java.nio.CharBuffer
import metaconfig.Input
import metaconfig.Position
import ujson._
import upickle.core.{Visitor, ObjArrVisitor}

final class JsonConfParser[J](input: Input)
    extends Parser[J]
    with CharBasedParser[J] {
  var line = 0
  val chars = input.chars
  val wrapped: CharBuffer = CharBuffer.wrap(chars)

  override def die(i: Int, msg: String): Nothing = {
    val pos = Position.Range(input, i, i)
    val error = pos.pretty("error", msg)
    throw new ParseException(error, i, pos.startLine, pos.startColumn) {
      // super.getMessage appends useless "at index N" suffix.
      override def getMessage: String = error
    }
  }

  private def trailingComma(i: Int): Int = char(i) match {
    case ',' =>
      var curr = i + 1
      var done = false
      while (!atEof(curr) && !done) {
        char(curr) match {
          case '/' =>
            curr = comment(curr) + 1
          case ' ' | '\n' =>
            curr = curr + 1
          case _ =>
            done = true
        }
      }
      char(curr) match {
        case ']' | '}' =>
          curr
        case _ =>
          i
      }
    case _ => i
  }

  private def comment(i: Int): Int = char(i) match {
    case '/' =>
      char(i + 1) match {
        case '/' =>
          var curr = i + 2
          while (!atEof(curr) && char(curr) != '\n') {
            curr += 1
          }
          curr
        case _ =>
          i
      }
    case _ =>
      i
  }

  def column(i: Int): Int = i
  def newline(i: Int): Unit = { line += 1 }
  def reset(i: Int): Int = {
    if (atEof(i)) {
      i
    } else {
      val next = char(i) match {
        case '/' => comment(i)
        case ',' => trailingComma(i)
        case _ => i
      }
      if (next == i) i
      else reset(next)
    }
  }

  def checkpoint(
      state: Int,
      i: Int,
      stack: List[ObjArrVisitor[_, J]],
      path: List[Any]
  ): Unit =
    ()

  def char(i: Int): Char = {
    if (i >= chars.length)
      throw new StringIndexOutOfBoundsException(i)
    chars(i)
  }
  def sliceString(i: Int, j: Int): CharSequence = wrapped.subSequence(i, j)
  def atEof(i: Int): Boolean = i >= chars.length
  def close(): Unit = ()
  def dropBufferUntil(i: Int): Unit = ()

}

object JsonConfParser extends Transformer[Input] {
  def transform[T](j: Input, f: Visitor[_, T]): T =
    new JsonConfParser(j).parse(f)
}
