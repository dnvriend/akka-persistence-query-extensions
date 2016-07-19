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

package akka.stream.integration
package xml

import akka.stream.integration.PersonDomain.{Address, Person}
import akka.stream.scaladsl.Flow

import scala.collection.immutable._
import scala.xml.pull.{EvElemEnd, EvElemStart, EvText, XMLEvent}

object PersonParser {
  def apply() = Flow[XMLEvent].statefulMapConcat[Person] { () =>
    var person = Person()
    var address = Address()
    var inFirstName = false
    var inLastName = false
    var inAge = false
    var inStreet = false
    var inHouseNr = false
    var inZip = false
    var inCity = false
    val empty = Iterable.empty[Person]

    event => event match {
      case EvElemStart(_, "first-name", _, _) =>
        inFirstName = true; empty
      case EvElemStart(_, "last-name", _, _) =>
        inLastName = true; empty
      case EvElemStart(_, "age", _, _) =>
        inAge = true; empty
      case EvText(text) if inFirstName =>
        person = person.copy(firstName = text); empty
      case EvText(text) if inLastName =>
        person = person.copy(lastName = text); empty
      case EvText(text) if inAge =>
        person = person.copy(age = text.toInt); empty
      case EvElemEnd(_, "first-name") =>
        inFirstName = false; empty
      case EvElemEnd(_, "last-name") =>
        inLastName = false; empty
      case EvElemEnd(_, "age") =>
        inAge = false; empty
      case EvElemStart(_, "street", _, _) =>
        inStreet = true; empty
      case EvElemStart(_, "house-number", _, _) =>
        inHouseNr = true; empty
      case EvElemStart(_, "zip-code", _, _) =>
        inZip = true; empty
      case EvElemStart(_, "city", _, _) =>
        inCity = true; empty
      case EvElemEnd(_, "street") =>
        inStreet = false; empty
      case EvElemEnd(_, "house-number") =>
        inHouseNr = false; empty
      case EvElemEnd(_, "zip-code") =>
        inZip = false; empty
      case EvElemEnd(_, "city") =>
        inCity = false; empty
      case EvText(text) if inStreet =>
        address = address.copy(street = text); empty
      case EvText(text) if inHouseNr =>
        address = address.copy(houseNumber = text); empty
      case EvText(text) if inZip =>
        address = address.copy(zipCode = text); empty
      case EvText(text) if inCity =>
        address = address.copy(city = text); empty

      case EvElemEnd(_, "person") =>
        val iter = Iterable(person.copy(address = address))
        person = Person()
        address = Address()
        iter

      case _ => empty
    }
  }
}
