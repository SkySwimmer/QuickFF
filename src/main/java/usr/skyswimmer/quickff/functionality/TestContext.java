package usr.skyswimmer.quickff.functionality;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.connective.lambda.LambdaPushContext;
import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.BaseControllerServer;

public class TestContext implements BaseContext {

	private BaseControllerServer instance;
	private ConnectiveHttpServer webserver;

	@Override
	public BaseContext createInstance() {
		return new TestContext();
	}

	@Override
	public String getName() {
		return "testserver";
	}

	@Override
	public void initServer(BaseControllerServer server) {
		instance = server;
	}

	@Override
	public void initWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
		webserver = server;
	}

	@Override
	public void setupWebServerHandlers(BaseControllerServer qFFS, ConnectiveHttpServer server,
			HttpHandlerSet handlerSet) {
		handlerSet.registerHandler("/", (LambdaPushContext req) -> {
			// Log
			instance.getLogger().info("Received " + req.getRequestMethod() + " " + req.getRequestPath()
					+ handleRequestBody(req.getRequestBodyAsString(), req));
		}, true, true, "POST", "DELETE", "GET", "PUT");
	}

	private String handleRequestBody(String body, LambdaPushContext req) {
		String msg = "";
		if (req.hasHeader("Content-Type"))
			msg += " [" + req.getHeader("Content-Type") + "]";
		if (!body.isEmpty())
			msg += ":\n" + body;
		return msg;
	}

}
