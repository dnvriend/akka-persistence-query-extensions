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
import com.typesafe.config.ConfigFactory

class ResumableQueryConfigTest extends TestSpec {

  val defaultConfig = ConfigFactory.parseString(
    """
      |resumable-query {
      |  snapshot-interval = "250"
      |  backpressure-buffer = "1"
      |  journal-plugin-id = ""
      |  snapshot-plugin-id = ""
      |}
    """.stripMargin
  ).getConfig("resumable-query")

  it should "parse config" in {
    ResumableQueryConfig(defaultConfig) shouldBe ResumableQueryConfig(Some(250), Some(1), None, None)
  }

  val journalAndSnapshotPluginId = ConfigFactory.parseString(
    """
      |resumable-query {
      |  snapshot-interval = "250"
      |  backpressure-buffer = "1"
      |  journal-plugin-id = "inmemory-journal"
      |  snapshot-plugin-id = "inmemory-snapshot-store"
      |}
    """.stripMargin
  ).getConfig("resumable-query")

  it should "parse journal and snapshot plugin id" in {
    ResumableQueryConfig(journalAndSnapshotPluginId) shouldBe ResumableQueryConfig(Some(250), Some(1), Some("inmemory-journal"), Some("inmemory-snapshot-store"))
  }

}
