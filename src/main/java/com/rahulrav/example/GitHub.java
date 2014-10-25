package com.rahulrav.example;

import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Path;

import java.util.List;

public interface GitHub {
  static class Contributor {
    String login;
    int contributions;
  }
  @GET("/repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@Path("owner") String owner, @Path("repo") String repo);

  @GET("/repos/{owner}/{repo}/contributors")
  void contributors(@Path("owner") String owner, @Path("repo") String repo, Callback<List<Contributor>> callback);
}
