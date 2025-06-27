package com.danby.happynode.search.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchRestHighLevelClient {

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    private static final String COLON = ":";
    private static final String HTTP = "http";

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        String address = elasticsearchProperties.getAddress();
        // 按冒号 ： 分隔
        String[] addressArr = address.split(COLON);
        // ip
        String host = addressArr[0];
        int port = Integer.parseInt(addressArr[1]);
        HttpHost httpHost = new HttpHost(host, port, HTTP);
        return new RestHighLevelClient(RestClient.builder(httpHost));


    }


}
