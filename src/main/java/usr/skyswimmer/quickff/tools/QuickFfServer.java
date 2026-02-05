package usr.skyswimmer.quickff.tools;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.connective.lambda.LambdaPushContext;

import com.google.gson.JsonObject;

import usr.skyswimmer.githubwebhooks.apps.GithubApp;
import usr.skyswimmer.githubwebhooks.server.GithubWebhookEventServer;
import usr.skyswimmer.quickff.tools.entities.WebhookPushEventEntity;
import usr.skyswimmer.quickff.tools.quickff.QuickFfRunner;
import usr.skyswimmer.quicktoolsutils.tasks.async.AsyncTaskManager;

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
		GithubWebhookEventServer server = new GithubWebhookEventServer(configFile, "quickff");
		logger = LogManager.getLogger("quickff");

		// Handler
		server.onWebhookActivate().addEventHandler(event -> {
			// Received event

			// Check event
			if (event.getEvent().equals("push")) {
				// Get fields
				LambdaPushContext req = event.handler();
				GithubApp app = event.getApp();
				JsonObject hookData = event.getEventData();
				if (app == null) {
					// Error
					logger.error("Could not run QuickFF webhook " + event.handler().getRequestPath()
							+ ": webhook was not configured with a GitHub app! Please add a 'app' statement to the webhook block with an app name, and create an app block under 'apps' in the configuration file for the app definition");
					req.setResponseStatus(500, "Internal Server Error");
					return;
				}

				// Check if installation is present
				if (!hookData.has("installation")) {
					logger.error("Webhook request " + req.getRequestMethod() + " " + req.getRequestPath()
							+ " used a malformed json for PUSH event: missing installation information");
					req.setResponseStatus(400, "Bad request");
					return;
				}

				// Read entity
				WebhookPushEventEntity push = new WebhookPushEventEntity();
				try {
					push.loadFromJson(hookData, "");
				} catch (IOException e) {
					logger.error("Webhook request " + req.getRequestMethod() + " " + req.getRequestPath()
							+ " used a malformed json for PUSH event: error thrown during parsing", e);
					req.setResponseStatus(400, "Bad request");
					return;
				}

				// Check if branch
				if (push.ref.startsWith("refs/heads/")) {
					// Get branch
					String targetBranch = push.ref.substring("refs/heads/".length());

					// Success, call quickff runner
					AsyncTaskManager.runAsync(() -> {
						QuickFfRunner.downloadAndRun(server.getWorkingDir(), targetBranch, push, app);
					});
				}
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
