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

package akka.persistence.query.extension

import akka.stream.integration.TestSpec
import akka.stream.scaladsl.extension.{ Sink => S }
import akka.stream.scaladsl.{ Sink, Source }

class JournalTest extends TestSpec {
  final val NumMessages = 1000

  "flow via persistent actor" should s"Write $NumMessages messages to the journal using the EventWriter API (bulk load ETL)" in {
    Source.repeat("foo").take(NumMessages).via(Journal(_ => Set("foo"), journal)).runWith(Sink.ignore).futureValue
    journal.currentEventsByTag("foo", 0).runWith(S.count).futureValue shouldBe NumMessages
  }

  "flow via persistent actor" should s"Write $NumMessages messages to the journal using a persistent-actor" in {
    Source.repeat("foo").take(NumMessages).via(Journal.flow(_ => Set("foo"))).runWith(Sink.ignore).futureValue
    journal.currentEventsByTag("foo", 0).runWith(S.count).futureValue shouldBe NumMessages
  }
}
