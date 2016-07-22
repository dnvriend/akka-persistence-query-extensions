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

import akka.stream.integration.PersonDomain.{ Address, Person }

import scala.xml.pull.{ EvElemEnd, EvElemStart, EvText }

object PersonParser {
  val flow = {
    import XMLParser._
    var person = Person()
    var address = Address()
    var inFirstName = false
    var inLastName = false
    var inAge = false
    var inStreet = false
    var inHouseNr = false
    var inZip = false
    var inCity = false

    XMLParser.flow {
      case EvElemStart(_, "first-name", _, _) =>
        inFirstName = true; emit()
      case EvElemStart(_, "last-name", _, _) =>
        inLastName = true; emit()
      case EvElemStart(_, "age", _, _) =>
        inAge = true; emit()

      case EvText(text) if inFirstName =>
        person = person.copy(firstName = text); emit()
      case EvText(text) if inLastName =>
        person = person.copy(lastName = text); emit()
      case EvText(text) if inAge =>
        person = person.copy(age = text.toInt); emit()

      case EvElemEnd(_, "first-name") =>
        inFirstName = false; emit()
      case EvElemEnd(_, "last-name") =>
        inLastName = false; emit()
      case EvElemEnd(_, "age") =>
        inAge = false; emit()
      case EvElemStart(_, "street", _, _) =>
        inStreet = true; emit()
      case EvElemStart(_, "house-number", _, _) =>
        inHouseNr = true; emit()
      case EvElemStart(_, "zip-code", _, _) =>
        inZip = true; emit()
      case EvElemStart(_, "city", _, _) =>
        inCity = true; emit()
      case EvElemEnd(_, "street") =>
        inStreet = false; emit()
      case EvElemEnd(_, "house-number") =>
        inHouseNr = false; emit()
      case EvElemEnd(_, "zip-code") =>
        inZip = false; emit()
      case EvElemEnd(_, "city") =>
        inCity = false; emit()

      case EvText(text) if inStreet =>
        address = address.copy(street = text); emit()
      case EvText(text) if inHouseNr =>
        address = address.copy(houseNumber = text); emit()
      case EvText(text) if inZip =>
        address = address.copy(zipCode = text); emit()
      case EvText(text) if inCity =>
        address = address.copy(city = text); emit()

      case EvElemEnd(_, "person") =>
        val iter = emit(person.copy(address = address))
        person = Person()
        address = Address()
        iter
    }
  }
}
