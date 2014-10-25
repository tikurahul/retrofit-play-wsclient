## Play + Retrofit

Retrofit is an amazing HTTP client scaffolding library. 
For more information about Retrofit checkout, [Square Retrofit](https://github.com/square/retrofit).

### Goal

The aim of this project is to make it more convenient to use Retrofit with the [Play Framework](https://www.playframework.com).

Retrofit supports the notion of non-blocking HTTP requests through `retrofit.Callback<T>`. The goal of this project is to make it really easy
to expose a `scala` wrapper that returns a `scala.concurrent.Future[T]` while also using Play Framework's `WS` library.

### Details

Let's say, we have the Retrofit HTTP interface defined:

```
public interface GitHub {
  static class Contributor {
    String login;
    int contributions;
  }
  @GET("/repos/{owner}/{repo}/contributors")
  void contributors(@Path("owner") String owner, @Path("repo") String repo, Callback<List<Contributor>> callback);
}
```

You can now expose a more convenient scala interface that looks like:

```
class GitHubWrapper(github: GitHub)(implicit context: ExecutionContext) {
  def contributors(owner: String, repo: String): Future[util.List[Contributor]] = {
    val callback = new WSCallback[util.List[Contributor]]()
    github.contributors(owner, repo, callback)
    callback.future map {
      case WSResult(result: util.List[Contributor], retrofitResponse: Response) => result
    }
  }
}
```

For more details check-out the example `com.rahulrav.example` packages in for both the Java and Scala examples.
