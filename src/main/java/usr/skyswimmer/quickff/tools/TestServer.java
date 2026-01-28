package usr.skyswimmer.quickff.tools;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.lambda.LambdaPushContext;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import usr.skyswimmer.quickff.config.ServerHostletEntity;
import usr.skyswimmer.quickff.connective.logger.Log4jManagerImpl;
import usr.skyswimmer.quickff.util.HashUtils;
import usr.skyswimmer.quickff.util.JsonUtils;

public class TestServer {

	private static Logger logger;
	public static File workingDir;
	public static File configFile;

	public static void main(String[] args) throws IOException {
		// Argument parsing
		if (args.length == 0) {
			System.err.println("Error: missing argument: configuration file");
			System.exit(1);
			return;
		}
		String configF = args[0];
		File configFile = new File(configF);
		if (!configFile.exists()) {
			System.err.println("Error: invalid argument: configuration file: file does not exist");
			System.exit(1);
			return;
		}
		workingDir = configFile.getParentFile();
		TestServer.configFile = configFile;

		// Logging
		logger = LogManager.getLogger("QuickFF");
		new Log4jManagerImpl().assignAsMain();

		// Load config
		logger.info("Loading configuration...");
		JsonObject config = JsonUtils.loadConfig(configFile);

		// Load working directory
		if (config.has("workingDirectory")) {
			String dir = config.get("workingDirectory").getAsString();
			File direct = new File(dir);
			if (direct.isAbsolute())
				direct = new File(workingDir, dir);
			if (!direct.exists() || !direct.isDirectory())
				throw new IOException(
						"Working directory does not exist or is not a directory: " + direct.getAbsolutePath());
			workingDir = direct;
		}

		// Create server
		logger.info("Loading server settings...");
		ServerHostletEntity host = new ServerHostletEntity();
		host.loadFromJson(config, "config");
		String secret = JsonUtils.getElementOrError("config", config, "secret").getAsString();

		// Adapter
		if (ConnectiveHttpServer.findAdapter(host.adapter.protocol) == null)
			throw new IOException("Protocol adapter unrecognized: " + host.adapter.protocol);

		// Create instance
		logger.info("Creating server instance...");
		ConnectiveHttpServer server = ConnectiveHttpServer.create(host.adapter.protocol, host.adapter.parameters);

		// Create server
		server.registerHandler("/", (LambdaPushContext req) -> {
			// Log
			logger.info("Received " + req.getRequestMethod() + " " + req.getRequestPath()
					+ handleRequestBody(req.getRequestBodyAsString(), req, secret));
		}, true, true, "POST", "DELETE", "GET", "PUT");

		// Start
		logger.info("Starting server...");
		server.start();

		// Wait for exit
		logger.info("Server started successfully!");
		server.waitForExit();

		// Done
		logger.info("Server closed.");
	}

	private static String handleRequestBody(String body, LambdaPushContext req, String secret) {
		String msg = "";
		if (req.hasHeader("Content-Type"))
			msg += " [" + req.getHeader("Content-Type") + "]";
		boolean hadHeader = false;
		for (String header : req.getHeaders().getHeaderNames()) {
			if (!header.equals("Content-Type") && !header.equals("Host") && !header.equals("Content-Length")
					&& !header.equals("User-Agent") && !header.equals("Accept")) {
				String value = req.getHeader(header);
				msg += "\n" + header + ": " + value;
				hadHeader = true;
			}
		}
		if (!body.isEmpty()) {
			try {
				msg += "\n\nHMAC: " + HashUtils.hmac256(body.getBytes("UTF-8"), secret);
			} catch (Exception e) {
			}
		}
		if (hadHeader)
			msg += "\n\n";
		if (!body.isEmpty() && body.startsWith("{")) {
			// Try json
			try {
				body = new Gson().newBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(body));
			} catch (Exception e) {
			}
		}
		if (!body.isEmpty())
			msg += ":\n" + body;
		return msg;
	}

}
