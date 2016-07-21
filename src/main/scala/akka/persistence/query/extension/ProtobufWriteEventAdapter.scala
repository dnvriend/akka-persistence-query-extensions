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

import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import akka.stream.integration.ProtobufWriter

import scala.reflect.ClassTag

trait ProtobufWriteEventAdapter extends WriteEventAdapter {
  /**
   * Converts the application domain model to protobuf
   * using a [[akka.stream.integration.ProtobufWriter]]
   * for storage and generic interchange format and instructs the
   * journal implementation to tag the message.
   */
  protected def serializeTagged[A: ClassTag: ProtobufWriter](msg: A, tags: Set[String]): Tagged = {
    val proto = implicitly[ProtobufWriter[A]].write(msg)
    Tagged(proto, tags + implicitly[ClassTag[A]].runtimeClass.getSimpleName)
  }
}
