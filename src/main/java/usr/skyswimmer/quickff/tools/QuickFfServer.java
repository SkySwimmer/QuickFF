package usr.skyswimmer.quickff.tools;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import usr.skyswimmer.githubwebhooks.api.server.GithubWebhookEventServer;

public class QuickFfServer {

	private static Logger logger;

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

		// Create instance
		logger = LogManager.getLogger("quickff");
		GithubWebhookEventServer server = new GithubWebhookEventServer(configFile, "quickff");

		// Handler
		server.onWebhookActivate().addEventHandler(event -> {
			// Received event

			// Check event
			if (event.getEvent().equals("push")) {
				// Handle
				event = event;
			}
		});

		// Init
		server.initServer();

		// Start
		server.start();

		// Wait
		server.waitForExit();

		// Done
		logger.info("Server closed.");
	}
}
