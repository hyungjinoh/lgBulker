package com.rayful.lgbulker.config;

import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;


@Configuration
public class ElasticsearchConfig {

  @Bean
  public RestClient restClient() {
    return RestClient.builder(new HttpHost("122.199.202.101", 19200, "http"))
                     .setDefaultHeaders(new BasicHeader[]{
                             new BasicHeader("X-Elastic-Product", "Elasticsearch"),
                             new BasicHeader("Authorization", "Basic " + Base64.getEncoder()
                                                                               .encodeToString("elastic:elastic".getBytes()))
                     })
                     .build();
  }

}
