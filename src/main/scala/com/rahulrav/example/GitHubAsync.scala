package com.rahulrav.example

import java.util

import com.rahulrav.example.GitHub.Contributor
import com.rahulrav.retrofit.play.{WSCallback, WSResult, WSRetrofitClient}
import retrofit.RestAdapter
import retrofit.client.Response

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object GitHubWrapper {
  def apply(github: GitHub)(implicit context: ExecutionContext) = {
    new GitHubWrapper(github)(context)
  }
}

/**
 * Wraps the retrofit implementation of the interface defining all the HTTP APIs.
 * Exposes convinient Scala Futures.
 */

class GitHubWrapper(github: GitHub)(implicit context: ExecutionContext) {
  def contributors(owner: String, repo: String): Future[util.List[Contributor]] = {
    val callback = new WSCallback[util.List[Contributor]]()
    github.contributors(owner, repo, callback)
    callback.future map {
      case WSResult(result: util.List[Contributor], retrofitResponse: Response) => result
    }
  }
}

object Main extends App {

  import scala.concurrent.ExecutionContext.Implicits.global

  val client = WSRetrofitClient(WSRetrofitClient.DefaultTimeout)
  val restAdapter: RestAdapter = new RestAdapter.Builder().setEndpoint(GitHubClient.API_URL).setClient(client).build
  val github: GitHubWrapper = GitHubWrapper(restAdapter.create(classOf[GitHub]))

  /** Now you should be able to do something like:  */
  github.contributors("square", "retrofit") map {
    contributors => {
      contributors.asScala.foreach {
        contributor => println(s"${contributor.login}, ${contributor.contributions}")
      }
    }
  }

  /** (or) */
  val contributors = Await.result(github.contributors("square", "retrofit"), Duration.Inf)
  contributors.asScala.foreach {
    contributor => println(s"${contributor.login}, ${contributor.contributions}")
  }
}
