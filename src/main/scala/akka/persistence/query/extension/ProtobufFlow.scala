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

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.stream.integration.ProtobufReader
import akka.stream.scaladsl.Flow
import com.google.protobuf.Message

object ProtobufFlow extends ProtobufFlow

trait ProtobufFlow {
  def fromProtoAs[A: ProtobufReader]: Flow[EventEnvelope, A, NotUsed] =
    Flow[EventEnvelope]
      .collect {
        case EventEnvelope(_, _, _, proto: Message) =>
          implicitly[ProtobufReader[A]].read(proto)
      }
}
