package com.accenture.twodigits.kafkademo;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonSerializer;

/**
 * This implementation of a Kafka Streams App takes the values
 * from the Kafka topic, transforms them and stores them into a
 * new Kafka topics.
 * 
 * See {@link https://quarkus.io/guides/kafka-streams}
 * 
 * @author thomas.jentsch
 */
@ApplicationScoped
public class StreamApp {
	
    public static class TemperatureReading{
        public String station;
        public Integer temperature;
    }

    final static String INPUT_TOPIC = "temperature-readings";
    final static String OUTPUT_TOPIC = "max-temperatures";
    
	@Produces
    public Topology getTopology() {
        final Serde<String> stringSerde = Serdes.String();
        final Serde<TemperatureReading> tempSerde = getJsonSerde();
        final StreamsBuilder builder = new StreamsBuilder();
        
        builder
            .stream(INPUT_TOPIC, Consumed.with(stringSerde, tempSerde))     // Consuming the input topic temperature-readings
            .groupByKey(Grouped.with(stringSerde, tempSerde))               // grouping all events by Key "temp station"
            .windowedBy(TimeWindows.of(Duration.ofSeconds(20)))             // windowing all events in 20 sec windows
            .reduce((aggValue, newValue) -> 
            			newValue.temperature > aggValue.temperature ? newValue : aggValue) // finding the max temp. within the window
            .toStream()
            .filter((k,v) -> v.station.equalsIgnoreCase("S-04"))			// Filter to Station S-04
            .selectKey((k,v) -> k.window().endTime().toString())			// Use Window End Time as key
            .to(OUTPUT_TOPIC, Produced.with(stringSerde, tempSerde)); 		// writing to output topic max-temperatures
        
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

}
