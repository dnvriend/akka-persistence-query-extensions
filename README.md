# akka-persistence-query-extensions

[![Build Status](https://travis-ci.org/dnvriend/akka-persistence-query-extensions.svg?branch=master)](https://travis-ci.org/dnvriend/akka-persistence-query-extensions)
[![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-query-extensions/images/download.svg)](https://bintray.com/dnvriend/maven/akka-persistence-query-extensions/_latestVersion)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/641e2c61ff8f40048e538b34a433de66)](https://www.codacy.com/app/dnvriend/akka-persistence-query-extensions?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=dnvriend/akka-persistence-query-extensions&amp;utm_campaign=Badge_Grade)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

akka-persistence-query-extensions contain components that are usable with akka-persistence-query

## Installation
Add the following to your `build.sbt`:

```scala
resolvers += Resolver.jcenterRepo

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-query-extensions" % "0.0.8"
```

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Components
The library contains the following components:

## akka.persistence.query.extension.ResumableQuery
A helper component for `akka.persistence.query.scaladsl.ReadJournal` that stores the latest offset in
the akka-persistence journal so that the query can be resumed after being restarted from a previous offset.

The component only supports eventsByTag or eventsByPersistenceId for they return a stream of `EventEnvelope`.

### Configuration:
The resumable query component can be configured globally. This setting is used for all queries:

```
resumable-query {
  snapshot-interval = "250"
  backpressure-buffer = "1"
  journal-plugin-id = ""
  snapshot-plugin-id = ""
}
```

It supports configuring:

- snapshot-interval,
- backpressure-buffer,
- the journal plugin to use, when left empty, the default journal plugin will be used,
- the snapshot plugin to use, when left empty, the default snapshot plugin will be used.

### Query name
Each query should have an unique name, like for example `MessageReceivedEventQuery`, which will be used to write the offset and recover from it. The query name can also be used to configure a query individually. To configure the `MessageReceivedEventQuery`, the following configuration should be created:

```
MessageReceivedEventQuery {
  snapshot-interval = "250"
  backpressure-buffer = "1"
  journal-plugin-id = ""
  snapshot-plugin-id = ""
}
```

### Example usage:

```scala
import akka.stream.scaladsl.{ FileIO, Flow, Source }
import akka.persistence.query.extension.ResumableQuery
import akka.stream.integration.io._
import akka.stream.integration.xml.{ Validation, ValidationResult }

// for example, creating a never completing flow that resumes after restarting
// in this case I used it to send messages to a shard region
ResumableQuery("MessageReceivedEventQuery", offset ⇒ journal.eventsByTag(classOf[MessageReceivedEvent].getSimpleName, offset + 1))
  .join(fromProtoAs[MessageReceivedEvent].mapAsync(1) { msg =>
    messageReceivedRegion ? MessageReceived(msg.id, msg.foo, msg.bar)
  }).run()

// processing files and generating MD5 hashes and storing them in the journal using the
// akka.persistence.query.extension.Journal component
ResumableQuery("GenerateMD5Query", offset => journal.eventsByTag(classOf[GenerateMD5Command].getSimpleName, offset + 1))
  .join(fromProtoAs[GenerateMD5Command]
    .via(generateMD5Flow(baseDir))
    .via(Journal.flow())).run()

// generating MD5 is relatively easy using FileIO and the
// akka.stream.integration.io.DigestCalculator
def generateMD5Flow(implicit log: LoggingAdapter) = Flow[GenerateMD5Command].flatMapConcat {
 case GenerateMD5Command(fileName) =>
  FileIO.fromFile(new File(fileName)).via(DigestCalculator.hexString(Algorithm.MD5))
    .map(hash => MD5Generated(fileName, hash))
    .recover {
      case cause: Throwable =>
        MD5GeneratedFailed(fileName, cause.getMessage)
    }
}

// non-completing flow that resumes to validate XSD
ResumableQuery("ValidateXSDQuery", offset => journal.eventsByTag(classOf[ValidateFile].getSimpleName, offset + 1))
  .join(fromProtoAs[ValidateFile]
    .via(validateXSDFlow(baseDir)).via(Journal.flow())).run()

// processing files and validating XSD
def validateXSDFlow(implicit log: LoggingAdapter) = Flow[ValidateFile].flatMapConcat {
 case ValidateFile(fileName) =>
  Source.single(fileName).flatMapConcat { fileName =>
    FileIO.fromFile(new File(fileName)).via(Validation("/xsd/file.xsd")).map {
      case ValidationResult(Failure(cause)) =>
        ValidationFailed(fileName, cause.getMessage)
      case _: ValidationResult => Validated(fileName)
    }
  }.recover {
    case cause: Throwable =>
      ValidationFailed(fileName, cause.getClass.getName)
  }
}

// non-completing flow that moves files
ResumableQuery("MoveToDirQuery", offset => journal.eventsByTag(classOf[MoveToDirCmd].getSimpleName, offset + 1))
  .join(fromProtoAs[MoveToDirCmd]
    .via(moveToDirFlow).via(Journal.flow())).run()

def moveToHistoryFlow(implicit log: LoggingAdapter) = Flow[MoveToDirCmd].flatMapConcat {
 case MoveToDirCmd(from, to) =>
  Source.single(FileUtilsCommand.MoveFile(from, to))
    .via(FileUtils.moveFile).map {
      case MoveFileResult(Failure(cause)) =>
        FileMoveFailed(fileName, cause.getMessage)
      case _: MoveFileResult => FileMoved(from, to)
    }
}


import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Flow
import com.google.protobuf.Message

// type classes to convert from/to protobuf
import com.google.protobuf.Message

trait ProtobufFormat[A] extends ProtobufReader[A] with ProtobufWriter[A]

trait ProtobufReader[A] {
  def read(proto: Message): A
}

trait ProtobufWriter[A] {
  def write(msg: A): Message
}

// converts from the data model to the domain model
object ProtobufAdapterFlow {
  def fromProtoAs[A: ProtobufReader]: Flow[EventEnvelope, A, NotUsed] =
    Flow[EventEnvelope]
      .collect {
        case EventEnvelope(_, _, _, proto: Message) =>
          implicitly[ProtobufReader[A]].read(proto)
      }
}
```

## akka.persistence.query.extension.Journal
A flow that stores messages in the journal.

# Whats new?
- v0.0.8 (2016-08-22)
  - Akka 2.4.8 -> 2.4.9

- v0.0.7 (2016-07-25)
  - Removed AckJournal, it only introduced design problems as its functionality can be created using the `akka.stream.integration.activemq.ActiveMqConsumer` with the `akka.persistence.query.extension.Journal.flow` and an `akka.stream.integration.activemq.AckSink.complete`

- v0.0.6 (2016-07-24)
  - Change to the Journal API, the apply() must not be used as it expresses the wrong intention. Apply should be used for function application only.
  - Replaced the .apply with .writer which expresses the intension correctly.
  - Reversed the order of the arguments as defaults should be on the right side.

- v0.0.5 (2016-07-23)
  - Moved all non-akka-persistence components to the non-official [akka-stream-extensions](https://github.com/dnvriend/akka-stream-extensions) project.
  - Journal by default uses the non-official EventWriter bulk API, which is only supported by [akka-persistence-jdbc](https://github.com/dnvriend/akka-persistence-jdbc) and [akka-persistence-inmemory](https://github.com/dnvriend/akka-persistence-inmemory) for bulk loading events.

- v0.0.4 (2016-07-22)
  - Added a `akka.stream.contrib.Counter.sink`, counts consumed elements and returns Future[Long],
  - Added a `akka.stream.integration.xml.XMLParser` for parsing large XML files sequentially.

- v0.0.3 (2016-07-20)
  - changes

- v0.0.2 (2016-07-19)
  - Configuration for the ResumableQuery
  
- v0.0.1 (2016-07-18)
  - Initial release

Have fun!

