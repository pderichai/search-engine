package edu.washington.cs.dericp

import java.io.PrintWriter

import ch.ethz.dal.tinyir.io.TipsterStream
import ch.ethz.dal.tinyir.processing.XMLDocument

import scala.io.Source

object InvertedIndex {
  // the minimum number of documents a term must appear in --- this helps prune typos
  val MIN_NUM_DOCS = 1

  def createInvertedIndex(filepath: String): Map[String, List[DocData]] = {
    // XMLDocument stream
    def docs = new TipsterStream(filepath).stream
    docs.flatMap(doc => doc.tokens.filter(!Utils.STOP_WORDS.contains(_)).map(token => (token, doc.name)))
    // [(token, docID), ...] - stop words
        .groupBy(_._1)
        // {token -> [(token, docID), ...], ...}
        .mapValues(_.map(tuple => tuple._2)
        // {token -> [docID, ...], ...}
        .groupBy(identity)
        // {token -> {docID -> [docID, docID, ...], ...}, ...}
        .map{ case(docID, docIDs) => (docID, docIDs.size) }
        // {token -> {docID -> docIDCount, ...}, ...}
        .map(tuple => new DocData(tuple._1, tuple._2)).toList.sorted)
        // {token -> [DocData1, DocData2, ...], ...}
        // get rip of stop words and rarely occurring term
        //.filter{ case(key, value) => value.length >= MIN_NUM_DOCS }
  }

  /**
    * Writes the inverted index to a file.
    *
    * @param invIdx the inverted index
    */
  def writeInvertedIndexToFile(invIdx: Map[String, List[DocData]]): Unit = {
    val pw = new PrintWriter("src/main/resources/inverted-index")

    // method writes a line to a file
    def writeLineToFile(termToDocDatas: (String, List[DocData])): Unit = {
      // write the term
      pw.print(termToDocDatas._1 + " ")
      // get the docIDs to counts
      val line = termToDocDatas._2.mkString(" ")
      pw.println(line)
    }

    invIdx.foreach(writeLineToFile(_))

    pw.close()
  }

  /**
    * Reads an inverted index from a file.
    *
    * @param filepath
    * @return
    */
  def readInvertedIndexFromFile(filepath: String) : Map[String, List[DocData]] = {
    val invIdx = new collection.mutable.HashMap[String, List[DocData]]
    val lines: Iterator[Array[String]] = Source.fromFile(filepath).getLines().map(l => l.split("\\s+"))

    // add a line from the file to the inverted index
    def addLineToIndex(line: Array[String]): Unit = {
      if (!line.isEmpty) {
        val term = line(0)
        // collection of tuples (docID, freq)
        val docIDsAndFreqs = line.drop(1).map(str => str.split(":"))
        val docDatas = docIDsAndFreqs.map{ arr => new DocData(arr(0), arr(1).toInt) }.toList
        invIdx += ((term, docDatas))
      }
    }

    lines.foreach(addLineToIndex(_))

    Map() ++ invIdx
  }


  /**
    *
    * @param query
    * @param invIdx
    * @return
    */
  def listIntersection(query: List[String], invIdx: Map[String, List[DocData]]) : List[String] = {
    // list of the docIDs that contain all terms in the query
    val output = scala.collection.mutable.ListBuffer.empty[String]
    // the inverted index but only with the terms in the query
    // TODO: make sure we actually need to use a vector here
    val termToDocIDsOnlyQueryTerms: Map[String, Vector[String]] =
      invIdx.filter{ case(term, _) => query.contains(term) }.mapValues(docDatas => docDatas.map(_.id()).to[Vector])
    // each terms mapped to the index we're currently looking at
    val termToCurrIdx = collection.mutable.Map() ++ termToDocIDsOnlyQueryTerms.mapValues(_ => 0)

    // see if we have reached the end of a term's doc list
    var keepSearching = true

    // increment the index for a term in termToCurrIdx, check if we have reached end of posting list
    def incrIdx(term: String): Unit = {
      val newIndex = termToCurrIdx(term) + 1
      if (newIndex < termToDocIDsOnlyQueryTerms(term).size) {
        termToCurrIdx(term) = newIndex
      } else {
        keepSearching = false
      }
    }

    // find min based on doc ID (_2) and return corresponding term
    def min(a: (String, String), b: (String, String)) : (String, String) = {
      if (a._2 < b._2) a
      else b
    }

    // find max based on doc ID (_2) and return corresponding term
    def max(a: (String, String), b: (String, String)) : (String, String) = {
      if (a._2 > b._2) a
      else b
    }

    // continuously search for intersections
    while (keepSearching) {
      // look at the current doc for each term
      val termToCurrDocID = termToCurrIdx.map{ case(term, index) => (term, termToDocIDsOnlyQueryTerms(term)(index)) }
      val termWithLowestIdx = termToCurrDocID.foldRight(("", "ZZZZZZZZZZZZZZZ"))(min)._1
      // TODO: need to handle case where term isn't in any documents
      val docIDWithLowestIdx = termToCurrDocID(termWithLowestIdx)
      val docIDWithHighestIdx = termToCurrDocID(termToCurrDocID.foldRight(("", ""))(max)._1)

      //println("lowest: " + termWithLowestIdx)
      //println("hightest: " + docIDWithHighestIdx)
      //println("==========================")

      // if lowest doc == highest doc, we found intersection, otherwise increment the lowest doc and keep searching
      if (docIDWithLowestIdx.equals(docIDWithHighestIdx)) {
        //println("hiiiii")
        //println(highestTermDoc)
        output += docIDWithHighestIdx
        termToCurrIdx.foreach{ case (term, _) => incrIdx(term) }
      } else {
        //println("hello")
        incrIdx(termWithLowestIdx)
      }
    }

    // returning the final list of doc IDs with all query terms
    output.toList
  }
}

