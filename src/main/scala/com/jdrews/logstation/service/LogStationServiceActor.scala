package com.jdrews.logstation.service

import akka.actor._
import akka.pattern._
import com.typesafe.config.{ConfigRenderOptions, Config, ConfigFactory}
import com.jdrews.logstation.tailer.{LogTailerActor, LogThisFile}
import com.jdrews.logstation.utils.LogStationColorizer


import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.matching.Regex

/**
 * Created by jdrews on 2/21/2015.
 */
class LogStationServiceActor extends Actor with ActorLogging{
    private var logTailers = Set.empty[ActorRef]
    private var logStationColorizers = Set.empty[ActorRef]

    def receive = {
        case logThisFile: LogThisFile =>
            log.info(s"About to begin logging ${logThisFile.logFile}")

            val logTailerActor = context.actorOf(Props[LogTailerActor],
                name = s"LogTailerActor-${logThisFile.logFile.replaceAll("[^A-Za-z0-9]", ":")}")
            logTailerActor ! logThisFile
            context watch logTailerActor
            logTailers += logTailerActor

            val logStationColorizer = context.actorOf(Props[LogStationColorizer], name = s"LogStationColorizer-${logThisFile.logFile.replaceAll("[^A-Za-z0-9]", ":")}")
            context watch logStationColorizer
            logStationColorizers +=logStationColorizer
        case syntax: Map[String, Regex] =>
            logStationColorizers.foreach(colorizer => colorizer ! syntax)
        case ServiceShutdown =>
            // for each logTailers and logStationColorizers, send shutdown call and wait for it to shut down.
            log.info("got ServiceShutdown")
            logTailers.foreach(actor =>
                try {
                    Await.result(gracefulStop(actor, 20 seconds, ServiceShutdown), 20 seconds)
                } catch {
                    case e: AskTimeoutException ⇒ log.error("The actor didn't stop in time!" + e.toString)
                }
            )
            logStationColorizers.foreach(actor =>
                try {
                    Await.result(gracefulStop(actor, 20 seconds, ServiceShutdown), 20 seconds)
                } catch {
                    case e: AskTimeoutException ⇒ log.error("The actor didn't stop in time!" + e.toString)
                }
            )
            context stop self
        case actTerminated: Terminated => log.info(actTerminated.toString)
        case something => log.warning(s"huh? $something")
    }
}