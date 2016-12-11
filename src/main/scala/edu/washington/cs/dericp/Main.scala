package edu.washington.cs.dericp

import scala.io.StdIn
import ch.ethz.dal.tinyir.io.TipsterStream
import com.github.aztek.porterstemmer.PorterStemmer

object Main {

  def main(args: Array[String]): Unit = {
    println("Which relevance model would you like to use? LANGUAGE or TERM:")
    val model = StdIn.readLine().toLowerCase
    println("Using " + model + " model.")
    println()

    println("Build a new inverted index from scratch? TRUE/FALSE:")
    val newIndex = StdIn.readLine().toBoolean
    println("Building inverted index...")

    val invIdx = {
      if (newIndex) {
        InvertedIndex.createInvertedIndex("src/main/resources/documents")
      } else {
        InvertedIndex.readInvertedIndexFromFile("inverted-index")
      }
    }

    println("Building relevance model...")

    // get the document lengths
    def docs = new TipsterStream("src/main/resources/documents").stream.take(1000)
    val docLengths = docs.map(doc => (doc.name -> doc.tokens.length)).toMap

    // TODO: figure out the other case?
    val relevanceModel = model match {
      case "LANGUAGE" => new LanguageModel(invIdx, docLengths, .01)
      case "TERM" => new TermModel(invIdx, docLengths)
      case _ => throw new IllegalArgumentException("Invalid relevance model name. Please enter one of the options.")
    }

    var keepQuerying = true

    while (keepQuerying) {
      println()
      println("Please enter your query:")
      var query = StdIn.readLine().split("\\s+").map(term => PorterStemmer.stem(term.toLowerCase))
      println()

      println("Getting top documents...")
      println(relevanceModel.topNDocs(query, 100).mkString(","))

      println("Would you like to enter another query? TRUE/FALSE:")
      keepQuerying = StdIn.readLine().toBoolean
    }
  }
}

