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

// enable publishing to jcenter
homepage := Some(url("https://github.com/dnvriend/akka-persistence-query-extensions"))

pomIncludeRepository := (_ => false)

pomExtra := <scm>
  <url>https://github.com/dnvriend/akka-persistence-query-extensions</url>
  <connection>scm:git@github.com:dnvriend/akka-persistence-query-extensions.git</connection>
</scm>
  <developers>
    <developer>
      <id>dnvriend</id>
      <name>Dennis Vriend</name>
      <url>https://github.com/dnvriend</url>
    </developer>
  </developers>

publishMavenStyle := true

bintrayPackageLabels := Seq("akka-stream", "akka-persistence-query", "extensions")

bintrayPackageAttributes ~=
  (_ ++ Map(
    "website_url" -> Seq(bintry.Attr.String("https://github.com/dnvriend/akka-persistence-query-extensions")),
    "github_repo" -> Seq(bintry.Attr.String("https://github.com/dnvriend/akka-persistence-query-extensions.git")),
    "issue_tracker_url" -> Seq(bintry.Attr.String("https://github.com/dnvriend/akka-persistence-query-extensions/issues/"))
    )
  )