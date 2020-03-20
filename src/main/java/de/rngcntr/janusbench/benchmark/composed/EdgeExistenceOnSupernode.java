package de.rngcntr.janusbench.benchmark.composed;

import de.rngcntr.janusbench.benchmark.simple.EdgeExistenceBenchmark;
import de.rngcntr.janusbench.benchmark.simple.InsertSupernodeVerticesBenchmark;
import de.rngcntr.janusbench.benchmark.simple.InsertVerticesBenchmark;
import de.rngcntr.janusbench.util.BenchmarkProperty;
import de.rngcntr.janusbench.util.BenchmarkProperty.Tracking;
import de.rngcntr.janusbench.util.ComposableBenchmark;
import de.rngcntr.janusbench.util.ComposedBenchmark;

import org.apache.tinkerpop.gremlin.structure.Vertex;

public class EdgeExistenceOnSupernode extends ComposableBenchmark {

    public int runs;
    public int stepsPerRun;
    public int edgesPerStep;
    public String approach;

    private EdgeExistenceBenchmark.Approach lookupApproach;

    public EdgeExistenceOnSupernode() {
        super();
    }

    public void buildUp() {
        // read the approach from the configuration
        this.lookupApproach = EdgeExistenceBenchmark.Approach.valueOf(approach);

        // prepare a vertex to later become a supernode
        final InsertVerticesBenchmark ivb = new InsertVerticesBenchmark(connection, 1);
        ivb.run();
        final Vertex supernode = connection.g().V().next();

        // create the inserter benchmark
        final InsertSupernodeVerticesBenchmark isvb =
            new InsertSupernodeVerticesBenchmark(connection, edgesPerStep, supernode);
        final BenchmarkProperty connectionsBefore =
            new BenchmarkProperty("connectionsBefore", () -> connection.g().V(supernode).outE().count().next());
        isvb.collectBenchmarkProperty(connectionsBefore, Tracking.BEFORE);
        final BenchmarkProperty connectionsAfter =
            new BenchmarkProperty("connectionsAfter", () -> connection.g().V(supernode).outE().count().next());
        isvb.collectBenchmarkProperty(connectionsAfter, Tracking.AFTER);

        // create edge existence checker
        final String supernodeName = (String) connection.g().V(supernode).values("name").next();
        final EdgeExistenceBenchmark<String> eeb = new EdgeExistenceBenchmark<String>(
            connection, supernode, "name", new String[] {supernodeName});
        eeb.useApproach(lookupApproach);
        final BenchmarkProperty connections =
            new BenchmarkProperty("connections", () -> connection.g().V(supernode).outE().count().next());
        eeb.collectBenchmarkProperty(connections, Tracking.AFTER);

        // compose benchmark
        final ComposedBenchmark cb = new ComposedBenchmark(connection, runs);
        cb.setCollectResults(false);
        cb.addComponent(isvb, stepsPerRun);
        cb.addComponent(eeb);

        addComponent(cb);
    }
}