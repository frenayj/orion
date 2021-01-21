package com.pinterest.orion.core.automation.operator.memq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.pinterest.orion.core.Attribute;
import com.pinterest.orion.core.actions.memq.MemqTopicCreationAction;
import com.pinterest.orion.core.automation.sensor.memq.MemqClusterSensor;
import com.pinterest.orion.core.automation.sensor.memq.MemqTopicRepoSensor;
import com.pinterest.orion.core.automation.sensor.memq.TopicConfig;
import com.pinterest.orion.core.memq.MemqCluster;

public class MemqTopicOperator extends MemqOperator {

  @Override
  public String getName() {
    return "Topic Operator";
  }

  @Override
  public void operate(MemqCluster cluster) throws Exception {
    Attribute targetTopicConfigsAttribute = cluster
        .getAttribute(MemqTopicRepoSensor.TARGET_TOPIC_CONFIGS);
    if (targetTopicConfigsAttribute == null) {
      return;
    }

    Attribute topicConfigsAttribute = cluster.getAttribute(MemqClusterSensor.TOPIC_CONFIG);
    if (topicConfigsAttribute == null) {
      return;
    }

    Map<String, TopicConfig> targetTopicConfigs = targetTopicConfigsAttribute.getValue();
    Map<String, TopicConfig> topicConfigs = topicConfigsAttribute.getValue();

    SetView<String> newTopics = Sets.difference(targetTopicConfigs.keySet(), topicConfigs.keySet());
    List<TopicConfig> topics = new ArrayList<>();
    for (String topicName : newTopics) {
      topics.add(targetTopicConfigs.get(topicName));
    }
    Collections.sort(topics);
    
    for (TopicConfig topicConfig : topics) {
      MemqTopicCreationAction action = new MemqTopicCreationAction();
      action.setAttribute(MemqTopicCreationAction.ATTR_TOPIC_CONFIG_KEY, topicConfig);
      action.setAttribute(MemqTopicCreationAction.ATTR_TOPIC_NAME_KEY, topicConfig.getTopic());
      dispatch(action);
      return;
    }
  }

}
