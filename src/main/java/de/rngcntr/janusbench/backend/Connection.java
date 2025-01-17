package de.rngcntr.janusbench.backend;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.opencypher.gremlin.client.CypherGremlinClient;
import org.opencypher.gremlin.client.CypherResultSet;
import org.opencypher.gremlin.client.CypherStatement.Submittable;

/**
 * A Connection manages all interactions with a graph instance.
 *
 * @author Florian Grieskamp
 */
public class Connection {

    private int TIMEOUT_MS = 60000;
    private final String propertiesFileName;
    private PropertiesConfiguration conf;

    private GraphTraversalSource g;
    private Cluster cluster;
    private Client gremlinClient;
    private CypherGremlinClient cypherClient;
    private final UUID sessionUuid;

    private static final Logger log = Logger.getLogger(Connection.class);

    /**
     * Initializes a new Connection with the parameters given in the property file.
     *
     * @param propertiesFile The file to read the properties from.
     * This file is a TinkerPop <code>remote-graph.properties</code> file.
     */
    public Connection(final File propertiesFile) {
        this.propertiesFileName = propertiesFile.getAbsolutePath();
        sessionUuid = UUID.randomUUID();
    }

    /**
     * Sets a timeout after which a stable connection should be established.
     * @param timeoutMs The timeout in milliseconds.
     */
    public void setTimeout(int timeoutMs) { this.TIMEOUT_MS = timeoutMs; }

    /**
     * Opens a connection to the graph instance using the given settings.
     *
     * @throws ConfigurationException if the given settings are invalid or the connection can't be
     *     established within the time limit.
     * @see #setTimeout(int)
     */
    public void open() throws ConfigurationException {
        conf = new PropertiesConfiguration(propertiesFileName);

        try {
            cluster = Cluster.open(conf.getString("gremlin.remote.driver.clusterFile"));
            gremlinClient = cluster.connect(sessionUuid.toString(), false);
            gremlinClient.alias("g");
            g = traversal().withRemote(propertiesFileName);

            cypherClient = CypherGremlinClient.translating(gremlinClient);
        } catch (final Exception ex) {
            throw new ConfigurationException(ex);
        }

        // wait for connection to become stable
        boolean connected = false;
        final long maxTime = System.currentTimeMillis() + TIMEOUT_MS;
        while (!connected && System.currentTimeMillis() < maxTime) {
            try {
                connected = gremlinClient.submit("true").one().getBoolean();
            } catch (final RuntimeException rex) {
            }
        }

        if (!connected) {
            throw new ConfigurationException(
                String.format("Unable to reach cluster within %sms", TIMEOUT_MS));
        }
    }

    /**
     * Terminates the connection.
     *
     * @throws Exception if closing one of the connection components raises an Exception.
     */
    public void close() throws Exception {
        try {
            g.close();
            gremlinClient.close();
            cluster.close();
        } finally {
            g = null;
            gremlinClient = null;
            cluster = null;
        }
    }

    /**
     * Returns a GraphTraversalSource to perform queries on.
     *
     * @return A GraphTraversalSource which operates on the connected graph.
     */
    public GraphTraversalSource g() { return g; }

    /**
     * Synchronously sends a traversal to the graph database and blocks until the traversal is
     * executed completely.
     *
     * @param traversal The traversal in string representation.
     * @return The results of the query.
     */
    public ResultSet submit(final String traversal) {
        return awaitResults(gremlinClient.submit(traversal));
    }

    /**
     * Synchronously cypher query to the graph database and blocks until its execution is completed.
     *
     * @param traversal The cypher representation of the query.
     * @return The results of the query.
     */
    public CypherResultSet submitCypher(final String cypher) { return cypherClient.submit(cypher); }

    /**
     * Synchronously cypher query to the graph database and blocks until its execution is completed.
     *
     * @param traversal The cypher representation of the query.
     * @param parameters A map of values for parameterized queries.
     * @return The results of the query.
     */
    public CypherResultSet submitCypher(final String cypher, Map<String, Object> parameters) {
        Submittable s = cypherClient.statement(cypher);

        if (parameters != null) {
            for (Entry<String, Object> param : parameters.entrySet()) {
                s = s.addParameter(param.getKey(), param.getValue());
            }
        }

        return s.submit().join();
    }

    /**
     * Asynchronously sends a traversal to the graph database. A ResultSet is returned immediately
     * and will be populated with values once the query finishes.
     *
     * @param traversal The traversal in string representation.
     * @param parameters A map of values for parameterized queries.
     * @return The results of the query. These will not contain any data until the query has
     *     finished.
     * @throws TimeoutException if the traversal submission causes a {@link
     *     java.lang.RuntimeException}.
     */
    public ResultSet submitAsync(final String traversal, final Map<String, Object> parameters)
        throws TimeoutException {
        ResultSet results = null;

        try {
            results = gremlinClient.submit(traversal, parameters);
        } catch (RuntimeException rex) {
            log.warn("submitAsync failed");
            throw new TimeoutException("submitAsync failed");
        }

        return results;
    }

    /**
     * Synchronously sends a traversal to the graph database and blocks until the traversal is
     * executed completely.
     *
     * @param traversal The traversal in string representation.
     * @param parameters A map of values for parameterized queries.
     * @return The results of the query.
     */
    public ResultSet submit(final String traversal, final Map<String, Object> parameters) {
        return awaitResults(gremlinClient.submit(traversal, parameters));
    }

    /**
     * Synchronously sends a traversal to the graph database and blocks until the traversal is
     * executed completely.
     *
     * @param traversal The traversal to execute.
     * @return The results of the query.
     */
    public ResultSet submit(final GraphTraversal<?, ?> traversal) {
        return awaitResults(gremlinClient.submit(traversal));
    }

    /**
     * Synchronously waits for results to become available. This call blocks until all results are
     * available.
     *
     * @param rs The ResultSet to wait for.
     * @return The same ResultSet once all results are available.
     */
    public ResultSet awaitResults(final ResultSet rs) {
        while (!rs.allItemsAvailable()) {
            // TODO find a better solution than busy waiting
        }
        return rs;
    }
}
