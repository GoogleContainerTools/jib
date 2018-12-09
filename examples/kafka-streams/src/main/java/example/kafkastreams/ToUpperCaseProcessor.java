package example.kafkastreams;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * In this example, we implement a simple {@link String#toUpperCase} program using the high-level Streams DSL
 * that reads from a source topic, where the values of messages represent lines of text,
 * and writes out the individual uppercased tokens in the lines into a sink topic.
 * Topic names are provided as environment variables {@code ENV_STREAMS_INPUT} and {@code ENV_STREAMS_OUTPUT}.
 * <br>
 * <a href="http://kafka.apache.org/documentation/streams">Kafka Streams Documentation</a>
 */
public class ToUpperCaseProcessor {

    // Required values
    private static final String ENV_BOOTSTRAP_SERVERS = "BOOTSTRAP_SERVERS";
    private static final String ENV_STREAMS_INPUT = "STREAMS_INPUT";
    private static final String ENV_STREAMS_OUTPUT = "STREAMS_OUTPUT";

    // Values with defaults
    private static final String ENV_APPLICATION_ID = "APPLICATION_ID";
    private static final String ENV_CLIENT_ID = "CLIENT_ID";
    private static final String ENV_OFFSET_RESET = "AUTO_OFFSET_RESET";

    private static final Logger LOGGER = LoggerFactory.getLogger(ToUpperCaseProcessor.class);

    public static void main(String[] args) throws Exception {
        final String inputTopic = System.getenv(ENV_STREAMS_INPUT);
        final String outputTopic = System.getenv(ENV_STREAMS_OUTPUT);

        LOGGER.info("input topic = {}", inputTopic);
        LOGGER.info("output topic = {}", outputTopic);
        if (isBlank(inputTopic) || isBlank(outputTopic)) {
            LOGGER.error("Input topic variable '{}' or output topic variable '{}' is empty.",
                    ENV_STREAMS_INPUT, ENV_STREAMS_OUTPUT);
            System.exit(1);
        } else if (inputTopic.equals(outputTopic)) {
            LOGGER.error("Input and output topics are the same. Exiting to disallow Kafka consumption loops.",
                    ENV_STREAMS_INPUT, ENV_STREAMS_OUTPUT);
            System.exit(1);
        }

        final String defaultApplicationId = "streams-wordcount";
        final String defaultClientId = "wordcount-client";
        Properties props = getProperties(defaultApplicationId, defaultClientId);

        KafkaStreams streams = createStreams(props, inputTopic, outputTopic);
        startStream(streams);
    }

    private static KafkaStreams createStreams(Properties streamConfiguration, String input, String output) {
        final Serde<String> stringSerde = Serdes.String();
        final StreamsBuilder builder = new StreamsBuilder();

        builder.stream(input, Consumed.with(stringSerde, stringSerde))
                .mapValues((ValueMapper<String, String>) String::toUpperCase) // uppercase a line
                .flatMapValues(v -> Arrays.asList(v.split("\\W+")))           // split lines into tokens
                .filter((k, v) -> !v.trim().isEmpty())                        // filter empty tokens
                .to(output, Produced.with(stringSerde, stringSerde));         // output to a new topic

        return new KafkaStreams(builder.build(), streamConfiguration);
    }

    private static void startStream(KafkaStreams stream) {
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("stream-shutdown-hook") {
            @Override
            public void run() {
                LOGGER.info("Closing Stream...");
                stream.close();
                latch.countDown();
            }
        });

        try {
            LOGGER.info("Starting Stream...");
            stream.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

    private static Properties getProperties(String defaultApplicationId, String defaultClientId) {
        String bootstrapServers = System.getenv(ENV_BOOTSTRAP_SERVERS);
        if (isBlank(bootstrapServers)) {
            LOGGER.error("Undefined environment variable '{}'. Will be unable to communicate with Kafka.",
                    ENV_BOOTSTRAP_SERVERS);
            System.exit(1);
        }

        String appId = System.getenv(ENV_APPLICATION_ID);
        if (isBlank(appId)) {
            LOGGER.warn("Undefined environment variable '{}'. Using default '{}'",
                    ENV_APPLICATION_ID, defaultApplicationId);
            appId = defaultApplicationId;
        }

        String clientId = System.getenv(ENV_CLIENT_ID);
        if (isBlank(clientId)) {
            LOGGER.warn("Undefined environment variable '{}'. Using default '{}'",
                    ENV_CLIENT_ID, defaultApplicationId);
            clientId = defaultClientId;
        }

        String offsetReset = System.getenv(ENV_OFFSET_RESET);
        if (isBlank(offsetReset)) {
            final String defaultOffset = "latest";
            LOGGER.warn("Undefined environment variable '{}'. Using default '{}'",
                    ENV_OFFSET_RESET, defaultOffset);
            offsetReset = defaultOffset;
        }

        Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, String.valueOf(bootstrapServers));
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        props.put(StreamsConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, "exactly_once");
        return props;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
