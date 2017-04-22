package kr.bydelta.koala.server

import colossus.core._
import colossus.protocols.http.server.Initializer
import kr.bydelta.koala.traits.{CanCompileDict, CanDepParse, CanTag}

/**
  * Initializer class for Service Providers
  *
  * @param worker    Worker Reference of Colossus.
  * @param getTagger A non-ary function that generates Tagger
  * @param getParser A non-ary funciton that generates Parser
  * @param dict      A dictionary singleton object.
  */
class ServiceInitializer(worker: InitContext)
                        (implicit val getTagger: () => CanTag[_],
                         implicit val getParser: () => CanDepParse,
                         implicit val dict: CanCompileDict) extends Initializer(worker) {
  /**
    * Generated Tagger (of this thread)
    */
  lazy val tagger = getTagger()
  /**
    * Generated Parser (of this thread)
    */
  lazy val parser = getParser()

  override def onConnect =
    context => new Service(context, tagger, parser, dict)
}
