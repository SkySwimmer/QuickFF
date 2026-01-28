package usr.skyswimmer.quickff.functionality.roots.test;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.ContentSource;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.quicktools.api.context.AttachToContext;
import org.asf.quicktools.api.context.BaseContextComponent;
import org.asf.quicktools.api.context.IBaseContextReceiver;
import org.asf.quicktools.server.BaseControllerServer;

import usr.skyswimmer.quickff.functionality.TestContext;

@AttachToContext(TestContext.class)
public class TestRootComponent implements BaseContextComponent<TestContext> {

	private TestContext context;
	private ConnectiveHttpServer webserver;

	@Override
	public IBaseContextReceiver<TestContext> createInstance() {
		return new TestRootComponent();
	}

	@Override
	public void initServer(TestContext ctx, BaseControllerServer server) {
		// TODO Auto-generated method stub
		context = ctx;
		ctx = ctx;
	}

	@Override
	public void initWebserver(TestContext ctx, BaseControllerServer qFFS, ConnectiveHttpServer server) {
		// TODO Auto-generated method stub
		ctx = ctx;
		webserver = server;
	}

	@Override
	public ContentSource setupWebServer(TestContext ctx, BaseControllerServer qFFS, ConnectiveHttpServer server,
			ContentSource parent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setupWebServerHandlers(TestContext ctx, BaseControllerServer qFFS, ConnectiveHttpServer server,
			HttpHandlerSet handlerSet) {
		// TODO Auto-generated method stub
		ctx = ctx;
	}

	@Override
	public void startServer(TestContext ctx, BaseControllerServer server) {
		// TODO Auto-generated method stub
		ctx = ctx;
	}

	@Override
	public void startWebserver(TestContext ctx, BaseControllerServer qFFS, ConnectiveHttpServer server) {
		// TODO Auto-generated method stub
		ctx = ctx;
	}

	@Override
	public void stopServer(TestContext ctx, BaseControllerServer server) {
		// TODO Auto-generated method stub
		ctx = ctx;
	}

	@Override
	public void stopWebserver(TestContext ctx, BaseControllerServer qFFS, ConnectiveHttpServer server) {
		// TODO Auto-generated method stub
		ctx = ctx;
	}

	@Override
	public void onDestroy(TestContext ctx, BaseControllerServer server) {
		ctx = ctx;
	}

}
