# Kafka Nukleus Implementation

[![Build Status][build-status-image]][build-status]

[build-status-image]: https://travis-ci.org/reaktivity/nukleus-kafka.java.svg?branch=develop
[build-status]: https://travis-ci.org/reaktivity/nukleus-kafka.java

Fetching messages is supported as follows:

```
client nukleus -> Begin with extension data -> nukleus-kafka -> (other nuklei) -> network -> Kafka (cluster)
               <- Data frames with ext data <-               <-                <-
```

### Begin (incoming from client)

The extension data contains the following fields:

- topic name (required): messages will be fetched from this topic
- fetch offsets (required): message will be fetched starting from the given offsets. The first offset is assumed to be partition 0, the next partition 1, etc. If the number of partitions over which the topic is spread exceeds the number of given offsets, offset zero is assumed for the excess partitions.
- fetch key (optional): if specified then only messages whose message key matches this value will be sent back to the client.
- headers (optional): if specified, each header (key and value) represents a condition which messages must match in order to be sent back to the client. All conditions must be met. For example, {{"header1", "value1"}, {"header2", "value2"}} means only messages containing headers "header1" and "header2" with values "value1" and "value2" respectively will be sent back to the client.

### Data (reply stream from nukleus-kafka)

Each data frame represents the value of one Kafka message (a.k.a. record). The extension data gives the high watermark offsets which could be used subsequently to fetch all messages following this message (if the client disconnect and reconnects later).

### Compacted Topics

Topics which are configured in Kafka with property "cleanup.policy" set to "compact" are treated specially, in the following ways:

- A cache is maintained in order to enhance performance for subscriptions to a particular message key, and where possible only deliver the latest message for the key.
- This cache is kept up to date all the time by doing proactive fetches, unless this turned off by setting system property `nukleus.kafka.topic.bootstrap.enabled` to "false".

### Configuration

The following system properties are currently supported for configuration:

- `nukleus.kafka.fetch.max.bytes` (integer, default 50 MiB): maximum value that will be specified as fetch.max.bytes in fetch requests made to Kafka.
- `nukleus.kafka.fetch.partition.max.bytes` (integer, default 1 MiB): maximum size of a partition response. Should be set to the highest configured value for Kafka broker or topic configuration property "max.message.bytes".
- `nukleus.kafka.topic.bootstrap.enabled` (boolean default true): caching of message keys and latest offsets is enabled for compacted topics to improve performance.
