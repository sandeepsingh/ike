package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.lucene.SpanQueryCaptureGroup
import nl.inl.blacklab.search.sequences.SpanQuerySequence
import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.{ GeneralizeToDisj, TokenizedQuery }
import org.apache.lucene.search.spans.SpanQuery

import scala.collection.JavaConverters._

object GeneralizedQuerySampler {

  def buildGeneralizedSpanQuery(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    tables: Map[String, Table],
    sampleSize: Int
  ): SpanQuery = {
    def buildSpanQuery(qexpr: QExpr): SpanQuery = {
      searcher.createSpanQuery(
        BlackLabSemantics.blackLabQuery(QueryLanguage.interpolateTables(qexpr, tables).get)
      )
    }
    val generalizations = qexpr.generalizations.get
    // TODO do this by running the unlabelled query?
    val generalizingSpanQueries = generalizations.zip(qexpr.getNamedTokens).map {
      case (GeneralizeToDisj(qexprs), (name, original)) =>
        if (qexprs.isEmpty) {
          buildSpanQuery(QNamed(original, name))
        } else {
          val originalSq = buildSpanQuery(original)
          val extensions = qexprs.map(buildSpanQuery)
          new SpanQueryTrackingDisjunction(originalSq, extensions, name)
        }
      // TODO actually use wildcard generalizations
      case (_, (name, original)) => buildSpanQuery(QNamed(original, name))
    }
    var remaining = generalizingSpanQueries
    var chunked = List[SpanQuery]()
    qexpr.tokenSequences.map { ts =>
      val (chunk, rest) = remaining.splitAt(ts.size)
      remaining = rest
      val next = if (ts.isCaptureGroup) {
        new SpanQueryCaptureGroup(new SpanQuerySequence(chunk.asJava), ts.columnName.get)
      } else {
        new SpanQuerySequence(chunk.asJava)
      }
      chunked = next :: chunked
    }
    require(remaining.isEmpty)
    new SpanQuerySequence(chunked.reverse.asJava)
  }
}

/** Sampler that returns hits that could be matched by the generalizations of the input
  * query.
  *
  * @param maxEdits maximum edits a sentence can be from the query to be returned
  */
case class GeneralizedQuerySampler(maxEdits: Int)
    extends Sampler() {

  require(maxEdits >= 0)

  def buildFuzzySequenceQuery(tokenizedQuery: TokenizedQuery, searcher: Searcher,
    tables: Map[String, Table]): SpanQuery = {
    val gs = GeneralizedQuerySampler.buildGeneralizedSpanQuery(tokenizedQuery,
      searcher, tables, 200)
    if (tokenizedQuery.size < maxEdits) {
      new SpanQueryMinimumValidCaptures(gs, maxEdits, tokenizedQuery.getNames)
    } else {
      gs
    }
  }

  override def getSample(qexpr: TokenizedQuery, searcher: Searcher,
    targetTable: Table, tables: Map[String, Table]): Hits = {
    searcher.find(buildFuzzySequenceQuery(qexpr, searcher, tables))
  }

  override def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    startFromDoc: Int,
    startFromToken: Int
  ): Hits = {
    val rowQuery = Sampler.buildLabelledQuery(qexpr, targetTable)
    val sequenceQuery = buildFuzzySequenceQuery(qexpr, searcher, tables)
    val rowSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(rowQuery))
    searcher.find(new SpanQueryFilterByCaptureGroups(sequenceQuery, rowSpanQuery,
      targetTable.cols, startFromDoc, startFromToken))
  }
}
