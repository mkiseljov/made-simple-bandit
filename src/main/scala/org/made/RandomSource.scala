package org.made

import org.apache.flink.streaming.api.functions.source.SourceFunction

import scala.util.Random

class RandomSource(numUsers: Int) extends SourceFunction[Request] {
  var stopStream = false
  override def run(ctx: SourceFunction.SourceContext[Request]): Unit = {
    while(!stopStream) {
      Thread.sleep(500)
      ctx.collect(Request(Random.nextInt(numUsers)))
    }
  }
  override def cancel(): Unit = {
    stopStream = true
  }
}
