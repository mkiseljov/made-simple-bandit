package org.made


import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import io.circe._
import io.circe.syntax._
import CirceDeserializers._
import CirceSerializers._

import scala.util.Random

object GeneratorJob {
  def main(args: Array[String]): Unit = {
    // set up the execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val params = ParameterTool.fromArgs(args)
    val jobParams = JobParams(params)
    env
      .addSource(new RandomSource(jobParams.numUsers))
      .map(_.asJson.noSpaces)
      .addSink(new FlinkKafkaProducer(
        jobParams.inputTopic,
        new SimpleStringSchema,
        jobParams.kafkaProperties
      ))


    val predicts = env
      .addSource(new FlinkKafkaConsumer(
        jobParams.outputTopic,
        new SimpleStringSchema,
        jobParams.kafkaProperties
      )).name(jobParams.weightTopic)
      .flatMap(x => {
        parser.parse(x).getOrElse(Json.Null).as[Predict].toOption
      })
      .name("parse predicts")
      .uid("parse-predicts")

    val ids = Array.range(0, jobParams.numItems)
    val dist = ids.map(_ / ids.sum.toDouble)
    val cdf = dist.scanLeft(.0)(_ + _)

    predicts
      .flatMap(predict => {
        val possibleClicks = Array.range(0, 3).map(x => sampleId(cdf))
        predict.itemList.map(x => {
          Feedback(predict.userId, x, if(possibleClicks.contains(x)) 1 else 0)
        })
      })
      .map(_.asJson.noSpaces)
      .addSink(new FlinkKafkaProducer(
        jobParams.feedbackTopic,
        new SimpleStringSchema,
        jobParams.kafkaProperties
      ))

    // execute program
    env.execute("GeneratorJob")
  }

  private def sampleId(cdf: Array[Double]) = {
    val t = Random.nextDouble()
    cdf.zipWithIndex.filter(_._1 >= t).head._2 - 1
  }
}
