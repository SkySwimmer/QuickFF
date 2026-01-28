package usr.skyswimmer.quickff.functionality;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.BaseControllerServer;

public class TestContext2 implements BaseContext {

	private BaseControllerServer instance;
	private ConnectiveHttpServer webserver;

	@Override
	public BaseContext createInstance() {
		return new TestContext2();
	}

	@Override
	public String getName() {
		return "maintests2";
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

}
