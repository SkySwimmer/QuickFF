package org.asf.quicktools.server;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.ContentSource;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.cyan.fluid.bytecode.FluidClassPool;
import org.asf.quicktools.api.connective.ConnectiveTypesRegistry;
import org.asf.quicktools.api.context.AddContextComponent;
import org.asf.quicktools.api.context.AddContextComponents;
import org.asf.quicktools.api.context.AddRootComponent;
import org.asf.quicktools.api.context.AddRootComponents;
import org.asf.quicktools.api.context.AttachToContext;
import org.asf.quicktools.api.context.AttachToContextComponent;
import org.asf.quicktools.api.context.AttachToContextComponents;
import org.asf.quicktools.api.context.AttachToContexts;
import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.api.context.BaseContextComponent;
import org.asf.quicktools.api.context.IBaseContextReceiver;
import org.asf.quicktools.api.events.ContextComponentInitEvent;
import org.asf.quicktools.api.events.ContextComponentInstanceCreatedEvent;
import org.asf.quicktools.api.events.ContextComponentPreInitEvent;
import org.asf.quicktools.api.events.ContextInitEvent;
import org.asf.quicktools.api.events.ContextInstanceCreatedEvent;
import org.asf.quicktools.api.events.ContextPreInitEvent;
import org.asf.quicktools.api.events.StartServerEvent;
import org.asf.quicktools.api.events.StartWebserverEvent;
import org.asf.quicktools.api.events.StopServerEvent;
import org.asf.quicktools.api.events.StopWebserverEvent;
import org.asf.quicktools.connective.logger.Log4jManagerImpl;
import org.asf.quicktools.server.config.ServerHostletEntity;
import org.asf.quicktools.server.config.VhostHostletEntity;
import org.asf.quicktools.server.vhost.VhostDefinition;
import org.asf.quicktools.server.vhost.VhostList;
import org.asf.quicktools.server.vhost.VhostsContentSource;
import org.asf.quicktools.util.JsonUtils;
import org.asf.quicktools.util.events.EventBus;
import org.asf.quicktools.util.events.IEventReceiver;
import org.asf.quicktools.util.scanning.ClassScanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class BaseControllerServer {

	private static boolean debugMode = false;
	static {
		// Setup logging
		if (System.getProperty("debugMode") != null) {
			System.setProperty("log4j2.configurationFile",
					BaseControllerServer.class.getResource("/log4j2-ide.xml").toString());
			debugMode = true;
		} else {
			System.setProperty("log4j2.configurationFile",
					BaseControllerServer.class.getResource("/log4j2.xml").toString());
		}
		new Log4jManagerImpl().assignAsMain();
	}

	private Logger logger;
	private File configFile;
	private File workingDir;
	private boolean running;

	private ConnectiveHttpServer[] servers;
	private FluidClassPool pool;
	private ClassScanner scanner;

	private ArrayList<BaseContext> contexts = new ArrayList<BaseContext>();
	private HashMap<ConnectiveHttpServer, ArrayList<BaseContext>> contextsPerServer = new HashMap<ConnectiveHttpServer, ArrayList<BaseContext>>();
	private HashMap<BaseContext, ConnectiveHttpServer> contextsParentServers = new HashMap<BaseContext, ConnectiveHttpServer>();

	private HashMap<BaseContext, ArrayList<BaseContextComponent<?>>> contextRootComponents = new HashMap<BaseContext, ArrayList<BaseContextComponent<?>>>();
	private HashMap<IBaseContextReceiver<?>, BaseContext> contextRootComponentParents = new HashMap<IBaseContextReceiver<?>, BaseContext>();

	private HashMap<IBaseContextReceiver<?>, IBaseContextReceiver<?>> contextComponentParents = new HashMap<IBaseContextReceiver<?>, IBaseContextReceiver<?>>();
	private HashMap<IBaseContextReceiver<?>, ArrayList<IBaseContextReceiver<?>>> contextComponentChildren = new HashMap<IBaseContextReceiver<?>, ArrayList<IBaseContextReceiver<?>>>();

	/**
	 * Creates the server instance
	 * 
	 * @param configFile Configuration file
	 * @param pool       Class pool, <b>warning: closes after initialization to
	 *                   conserve memory</b>
	 * @param scope      Scope name
	 */
	public BaseControllerServer(File configFile, FluidClassPool pool, String scope) {
		this.configFile = configFile;
		this.workingDir = configFile.getParentFile();
		this.pool = pool;
		logger = LogManager.getLogger(scope);
	}

	/**
	 * Retrieves the logger instance used by the server
	 * 
	 * @return Logger instance
	 */
	public Logger getLogger() {
		return logger;
	}

	/**
	 * Initializes the server
	 * 
	 * @throws IOException If initialization fails
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void initServer() throws IOException {
		// Load configuration
		scanner = new ClassScanner(getClass().getClassLoader(), pool);
		logger.info("Loading configuration...");
		JsonObject config = loadConfig();

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

		// Initialize types
		logger.info("Initializing types...");
		ConnectiveTypesRegistry.init(pool);

		// Initialize contexts
		logger.info("Loading contexts...");
		for (BaseContext ctx : contexts) {
			// Add to event system
			EventBus.getInstance().addAllEventsFromReceiver(ctx);
			EventBus.getInstance().dispatchEvent(new ContextPreInitEvent(ctx, this));

			// Create hierarchy
			createContextHierarchy(ctx, pool, scanner);
		}

		// Initialize contexts
		logger.info("Initializing contexts...");
		for (BaseContext ctx : contexts) {
			// Create hierarchy
			logger.info("Initializing context: " + ctx.getName() + "...");
			EventBus.getInstance().dispatchEvent(new ContextInitEvent(ctx, this));
			ctx.initServerBaseContext(this);
			ctx.initServer(this);

			// Call for all
			logger.info("Initializing context components: " + ctx.getName() + "...");
			for (BaseContextComponent comp : getRootComponents(ctx, BaseContextComponent.class)) {
				forAllComponents(IBaseContextReceiver.class, comp, c -> {
					logger.info("Initializing context component: " + c.getClass().getTypeName() + "...");
					if (c instanceof BaseContextComponent)
						EventBus.getInstance()
								.dispatchEvent(new ContextComponentInitEvent((BaseContextComponent<?>) c, ctx, this));
					c.initServerBaseContext(ctx, this);
					c.initServer(ctx, this);
				});
			}
		}

		// Set up server
		logger.info("Preparing server instances...");
		ArrayList<ConnectiveHttpServer> servers = new ArrayList<ConnectiveHttpServer>();

		// Load vhosts
		HashMap<String, VhostList> lists = new LinkedHashMap<String, VhostList>();
		if (config.has("vhostSets")) {
			logger.info("Reading vhost definitions...");
			JsonObject vhostsConfig = config.get("vhostSets").getAsJsonObject();
			for (String key : vhostsConfig.keySet()) {
				// Load configuration
				logger.info("Reading configuration for " + key + "...");
				JsonObject vhostList = vhostsConfig.get(key).getAsJsonObject();
				String defaultHost = null;
				if (vhostList.has("default")) {
					defaultHost = vhostList.get("default").getAsString();
				}
				VhostDefinition defaultVhost = null;
				HashMap<String, VhostDefinition> vhosts = new LinkedHashMap<String, VhostDefinition>();
				JsonObject hosts = JsonUtils.getElementOrError("vhost list -> " + key, vhostList, "vhosts")
						.getAsJsonObject();
				VhostList list = new VhostList();
				for (String host : hosts.keySet()) {
					JsonObject hostEnt = hosts.get(host).getAsJsonObject();
					VhostHostletEntity entity = new VhostHostletEntity();
					logger.info("Defining vhost: " + host + "...");
					entity.loadFromJson(hostEnt, "vhost list -> " + key + " -> " + host);
					if (entity.domains.isEmpty())
						entity.domains.add(host);
					if (entity.handlerSetType != null) {
						if (!ConnectiveTypesRegistry.hasHandlerSetType(entity.handlerSetType))
							throw new IOException("Handler set type unrecognized: " + entity.handlerSetType);
					}
					if (entity.contentSource != null) {
						if (!ConnectiveTypesRegistry.hasContentSourceType(entity.contentSource))
							throw new IOException("Content source type unrecognized: " + entity.contentSource);
					}
					VhostDefinition def = new VhostDefinition();
					def.config = entity;
					def.hostEntryName = host;
					if (host.equals(defaultHost))
						defaultVhost = def;
					vhosts.put(host, def);
					for (String domain : entity.domains) {
						if (!list.vhostsByDomain.containsKey(domain)) {
							list.vhostsByDomain.put(domain, def);
						}
					}
				}
				list.defaultDef = defaultVhost;
				list.defs = vhosts.values().toArray(t -> new VhostDefinition[t]);
				lists.put(key, list);
			}
		}

		// Get configuration
		JsonObject webserversConfig = JsonUtils.getElementOrError("config", config, "webserver").getAsJsonObject();
		logger.info("Reading server definitions...");
		for (String key : webserversConfig.keySet()) {
			// Load configuration
			logger.info("Reading configuration for " + key + "...");
			JsonObject webserver = webserversConfig.get(key).getAsJsonObject();
			ServerHostletEntity entity = new ServerHostletEntity();
			entity.loadFromJson(webserver, "webserver -> " + key);
			if (entity.handlerSetType != null) {
				if (!ConnectiveTypesRegistry.hasHandlerSetType(entity.handlerSetType))
					throw new IOException("Handler set type unrecognized: " + entity.handlerSetType);
			}
			if (entity.contentSource != null) {
				if (!ConnectiveTypesRegistry.hasContentSourceType(entity.contentSource))
					throw new IOException("Content source type unrecognized: " + entity.contentSource);
			}

			// Adapter
			if (ConnectiveHttpServer.findAdapter(entity.adapter.protocol) == null)
				throw new IOException("Protocol adapter unrecognized: " + entity.adapter.protocol);

			// Set up server
			// Create instance
			logger.info("Creating server instance for " + key + "...");
			ConnectiveHttpServer server = ConnectiveHttpServer.create(entity.adapter.protocol,
					entity.adapter.parameters);
			synchronized (contextsPerServer) {
				if (!contextsPerServer.containsKey(server))
					contextsPerServer.put(server, new ArrayList<BaseContext>());
			}

			// Add contexts
			ArrayList<BaseContext> sharedContexts = new ArrayList<BaseContext>();
			HashMap<BaseContext, BaseContext> newContextInstances = new HashMap<BaseContext, BaseContext>();
			for (String ctx : entity.contextNames) {
				if (!contexts.stream().anyMatch(t -> t.getName().equals(ctx)))
					throw new IOException("Context type unrecognized: " + ctx);
				BaseContext ct = contexts.stream().filter(t -> t.getName().equals(ctx)).findFirst().get();
				if (!newContextInstances.containsKey(ct))
					newContextInstances.put(ct, copyContextFor(key, ct, server));
				sharedContexts.add(newContextInstances.get(ct));
			}

			// Defaults
			String handlerSetTopType = "default";
			if (entity.handlerSetType != null)
				handlerSetTopType = entity.handlerSetType;
			String contentSourceTopType = "default";
			if (entity.contentSource != null)
				contentSourceTopType = entity.contentSource;

			// Load vhosts
			logger.info("Configuring vhosts for " + key + "...");
			VhostDefinition preferredDefault = null;
			ArrayList<String> vhostMemory = new ArrayList<String>();
			HashMap<String, VhostDefinition> vhostsByDomain = new HashMap<String, VhostDefinition>();
			HashMap<String, VhostDefinition> vhostsByDomainWildcards = new HashMap<String, VhostDefinition>();
			if (!entity.vhostSets.isEmpty()) {
				for (String vhostSetId : entity.vhostSets) {
					logger.info("Configuring vhosts set " + vhostSetId + " for " + key + "...");
					if (!lists.containsKey(vhostSetId)) {
						// Error
						throw new IOException("Unrecognized vhost set: " + vhostSetId);
					}

					// Get set
					VhostList set = lists.get(vhostSetId);
					if (preferredDefault == null && set.defaultDef != null)
						preferredDefault = set.defaultDef;

					// Load domains
					for (String domain : set.vhostsByDomain.keySet()) {
						if (!vhostMemory.contains(domain.toLowerCase())) {
							logger.info("Configuring vhosts domain " + domain + " for " + key + "...");
							VhostDefinition vhost = set.vhostsByDomain.get(domain);

							// Create copy
							VhostDefinition vhostSpecific = new VhostDefinition();
							vhostSpecific.config = vhost.config;
							vhostSpecific.hostEntryName = vhost.hostEntryName;
							vhost = vhostSpecific;

							// Add contexts
							for (String ctx : vhost.config.contextNames) {
								if (!contexts.stream().anyMatch(t -> t.getName().equals(ctx)))
									throw new IOException("Context type unrecognized: " + ctx);
								BaseContext ct = contexts.stream().filter(t -> t.getName().equals(ctx)).findFirst()
										.get();
								if (!newContextInstances.containsKey(ct))
									newContextInstances.put(ct, copyContextFor(key, ct, server));
								vhost.contexts.add(newContextInstances.get(ct));
							}

							// Get settings
							String handlerSetType = handlerSetTopType;
							String contentSourceType = contentSourceTopType;
							if (vhost.config.handlerSetType != null)
								handlerSetType = vhost.config.handlerSetType;
							if (vhost.config.contentSource != null)
								contentSourceType = vhost.config.contentSource;

							// Create default handlers set
							logger.info("Creating handler set of type " + handlerSetType + " for " + key + "...");
							HttpHandlerSet handlerSet = ConnectiveTypesRegistry.createHandlerSet(handlerSetType);

							// Create content source
							logger.info("Creating contentsource of type " + contentSourceType + " for " + key + "...");
							ContentSource source = ConnectiveTypesRegistry.createContentSource(contentSourceType,
									handlerSet, null);

							// Set up content source
							Container<ContentSource> sVal = new Container<ContentSource>();
							sVal.value = source;
							setupContentSource(source, handlerSet, server,
									"vhost " + vhost.hostEntryName + " for " + key, vhost.contexts,
									src -> sVal.value = src); // FIXME: a way to access context properties

							// Set source
							vhost.source = sVal.value;

							// Check domain
							if (domain.startsWith("*.")) {
								// Wildcard
								String domainO = domain;
								domain = domain.substring(2);
								if (domain.contains("*"))
									throw new IOException("Invalid domain: " + domainO
											+ ": wildcard too complex, only supporting basic startswith");
								vhostsByDomainWildcards.put(domain.toLowerCase(), vhost);
							} else {
								// Non wildcard
								if (domain.contains("*"))
									throw new IOException("Invalid domain: " + domain
											+ ": wildcard too complex, only supporting basic startswith");
								vhostsByDomain.put(domain.toLowerCase(), vhost);
							}
							vhostMemory.add(domain.toLowerCase());
						}
					}
				}
			}

			// Create default handlers set
			logger.info("Creating handler set of type " + handlerSetTopType + " for " + key + "...");
			HttpHandlerSet topSet = ConnectiveTypesRegistry.createHandlerSet(handlerSetTopType);

			// Create content source
			logger.info("Creating contentsource of type " + contentSourceTopType + " for " + key + "...");
			ContentSource topSource = ConnectiveTypesRegistry.createContentSource(contentSourceTopType, topSet, null);

			// Update
			server.setContentSource(topSource);

			// Apply default headers
			for (String name : entity.defaultHeaders.keySet()) {
				server.getDefaultHeaders().addHeader(name, entity.defaultHeaders.get(name));
			}

			// Set up content source
			setupContentSource(topSource, topSet, server, "<default> for " + key, sharedContexts, src -> {
				server.setContentSource(src);
			}); // FIXME: a way to access context properties

			// If needed, add vhosts
			if (!entity.vhostSets.isEmpty()) {
				// Add vhosts
				server.setContentSource(
						new VhostsContentSource(vhostsByDomain, vhostsByDomainWildcards, preferredDefault));
			}

			// Add server
			servers.add(server);
		}

		// Assign
		this.servers = servers.toArray(t -> new ConnectiveHttpServer[t]);

		// Clean
		pool.close();
	}

	private static class Container<T> {
		public T value;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void setupContentSource(ContentSource source, HttpHandlerSet set, ConnectiveHttpServer server, String scope,
			ArrayList<BaseContext> contexts, Consumer<ContentSource> sourceUpdater) {
		// Set up content source
		logger.info("Configuring content source " + scope + "...");

		// Call init on loaded contexts
		logger.info("Initializing contexts for server on content source " + scope + "...");
		for (BaseContext ctx : contexts) {
			logger.info("Initializing context " + ctx.getName() + " on content source " + scope + "...");

			// Setup handlers
			ctx.setupWebServerHandlers(this, server, set);

			// Setup content source
			ContentSource newContentSource = ctx.setupWebServer(this, server, source);
			if (newContentSource != null) {
				newContentSource.unsafe().unsafeSetParent(source);
				sourceUpdater.accept(newContentSource);
				source = newContentSource;
			}

			// Call for all
			logger.info("Initializing context components: " + ctx.getName() + "...");
			Container<ContentSource> sVal = new Container<ContentSource>();
			sVal.value = source;
			for (BaseContextComponent comp : getRootComponents(ctx, BaseContextComponent.class)) {
				forAllComponents(IBaseContextReceiver.class, comp, c -> {
					logger.info("Initializing context component " + c.getClass().getTypeName() + " on content source "
							+ scope + "...");
					if (c instanceof BaseContextComponent) {
						// Init webserver
						BaseContextComponent ct = (BaseContextComponent) c;

						// Setup handlers
						ct.setupWebServerHandlers(ctx, this, server, set);

						// Setup content source
						ContentSource newContentSource2 = ct.setupWebServer(ctx, this, server, sVal.value);
						if (newContentSource2 != null) {
							newContentSource2.unsafe().unsafeSetParent(sVal.value);
							sourceUpdater.accept(newContentSource2);
							sVal.value = newContentSource2;
						}
					}
				});
			}
			source = sVal.value;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private BaseContext copyContextFor(String scope, BaseContext ctx, ConnectiveHttpServer server) {
		// Create instance
		logger.info("Copying context " + ctx.getName() + " for " + scope + "...");
		BaseContext ctxNew = ctx.createInstance();
		EventBus.getInstance().addAllEventsFromReceiver(ctxNew);
		EventBus.getInstance().dispatchEvent(new ContextInstanceCreatedEvent(ctxNew, this, server));
		EventBus.getInstance().dispatchEvent(new ContextPreInitEvent(ctxNew, this));
		synchronized (contextRootComponents) {
			contextRootComponents.put(ctxNew, new ArrayList<BaseContextComponent<?>>());
		}
		synchronized (contextsPerServer) {
			contextsPerServer.get(server).add(ctxNew);
		}
		synchronized (contextsParentServers) {
			contextsParentServers.put(ctxNew, server);
		}

		// Copy child components
		for (BaseContextComponent comp : getRootComponents(ctx, BaseContextComponent.class)) {
			// Copy root
			copyRootComponentFor(scope, ctxNew, comp, server);
		}

		// Init
		logger.info("Initializing context: " + ctxNew.getName() + "...");
		EventBus.getInstance().dispatchEvent(new ContextInitEvent(ctxNew, this));
		ctxNew.initServer(this);
		ctxNew.initWebserver(this, server);

		// Call for all
		logger.info("Initializing context components: " + ctx.getName() + "...");
		for (BaseContextComponent comp : getRootComponents(ctxNew, BaseContextComponent.class)) {
			forAllComponents(IBaseContextReceiver.class, comp, c -> {
				logger.info("Initializing context component: " + c.getClass().getTypeName() + "...");
				if (c instanceof BaseContextComponent)
					EventBus.getInstance()
							.dispatchEvent(new ContextComponentInitEvent((BaseContextComponent<?>) c, ctx, this));
				c.initServer(ctx, this);
			});
		}

		// Return
		return ctxNew;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void copyRootComponentFor(String scope, BaseContext ctxNew, BaseContextComponent<?> comp,
			ConnectiveHttpServer server) {
		// Create instance
		logger.info("Copying root component " + comp.getClass().getTypeName() + " for " + scope + "...");
		BaseContextComponent<?> instNew = (BaseContextComponent<?>) comp.createInstance();
		if (!instNew.getClass().getTypeName().equals(comp.getClass().getTypeName()))
			throw new RuntimeException("Error: " + comp.getClass().getTypeName()
					+ " createInstance returned incorrect type " + instNew.getClass().getTypeName());
		EventBus.getInstance().addAllEventsFromReceiver(instNew);
		EventBus.getInstance().dispatchEvent(new ContextComponentInstanceCreatedEvent(instNew, ctxNew, this, server));
		EventBus.getInstance().dispatchEvent(new ContextComponentPreInitEvent(instNew, ctxNew, this));

		// Add
		addRootComponent(ctxNew, instNew);
		((BaseContextComponent) instNew).initWebserver(ctxNew, this, server);

		// Copy child components
		for (IBaseContextReceiver<?> child : getChildComponents(comp, IBaseContextReceiver.class)) {
			copyComponentFor(scope, ctxNew, instNew, child, server);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void copyComponentFor(String scope, BaseContext ctxNew, IBaseContextReceiver<?> parent,
			IBaseContextReceiver<?> comp, ConnectiveHttpServer server) {
		// Create instance
		logger.info("Copying component " + comp.getClass().getTypeName() + " for " + scope + "...");
		IBaseContextReceiver<?> instNew = comp.createInstance();
		if (!instNew.getClass().getTypeName().equals(comp.getClass().getTypeName()))
			throw new RuntimeException("Error: " + comp.getClass().getTypeName()
					+ " createInstance returned incorrect type " + instNew.getClass().getTypeName());
		if (instNew instanceof BaseContextComponent) {
			EventBus.getInstance().addAllEventsFromReceiver((BaseContextComponent<?>) instNew);
			EventBus.getInstance().dispatchEvent(
					new ContextComponentInstanceCreatedEvent((BaseContextComponent<?>) instNew, ctxNew, this, server));
			EventBus.getInstance()
					.dispatchEvent(new ContextComponentPreInitEvent((BaseContextComponent<?>) instNew, ctxNew, this));
			((BaseContextComponent) instNew).initWebserver(ctxNew, this, server);
		}

		// Add
		addComponent(parent, instNew);

		// Copy child components
		for (IBaseContextReceiver<?> child : getChildComponents(comp, IBaseContextReceiver.class)) {
			copyComponentFor(scope, ctxNew, instNew, child, server);
		}
	}

	/**
	 * Checks if the server is running
	 * 
	 * @return True if running, false otherwise
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Starts all servers
	 * 
	 * @throws IOException
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void start() throws IOException {
		if (running)
			throw new IllegalStateException("Server already running!");
		running = true;
		logger.info("Starting servers...");
		for (ConnectiveHttpServer server : servers) {
			for (BaseContext ctx : getContexts(server, BaseContext.class))
				ctx.startServer(this);
		}
		EventBus.getInstance().dispatchEvent(new StartServerEvent(this));
		for (ConnectiveHttpServer server : servers) {
			EventBus.getInstance().dispatchEvent(new StartWebserverEvent(this, server));
			for (BaseContext ctx : getContexts(server, BaseContext.class)) {
				ctx.startWebserver(this, server);
				for (BaseContextComponent comp : getRootComponents(ctx, BaseContextComponent.class)) {
					forAllComponents(BaseContextComponent.class, comp, c -> {
						c.startWebserver(ctx, this, server);
					});
				}
			}
			server.start();
		}
		logger.info("Started successfully!");
	}

	/**
	 * Stops the HTTP server
	 * 
	 * @throws IOException If stopping the server fails
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void stop() throws IOException {
		if (!running)
			return;
		logger.info("Stopping servers...");
		for (ConnectiveHttpServer server : servers) {
			for (BaseContext ctx : getContexts(server, BaseContext.class))
				ctx.stopServer(this);
		}
		EventBus.getInstance().dispatchEvent(new StopServerEvent(this));
		for (ConnectiveHttpServer server : servers) {
			server.stop();
			EventBus.getInstance().dispatchEvent(new StopWebserverEvent(this, server));
			for (BaseContext ctx : getContexts(server, BaseContext.class)) {
				ctx.stopWebserver(this, server);
				for (BaseContextComponent comp : getRootComponents(ctx, BaseContextComponent.class)) {
					forAllComponents(BaseContextComponent.class, comp, c -> {
						c.stopWebserver(ctx, this, server);
					});
				}
			}
		}
		running = false;
		logger.info("Stopped successfully!");

	}

	/**
	 * Stops the HTTP server without waiting for all clients to disconnect
	 * 
	 * @throws IOException If stopping the server fails
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void stopForced() throws IOException {
		if (!running)
			return;
		logger.info("Stopping servers...");
		for (ConnectiveHttpServer server : servers) {
			for (BaseContext ctx : getContexts(server, BaseContext.class))
				ctx.stopServer(this);
		}
		EventBus.getInstance().dispatchEvent(new StopServerEvent(this));
		for (ConnectiveHttpServer server : servers) {
			server.stopForced();
			EventBus.getInstance().dispatchEvent(new StopWebserverEvent(this, server));
			for (BaseContext ctx : getContexts(server, BaseContext.class)) {
				ctx.stopWebserver(this, server);
				for (BaseContextComponent comp : getRootComponents(ctx, BaseContextComponent.class)) {
					forAllComponents(BaseContextComponent.class, comp, c -> {
						c.stopWebserver(ctx, this, server);
					});
				}
			}
		}
		running = false;
		logger.info("Stopped successfully!");
	}

	private <T extends IBaseContextReceiver<?>> void forAllComponents(Class<T> type, T comp, Consumer<T> action) {
		action.accept(comp);
		for (T child : getChildComponents(comp, type)) {
			forAllComponents(type, child, action);
		}
	}

	/**
	 * Waits for the server to shut down
	 */
	public void waitForExit() {
		for (ConnectiveHttpServer server : servers)
			server.waitForExit();
	}

	/**
	 * Reads the server config file
	 * 
	 * @throws IOException If the config fails to load
	 */
	public JsonObject loadConfig() throws IOException {
		try {
			return JsonParser.parseString(Files.readString(configFile.toPath())).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException("Invalid config file", e);
		}
	}

	/**
	 * Retrieves the HTTP server instances
	 * 
	 * @return Array of ConnectiveHttpServer instances
	 */
	public ConnectiveHttpServer[] getServers() {
		return servers;
	}

	/**
	 * Retrieves the working directory
	 * 
	 * @return Working directory file
	 */
	public File getWorkingDir() {
		return workingDir;
	}

	/**
	 * Retrieves the configuration file
	 * 
	 * @return Configuration directory file
	 */
	public File getConfigFile() {
		return configFile;
	}

	/**
	 * Checks if running in debug mode
	 * 
	 * @return True if in debug mode, false otherwise
	 */
	public boolean isDebugMode() {
		return debugMode;
	}

	@SuppressWarnings({ "rawtypes" })
	private void createContextHierarchy(BaseContext ctx, FluidClassPool pool, ClassScanner scanner) {
		// Go through pool
		logger.info("Initializing context: " + ctx.getName() + "...");
		logger.info("Finding root compontents for context: " + ctx.getName() + "...");
		for (Class<? extends BaseContextComponent> cls : scanner
				.findClassInstancesExtending(BaseContextComponent.class)) {
			if (cls.isAnnotationPresent(AttachToContexts.class)) {
				// Get target
				logger.debug("Considering " + cls.getClass().getTypeName() + "...");
				for (AttachToContext anno : cls.getAnnotation(AttachToContexts.class).value()) {
					if (anno.value().isAssignableFrom(ctx.getClass())) {
						// Add
						addRootComponent(ctx, cls);
					} else
						logger.debug("Did not use " + cls.getClass().getTypeName() + ", incorrect type.");
				}
			} else if (cls.isAnnotationPresent(AttachToContext.class)) {
				// Get target
				logger.debug("Considering " + cls.getClass().getTypeName() + "...");
				if (cls.getAnnotation(AttachToContext.class).value().isAssignableFrom(ctx.getClass())) {
					// Add
					addRootComponent(ctx, cls);
				} else
					logger.debug("Did not use " + cls.getClass().getTypeName() + ", incorrect type.");
			}
		}
		if (ctx.getClass().isAnnotationPresent(AddRootComponent.class)) {
			Class<? extends BaseContextComponent> cls = ctx.getClass().getAnnotation(AddRootComponent.class).value();
			addRootComponent(ctx, cls);
		} else if (ctx.getClass().isAnnotationPresent(AddRootComponents.class)) {
			for (AddRootComponent anno : ctx.getClass().getAnnotation(AddRootComponents.class).value()) {
				Class<? extends BaseContextComponent> cls = anno.value();
				addRootComponent(ctx, cls);
			}
		}
	}

	/**
	 * Adds root components by type
	 * 
	 * @param <T>     Root component type
	 * @param context Context instance
	 * @param child   Component class of the component to add to the context as a
	 *                root component
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <T extends BaseContextComponent<?>> void addRootComponent(BaseContext context, Class<T> child) {
		T inst;
		logger.info("Adding root component " + child.getTypeName() + " to context: " + context.getName() + "...");
		try {
			// Find constructor
			Constructor<T> ctor = child.getConstructor();

			// Try setting accessible
			ctor.setAccessible(true);

			// Create instance
			inst = ctor.newInstance();
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException("Could not instantiate component: " + child.getTypeName()
					+ ": no accessible parameterless constructor", e);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException(
					"Could not instantiate component: " + child.getTypeName() + ": object instantiation failed", e);
		}

		// Remove from old parent
		boolean hadParent = removeComponentFromParent(inst);

		// Set new parent
		synchronized (contextRootComponentParents) {
			contextRootComponentParents.put(inst, context);
		}

		// Update lists
		synchronized (contextComponentChildren) {
			// Update list of parent components
			synchronized (contextRootComponents) {
				ArrayList<BaseContextComponent<?>> components = contextRootComponents.get(context);
				if (!components.contains(inst)) {
					// Add
					components.add(inst);
				}
			}

			// Create lists if needed
			if (!contextComponentChildren.containsKey(inst)) {
				contextComponentChildren.put(inst, new ArrayList<IBaseContextReceiver<?>>());
			}
		}

		if (!hadParent) {
			// Add to event system
			EventBus.getInstance().addAllEventsFromReceiver(inst);

			// Preinit
			EventBus.getInstance().dispatchEvent(new ContextComponentPreInitEvent(inst, context, this));
			if (running) {
				// Call other events since server's already running
				EventBus.getInstance().dispatchEvent(new ContextComponentInitEvent(inst, context, this));

				// Call init
				((BaseContextComponent) inst).initServer(context, this);

				// If relative to a server, run init webserver for specific server
				// Otherwise, run for all
				if (contextsParentServers.containsKey(context)) {
					ConnectiveHttpServer srv = contextsParentServers.get(context);
					((BaseContextComponent) inst).initWebserver(context, this, srv);
				} else {
					for (ConnectiveHttpServer srv : servers) {
						((BaseContextComponent) inst).initWebserver(context, this, srv);
					}
				}

				// Call init
				((BaseContextComponent) inst).startServer(context, this);

				// If relative to a server, run start webserver for specific server
				// Otherwise, run for all
				if (contextsParentServers.containsKey(context)) {
					ConnectiveHttpServer srv = contextsParentServers.get(context);
					((BaseContextComponent) inst).startWebserver(context, this, srv);
				} else {
					for (ConnectiveHttpServer srv : servers) {
						((BaseContextComponent) inst).startWebserver(context, this, srv);
					}
				}
			}
		}

		// Create child components
		logger.debug("Finding child components to add...");
		for (Class<? extends IBaseContextReceiver> cls : scanner
				.findClassInstancesExtending(IBaseContextReceiver.class)) {
			if (cls.isAnnotationPresent(AttachToContextComponents.class)) {
				// Get target
				logger.debug("Considering " + cls.getClass().getTypeName() + "...");
				for (AttachToContextComponent anno : cls.getAnnotation(AttachToContextComponents.class).value()) {
					if (anno.value().isAssignableFrom(child)) {
						// Add
						addComponent(inst, cls);
					} else
						logger.debug("Did not use " + cls.getClass().getTypeName() + ", incorrect type.");
				}
			} else if (cls.isAnnotationPresent(AttachToContextComponent.class)) {
				// Get target
				logger.debug("Considering " + cls.getClass().getTypeName() + "...");
				if (cls.getAnnotation(AttachToContextComponent.class).value().isAssignableFrom(child)) {
					// Add
					addComponent(inst, cls);
				} else
					logger.debug("Did not use " + cls.getClass().getTypeName() + ", incorrect type.");
			}
		}
		if (child.isAnnotationPresent(AddRootComponent.class)) {
			Class<? extends BaseContextComponent> cls = child.getAnnotation(AddRootComponent.class).value();
			addComponent(inst, cls);
		} else if (child.isAnnotationPresent(AddRootComponents.class)) {
			for (AddRootComponent anno : child.getAnnotation(AddRootComponents.class).value()) {
				Class<? extends BaseContextComponent> cls = anno.value();
				addComponent(inst, cls);
			}
		}
	}

	/**
	 * Adds root components to context instances
	 * 
	 * @param context Context instance
	 * @param child   Component to add to the context as a root component
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void addRootComponent(BaseContext context, BaseContextComponent<?> child) {
		// Log
		logger.info("Adding root component " + child.getClass().getTypeName() + " to context: " + context.getName()
				+ "...");

		// Remove from old parent
		boolean hadParent = removeComponentFromParent(child);

		// Set new parent
		synchronized (contextRootComponentParents) {
			contextRootComponentParents.put(child, context);
		}

		// Update lists
		synchronized (contextComponentChildren) {
			// Update list of parent components
			synchronized (contextRootComponents) {
				ArrayList<BaseContextComponent<?>> components = contextRootComponents.get(context);
				if (!components.contains(child)) {
					// Add
					components.add(child);
				}
			}

			// Create lists if needed
			if (!contextComponentChildren.containsKey(child)) {
				contextComponentChildren.put(child, new ArrayList<IBaseContextReceiver<?>>());
			}
		}

		if (!hadParent) {
			// Add to event system
			EventBus.getInstance().addAllEventsFromReceiver(child);

			// Preinit
			EventBus.getInstance().dispatchEvent(new ContextComponentPreInitEvent(child, context, this));
			if (running) {
				// Call other events since server's already running
				EventBus.getInstance().dispatchEvent(new ContextComponentInitEvent(child, context, this));

				// Call init
				((BaseContextComponent) child).initServer(context, this);

				// If relative to a server, run init webserver for specific server
				// Otherwise, run for all
				if (contextsParentServers.containsKey(context)) {
					ConnectiveHttpServer srv = contextsParentServers.get(context);
					((BaseContextComponent) child).initWebserver(context, this, srv);
				} else {
					for (ConnectiveHttpServer srv : servers) {
						((BaseContextComponent) child).initWebserver(context, this, srv);
					}
				}

				// Call init
				((BaseContextComponent) child).startServer(context, this);

				// If relative to a server, run start webserver for specific server
				// Otherwise, run for all
				if (contextsParentServers.containsKey(context)) {
					ConnectiveHttpServer srv = contextsParentServers.get(context);
					((BaseContextComponent) child).startWebserver(context, this, srv);
				} else {
					for (ConnectiveHttpServer srv : servers) {
						((BaseContextComponent) child).startWebserver(context, this, srv);
					}
				}
			}
		}
	}

	/**
	 * Checks if context components are present on a context instance by type
	 * 
	 * @param context Context instance
	 * @param type    Component type
	 * @return True if present, false otherwise
	 */
	public <T extends BaseContextComponent<?>> boolean hasRootComponent(BaseContext context, Class<T> type) {
		ArrayList<BaseContextComponent<?>> components = null;
		synchronized (contextRootComponents) {
			components = contextRootComponents.get(context);
		}
		if (components == null)
			return false;
		for (BaseContextComponent<?> obj : components) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return true;
		}
		for (BaseContextComponent<?> obj : components) {
			if (type.isAssignableFrom(obj.getClass()))
				return true;
		}
		return false;
	}

	/**
	 * Retrieves context components by type
	 * 
	 * @param context Context instance
	 * @param type    Component type
	 * @return BaseContextComponent instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends BaseContextComponent<?>> T getRootComponent(BaseContext context, Class<T> type) {
		ArrayList<BaseContextComponent<?>> components = null;
		synchronized (contextRootComponents) {
			components = contextRootComponents.get(context);
		}
		if (components == null)
			return null;
		for (BaseContextComponent<?> obj : components) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return (T) obj;
		}
		for (BaseContextComponent<?> obj : components) {
			if (type.isAssignableFrom(obj.getClass()))
				return (T) obj;
		}
		return null;
	}

	/**
	 * Retrieves context components by type
	 * 
	 * @param context Context instance
	 * @param type    Component type
	 * @return Array of BaseContextComponent instances
	 */
	@SuppressWarnings("unchecked")
	public <T extends BaseContextComponent<?>> T[] getRootComponents(BaseContext context, Class<T> type) {
		ArrayList<BaseContextComponent<?>> components = null;
		synchronized (contextRootComponents) {
			components = contextRootComponents.get(context);
		}
		if (components == null)
			return (T[]) new BaseContextComponent<?>[0];
		ArrayList<T> entries = new ArrayList<T>();
		for (BaseContextComponent<?> obj : components) {
			if (type.getTypeName().equals(obj.getClass().getTypeName())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		for (BaseContextComponent<?> obj : components) {
			if (type.isAssignableFrom(obj.getClass())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		return (T[]) entries.toArray(t -> new BaseContextComponent<?>[t]);
	}

	/**
	 * Adds components by type
	 * 
	 * @param <T>    Component type
	 * @param parent The parent component
	 * @param child  Component class of the component to add to the parent
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <T extends IBaseContextReceiver<?>> void addComponent(IBaseContextReceiver<?> parent, Class<T> child) {
		T inst;
		logger.info("Adding component " + child.getTypeName() + " to parent component: "
				+ parent.getClass().getTypeName() + "...");

		try {
			// Find constructor
			Constructor<T> ctor = child.getConstructor();

			// Try setting accessible
			ctor.setAccessible(true);

			// Create instance
			inst = ctor.newInstance();
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException("Could not instantiate component: " + child.getTypeName()
					+ ": no accessible parameterless constructor", e);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException(
					"Could not instantiate component: " + child.getTypeName() + ": object instantiation failed", e);
		}

		// Remove from old parent
		boolean hadParent = removeComponentFromParent(inst);

		// Set new parent
		synchronized (contextComponentParents) {
			contextComponentParents.put(inst, parent);
		}

		// Update lists
		synchronized (contextComponentChildren) {
			// Update list of parent components
			ArrayList<IBaseContextReceiver<?>> cs = contextComponentChildren.get(parent);
			if (cs != null) {
				// Add
				if (!cs.contains(inst))
					cs.add(inst);
			}

			// Create lists if needed
			if (!contextComponentChildren.containsKey(inst)) {
				contextComponentChildren.put(inst, new ArrayList<IBaseContextReceiver<?>>());
			}
		}

		if (!hadParent) {
			// Add to event system
			if (inst instanceof IEventReceiver)
				EventBus.getInstance().addAllEventsFromReceiver((IEventReceiver) inst);

			// Preinit
			if (inst instanceof BaseContextComponent) {
				// Get parent
				BaseContext parentContext = null;
				synchronized (contextComponentParents) {
					// Check present
					IBaseContextReceiver<?> topParent = inst;
					while (contextComponentParents.containsKey(topParent)) {
						topParent = contextComponentParents.get(topParent);
					}

					// Check root
					synchronized (contextRootComponentParents) {
						if (contextRootComponentParents.containsKey(topParent)) {
							// Get context
							parentContext = contextRootComponentParents.get(topParent);
						}
					}
				}
				BaseContextComponent comp = (BaseContextComponent) inst;
				EventBus.getInstance().dispatchEvent(new ContextComponentPreInitEvent(comp, parentContext, this));
				if (running) {
					// Call other events since server's already running
					EventBus.getInstance().dispatchEvent(new ContextComponentInitEvent(comp, parentContext, this));

					// Call init
					((BaseContextComponent) inst).initServer(parentContext, this);

					// If relative to a server, run init webserver for specific server
					// Otherwise, run for all
					if (contextsParentServers.containsKey(parentContext)) {
						ConnectiveHttpServer srv = contextsParentServers.get(parentContext);
						((BaseContextComponent) inst).initWebserver(parentContext, this, srv);
					} else {
						for (ConnectiveHttpServer srv : servers) {
							((BaseContextComponent) inst).initWebserver(parentContext, this, srv);
						}
					}

					// Call init
					((BaseContextComponent) inst).startServer(parentContext, this);

					// If relative to a server, run start webserver for specific server
					// Otherwise, run for all
					if (contextsParentServers.containsKey(parentContext)) {
						ConnectiveHttpServer srv = contextsParentServers.get(parentContext);
						((BaseContextComponent) inst).startWebserver(parentContext, this, srv);
					} else {
						for (ConnectiveHttpServer srv : servers) {
							((BaseContextComponent) inst).startWebserver(parentContext, this, srv);
						}
					}
				}
			}
		}

		// Create child components
		logger.debug("Finding child components to add...");
		for (Class<? extends IBaseContextReceiver> cls : scanner
				.findClassInstancesExtending(IBaseContextReceiver.class)) {
			if (cls.isAnnotationPresent(AttachToContextComponents.class)) {
				// Get target
				logger.debug("Considering " + cls.getClass().getTypeName() + "...");
				for (AttachToContextComponent anno : cls.getAnnotation(AttachToContextComponents.class).value()) {
					if (anno.value().isAssignableFrom(child)) {
						// Add
						addComponent(inst, cls);
					} else
						logger.debug("Did not use " + cls.getClass().getTypeName() + ", incorrect type.");
				}
			} else if (cls.isAnnotationPresent(AttachToContextComponent.class)) {
				// Get target
				logger.debug("Considering " + cls.getClass().getTypeName() + "...");
				if (cls.getAnnotation(AttachToContextComponent.class).value().isAssignableFrom(child)) {
					// Add
					addComponent(inst, cls);
				} else
					logger.debug("Did not use " + cls.getClass().getTypeName() + ", incorrect type.");
			}
		}
		if (child.isAnnotationPresent(AddContextComponent.class)) {
			Class<? extends IBaseContextReceiver> cls = child.getAnnotation(AddContextComponent.class).value();
			addComponent(inst, cls);
		} else if (child.isAnnotationPresent(AddContextComponents.class)) {
			for (AddContextComponent anno : child.getAnnotation(AddContextComponents.class).value()) {
				Class<? extends IBaseContextReceiver> cls = anno.value();
				addComponent(inst, cls);
			}
		}
	}

	/**
	 * Adds components to a parent, removing from the old parent if needed
	 * 
	 * @param parent The parent component
	 * @param child  Component to add to the parent
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void addComponent(IBaseContextReceiver<?> parent, IBaseContextReceiver<?> child) {
		// Log
		logger.info("Adding component " + child.getClass().getTypeName() + " to parent component: "
				+ parent.getClass().getTypeName() + "...");

		// Remove from old parent
		boolean hadParent = removeComponentFromParent(child);

		// Set new parent
		synchronized (contextComponentParents) {
			contextComponentParents.put(child, parent);
		}

		// Update lists
		synchronized (contextComponentChildren) {
			// Update list of parent components
			ArrayList<IBaseContextReceiver<?>> cs = contextComponentChildren.get(parent);
			if (cs != null) {
				// Add
				if (!cs.contains(child))
					cs.add(child);
			}

			// Create lists if needed
			if (!contextComponentChildren.containsKey(child)) {
				contextComponentChildren.put(child, new ArrayList<IBaseContextReceiver<?>>());
			}
		}

		if (!hadParent) {
			// Add to event system
			if (child instanceof IEventReceiver)
				EventBus.getInstance().addAllEventsFromReceiver((IEventReceiver) child);

			// Preinit
			if (child instanceof BaseContextComponent) {
				// Get parent
				BaseContext parentContext = null;
				synchronized (contextComponentParents) {
					// Check present
					IBaseContextReceiver<?> topParent = child;
					while (contextComponentParents.containsKey(topParent)) {
						topParent = contextComponentParents.get(topParent);
					}

					// Check root
					synchronized (contextRootComponentParents) {
						if (contextRootComponentParents.containsKey(topParent)) {
							// Get context
							parentContext = contextRootComponentParents.get(topParent);
						}
					}
				}
				BaseContextComponent<?> comp = (BaseContextComponent<?>) child;
				EventBus.getInstance().dispatchEvent(new ContextComponentPreInitEvent(comp, parentContext, this));
				if (running) {
					// Call other events since server's already running
					EventBus.getInstance().dispatchEvent(new ContextComponentInitEvent(comp, parentContext, this));

					// Call init
					((BaseContextComponent) child).initServer(parentContext, this);

					// If relative to a server, run init webserver for specific server
					// Otherwise, run for all
					if (contextsParentServers.containsKey(parentContext)) {
						ConnectiveHttpServer srv = contextsParentServers.get(parentContext);
						((BaseContextComponent) child).initWebserver(parentContext, this, srv);
					} else {
						for (ConnectiveHttpServer srv : servers) {
							((BaseContextComponent) child).initWebserver(parentContext, this, srv);
						}
					}

					// Call init
					((BaseContextComponent) child).startServer(parentContext, this);

					// If relative to a server, run start webserver for specific server
					// Otherwise, run for all
					if (contextsParentServers.containsKey(parentContext)) {
						ConnectiveHttpServer srv = contextsParentServers.get(parentContext);
						((BaseContextComponent) child).startWebserver(parentContext, this, srv);
					} else {
						for (ConnectiveHttpServer srv : servers) {
							((BaseContextComponent) child).startWebserver(parentContext, this, srv);
						}
					}
				}
			}
		}
	}

	/**
	 * Removes components
	 * 
	 * @param child Component to remove
	 */
	private boolean removeComponentFromParent(IBaseContextReceiver<?> child) {
		// Check root
		if (child instanceof BaseContextComponent) {
			BaseContextComponent<?> component = (BaseContextComponent<?>) child;
			synchronized (contextRootComponentParents) {
				if (contextRootComponentParents.containsKey(component)) {
					// Root component being destroyed'

					// Get parent
					BaseContext parent = contextRootComponentParents.get(component);

					// Remove from list
					contextRootComponentParents.remove(component);

					// Remove from parent
					synchronized (contextRootComponents) {
						ArrayList<BaseContextComponent<?>> components = contextRootComponents.get(parent);
						if (components.contains(component)) {
							// Remove
							components.remove(component);
						}
					}

					// Success
					return true;
				}
			}
		}

		IBaseContextReceiver<?> parent = null;
		synchronized (contextComponentParents) {
			// Check present
			if (!contextComponentParents.containsKey(child)) {
				return false;
			}

			// Remove
			parent = contextComponentParents.remove(child);
		}

		// Remove from parent
		synchronized (contextComponentChildren) {
			ArrayList<IBaseContextReceiver<?>> cs = contextComponentChildren.get(parent);
			if (cs != null) {
				// Remove
				if (cs.contains(child))
					cs.remove(child);
			}
		}

		// Success
		return true;
	}

	/**
	 * Destroys a component
	 * 
	 * @param component Component to destroy
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void destroyComponent(IBaseContextReceiver<?> component) {
		// Log
		logger.info("Destroying component " + component.getClass().getTypeName() + "...");

		// First remove root components if a root component
		boolean destroyed = false;
		boolean wasRoot = false;
		BaseContext parentContext = null;
		synchronized (contextRootComponentParents) {
			if (contextRootComponentParents.containsKey(component)) {
				// Root component being destroyed'

				// Get parent
				BaseContext parent = contextRootComponentParents.get(component);
				parentContext = parent;

				// Remove from list
				contextRootComponentParents.remove(component);

				// Remove from parent
				synchronized (contextRootComponents) {
					ArrayList<BaseContextComponent<?>> components = contextRootComponents.get(parent);
					if (components.contains(component)) {
						// Remove
						components.remove(component);
					}
				}

				// Mark destroyed
				destroyed = true;
				wasRoot = true;
			}
		}

		// Check root
		if (!wasRoot) {
			// Get parent
			synchronized (contextComponentParents) {
				// Check present
				IBaseContextReceiver<?> topParent = component;
				while (contextComponentParents.containsKey(topParent)) {
					topParent = contextComponentParents.get(topParent);
				}

				// Check root
				synchronized (contextRootComponentParents) {
					if (contextRootComponentParents.containsKey(topParent)) {
						// Get context
						parentContext = contextRootComponentParents.get(topParent);
					}
				}
			}

			// Remove normally
			if (removeComponentFromParent(component))
				destroyed = true;
		}

		// Check destroyed
		if (destroyed) {
			// Destroy child components
			IBaseContextReceiver<?>[] children = null;
			synchronized (contextComponentChildren) {
				ArrayList<IBaseContextReceiver<?>> cs = contextComponentChildren.get(component);
				if (cs != null) {
					// Got child list
					children = cs.toArray(t -> new IBaseContextReceiver<?>[t]);
				}
			}
			if (children != null) {
				// Destroy each
				for (IBaseContextReceiver<?> c : children)
					destroyComponent(c);
			}

			// Remove from event system
			if (component instanceof IEventReceiver)
				EventBus.getInstance().removeAllEventsFromReceiver((IEventReceiver) component);

			// Call destroy
			((IBaseContextReceiver) component).onDestroy(parentContext, this);

			// Remove list
			synchronized (contextComponentChildren) {
				contextComponentChildren.remove(component);
			}
		}
	}

	/**
	 * Retrieves the parent component of the given context component instance
	 * 
	 * @param component Component to retrieve the parent component of
	 * @return IBaseContextReceiver instance or null
	 */
	public IBaseContextReceiver<?> getParentComponent(IBaseContextReceiver<?> component) {
		synchronized (contextComponentParents) {
			return contextComponentParents.get(component);
		}
	}

	/**
	 * Checks if a specific child component is present
	 * 
	 * @param parent Parent component instance
	 * @param type   Component type
	 * @return True if present, false otherwise
	 */
	public <T extends IBaseContextReceiver<?>> boolean hasChildComponent(IBaseContextReceiver<?> parent,
			Class<T> type) {
		IBaseContextReceiver<?>[] components = null;
		synchronized (contextComponentChildren) {
			ArrayList<IBaseContextReceiver<?>> cs = contextComponentChildren.get(parent);
			if (cs != null)
				components = cs.toArray(t -> new IBaseContextReceiver<?>[t]);
		}
		if (components == null)
			return false;
		for (IBaseContextReceiver<?> obj : components) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return true;
		}
		for (IBaseContextReceiver<?> obj : components) {
			if (type.isAssignableFrom(obj.getClass()))
				return true;
		}
		return false;
	}

	/**
	 * Retrieves child components by type
	 * 
	 * @param parent Parent component instance
	 * @param type   Component type
	 * @return BaseContextComponent instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends IBaseContextReceiver<?>> T getChildComponent(IBaseContextReceiver<?> parent, Class<T> type) {
		IBaseContextReceiver<?>[] components = null;
		synchronized (contextComponentChildren) {
			ArrayList<IBaseContextReceiver<?>> cs = contextComponentChildren.get(parent);
			if (cs != null)
				components = cs.toArray(t -> new IBaseContextReceiver<?>[t]);
		}
		if (components == null)
			return null;
		for (IBaseContextReceiver<?> obj : components) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return (T) obj;
		}
		for (IBaseContextReceiver<?> obj : components) {
			if (type.isAssignableFrom(obj.getClass()))
				return (T) obj;
		}
		return null;
	}

	/**
	 * Retrieves child components by type
	 * 
	 * @param parent Parent component instance
	 * @param type   Component type
	 * @return Array of BaseContextComponent instances
	 */
	@SuppressWarnings("unchecked")
	public <T extends IBaseContextReceiver<?>> T[] getChildComponents(IBaseContextReceiver<?> parent, Class<T> type) {
		IBaseContextReceiver<?>[] components = null;
		synchronized (contextComponentChildren) {
			ArrayList<IBaseContextReceiver<?>> cs = contextComponentChildren.get(parent);
			if (cs != null)
				components = cs.toArray(t -> new IBaseContextReceiver<?>[t]);
		}
		if (components == null)
			return (T[]) new IBaseContextReceiver<?>[0];
		ArrayList<T> entries = new ArrayList<T>();
		for (IBaseContextReceiver<?> obj : components) {
			if (type.getTypeName().equals(obj.getClass().getTypeName())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		for (IBaseContextReceiver<?> obj : components) {
			if (type.isAssignableFrom(obj.getClass())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		return (T[]) entries.toArray(t -> new IBaseContextReceiver<?>[t]);
	}

	/**
	 * Checks if context instances are present by type
	 * 
	 * @param server The server instance to retrieve instance contexts for
	 * @param type   Context type
	 * @return True if present, false otherwise
	 */
	public <T extends BaseContext> boolean hasContext(ConnectiveHttpServer server, Class<T> type) {
		ArrayList<BaseContext> contexts = contextsPerServer.get(server);
		if (contexts == null)
			return false;
		for (BaseContext obj : contexts) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return true;
		}
		for (BaseContext obj : contexts) {
			if (type.isAssignableFrom(obj.getClass()))
				return true;
		}
		return false;
	}

	/**
	 * Retrieves context instances by type
	 * 
	 * @param server The server instance to retrieve instance contexts for
	 * @param type   Context type
	 * @return Context instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends BaseContext> T getContext(ConnectiveHttpServer server, Class<T> type) {
		ArrayList<BaseContext> contexts = contextsPerServer.get(server);
		if (contexts == null)
			return null;
		for (BaseContext obj : contexts) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return (T) obj;
		}
		for (BaseContext obj : contexts) {
			if (type.isAssignableFrom(obj.getClass()))
				return (T) obj;
		}
		return null;
	}

	/**
	 * Retrieves context instances by type
	 * 
	 * @param server The server instance to retrieve instance contexts for
	 * @param type   Context type
	 * @return Context instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends BaseContext> T[] getContexts(ConnectiveHttpServer server, Class<T> type) {
		ArrayList<BaseContext> contexts = contextsPerServer.get(server);
		if (contexts == null)
			return (T[]) new BaseContext[0];
		ArrayList<T> entries = new ArrayList<T>();
		for (BaseContext obj : contexts) {
			if (type.getTypeName().equals(obj.getClass().getTypeName())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		for (BaseContext obj : contexts) {
			if (type.isAssignableFrom(obj.getClass())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		return (T[]) entries.toArray(t -> new BaseContext[t]);
	}

	/**
	 * Checks if context instances are present by type
	 * 
	 * @param type Context type
	 * @return True if present, false otherwise
	 */
	public <T extends BaseContext> boolean hasContext(Class<T> type) {
		for (BaseContext obj : contexts) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return true;
		}
		for (BaseContext obj : contexts) {
			if (type.isAssignableFrom(obj.getClass()))
				return true;
		}
		return false;
	}

	/**
	 * Retrieves context instances by type
	 * 
	 * @param type Context type
	 * @return Context instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends BaseContext> T getContext(Class<T> type) {
		for (BaseContext obj : contexts) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return (T) obj;
		}
		for (BaseContext obj : contexts) {
			if (type.isAssignableFrom(obj.getClass()))
				return (T) obj;
		}
		return null;
	}

	/**
	 * Retrieves context instances by type
	 * 
	 * @param type Context type
	 * @return Context instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends BaseContext> T[] getContexts(Class<T> type) {
		ArrayList<T> entries = new ArrayList<T>();
		for (BaseContext obj : contexts) {
			if (type.getTypeName().equals(obj.getClass().getTypeName())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		for (BaseContext obj : contexts) {
			if (type.isAssignableFrom(obj.getClass())) {
				if (!entries.contains((T) obj))
					entries.add((T) obj);
			}
		}
		return (T[]) entries.toArray(t -> new BaseContext[t]);
	}

	/**
	 * Adds contexts to this object
	 * 
	 * @param ctx Context to store
	 */
	public void addContext(BaseContext ctx) {
		if (!contexts.contains(ctx)) {
			contexts.add(ctx);
			synchronized (contextRootComponents) {
				contextRootComponents.put(ctx, new ArrayList<BaseContextComponent<?>>());
			}
		}
	}

	/**
	 * Removes contexts
	 * 
	 * @param ctx Context to remove
	 */
	public void removeContext(BaseContext ctx) {
		if (contexts.contains(ctx)) {
			// Remove from list
			contexts.remove(ctx);

			// Destroy components
			for (BaseContextComponent<?> component : getRootComponents(ctx, BaseContextComponent.class)) {
				destroyComponent(component);
			}

			// Remove from list
			synchronized (contextRootComponents) {
				// Remove
				contextRootComponents.remove(ctx);
			}
		}
	}

}
