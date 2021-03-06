package com.twitter.zipkin.web

import com.twitter.common.zookeeper.ServerSetImpl
import com.twitter.finagle.http.Http
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder, Server}
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.finatra.{AppService, Controller, FinatraServer}
import com.twitter.ostrich.admin.{ServiceTracker, Service}
import com.twitter.logging.Logger
import com.twitter.io.{Files, TempFile}
import com.twitter.zipkin.config.ZipkinWebConfig
import com.twitter.zipkin.gen
import java.net.InetSocketAddress

class ZipkinWeb(config: ZipkinWebConfig) extends Service {

  val log = Logger.get()
  var server: Option[Server] = None

  def start() {
    val clientBuilder = ClientBuilder()
      .codec(ThriftClientFramedCodec())
      .hostConnectionLimit(config.hostConnectionLimit)

    val clientService = config.queryClient match {
      case Left(address) => {
        clientBuilder.hosts(address)
          .build()
      }
      case Right(zk) => {
        val serverSet = new ServerSetImpl(zk, config.queryServerSetPath)
        val cluster = new ZookeeperServerSetCluster(serverSet) {
          override def ready() = super.ready
        }
        clientBuilder.cluster(cluster)
          .build()
      }
    }

    val client = new gen.ZipkinQuery.FinagledClient(clientService)

    val resource = config.resource
    val app = config.appConfig(client)

    FinatraServer.register(resource)
    FinatraServer.register(app)

    val finatraService = new AppService
    val service = finatraService

    server = Some {
      ServerBuilder()
        .codec(Http())
        .bindTo(new InetSocketAddress(config.serverPort))
        .name("ZipkinWeb")
        .build(service)
    }
    log.info("Finatra service started in port: " + config.serverPort)
    ServiceTracker.register(this)
  }

  def shutdown() {
    server.foreach { _.close() }
  }
}

class Resource(resourceDirs: Map[String, String]) extends Controller {
  resourceDirs.foreach { case (dir, contentType) =>
    get("/public/" + dir + "/:id") { request =>
      val file = TempFile.fromResourcePath("/public/" + dir + "/" + request.params("id"))
      if (file.exists()) {
        render.status(200).body(Files.readBytes(file)).header("Content-Type", contentType).toFuture
      } else {
        render.status(404).body("Not Found").toFuture
      }
    }
  }
}


