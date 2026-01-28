package usr.skyswimmer.quickff.tools;

import java.io.File;
import java.io.IOException;

import org.asf.cyan.fluid.bytecode.FluidClassPool;
import org.asf.quicktools.server.BaseControllerServer;

import usr.skyswimmer.quickff.functionality.TestContext;
import usr.skyswimmer.quickff.functionality.BaseGithubWebhookContext;

public class TestServer {

	public static void main(String[] args) throws IOException {
		// Argument parsing
		if (args.length == 0) {
			System.err.println("Error: missing argument: configuration file");
			System.exit(1);
			return;
		}
		String config = args[0];
		File configFile = new File(config);
		if (!configFile.exists()) {
			System.err.println("Error: invalid argument: configuration file: file does not exist");
			System.exit(1);
			return;
		}

		// Create server
		FluidClassPool pool = FluidClassPool.create();
		BaseControllerServer server = new BaseControllerServer(configFile, pool, "quickff");
		server.addContext(new TestContext());
		server.addContext(new BaseGithubWebhookContext());

		// Import
		pool.importAllSources();

		// Init
		server.initServer();

		// Run
		server.start();

		// Wait
		server.waitForExit();
	}

}
