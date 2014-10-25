package com.rahulrav.retrofit.play

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.{ExecutorService, TimeUnit}

import play.api.http.{ContentTypeOf, Writeable}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.ws.ning.NingAsyncHttpClientConfigBuilder
import play.api.libs.ws.{DefaultWSClientConfig, WSResponseHeaders}
import retrofit.client.{Header, Request, Response}
import retrofit.mime.{TypedInput, TypedOutput}
import retrofit.{Callback, RetrofitError}

import scala.collection.JavaConversions._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Try

/**
 * The client that provides the Retrofit plumbing
 * so that you can use the Netty HTTP stack and Play's WS client.
 */

object WSRetrofitClient {

  // 2 minute default request timeout
  val DefaultTimeout = 2 * 60 * 1000

  /** Scala friendly API. */
  def apply(implicit context: ExecutionContext) = {
    new WSRetrofitClient(DefaultTimeout)(context)
  }

  def apply(defaultRequestTimeOut: Int)(implicit context: ExecutionContext) = {
    new WSRetrofitClient(defaultRequestTimeOut)(context)
  }

  /** Java friendly API. */
  def apply(defaultRequestTimeout: Int, executorService: ExecutorService) = {
    val context = ExecutionContext.fromExecutorService(executorService)
    new WSRetrofitClient(defaultRequestTimeout)(context)
  }

}

class WSRetrofitClient(val defaultRequestTimeout: Int)(implicit context: ExecutionContext) extends retrofit.client.Client {

  private[this] val EmptyString = ""

  // headers
  val ContentTypeHeader = "Content-Type"
  val StatusLine = "Status-Line"

  // the implicit ning client
  val client = new play.api.libs.ws.ning.NingWSClient(wsConfig())

  /** Executes a retrofit request. */
  override def execute(request: Request): Response = {

    val method = request.getMethod
    val body: TypedOutput = request.getBody
    val url = request.getUrl
    val headers: Seq[(String, String)] = request.getHeaders.map({
      header => {
        (header.getName, header.getValue)
      }
    })

    val requestBuilder = client.url(url).withHeaders(headers: _*).withFollowRedirects(true).withRequestTimeout(defaultRequestTimeout)
    val eventualResponse: Future[(WSResponseHeaders, Enumerator[Array[Byte]])] = method match {
      case "GET" => {
        if (body != null) {
          throw new RuntimeException("GET requests cannot contain a request body.")
        }
        requestBuilder.stream()
      }
      case "POST" | "PUT" | "DELETE" => {
        requestBuilder.withMethod(method).withBody(body)(wrt = toWriteable(body), ct = toContentTypeOf(body))
        requestBuilder.stream()
      }
      case "HEAD" => {
        requestBuilder.withMethod("HEAD").stream()
      }
      case _ => {
        throw new RuntimeException("Unsupported HTTP method")
      }
    }
    val (wsResponseHeaders, wsEnumerator) = Await.result(eventualResponse, Duration(defaultRequestTimeout, TimeUnit.MILLISECONDS))
    val contentType = getHeader(wsResponseHeaders, ContentTypeHeader)
    val responseHeaders = for {
      key <- wsResponseHeaders.headers.keySet.toList
    } yield {
      new Header(key, getHeader(wsResponseHeaders, key))
    }

    // consume the stream, as the Retrofit library needs not only the stream
    // but the total size in bytes
    // Content-Length unfortunately is not dependable as we still need to support chunked responses
    val consumeStream: Iteratee[Array[Byte], (ByteArrayOutputStream, Int)] = Iteratee.fold((new ByteArrayOutputStream(), 0)) {
      case ((out, length), bytes: Array[Byte]) => {
        out.write(bytes)
        (out, length + bytes.length)
      }
    }

    val (out, totalSize) = Await.result(wsEnumerator |>>> consumeStream, Duration.Inf)

    val responseBody = new TypedInput {
      override val in = new ByteArrayInputStream(out.toByteArray)
      override def length(): Long = totalSize
      override def mimeType(): String = contentType
    }

    new retrofit.client.Response(request.getUrl, wsResponseHeaders.status, getHeader(wsResponseHeaders, StatusLine), responseHeaders, responseBody)

  }

  def close() = {
    client.close()
  }

  def wsConfig(): com.ning.http.client.AsyncHttpClientConfig = {
    val clientConfig = new DefaultWSClientConfig(Some(defaultRequestTimeout.toLong))
    val secureDefaults: com.ning.http.client.AsyncHttpClientConfig = new NingAsyncHttpClientConfigBuilder(clientConfig).build()
    val builder = new com.ning.http.client.AsyncHttpClientConfig.Builder(secureDefaults)
    builder.setCompressionEnabled(true)
    builder.build()
  }

  private[this] def getHeader(wsResponseHeaders: WSResponseHeaders, header: String): String = {
    wsResponseHeaders.headers.get(header) match {
      case Some(head :: rest) => head
      case _ => EmptyString
    }
  }

  private[this] def toWriteable(typedOutput: TypedOutput): Writeable[TypedOutput] = {
    new Writeable[TypedOutput](transform = {
      typedOutput => {
        val out = new ByteArrayOutputStream()
        typedOutput.writeTo(out)
        out.toByteArray
      }
    }, contentType = Some(typedOutput.mimeType()))
  }


  private[this] def toContentTypeOf(typedOutput: TypedOutput): ContentTypeOf[TypedOutput] = {
    ContentTypeOf(mimeType = Some(typedOutput.mimeType()))
  }

}

/**
 * Wraps a Retrofit Callback, and exposes a conventional Future, so the callee does not have to deal with the callback.
 */
class WSCallback[A] extends Callback[A] {

  private[this] val promise = Promise[WSResult[A]]()
  lazy val future = promise.future

  override def success(result: A, response: Response) = {
    promise.complete(Try(WSResult(result, response)))
  }

  override def failure(error: RetrofitError) = {
    promise.failure(error)
  }
}

/** The actual response intended, with the complete Retrofit Response in case the client needs to look at the response headers. */
case class WSResult[A](result: A, response: Response)


