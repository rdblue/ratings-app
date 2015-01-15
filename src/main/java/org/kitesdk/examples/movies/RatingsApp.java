/*
 * Copyright 2014 Cloudera, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kitesdk.examples.movies;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.kitesdk.data.DatasetReader;
import org.kitesdk.data.Datasets;
import org.kitesdk.data.View;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.velocity.VelocityRoute;

import static spark.Spark.externalStaticFileLocation;
import static spark.Spark.get;
import static spark.Spark.post;

public class RatingsApp {

  private static final Random rand = new Random();
  private static final String MOVIES_URI = "dataset:file:data/movies";
  private static final FlumeClient FLUME = new FlumeClient("localhost", 41415);

  private static List<Movie> MOVIES = null;

  public static void main(String[] args) {
    String moviesUri = MOVIES_URI;
    switch (args.length) {
      case 2:
        externalStaticFileLocation(args[1]);
      case 1:
        moviesUri = args[0];
    }

    MOVIES = cache(Datasets.load(moviesUri, Movie.class));

    get(new VelocityRoute("/") {
      @Override
      public Object handle(Request request, Response response) {
        Map<String, Object> model = Maps.newHashMap();
        model.put("title", "Welcome!");
        return new ModelAndView(model, "index.vm");
      }
    });

    post(new VelocityRoute("/") {
      @Override
      public Object handle(Request request, Response response) {
        response.redirect("/" + request.queryParams("name"));
        return new ModelAndView(Maps.newHashMap(), "redirect.vm");
      }
    });

    get(new VelocityRoute("/:name") {
      @Override
      public Object handle(Request request, Response response) {
        Map<String, Object> model = Maps.newHashMap();
        model.put("title", "Hello " + request.params("name") + "!");
        model.put("id", idFor(request.params("name")));
        model.put("name", request.params("name"));
        model.put("movies", sample(MOVIES, 10));

        return new ModelAndView(model, "rate.vm");
      }
    });

    post(new VelocityRoute("/:name") {
      @Override
      public Object handle(Request request, Response response) {
        Rating rating = new Rating(
            System.currentTimeMillis(),
            idFor(request.params("name")),
            request.queryMap("movie_id").longValue(),
            request.queryMap("rating").integerValue()
        );

        System.err.println("Sending rating: " + rating);
        FLUME.send(rating);

        // done processing the rating, redirect back to the rating form
        response.redirect("/" + request.params("name"));
        return new ModelAndView(Maps.newHashMap(), "redirect.vm");
      }
    });

  }

  /**
   * Creates a random but consistent ID for the given user name.
   *
   * @param name a user name.
   * @return a random ID in [0,1000)
   */
  private static long idFor(String name) {
    return (name.hashCode() & Integer.MAX_VALUE) % 1000;
  }

  /**
   * Returns a list of {@code num} random items from the given {@link List}.
   *
   * @param source a {@code List}
   * @param num an int
   * @param <E> the type of items in the list
   * @return a {@code List} of {@code num} random items from the source list
   */
  private static <E> List<E> sample(List<E> source, int num) {
    int size = source.size();
    List<E> list = Lists.newArrayListWithExpectedSize(num);
    for (int i = 0; i < num; i += 1) {
      list.add(source.get(rand.nextInt(size)));
    }
    return list;
  }

  /**
   * Returns the contents of a {@link View} as a {@link List}.
   *
   * @param view a {@code View} of source data
   * @param <E> the type of items in the view
   * @return the contents of {@code view} as a {@code List}
   */
  private static <E> List<E> cache(View<E> view) {
    List<E> list = Lists.newArrayList();
    try (DatasetReader<E> reader = view.newReader()) {
      for (E record : reader) {
        list.add(record);
      }
    }
    return list;
  }
}
