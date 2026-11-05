package org.ulpgc.tarantino.bench.broker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerToolTest {

    @TempDir
    Path run;

    @Test
    void summarisesOneP50AndOneThroughputPerTrialOfEachConfiguration() throws IOException {
        Files.write(run.resolve(BrokerTool.FILE_NAME), List.of(BrokerTool.HEADER,
                "hazelcast,1,1,30000,300,500,900,20000.0",
                "hazelcast,1,2,30000,500,700,950,30000.0",
                "activemq-persistent,8,1,30000,2000,4000,6000,5000.0"));

        BrokerTool.run(List.of(run.toString()));

        String summary = Files.readString(run.resolve("summary.csv"));
        assertTrue(summary.contains("broker_latency,\"broker=hazelcast,consumers=1\",400.00"), summary);
        assertTrue(summary.contains("broker_throughput,\"broker=hazelcast,consumers=1\",25000.00"), summary);
        assertTrue(summary.contains("broker_latency,\"broker=activemq-persistent,consumers=8\",2000.00"), summary);
    }
}
