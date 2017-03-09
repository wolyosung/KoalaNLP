package kr.bydelta.koala.kmr

import java.util

import kr.bydelta.koala.data.{Morpheme, Sentence, Word}
import kr.bydelta.koala.traits.CanTag
import kr.bydelta.koala.{POS, fromKomoranTag}
import kr.co.shineware.nlp.komoran.core.analyzer.Komoran
import kr.co.shineware.util.common.model.{Pair => KPair}

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

/**
  * 코모란 형태소분석기.
  */
class Tagger extends CanTag[java.util.List[java.util.List[KPair[String, String]]]] {
  /**
    * 코모란 분석기 객체.
    */
  lazy val komoran = {
    Dictionary.extractResource()
    val komoran = new Komoran(Dictionary.getExtractedPath)

    if (Dictionary.userDict.exists())
      komoran.setUserDic(Dictionary.userDict.getAbsolutePath)
    komoran
  }

  override def tagSentenceRaw(text: String): util.List[util.List[KPair[String, String]]] =
    komoran.analyze(text)

  override def tagParagraph(text: String): Seq[Sentence] = {
    splitSentences(convert(tagSentenceRaw(text)).words)
  }

  override private[koala] def convert(result: util.List[util.List[KPair[String, String]]]): Sentence =
    Sentence(
      result.map {
        word =>
          val originalWord = word.map(_.getFirst).mkString
          Word(
            originalWord,
            word.map {
              pair =>
                Morpheme(
                  pair.getFirst,
                  pair.getSecond,
                  fromKomoranTag(pair.getSecond)
                )
            }
          )
      }
    )

  /**
    * 분석결과를 토대로 문장을 분리함.
    *
    * @param para 분리할 문단.
    * @param pos  현재 읽고있는 위치.
    * @param open 현재까지 열려있는 묶음기호 Stack.
    * @param acc  현재까지 분리된 문장들.
    * @return 문장단위로 분리된 결과
    */
  private def splitSentences(para: Seq[Word],
                             pos: Int = 0,
                             open: List[String] = List(),
                             acc: ArrayBuffer[Sentence] = ArrayBuffer()): Seq[Sentence] =
    if (para.isEmpty) acc
    else {
      import kr.bydelta.koala.Implicit._
      val rawEndmark = para.indexWhere(_.exists(POS.SF), pos)
      val rawParen = para.indexWhere({
        e =>
          e.exists(POS.SS) ||
            Tagger.openParenRegex.findFirstMatchIn(e.surface).isDefined ||
            Tagger.closeParenRegex.findFirstMatchIn(e.surface).isDefined ||
            Tagger.quoteRegex.findFirstMatchIn(e.surface).isDefined
      }, pos)

      val endmark = if (rawEndmark == -1) para.length else rawEndmark
      val paren = if (rawParen == -1) para.length else rawParen

      if (endmark == paren && paren == para.length) {
        acc += Sentence(para)
        acc
      } else if (open.isEmpty) {
        if (endmark < paren) {
          val (sent, next) = para.splitAt(endmark + 1)
          acc += Sentence(sent)
          splitSentences(next, 0, open, acc)
        } else {
          val parenStr = para(paren)
          val surface = parenStr.surface
          var nOpen = open
          if (Tagger.closeParenRegex.findFirstMatchIn(surface).isEmpty) {
            nOpen +:= surface
          }
          splitSentences(para, paren + 1, nOpen, acc)
        }
      } else {
        if (paren == para.length) {
          acc += Sentence(para)
          acc
        } else {
          val parenStr = para(paren)
          val surface = parenStr.surface
          var nOpen = open
          if (Tagger.openParenRegex.findFirstMatchIn(surface).isDefined) {
            nOpen +:= surface
          } else if (Tagger.closeParenRegex.findFirstMatchIn(surface).isDefined) {
            nOpen = nOpen.tail
          } else {
            val top = nOpen.head
            if (surface == top) nOpen = nOpen.tail
            else nOpen +:= surface
          }
          splitSentences(para, paren + 1, nOpen, acc)
        }
      }
    }
}

/**
  * 코모란 분석기의 Companion object.
  */
private[koala] object Tagger {
  private val quoteRegex = "(?U)[\'\"]{1}".r
  private val openParenRegex = "(?U)[\\(\\[\\{<〔〈《「『【‘“]{1}".r
  private val closeParenRegex = "(?U)[\\)\\]\\}>〕〉《」』】’”]{1}".r
}
