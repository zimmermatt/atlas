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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object LocalEurekaServer {

  // needed to run the route
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  private val localIp = "127.0.0.1"
  private val localPort = 63636

  private val minimalVipPayload =
    s"""{
       |  "applications": {
       |    "application": [
       |      {
       |        "name": "ATLAS_LWCAPI",
       |        "instance": [
       |          {
       |            "instanceId": "nfml-mazimmerKXH",
       |            "status": "UP",
       |            "dataCenterInfo": {
       |              "name": "dev_laptop",
       |              "metadata": {
       |                "local-ipv4": "${localIp}"
       |              }
       |            },
       |            "port": {
       |              "$$": ${localPort}
       |            }
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
       |""".stripMargin

  private val fullVipPayload =
    s"""{
       |  "applications": {
       |    "application": [
       |      {
       |        "name": "ATLAS_LWCAPI",
       |        "instance": [
       |          {
       |            "instanceId": "nfml-mazimmerKXH",
       |            "app": "ATLAS_LWCAPI",
       |            "appGroupName": "UNKNOWN",
       |            "ipAddr": "${localIp}",
       |            "sid": "builds",
       |            "homePageUrl": "http://${localIp}:${localPort}/lwc/Status",
       |            "statusPageUrl": "http://${localIp}:${localPort}/lwc/Status",
       |            "healthCheckUrl": "http://${localIp}:${localPort}/lwc/healthcheck",
       |            "secureHealthCheckUrl": "https://${localIp}:7004/lwc/healthcheck",
       |            "vipAddress": "atlas_lwcapi-local:${localPort},atlas_lwcapi-local:7101",
       |            "secureVipAddress": "atlas_lwcapi-local:7004,atlas_lwcapi-local:7004",
       |            "countryId": 1,
       |            "dataCenterInfo": {
       |              "@class": "com.netflix.appinfo.AmazonInfo",
       |              "name": "Amazon",
       |              "metadata": {
       |                "mac": "0a:26:89:6c:bf:42",
       |                "local-hostname": "ip-127-0-0-1.nflx.internal",
       |                "instance-id": "nfml-mazimmerKXH",
       |                "availability-zone": "lg-f-2",
       |                "instance-type": "macbookpro",
       |                "ami-id": "ami-05f9e2289b4f5ac4f",
       |                "accountId": "149510111645",
       |                "public-hostname": "${localIp}",
       |                "vpc-id": "vpc-9ae2c628",
       |                "local-ipv4": "${localIp}"
       |              }
       |            },
       |            "hostName": "${localIp}",
       |            "status": "UP",
       |            "leaseInfo": {
       |              "renewalIntervalInSecs": 30,
       |              "durationInSecs": 90,
       |              "registrationTimestamp": 1534890111109,
       |              "lastRenewalTimestamp": 1534985265972,
       |              "evictionTimestamp": 0,
       |              "serviceUpTimestamp": 1534890111109
       |            },
       |            "isCoordinatingDiscoveryServer": false,
       |            "lastUpdatedTimestamp": 1534890111109,
       |            "lastDirtyTimestamp": 1534890110941,
       |            "actionType": "ADDED",
       |            "asgName": "atlas_lwcapi-local-v42",
       |            "overriddenStatus": "UNKNOWN",
       |            "port": {
       |              "$$": ${localPort},
       |              "@enabled": "true"
       |            },
       |            "securePort": {
       |              "$$": 7004,
       |              "@enabled": "true"
       |            },
       |            "metadata": {
       |              "ec2InstanceId": "nfml-mazimmerKXH",
       |              "eureka.client.initTime": "1534890062292"
       |            }
       |          }
       |        ]
       |      }
       |    ],
       |    "versions__delta": "1",
       |    "apps__hashcode": "STARTING_90_UP_828_"
       |  }
       |}
       |""".stripMargin

  private val malformedVipPayload =
    s"""{{
       |  "applications": {
       |    "application": [
       |      {
       |        "name": "ATLAS_LWCAPI",
       |        "instance": [
       |          {
       |            "instanceId": "nfml-mazimmerKXH",
       |            "status": "UP",
       |            "dataCenterInfo": {
       |              "name": "dev_laptop",
       |              "metadata": {
       |                "local-ipv4": "${localIp}"
       |              }
       |            },
       |            "port": {
       |              "$$": ${localPort}
       |            }
       |          }
       |        ]
       |      }
       |    ]
       |  }
       |}
       |""".stripMargin

  private val vips = Map(
    "local-dev:7001"           -> minimalVipPayload,
    "local-dev-minimal:7001"   -> minimalVipPayload,
    "local-dev-full:7001"      -> fullVipPayload,
    "local-dev-malformed:7001" -> malformedVipPayload
  )

  def main(args: Array[String]) {
    import akka.http.scaladsl.model.ContentTypes.`application/json`

    val route: Route =
      get {
        pathPrefix("v2" / "vips" / Remaining) { vip =>
          println(s">> Looking for $vip")
          val responseJson = vips.get(vip)
          println(s">> Found: $responseJson")
          responseJson match {
            case Some(item) =>
              val response =
                HttpResponse(StatusCodes.OK, entity = HttpEntity(`application/json`, item))
              complete(response)
            case None => complete(StatusCodes.NotFound)
          }
        }
      }

    val port = 7102
    val hostName = "localhost"
    println(s"Server coming online at http://$hostName:$port/\n")
    val serverFuture = for {
      bindingFuture         <- Http().bindAndHandle(route, hostName, port)
      keepServerAliveFuture <- Future.never
    } yield keepServerAliveFuture

    Await.ready(serverFuture, Duration.Inf)
  }
}
