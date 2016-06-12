/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.luminis;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import org.apache.flink.streaming.connectors.elasticsearch2.ElasticsearchSink;
import org.apache.flink.streaming.connectors.elasticsearch2.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch2.RequestIndexer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer082;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;
import org.apache.log4j.spi.LoggerFactory;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple example on how to read with a Kafka consumer
 *
 * Note that the Kafka source is expecting the following parameters to be setE
 *  - "bootstrap.servers" (comma separated list of kafka brokers)
 *  - "zookeeper.connect" (comma separated list of zookeeper servers)
 *  - "group.id" the id of the consumer group
 *  - "topic" the name of the topic to read data from.
 *
 * You can pass these required parameters using "--bootstrap.servers host:port,host1:port1 --zookeeper.connect host:port --topic testTopic"
 *
 * This is a valid input example:
 * 		--topic test --bootstrap.servers localhost:9092 --zookeeper.connect localhost:2181 --group.id myGroup
 *
 *
 */
public class ReadKafkaWriteElastic {

    public static void main(String[] args) throws Exception {
        // create execution environment
        Logger logger = org.slf4j.LoggerFactory.getLogger(ReadKafkaWriteElastic.class);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // parse user parameters
        ParameterTool parameterTool = ParameterTool.fromArgs(args);

        DataStream<String> messageStream = env.addSource(new FlinkKafkaConsumer082<>(parameterTool.getRequired("topic"), new SimpleStringSchema(), parameterTool.getProperties()));

        messageStream.map(new MapFunction<String, String>() {
            @Override
            public String map(String s) throws Exception {
                return s + "FlinkTransformation";
            }
        }).print();

        Map<String, String> config = new HashMap<>();
        config.put(ElasticsearchSink.CONFIG_KEY_BULK_FLUSH_MAX_ACTIONS, "1");
        config.put(ElasticsearchSink.CONFIG_KEY_BULK_FLUSH_INTERVAL_MS, "1");

        config.put("cluster.name", "FlinkDemo");

        List<InetSocketAddress> transports = new ArrayList<>();
        transports.add(new InetSocketAddress(InetAddress.getByName("localhost"), 9300));

        ElasticsearchSink<String> sinkFunction = new ElasticsearchSink<String>(config, transports, new TestElasticsearchSinkFunction());

        messageStream.addSink(sinkFunction);

        env.execute();
    }
    private static class TestElasticsearchSinkFunction implements ElasticsearchSinkFunction<String> {
        private static final long serialVersionUID = 1L;

        public IndexRequest createIndexRequest(String element) {
            Map<String, Object> json = new HashMap<>();
            json.put("data", element);

            IndexRequest indexRequest =  Requests.indexRequest()
                    .index("flink").type("demo").source(json);
            return indexRequest;
        }

        @Override
        public void process(String element, RuntimeContext ctx, RequestIndexer indexer) {
            indexer.add(createIndexRequest(element));
        }
    }

}
