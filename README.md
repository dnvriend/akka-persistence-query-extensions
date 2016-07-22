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

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-query-extensions" % "0.0.4"
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
    .via(Journal())).run()

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
    .via(validateXSDFlow(baseDir)).via(Journal())).run()

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
    .via(moveToDirFlow).via(Journal())).run()

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

## akka.persistence.query.extension.AckJournal
A flow that stores messages in the journal, should be used together with `reactive-activemq`'s
`akka.stream.integration.activemq.ActiveMqConsumer` to store messages from a topic to the journal and acking each message
effectively removing the messages from the queue.

For example:

```scala
import akka.stream.integration.activemq.ActiveMqConsumer
import akka.stream.integration.{ AckUTup, MessageExtractor }
import spray.json.DefaultJsonProtocol._
import akka.stream.scaladsl.{ Flow, Source }
import spray.json._

// type classes for consumer
import akka.camel.CamelMessage
import akka.stream.integration.{ MessageExtractor, TestSpec }
import akka.stream.integration.JsonMessageExtractor._
import spray.json._

// the case class to marshal/unmarshal
case class MessageReceived(fileName: String, timestamp: Long)

// the JsonFormat for the case class
implicit val messageReceivedJsonFormat = jsonFormat2(MessageReceived)

// not really needed as importing `akka.stream.integration.JsonMessageExtractor._`
// brings this JsonMessageExtractor in implicit scope
//implicit val messageReceivedCamelFormat = implicitly[MessageExtractor[CamelMessage, MessageReceived]]

// a non-completing flow that receives messages from amq and stores them in the journal
val consumer: Source[AckUTup[MessageReceived], ActorRef] =
  ActiveMqConsumer[MessageReceived]("ImportDrawResultsFileConsumer")
AckJournal(consumer, preProcessor = Flow[ImportDrawResultsFileMessageReceivedEvent].log("com.github.dnvriend.amq.consumer"))
```

## akka.stream.integration.io.DigestCalculator
Given a stream of ByteString, it calculates a digest given a certain Algorithm.

## akka.stream.integration.io.FileUtils
A stage that does file operations. Very handy for stream processing file operations.

## akka.stream.integration.xml.Validation
Given a stream of ByteString, it validates an XML file given an XSD.

## akka.stream.integration.xml.XMLEventSource
Given an inputstream or filename, it creates a `Source[XMLEvent, NotUsed]` that can be used to process
an XML file. It can be used together with akka-stream's processing stages and the
`akka.persistence.query.extension.Journal` to store the transformed messages in the journal to be consumed
by other components. It can also be used with `reactive-activemq`'s
`akka.stream.integration.activemq.ActiveMqProducer` to send these messages to a VirtualTopic.

## akka.stream.integration.xml.XMLParser
It should be easy to write XML parsers to process large XML files efficiently. Most often this means reading the XML
sequentially, parsing a known XML fragment and converting it to DTO's using case classes. For such a use case the
`akka.stream.integration.xml.XMLParser` should help you get you up and running fast!

For example, let's process the following XML:

```xml
<orders>
    <order id="1">
        <item name="Pizza" price="12.00">
            <pizza>
                <crust type="thin" size="14"/>
                <topping>cheese</topping>
                <topping>sausage</topping>
            </pizza>
        </item>
        <item name="Breadsticks" price="4.00"/>
        <tax type="federal">0.80</tax>
        <tax type="state">0.80</tax>
        <tax type="local">0.40</tax>
    </order>
</orders>
```

Imagine we are interested in only orders, and only the tax, lets write two parsers:

```scala
import scala.xml.pull._
import akka.stream.scaladsl._
import akka.stream.integration.xml.XMLParser
import akka.stream.integration.xml.XMLParser._
import akka.stream.integration.xml.XMLEventSource

case class Order(id: String)

val orderParser: Flow[XMLEvent, Order] = {
 var orderId: String = null
 XMLParser.flow {
  case EvElemStart(_, "order", meta, _) ⇒
    orderId = getAttr(meta)("id"); emit()
  case EvElemEnd(_, "order") ⇒
    emit(Order(orderId))
 }
}

case class Tax(taxType: String, value: String)

val tagParser: Flow[XMLEvent, Tax] = {
  var taxType: String = null
  var taxValue: String = null
  XMLParser.flow {
    case EvElemStart(_, "tax", meta, _) =>
      taxType = getAttr(meta)("type"); emit()
    case EvText(text) ⇒
      taxValue = text; emit()
    case EvElemEnd(_, "tax") ⇒
      emit(Tax(taxType, taxValue))
  }
}

XMLEventSource.fromFileName("orders.xml")
 .via(orderParser).runForeach(println)

XMLEventSource.fromFileName("orders.xml")
 .via(tagParser).runForeach(println)
```

For a more complex example, please take a look at `akka.stream.integration.xml.PersonParser` in the test package of this library.

# Whats new?
- v0.0.4 (2016-07-22)
  - Added a `akka.stream.contrib.Counter.sink`, counts consumed elements and returns Future[Long],
  - Added a `akka.stream.integration.xml.XMLParser` that can help with creating an XMLParser for your types eg:

- v0.0.3 (2016-07-20)
  - changes

- v0.0.2 (2016-07-19)
  - Configuration for the ResumableQuery
  
- v0.0.1 (2016-07-18)
  - Initial release

Have fun!

