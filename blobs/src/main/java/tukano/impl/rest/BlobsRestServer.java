package tukano.impl.rest;

import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import utils.Args;
import utils.IP;


public class BlobsRestServer {
	final private static Logger Log = Logger.getLogger(BlobsRestServer.class.getName());

	static final String INETADDR_ANY = "0.0.0.0";
	static String SERVER_BASE_URI = "http://%s:%s/rest";

	public static final int PORT = 8070;

	public static String serverURI;

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	protected BlobsRestServer() {
		serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
	}


	protected void start() throws Exception {

		ResourceConfig config = new ResourceConfig();
		config.register(RestBlobsResource.class);
		JdkHttpServerFactory.createHttpServer( URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);

		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}


	public static void main(String[] args) throws Exception {
		Args.use(args);
		//Token.setSecret( Args.valueOf("-secret", ""));
		new BlobsRestServer().start();
	}
}