package com.riferrei.kafka.connect.pulsar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.source.SourceRecord;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static com.riferrei.kafka.connect.pulsar.PulsarSourceConnectorConfig.*;

public class PulsarSourceTaskTest extends AbstractBasicTest {

    @Test
    public void taskVersionShouldMatch() {
        String version = PropertiesUtil.getConnectorVersion();
        assertEquals(version, new PulsarSourceTask().version());
    }

    @Test
    public void checkConnectionLifeCycleMgmt() {
        assertDoesNotThrow(() -> {
            Map<String, String> props = new HashMap<>();
            props.put(SERVICE_URL_CONFIG, getServiceUrl());
            props.put(SERVICE_HTTP_URL_CONFIG, getServiceHttpUrl());
            props.put(TOPIC_PATTERN, TOPIC_PATTERN_VALUE);
            PulsarSourceTask task = new PulsarSourceTask();
            try {
                task.start(props);
            } finally {
                task.stop();
            }
        });
    }

    @Test
    public void shouldReadEarlierMessages() {
        final int numMsgs = 5;
        assertDoesNotThrow(() -> {
            produceMessages(TOPIC[0], numMsgs);
        });
        Map<String, String> props = new HashMap<>();
        props.put(SERVICE_URL_CONFIG, getServiceUrl());
        props.put(SERVICE_HTTP_URL_CONFIG, getServiceHttpUrl());
        props.put(BATCH_MAX_NUM_MESSAGES_CONFIG, String.valueOf(numMsgs));
        props.put(SUBSCRIPTION_INITIAL_POSITION_CONFIG, SubscriptionInitialPosition.Earliest.name());
        props.put(TOPIC_NAMES, TOPIC[0]);
        PulsarSourceTask task = new PulsarSourceTask();
        assertDoesNotThrow(() -> {
            try {
                task.start(props);
                assertEquals(numMsgs, task.poll().size());
            } finally {
                task.stop();
            }
        });
    }

    @Test
    public void shouldReadLatestMessages() {
        final int numMsgs = 5;
        Map<String, String> props = new HashMap<>();
        props.put(SERVICE_URL_CONFIG, getServiceUrl());
        props.put(SERVICE_HTTP_URL_CONFIG, getServiceHttpUrl());
        props.put(BATCH_MAX_NUM_MESSAGES_CONFIG, String.valueOf(numMsgs));
        props.put(SUBSCRIPTION_INITIAL_POSITION_CONFIG, SubscriptionInitialPosition.Latest.name());
        props.put(TOPIC_NAMES, TOPIC[0]);
        PulsarSourceTask task = new PulsarSourceTask();
        assertDoesNotThrow(() -> {
            try {
                task.start(props);
                produceMessages(TOPIC[0], numMsgs);
                assertEquals(numMsgs, task.poll().size());
            } finally {
                task.stop();
            }
        });
    }

    @Test
    public void ensureTopicNameIsNameOnly() {
        Map<String, String> props = new HashMap<>();
        props.put(SERVICE_URL_CONFIG, getServiceUrl());
        props.put(SERVICE_HTTP_URL_CONFIG, getServiceHttpUrl());
        props.put(TOPIC_NAMING_STRATEGY_CONFIG, TopicNamingStrategyOptions.NameOnly.name());
        props.put(TOPIC_NAMES, TOPIC[0]);
        PulsarSourceTask task = new PulsarSourceTask();
        assertDoesNotThrow(() -> {
            try {
                task.start(props);
                produceMessages(TOPIC[0], 1);
                List<SourceRecord> records = task.poll();
                assertEquals(TOPIC[0], records.get(0).topic());
            } finally {
                task.stop();
            }
        });
    }

    @Test
    public void ensureTopicNameIsFullyQualified() {
        Map<String, String> props = new HashMap<>();
        props.put(SERVICE_URL_CONFIG, getServiceUrl());
        props.put(SERVICE_HTTP_URL_CONFIG, getServiceHttpUrl());
        props.put(TOPIC_NAMING_STRATEGY_CONFIG, TopicNamingStrategyOptions.FullyQualified.name());
        props.put(TOPIC_NAMES, TOPIC[0]);
        PulsarSourceTask task = new PulsarSourceTask();
        assertDoesNotThrow(() -> {
            try {
                task.start(props);
                produceMessages(TOPIC[0], 1);
                List<SourceRecord> records = task.poll();
                assertEquals(fullyQualifiedTopic(TOPIC[0]), records.get(0).topic());
            } finally {
                task.stop();
            }
        });
    }

    @Test
    public void checkReadFromMultipleTopicsUsingNames() {
        final int numMsgsPerTopic = 5;
        final int numMsgsTotal = 15;
        Map<String, String> props = new HashMap<>();
        props.put(SERVICE_URL_CONFIG, getServiceUrl());
        props.put(SERVICE_HTTP_URL_CONFIG, getServiceHttpUrl());
        props.put(BATCH_MAX_NUM_MESSAGES_CONFIG, String.valueOf(numMsgsTotal));
        props.put(TOPIC_NAMES, listToString(TOPIC[0], TOPIC[1], TOPIC[2]));
        PulsarSourceTask task = new PulsarSourceTask();
        assertDoesNotThrow(() -> {
            try {
                task.start(props);
                produceMessages(TOPIC[0], numMsgsPerTopic);
                produceMessages(TOPIC[1], numMsgsPerTopic);
                produceMessages(TOPIC[2], numMsgsPerTopic);
                assertEquals(numMsgsTotal, task.poll().size());
            } finally {
                task.stop();
            }
        });
    }

    @Test
    public void partitionedTopicShouldCreateOneTopic() {
        final String topic = "customPartitionedTopic";
        final int partitions = 4;
        final int numMsgs = 4;
        assertDoesNotThrow(() -> {
            createPartitionedTopic(topic, partitions);
        });
        Map<String, String> props = new HashMap<>();
        props.put(SERVICE_URL_CONFIG, getServiceUrl());
        props.put(SERVICE_HTTP_URL_CONFIG, getServiceHttpUrl());
        props.put(BATCH_MAX_NUM_MESSAGES_CONFIG, String.valueOf(numMsgs));
        props.put(TOPIC_NAMES, topic);
        PulsarSourceTask task = new PulsarSourceTask();
        assertDoesNotThrow(() -> {
            try {
                task.start(props);
                produceMessages(topic, numMsgs);
                List<SourceRecord> records = task.poll();
                assertEquals(numMsgs, records.size());
                for (SourceRecord record : records) {
                    assertEquals(topic, record.topic());
                }
            } finally {
                task.stop();
            }
        });
    }
    
}