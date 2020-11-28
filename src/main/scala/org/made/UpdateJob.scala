package org.made

import org.apache.flink.streaming.api.scala._
import io.circe._
import io.circe.syntax._
import CirceDeserializers._
import CirceSerializers._
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.{EventTimeSessionWindows, ProcessingTimeSessionWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import org.apache.flink.util.Collector


object UpdateJob {
  def main(args: Array[String]): Unit = {
    // set up the execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val params = ParameterTool.fromArgs(args)
    val jobParams = JobParams(params)


    val feedback = env
      .addSource(new FlinkKafkaConsumer(
        jobParams.feedbackTopic,
        new SimpleStringSchema,
        jobParams.kafkaProperties
      )).name(jobParams.feedbackTopic)
      .flatMap(x => {
        parser.parse(x).getOrElse(Json.Null).as[Feedback].toOption
      })
      .name("parse feedback")
      .uid("parse-feedback")

    val sessionCounts = feedback
        .keyBy(_.userId)
        .window(ProcessingTimeSessionWindows.withGap(Time.seconds(5)))
        .process(new ProcessWindowFunction[Feedback, Iterable[ItemCounts], Int, TimeWindow] {
          override def process(key: Int,
                               context: Context,
                               elements: Iterable[Feedback],
                               out: Collector[Iterable[ItemCounts]]): Unit = {
            val counts = elements.groupBy(_.itemId).map(x => {
              ItemCounts(x._1, 1.0, if(x._2.exists(_.isClicked == 1)) 1.0 else .0)
            })
            out.collect(counts)
          }
        })

    sessionCounts
        .flatMap(x => x)
        .keyBy(_.itemId)
        .process(new KeyedProcessFunction[Int, ItemCounts, ItemCounts] {
          private lazy val state = getRuntimeContext
            .getState(new ValueStateDescriptor[ItemCounts]("item-counts", classOf[ItemCounts]))

          override def processElement(value: ItemCounts,
                                      ctx: KeyedProcessFunction[Int, ItemCounts, ItemCounts]#Context,
                                      out: Collector[ItemCounts]): Unit = {
            val st = Option(state.value).getOrElse(ItemCounts(value.itemId, .0, .0))
            val newState = ItemCounts(st.itemId, st.shows + value.shows, st.clicks + st.clicks)
            state.update(newState)
            out.collect(newState)
          }
        })
      .map(_.asJson.noSpaces)
      .addSink(new FlinkKafkaProducer(
        jobParams.weightTopic,
        new SimpleStringSchema,
        jobParams.kafkaProperties
      ))


    // execute program
    env.execute("Update Job")
  }
}
