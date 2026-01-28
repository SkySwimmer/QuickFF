package usr.skyswimmer.quickff.functionality;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.BaseControllerServer;

public class BaseGithubWebhookContext implements BaseContext {

	private BaseControllerServer instance;
	private ConnectiveHttpServer webserver;

	@Override
	public BaseContext createInstance() {
		return new BaseGithubWebhookContext();
	}

	@Override
	public String getName() {
		return "github-webhooks";
	}

	@Override
	public void initServer(BaseControllerServer server) {
		instance = server;
	}

	@Override
	public void initWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
		webserver = server;
	}

}
