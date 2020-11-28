package org.made

import java.util.Properties

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes.ByteArraySerde
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringDeserializer, StringSerializer}

case class JobParams(
                      kafkaProperties: Properties,
                      inputTopic: String,
                      outputTopic: String,
                      weightTopic: String,
                      feedbackTopic: String,
                      numUsers: Int,
                      numItems: Int,
                      numPredictions: Int,
                      smoothing: Double
                    )

object JobParams {
  def apply(params: ParameterTool): JobParams = {
    val kafkaProperties = new Properties() {
      put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, params.get("kafka-bootstrap-server", "kafka:9092"))
      put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      put(ConsumerConfig.GROUP_ID_CONFIG, params.get("kafka-group-id", "bandit"))
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
      put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)
      put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)
    }

    JobParams(
      kafkaProperties,
      params.get("input-topic", "input"),
      params.get("output-topic", "output"),
      params.get("weight-topic", "weight"),
      params.get("feedback-topic", "feedback"),
      params.getInt("num-users", 1000),
      params.getInt("num-items", 100),
      params.getInt("num-predictions", 100),
      params.getDouble("smoothing", 10.0)
    )
  }
}
