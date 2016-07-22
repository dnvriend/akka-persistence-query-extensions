/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.stream.integration.xml

import akka.stream.scaladsl.Flow

import scala.collection.immutable.Iterable
import scala.util.Try
import scala.xml.MetaData
import scala.xml.pull.XMLEvent

object XMLParser {
  def emit[A](elems: A*): Iterable[A] = Iterable[A](elems: _*)
  def getAttr(meta: MetaData, default: String = "")(key: String): String =
    meta.asAttrMap.getOrElse(key, default)
  def flow[A](parser: PartialFunction[XMLEvent, Iterable[A]]) = apply(parser)
  def apply[A](parser: PartialFunction[XMLEvent, Iterable[A]]) = {
    Flow[XMLEvent].statefulMapConcat[A](() => xml => Try(parser(xml)).getOrElse(emit()))
  }
}
