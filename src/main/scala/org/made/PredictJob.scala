package org.made

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import io.circe._
import io.circe.syntax._
import CirceDeserializers._
import CirceSerializers._
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction
import org.apache.flink.util.Collector

import scala.util.Random

object PredictJob {
  def main(args: Array[String]): Unit = {
    // set up the execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val params = ParameterTool.fromArgs(args)
    val jobParams = JobParams(params)

    val requests = env.addSource(new FlinkKafkaConsumer(
      jobParams.inputTopic,
      new SimpleStringSchema,
      jobParams.kafkaProperties
    )).name(jobParams.inputTopic)

    val parsedRequests = requests
        .flatMap(x => {
          parser.parse(x).getOrElse(Json.Null).as[Request].toOption
        })
        .name("parse request")
        .uid("parse-request")

    val itemStateDescriptor = new MapStateDescriptor[Int, ItemCounts]("item-counts-broadcast", classOf[Int], classOf[ItemCounts])

    val items = env.addSource(new FlinkKafkaConsumer(
      jobParams.weightTopic,
      new SimpleStringSchema,
      jobParams.kafkaProperties
    )).name(jobParams.weightTopic)
      .flatMap(x => {
        parser.parse(x).getOrElse(Json.Null).as[ItemCounts].toOption
      })
      .name("parse items")
      .uid("parse-items")
      .broadcast(itemStateDescriptor)

    val predicts = parsedRequests.connect(items)
        .process(new BroadcastProcessFunction[Request, ItemCounts, Predict] {
          override def processBroadcastElement(value: ItemCounts,
                                               ctx: BroadcastProcessFunction[Request, ItemCounts, Predict]#Context,
                                               out: Collector[Predict]): Unit = {
            ctx.getBroadcastState(itemStateDescriptor).put(value.itemId, value)
          }

          override def processElement(value: Request,
                                      ctx: BroadcastProcessFunction[Request, ItemCounts, Predict]#ReadOnlyContext,
                                      out: Collector[Predict]): Unit = {
            val state = Option(ctx.getBroadcastState(itemStateDescriptor))
            val counts = Array.range(0, jobParams.numItems).map(x => {
              state.flatMap(a => Option(a.get(x))).getOrElse(ItemCounts(x, .0, .0))
            })

            val logTotalShows = Math.log(counts.map(_.shows).sum + jobParams.smoothing * counts.length)

            val scores = counts.map(cnt => {
              val m = (cnt.clicks + jobParams.smoothing) / (cnt.shows + jobParams.smoothing)
              (cnt.itemId, m + Math.sqrt(2 * logTotalShows / (cnt.shows + jobParams.smoothing)))
            })

            val tieBreaked = scores
              .groupBy(_._2)
              .mapValues(x => Random.shuffle(x.toSeq).zipWithIndex)
              .flatMap(x => x._2.map(y => (y._1._1, y._1._2, y._2))).toArray
            val predictions = tieBreaked
              .sortBy(x => (-x._2, x._3))
              .slice(0, jobParams.numPredictions).map(_._1)
            out.collect(Predict(value.userId, predictions))
          }
        })

    predicts
      .map(_.asJson.noSpaces)
      .addSink(new FlinkKafkaProducer(
        jobParams.outputTopic,
        new SimpleStringSchema,
        jobParams.kafkaProperties
      ))

    // execute program
    env.execute("PredictJob")
  }
}
