package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

object OpGenerator {

  private def buildLeafMap(
    leafGenerator: QLeafGenerator,
    matches: Seq[QueryMatch]
  ): Map[QLeaf, IntMap[Int]] = {
    val operatorMap = scala.collection.mutable.Map[QLeaf, List[(Int, Int)]]()
    matches.zipWithIndex.foreach {
      case (queryMatch, index) =>
        val tokens = queryMatch.tokens
        val leaves = leafGenerator.generateLeaves(tokens)
        leaves.foreach { qLeaf =>
          val currentList = operatorMap.getOrElse(qLeaf, List[(Int, Int)]())
          operatorMap.put(qLeaf, (index, if (queryMatch.didMatch) 0 else 1) :: currentList)
        }
    }
    operatorMap.map { case (k, v) => k -> IntMap(v: _*) }.toMap
  }

  /** @param matches to generate the operators for
    * @param leafGenerator that determines what QLeaf op to suggest
    * @return map of SetRepeatedToken to the edit map of that op
    */
  def getRepeatedOpMatch(
    matches: QueryMatches,
    leafGenerator: QLeafGenerator
  ): Map[SetRepeatedToken, IntMap[Int]] = {
    val operatorMap = scala.collection.mutable.Map[SetRepeatedToken, List[(Int, Int)]]()
    matches.matches.zipWithIndex.foreach {
      case (queryMatch, matchIndex) =>
        val tokens = queryMatch.tokens
        tokens.zipWithIndex.foreach {
          case (token, tokenIndex) =>
            leafGenerator.generateLeaves(token).foreach { qLeaf =>
              val op = SetRepeatedToken(matches.queryToken.slot, tokenIndex + 1, qLeaf)
              val currentList = operatorMap.getOrElse(op, List[(Int, Int)]())
              operatorMap.put(op, (matchIndex, if (queryMatch.didMatch) 0 else 1) :: currentList)
            }
        }
    }
    operatorMap.map { case (k, v) => k -> IntMap(v: _*) }.toMap
  }

  /** @param matches to generate the map for
    * @param leafGenerator that determines which ops to build
    * @return map of SetToken ops to the edit map of that op
    */
  def getSetTokenOps(
    matches: QueryMatches,
    leafGenerator: QLeafGenerator
  ): Map[SetToken, IntMap[Int]] = {
    OpGenerator.buildLeafMap(leafGenerator, matches.matches).map {
      case (k, v) => SetToken(matches.queryToken.slot, k) -> v
    }
  }

  /** @param matches to generate the map for
    * @param leafGenerator that determines which ops to build
    * @return map of AddToken ops to the edit map of that op
    */
  def getAddTokenOps(
    matches: QueryMatches,
    leafGenerator: QLeafGenerator
  ): Map[AddToken, IntMap[Int]] = {
    require(matches.queryToken.slot.isInstanceOf[QueryToken])
    // AddToken ops implicitly match everything that is currently matched, add that back in
    val allreadyMatches = IntMap(matches.matches.zipWithIndex.flatMap {
      case (qMatch, index) => if (qMatch.didMatch) Some((index, 0)) else None
    }: _*)
    OpGenerator.buildLeafMap(leafGenerator, matches.matches).map {
      case (k, v) => AddToken(matches.queryToken.slot.token, k) ->
        v.unionWith(allreadyMatches, (_, v1, v2) => v1 + v2)
    }
  }
}

/** Abstract class for classes that 'generate' possibles operation that could be applied to
  * to a query and calculates what sentences that operation would a starting query to match
  */
abstract class OpGenerator {
  def generate(matches: QueryMatches): Map[QueryOp, IntMap[Int]]
}

