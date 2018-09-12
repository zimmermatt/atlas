/*
 * Copyright 2014-2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix

import java.time.Duration

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.netflix.atlas.config.ConfigManager
import com.netflix.atlas.eval.stream.Evaluator
import com.netflix.atlas.eval.stream.Evaluator.DataSource
import com.netflix.atlas.eval.stream.Evaluator.DataSources
import com.netflix.spectator.api.DefaultRegistry
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt

object MetricsStreamer {

  ConfigManager.update(ConfigFactory.load())
  ConfigManager.update(
    ConfigFactory
      .parseString("""
        |atlas.eval.stream {
        |  backends = [
        |    {
        |      host = "localhost"
        |      eureka-uri = "http://localhost:7102/v2/vips/local-dev:7001"
        |      instance-uri = "http://{local-ipv4}:{port}"
        |    },
        |    {
        |      host = "atlas"
        |      eureka-uri = "http://eureka/v2/vips/atlas-lwcapi:7001"
        |      instance-uri = "http://{local-ipv4}:{port}"
        |    },
        |    {
        |      host = "localhost-malformed-eureka-payload"
        |      eureka-uri = "http://localhost:7102/v2/vips/local-dev-malformed:7001"
        |      instance-uri = "http://{local-ipv4}:{port}"
        |    }
        |  ]
        |
        |  num-buffers = 3
        |}
      """.stripMargin)
  )

  private val registry = new DefaultRegistry()
  private val default_uri =
    "http://localhost:63636/api/v1/graph?q=nf.app,zimmermatt-atlas-standalone-local,:eq"
  private val uri_for_malformed_eureka_payload =
    "http://localhost-malformed-eureka-payload:63636/api/v1/graph?q=nf.app,zimmermatt-atlas-standalone-local,:eq"

  private val default_frequency = Duration.ofSeconds(5)
  private val heartbeat = Source
    .repeat(".\n")
    .throttle(1, 5.seconds, 1, ThrottleMode.Shaping)

  private implicit val system = ActorSystem("local", ConfigManager.current)
  private implicit val materializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
    val (uri, frequency) = if (args.length == 2) {
      args(0) -> Duration.ofSeconds(args(1).toInt)
    } else {
      default_uri -> default_frequency
    }

    val ds = DataSources.of(
      new DataSource("default-freq", Duration.ofSeconds(60L), uri),
      new DataSource("custom-freq", frequency, uri)
    )
    val datasources = Source.single(ds)
    //    val datasources = Source.repeat(ds).throttle(1, 1.minute, 1, ThrottleMode.Shaping)

    val evaluator = new Evaluator(ConfigManager.current, registry, system)
    val src =
      datasources
        .via(Flow.fromProcessor(() => evaluator.createStreamsProcessor()))
        .map { obj =>
          "==> \n" ++
          s"  ${obj.getId}\n" ++
          s"  ${obj.getMessage.toJson}\n"
        }
        .merge(heartbeat)

    //    val src =
    //      Source
    //        .fromPublisher(evaluator.createPublisher(uri))
    //        .map { obj =>
    //          "==>" ++ obj.toJson ++ "\n"
    //        }
    //        .merge(heartbeat)

    src.runWith(Sink.foreach(print))
  }
}
