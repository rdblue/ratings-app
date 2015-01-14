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

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.api.RpcClient;
import org.apache.flume.api.RpcClientFactory;
import org.apache.flume.event.EventBuilder;

@ThreadSafe
public class FlumeClient {
  private final RpcClient client;

  public FlumeClient(String host, int port) {
    this.client = RpcClientFactory.getDefaultInstance(host, port);
  }

  /**
   * Sends an Avro record to Flume.
   *
   * @param record an Avro {@link IndexedRecord}
   * @param <E> the record's type
   */
  public synchronized <E extends IndexedRecord> void send(E record) {
    Map<String, String> headers = Maps.newHashMap();
    headers.put("flume.avro.schema.literal", record.getSchema().toString(false));
    try {
      client.append(EventBuilder.withBody(serialize(record), headers));
    } catch (EventDeliveryException e) {
      Throwables.propagate(e);
    }
  }

  private static <E extends IndexedRecord> byte[] serialize(E record) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Encoder encoder = EncoderFactory.get().binaryEncoder(buffer, null);
    DatumWriter<E> writer = newWriter(record);
    try {
      writer.write(record, encoder);
      encoder.flush();
    } catch (IOException e) {
      Throwables.propagate(e);
    }
    return buffer.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private static <E extends IndexedRecord> DatumWriter<E> newWriter(E e) {
    return (DatumWriter<E>) SpecificData.get().createDatumWriter(e.getSchema());
  }
}
