## Movie Ratings Web App

This example shows a movie ratings web application that logs rating events to
HDFS for follow-on analysis. This is based on the [MovieLens movie
ratings][movielens] data set.

[movielens]: http://grouplens.org/datasets/movielens/

## Setup

These instructions are intended for the [QuickStart VM][quickstart-vm], but
should on most Hadoop clusters. Start by cloning this repository:

```
git clone https://github.com/rdblue/ratings-app.git
cd ratings-app
```

You should also [install the Kite CLI][kite-cli-install].

[quickstart-vm]: http://www.cloudera.com/content/cloudera/en/downloads/quickstart_vms/cdh-5-3-x.html
[kite-cli-install]: http://kitesdk.org/docs/0.17.1/Install-Kite.html

### Movies to rate

This app relies on the `u.item` table from MovieLens stored as a Kite dataset
to provide some movies to rate. You can use the [Kite CLI][kite-cli] to create
the dataset.

```
kite-dataset create dataset:file:data/movies \
                            --schema src/main/avro/movie.avsc
kite-dataset csv-import u.item dataset:file:data/movies \
                            --delimiter '|' --no-header
```

### Flume ratings pipeline

When a rating is submitted through the web application, it is sent to Flume,
which should be configured to add the rating to a ratings dataset. To configure
this pipeline, first create a ratings dataset.

```
kite-dataset partition-config rated_at:year rated_at:month rated_at:day \
                              --schema src/main/avro/rating.avsc \
                              --output ymd.json
kite-dataset create ratings --schema src/main/avro/rating.avsc \
                            --partition-by ymd.json
```

Next, create a Flume configuration and restart Flume to use it

```
kite-dataset flume-config ratings --channel-type memory --agent agent \
                                  --output flume.properties
sudo cp flume.properties /etc/flume-ng/conf/flume.conf
sudo /etc/init.d/flume-ng-agent restart
```

[kite-cli]: http://kitesdk.org/docs/0.17.1/cli-reference.html

### Web application

You can build the web application using the following command.

```
mvn clean install
```

This will create an uberjar that can be run on a Hadoop cluster, like the
QuickStart VM, by running the following.

```
hadoop jar target/ratings-app-0.17.1-jar-with-runtime.jar
```

