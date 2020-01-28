package de.rngcntr.janusbench.benchmark;

import java.util.Random;
import java.util.Date;
import java.util.ArrayList;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;

import de.rngcntr.janusbench.benchmark.*;

public class UpsertRandomEdgeBenchmark extends AbstractBenchmark {
    private Vertex[] a;
    private Vertex[] b;

    private Random rand;

    public UpsertRandomEdgeBenchmark(GraphTraversalSource g) {
        super(g);
    }

    public UpsertRandomEdgeBenchmark(GraphTraversalSource g, int stepSize) {
        super(g, stepSize);
    }

    public void buildUp() {
        // prepare edges to insert
        a = new Vertex[stepSize];
        b = new Vertex[stepSize];
        
        // get a list of all vertices to select from
        ArrayList<Vertex> allVertices = new ArrayList<Vertex>(g.V().toList());
        rand = new Random(System.nanoTime());

        for (int i = 0; i < stepSize; ++i) {
            // randomly choose an incoming vertex
            int selectedIndexA = rand.nextInt(allVertices.size());
            a[i] = allVertices.get(selectedIndexA);

            // one vertex less to sample from
            int selectedIndexB = rand.nextInt(allVertices.size() - 1);
            if (selectedIndexA == selectedIndexB) {
                // if the same index is selected, use another vertex instead
                selectedIndexB = allVertices.size() - 1;
            }
            b[i] = allVertices.get(selectedIndexB);
        }
    }

    public void performAction(BenchmarkResult result) {
        for (int index = 0; index < stepSize; ++index) {
            if (g.V(a[index]).in("knows").where(__.is(b[index])).hasNext()) {
                // edge already exists -> update
                Edge e = (Edge) g.V(a[index]).inE("knows").as("e").outV().where(__.is(b[index])).select("e").next();
                e.property("lastSeen", new Date());
            } else {
                // edge does not exist -> insert
                g.addE("knows").from(a[index]).to(b[index]).property("lastSeen", new Date()).next();
            }
        }
    }

    public void tearDown() {
    }
}
