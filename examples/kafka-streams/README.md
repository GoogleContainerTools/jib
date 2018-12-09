# Containerize a [Kafka Streams](http://kafka.apache.org/documentation/streams) application with Jib

Run `./mvnw clean package` to build your container

The following tutorial requires installation of `docker-compose`.

If `docker-compose` is unavailable, Kafka and Zookeeper can be started locally. 

The following steps can be used to run this application locally after creating the input and output topic, and producing `[lipsum.txt](lipsum.txt)` into the input topic (copy the executed commands from below, switching the Docker container names for `localhost`).  

```bash
export BOOTSTRAP_SERVERS=localhost:9092  # Assumes Kafka default port
export STREAMS_INPUT=streams-plaintext-input
export STREAMS_OUTPUT=streams-plaintext-output
export AUTO_OFFSET_RESET=earliest
./mvnw clean exec:java
```

## Start Kafka Cluster

Sometimes the Kafka container kills itself in below steps, and the consumer commands therefore may need to be re-executed. The Streams Application should reconnect on its own. 

This starts Kafka listening on `29092` on the host, and `9092` within the Docker network. Zookeeper is available on `2181`.

> *Terminal 1*

```bash
docker-compose up zookeeper kafka
```

## Create Kafka Topics

> *Terminal 2*

```bash
docker-compose exec kafka \
    bash -c "kafka-topics.sh --create --if-not-exists --zookeeper zookeeper:2181 --topic streams-plaintext-input --partitions=1 --replication-factor=1"
```

```bash
docker-compose exec kafka \
    bash -c "kafka-topics.sh --create --if-not-exists --zookeeper zookeeper:2181 --topic streams-plaintext-output --partitions=1 --replication-factor=1"
```

Verify topics exist

```bash
docker-compose exec kafka \
    bash -c "kafka-topics.sh --list --zookeeper zookeeper:2181"
```

## Produce Lorem Ipsum into input topic

> *Terminal 2*

```bash
docker-compose exec kafka \
    bash -c "cat /data/lipsum.txt | kafka-console-producer.sh --topic streams-plaintext-input --broker-list kafka:9092"
```

Verify that data is there (note: hard-coding `max-messages` to the number of lines of expected text)

```
docker-compose exec kafka \
    bash -c "kafka-console-consumer.sh --topic streams-plaintext-input --bootstrap-server kafka:9092 --from-beginning --max-messages=9"
```

## Start Console Consumer for output topic

> *Terminal 2*

```bash
docker-compose exec kafka \
    bash -c "kafka-console-consumer.sh --topic streams-plaintext-output --bootstrap-server kafka:9092 --from-beginning"
```

## Start Kafka Streams Application

> *Terminal 3*

```bash
docker-compose up kafka-streams
```

*Should see output in Terminal 2*

<kbd>Ctrl+C</kbd> on ***terminal 2*** after successful output and should see `Processed a total of 509 messages` if all words produced and consumed exactly once.  

## Extra

Redo the tutorial with more input data and partitions, then play with `docker-compose scale` to add more Kafka Streams applications.

## Cleanup environment

```bash
docker-compose rm -sf
# Clean up mounted docker volumes
docker volume ls | grep $(basename `pwd`) | awk '{print $2}' | xargs docker volume rm 
# Clean up networks
docker network ls | grep $(basename `pwd`) | awk '{print $2}' | xargs docker network rm
```

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

Learn [more about Apache Kafka & Kafka Streams](http://kafka.apache.org/documentation).

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/examples/kafka-streams)](https://github.com/igrigorik/ga-beacon)
