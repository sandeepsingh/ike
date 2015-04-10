package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.Hits
import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData

import scala.collection.JavaConverters._

class TestMatchesSampler extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def hitToAllCaptures(hits: Hits): Seq[Seq[String]] = {
    hits.asScala.map(hit => {
      val kwic = hits.getKwic(hit)
      hits.getCapturedGroups(hit).map(span => {
        if (span == null) {
          null
        } else {
          val captureKwic = span.start - hit.start
          kwic.getMatch("word").subList(
            captureKwic,
            captureKwic + span.end - span.start
          ).asScala.mkString(" ")
        }
      }).toSeq
    }).toSeq
  }

  def buildTable(positive: Seq[String], negative: Seq[String]): Table = {
    Table("testTable", Seq("testCol"),
      positive.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))),
      negative.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))))
  }

  "MatchesSampler" should "test correctly" in {
    val startingQuery = QueryLanguage.parse("(?<col1> {I, hate, it}) . " +
      "(?<col2> {great, mango, bananas}) .").get
    val table = Table(
      "test",
      Seq("col1", "col2"),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("I"))), TableValue(Seq(QWord("mango"))))),
        TableRow(Seq(TableValue(Seq(QWord("hate"))), TableValue(Seq(QWord("those")))))
      ),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("it"))), TableValue(Seq(QWord("great"))))),
        TableRow(Seq(TableValue(Seq(QWord("I"))), TableValue(Seq(QWord("bananas")))))
      )
    )

    val expectedResults = Seq(
      Seq("I", "mango"),
      Seq("It", "great")
    )
    assertResult(expectedResults)(hitToAllCaptures(MatchesSampler().getLabelledSample(
      startingQuery, searcher, table
    )))
  }
}