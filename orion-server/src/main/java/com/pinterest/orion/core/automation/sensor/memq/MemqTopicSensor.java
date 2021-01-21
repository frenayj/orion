package com.pinterest.orion.core.automation.sensor.memq;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import com.pinterest.orion.core.Attribute;
import com.pinterest.orion.core.PluginConfigurationException;
import com.pinterest.orion.core.automation.sensor.kafka.KafkaTopicSensor;
import com.pinterest.orion.core.kafka.KafkaCluster;
import com.pinterest.orion.core.kafka.KafkaTopicDescription;
import com.pinterest.orion.core.memq.MemqCluster;

public class MemqTopicSensor extends MemqSensor {

  public static final String NOTIFICATION_SERVERSET = "notificationServerset";
  public static final String NOTIFICATION_TOPIC = "notificationTopic";
  public static final String TOPICINFO = "topicinfo";

  @Override
  public String getName() {
    return "Topic Sensor";
  }

  @Override
  public void sense(MemqCluster cluster) throws Exception {
    Attribute attribute = cluster.getAttribute(MemqClusterSensor.TOPIC_CONFIG);
    if (attribute == null || attribute.getValue() == null) {
      // if there is no topic info data we can't really get any additional information
      // from the cluster
      return;
    }

    Map<String, AdminClient> readClusterClientMap = cluster.getReadClusterClientMap();
    Map<String, TopicConfig> topicConfigs = attribute.getValue();

    Map<String, MemqTopicDescription> topicInfo = new HashMap<>();
    Map<String, Map<String, KafkaTopicDescription>> topicDescMap = new HashMap<>();
    for (Entry<String, TopicConfig> entry : topicConfigs.entrySet()) {
      TopicConfig topicConfig = entry.getValue();
      String topic = topicConfig.getTopic();
      Properties outputHandlerConfig = topicConfig.getOutputHandlerConfig();
      String serversetFile = outputHandlerConfig.getProperty(NOTIFICATION_SERVERSET);
      Map<String, KafkaTopicDescription> topicDescriptionMap = topicDescMap.get(serversetFile);
      if (topicDescriptionMap == null) {
        topicDescriptionMap = new HashMap<>();
        topicDescMap.put(serversetFile, topicDescriptionMap);
        AdminClient adminClient = readClusterClientMap.get(serversetFile);
        if (adminClient == null) {
          adminClient = initializeAdminClient(serversetFile);
          readClusterClientMap.put(serversetFile, adminClient);
        }
        topicDescriptionMap.putAll(KafkaCluster.getTopicDescriptions(adminClient, logger,
            topicDescriptionMap, cluster.getClusterId()));
        KafkaTopicSensor.populateTopicConfigInfo(adminClient, topicDescriptionMap);
      }
      MemqTopicDescription desc = new MemqTopicDescription();
      desc.setConfig(topicConfig);
      topicInfo.put(topic, desc);
      String notificationTopic = outputHandlerConfig.getProperty(NOTIFICATION_TOPIC);
      KafkaTopicDescription kafkaTopicDescription = topicDescriptionMap.get(notificationTopic);
      desc.setReadAssignments(kafkaTopicDescription);
      topicInfo.put(topic, desc);
    }

    attribute = cluster.getAttribute(MemqClusterSensor.WRITE_ASSIGNMENTS);
    if (attribute != null) {
      Map<String, List<String>> writeBrokerAssignments = attribute.getValue();
      for (Entry<String, List<String>> entry : writeBrokerAssignments.entrySet()) {
        String topic = entry.getKey();
        MemqTopicDescription memqTopicDescription = topicInfo.get(topic);
        memqTopicDescription.setWriteAssignments(entry.getValue());
      }
    }

    setAttribute(cluster, TOPICINFO, topicInfo);
  }

  public static AdminClient initializeAdminClient(String serversetFile) throws PluginConfigurationException,
                                                                  IOException {
    AdminClient adminClient;
    String currentBootstrapServers = Files.readAllLines(new File(serversetFile).toPath()).get(0);
    Properties props = new Properties();
    props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, currentBootstrapServers);
    adminClient = AdminClient.create(props);
    return adminClient;
  }

}
