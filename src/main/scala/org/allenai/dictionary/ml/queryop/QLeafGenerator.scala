package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml.Token

object QLeafGenerator {

  /** @return True if word is a word that can be used in a string QExpr */
  def validWord(word: String): Boolean = {
    QueryLanguage.parser.wordRegex.findFirstIn(word).nonEmpty
  }

  /** @return True if pos is a POS tag that can be used in a string QExpr */
  def validPos(pos: String): Boolean = QExprParser.posTagSet contains pos

}

/** Given Tokens, builds QLeafs that would match that token
  *
  * @param pos whether to generate QPos
  * @param word whether to generate QWord
  * @param avoidSuggesting a specific QLeaf this should never suggest
  */
case class QLeafGenerator(pos: Boolean, word: Boolean,
    avoidSuggesting: Set[QLeaf] = Set()) {

  def generateLeaves(tokens: Seq[Token]): Iterable[QLeaf] = {
    if (tokens.isEmpty) {
      Seq()
    } else {
      val leaves = generateLeaves(tokens.head).toSet
      tokens.drop(1).foldLeft(leaves) {
        case (acc, next) => acc.intersect(generateLeaves(next).toSet)
      }
    }
  }

  def generateLeaves(token: Token): Iterable[QLeaf] = {
    val posOp = if (pos && QLeafGenerator.validPos(token.pos)) {
      Some(QPos(token.pos))
    } else {
      None
    }

    val wordOp = if (word && QLeafGenerator.validWord(token.word)) {
      Some(QWord(token.word))
    } else {
      None
    }

    val allOps = posOp ++ wordOp
    if (avoidSuggesting.isEmpty) {
      allOps
    } else {
      allOps.filter(!avoidSuggesting.contains(_))
    }
  }
}
