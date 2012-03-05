package org.apache.ivory.resource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletInputStream;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.ivory.cluster.util.EmbeddedCluster;
import org.apache.ivory.entity.store.ConfigurationStore;
import org.apache.ivory.entity.v0.EntityType;
import org.apache.ivory.entity.v0.cluster.Cluster;
import org.apache.ivory.entity.v0.feed.Feed;
import org.apache.ivory.util.EmbeddedServer;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class AbstractTestBase {
    protected static final String FEED_TEMPLATE1 = "/feed-template1.xml";
    protected static final String FEED_TEMPLATE2 = "/feed-template2.xml";
    protected static final String CLUSTER_FILE_TEMPLATE = "target/cluster-template.xml";

    protected static final String SAMPLE_PROCESS_XML = "/process-version-0.xml";
    protected static final String PROCESS_TEMPLATE = "/process-template.xml";

    protected static final String BASE_URL = "http://localhost:15000/";

    protected EmbeddedServer server;

    protected Unmarshaller unmarshaller;
    protected Marshaller marshaller;

    protected EmbeddedCluster cluster;
    protected WebResource service = null;

    private static final Pattern varPattern = Pattern.compile("##[A-Za-z0-9_]*##");

    public AbstractTestBase() {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(APIResult.class, Feed.class, Process.class, Cluster.class, ProcessInstancesResult.class);
            unmarshaller = jaxbContext.createUnmarshaller();
            marshaller = jaxbContext.createMarshaller();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeClass
    public void configure() throws Exception {
        if (new File("webapp/src/main/webapp").exists()) {
            this.server = new EmbeddedServer(15000, "webapp/src/main/webapp");
        } else if (new File("src/main/webapp").exists()) {
            this.server = new EmbeddedServer(15000, "src/main/webapp");
        } else {
            throw new RuntimeException("Cannot run jersey tests");
        }
        ClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);
        this.service = client.resource(UriBuilder.fromUri(BASE_URL).build());
        this.server.start();
        
        this.cluster = EmbeddedCluster.newCluster("##name##", false);
        Cluster clusterEntity = this.cluster.getCluster();
        FileOutputStream out = new FileOutputStream(CLUSTER_FILE_TEMPLATE);
        marshaller.marshal(clusterEntity, out);
        out.close();

        ClientResponse clientRepsonse;
        Map<String, String> overlay = new HashMap<String, String>();

        String testCluster = "testCluster";
        overlay.put("name", testCluster);
        InputStream testClusterStream = getServletInputStream(overlayParametersOverTemplate(CLUSTER_FILE_TEMPLATE, overlay));
        clientRepsonse = this.service.path("api/entities/submit/cluster").accept(MediaType.TEXT_XML).type(MediaType.TEXT_XML)
                .header("Remote-User", "testuser").post(ClientResponse.class, testClusterStream);

        String backupCluster = "backupCluster";
        overlay.put("name", backupCluster);
        InputStream backupClusterStream = getServletInputStream(overlayParametersOverTemplate(CLUSTER_FILE_TEMPLATE, overlay));
        clientRepsonse = this.service.path("api/entities/submit/cluster").accept(MediaType.TEXT_XML).type(MediaType.TEXT_XML)
                .header("Remote-User", "testuser").post(ClientResponse.class, backupClusterStream);
        
        //setup dependent workflow and lipath in hdfs
        FileSystem fs = FileSystem.get(this.cluster.getConf());
        fs.mkdirs(new Path("/examples/apps/aggregator"));
        fs.mkdirs(new Path("/examples/apps/aggregator/lib"));
    }

    /**
     * Converts a InputStream into ServletInputStream
     * 
     * @param fileName
     * @return ServletInputStream
     * @throws java.io.IOException
     */
    protected ServletInputStream getServletInputStream(String fileName) throws IOException {
        return getServletInputStream(new FileInputStream(fileName));
    }

    protected ServletInputStream getServletInputStream(final InputStream stream) throws IOException {
        return new ServletInputStream() {

            @Override
            public int read() throws IOException {
                return stream.read();
            }
        };
    }

    @AfterClass
    public void tearDown() throws Exception {
        ConfigurationStore.get().remove(EntityType.PROCESS, "testCluster");
        ConfigurationStore.get().remove(EntityType.PROCESS, "backupCluster");
//        this.cluster.shutdown();
        server.stop();
    }

    @BeforeTest
    public void cleanupStore() throws Exception {
        ConfigurationStore.get().remove(EntityType.PROCESS, "aggregator-coord");
    }

    protected ClientResponse submitToIvory(String template, Map<String, String> overlay, EntityType entityType) throws IOException {
        String tmpFile = overlayParametersOverTemplate(template, overlay);
        return submitFileToIvory(entityType, tmpFile);
    }

    private ClientResponse submitFileToIvory(EntityType entityType, String tmpFile) throws IOException {

        ServletInputStream rawlogStream = getServletInputStream(tmpFile);

        return this.service.path("api/entities/submit/" + entityType.name().toLowerCase()).header("Remote-User", "testuser")
                .accept(MediaType.TEXT_XML).type(MediaType.TEXT_XML).post(ClientResponse.class, rawlogStream);
    }

    protected void checkIfSuccessful(ClientResponse clientRepsonse) {
        String response = clientRepsonse.getEntity(String.class);
        try {
            APIResult result = (APIResult)unmarshaller.
                    unmarshal(new StringReader(response));
            Assert.assertEquals(result.getStatus(), APIResult.Status.SUCCEEDED);
        } catch (JAXBException e) {
            Assert.fail("Reponse " + response + " is not valid");
        }
    }

    protected String overlayParametersOverTemplate(String template, Map<String, String> overlay) throws IOException {
        File target = new File("webapp/target");
        if (!target.exists()) {
            target = new File("target");
        }

        File tmpFile = File.createTempFile("test", ".xml", target);
        OutputStream out = new FileOutputStream(tmpFile);

        InputStreamReader in;
        if (getClass().getResourceAsStream(template) == null) {
            in = new FileReader(template);
        } else {
            in = new InputStreamReader(getClass().getResourceAsStream(template));
        }
        BufferedReader reader = new BufferedReader(in);
        String line;
        while ((line = reader.readLine()) != null) {
            Matcher matcher = varPattern.matcher(line);
            while (matcher.find()) {
                String variable = line.substring(matcher.start(), matcher.end());
                line = line.replace(variable, overlay.get(variable.substring(2, variable.length() - 2)));
                matcher = varPattern.matcher(line);
            }
            out.write(line.getBytes());
            out.write("\n".getBytes());
        }
        reader.close();
        out.close();
        return tmpFile.getAbsolutePath();
    }
}