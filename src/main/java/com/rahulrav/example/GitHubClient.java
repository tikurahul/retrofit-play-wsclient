package com.rahulrav.example;

import com.rahulrav.retrofit.play.WSRetrofitClient;
import com.rahulrav.retrofit.play.WSRetrofitClient$;
import retrofit.RestAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adapted from the original Retrofit Sample at:
 * https://github.com/square/retrofit/blob/master/samples/github-client/src/main/java/com/example/retrofit/GitHubClient.java
 */
public class GitHubClient {

  static final String API_URL = "https://api.github.com";

  public static void main(String... args) {
    final ExecutorService service = Executors.newCachedThreadPool();
    final WSRetrofitClient client = WSRetrofitClient$.MODULE$.apply(WSRetrofitClient$.MODULE$.DefaultTimeout(), service);

    final RestAdapter restAdapter = new RestAdapter.Builder()
        .setEndpoint(API_URL)
        .setClient(client)
        .build();

    final GitHub github = restAdapter.create(GitHub.class);
    final List<GitHub.Contributor> contributors = github.contributors("square", "retrofit");
    for (final GitHub.Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }

    // close when done
    client.close();
  }
}
