package usr.skyswimmer.quickff.functionality;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.ContentSource;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.connective.lambda.LambdaRequestContext;
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
		return "maintests";
	}

	@Override
	public void initServer(BaseControllerServer server) {
		// TODO Auto-generated method stub
		instance = server;
		server = server;
	}

	@Override
	public void initWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
		// TODO Auto-generated method stub
		webserver = server;
		server = server;
	}

	@Override
	public void startServer(BaseControllerServer server) {
		// TODO Auto-generated method stub
		server = server;
	}

	@Override
	public void startWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
		// TODO Auto-generated method stub
		server = server;
	}

	@Override
	public void stopServer(BaseControllerServer server) {
		// TODO Auto-generated method stub
		server = server;
	}

	@Override
	public void stopWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
		// TODO Auto-generated method stub
		server = server;
	}

	@Override
	public ContentSource setupWebServer(BaseControllerServer qFFS, ConnectiveHttpServer server, ContentSource parent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setupWebServerHandlers(BaseControllerServer qFFS, ConnectiveHttpServer server,
			HttpHandlerSet handlerSet) {
		handlerSet.registerHandler("/tester", (LambdaRequestContext ctx) -> {
			ctx.setResponseContent("Hello World Tester");
		});
		handlerSet.registerHandler("/abctest2", (LambdaRequestContext ctx) -> {
			ctx.setResponseContent("Hello World Overridden");
		});
	}

}
