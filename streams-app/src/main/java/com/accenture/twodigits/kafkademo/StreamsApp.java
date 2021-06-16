package com.accenture.twodigits.kafkademo;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonSerializer;

public class StreamsApp {
    public static class TemperatureReading{
        public String station;
        public Integer temperature;
    }

    final static String APPLICATION_ID = "streams-app-v0.1.0";
    final static String APPLICATION_NAME = "Kafka Streams App";
    final static String INPUT_TOPIC = "temperature-readings";
    final static String OUTPUT_TOPIC = "max-temperatures";

    public static void main(String[] args) {
        System.out.printf("*** Starting %s Application ***%n", APPLICATION_NAME);

        Properties config = getConfig(); // preparing the config
        Topology topology = getTopology(); // describing the streams topology
        KafkaStreams streams =  startApp(config, topology); // starting the stream app

        setupShutdownHook(streams);
    }

    private static Properties getConfig(){
        Properties settings = new Properties();
        settings.put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        return settings;        
    }

    private static Topology getTopology() {
        final Serde<String> stringSerde = Serdes.String();
        final Serde<TemperatureReading> tempSerde = getJsonSerde();
        final Serde<Windowed<String>> windowedStringSerde = WindowedSerdes.timeWindowedSerdeFrom(String.class);
        final StreamsBuilder builder = new StreamsBuilder();
        
        final KTable<Windowed<String>, TemperatureReading> joinedKtable = builder
            .stream(INPUT_TOPIC, Consumed.with(stringSerde, tempSerde))     // Consuming the input topic temperature-readings
            .groupByKey(Grouped.with(stringSerde, tempSerde))               // grouping all events by Key "temp station"
            .windowedBy(TimeWindows.of(Duration.ofSeconds(30)))             // windowing all events in 30 sec windows
            .reduce((aggValue, newValue) -> newValue.temperature > aggValue.temperature ? newValue : aggValue); // finding the max temp. within the window

        joinedKtable
            .toStream()
            .to(OUTPUT_TOPIC, Produced.with(windowedStringSerde, tempSerde)); // writing to output topic max-temperatures

        // peeking to the stream to get the timewindow information to Sysout
        joinedKtable.toStream()
            .peek(new ForeachAction<Windowed<String>, TemperatureReading>() {
                @Override
                public void apply(Windowed<String> key, TemperatureReading value) {
                    System.out.println("TimeWindow at "+ key.window().endTime().toString() + ", the record is: "+ key.key() + ", " + "station=" + value.station + ", temperature=" + value.temperature);
                }
            });
            
        return builder.build();
    }

    private static Serde<TemperatureReading> getJsonSerde(){
        Map<String, Object> serdeProps = new HashMap<>();
        serdeProps.put("json.value.type", TemperatureReading.class);

        final Serializer<TemperatureReading> serializer = new KafkaJsonSerializer<>();
        serializer.configure(serdeProps, false);

        final Deserializer<TemperatureReading> deserializer = new KafkaJsonDeserializer<>();
        deserializer.configure(serdeProps, false);

        return Serdes.serdeFrom(serializer, deserializer);
    }

    private static KafkaStreams startApp(Properties config, Topology topology){
        KafkaStreams streams = new KafkaStreams(topology, config);
        streams.start();
        return streams;
    }

    private static void setupShutdownHook(KafkaStreams streams){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.printf("### Stopping %s Application ###%n", APPLICATION_NAME);
            streams.close();
        }));
    }
}
