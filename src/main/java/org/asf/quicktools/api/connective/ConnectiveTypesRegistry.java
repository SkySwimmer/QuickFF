package org.asf.quicktools.api.connective;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.ContentSource;
import org.asf.connective.IServerAdapterDefinition;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.cyan.fluid.bytecode.FluidClassPool;
import org.asf.quicktools.util.scanning.ClassScanner;

public class ConnectiveTypesRegistry {

	private static Logger logger;
	private static boolean inited;

	private static HashMap<String, IContentSourceInstanceCreator> contentSources = new HashMap<String, IContentSourceInstanceCreator>();
	private static HashMap<String, IHandlerSetInstanceCreator> handlerSets = new HashMap<String, IHandlerSetInstanceCreator>();

	public static void init(FluidClassPool pool) {
		if (inited)
			return;
		inited = true;
		logger = LogManager.getLogger("typemanager");

		// Create scanner
		ClassScanner scanner = new ClassScanner(ConnectiveTypesRegistry.class.getClassLoader(), pool);
		logger.info("Registering adapters...");
		for (Class<? extends IServerAdapterDefinition> def : scanner
				.findAnnotatedClassInstances(ConnectiveAdapter.class, IServerAdapterDefinition.class)) {
			IServerAdapterDefinition inst;
			logger.info("Initializing adapter type: " + def.getTypeName() + "...");
			try {
				// Find constructor
				Constructor<? extends IServerAdapterDefinition> ctor = def.getConstructor();

				// Try setting accessible
				ctor.setAccessible(true);

				// Create instance
				inst = ctor.newInstance();
			} catch (NoSuchMethodException | SecurityException e) {
				scanner.close();
				throw new RuntimeException("Could not instantiate type: " + def.getTypeName()
						+ ": no accessible parameterless constructor", e);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				scanner.close();
				throw new RuntimeException(
						"Could not instantiate type: " + def.getTypeName() + ": object instantiation failed", e);
			}
			logger.info("Registering adapter: " + inst.getName() + "...");
			ConnectiveHttpServer.registerAdapter(inst);
		}
		logger.info("Registering content sources...");
		for (Class<? extends IContentSourceInstanceCreator> def : scanner
				.findAnnotatedClassInstances(ConnectiveContentSource.class, IContentSourceInstanceCreator.class)) {
			IContentSourceInstanceCreator inst;
			String id = def.getAnnotation(ConnectiveContentSource.class).value();
			logger.info("Initializing content source type: " + def.getTypeName() + "...");
			try {
				// Find constructor
				Constructor<? extends IContentSourceInstanceCreator> ctor = def.getConstructor();

				// Try setting accessible
				ctor.setAccessible(true);

				// Create instance
				inst = ctor.newInstance();
			} catch (NoSuchMethodException | SecurityException e) {
				scanner.close();
				throw new RuntimeException("Could not instantiate type: " + def.getTypeName()
						+ ": no accessible parameterless constructor", e);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				scanner.close();
				throw new RuntimeException(
						"Could not instantiate type: " + def.getTypeName() + ": object instantiation failed", e);
			}
			logger.info("Registering content source: " + id + "...");
			contentSources.put(id, inst);
		}
		logger.info("Registering handler sets...");
		for (Class<? extends IHandlerSetInstanceCreator> def : scanner
				.findAnnotatedClassInstances(ConnectiveHandlerSet.class, IHandlerSetInstanceCreator.class)) {
			IHandlerSetInstanceCreator inst;
			String id = def.getAnnotation(ConnectiveHandlerSet.class).value();
			logger.info("Initializing handler set type: " + def.getTypeName() + "...");
			try {
				// Find constructor
				Constructor<? extends IHandlerSetInstanceCreator> ctor = def.getConstructor();

				// Try setting accessible
				ctor.setAccessible(true);

				// Create instance
				inst = ctor.newInstance();
			} catch (NoSuchMethodException | SecurityException e) {
				scanner.close();
				throw new RuntimeException("Could not instantiate type: " + def.getTypeName()
						+ ": no accessible parameterless constructor", e);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				scanner.close();
				throw new RuntimeException(
						"Could not instantiate type: " + def.getTypeName() + ": object instantiation failed", e);
			}
			logger.info("Registering handler set type: " + id + "...");
			handlerSets.put(id, inst);
		}
		scanner.close();
	}

	public static ContentSource createContentSource(String type, HttpHandlerSet handlers, ContentSource parent) {
		if (!contentSources.containsKey(type))
			return null;
		return contentSources.get(type).createInstance(handlers, parent);
	}

	public static HttpHandlerSet createHandlerSet(String type) {
		if (!handlerSets.containsKey(type))
			return null;
		return handlerSets.get(type).createInstance();
	}

	public static boolean hasContentSourceType(String type) {
		return contentSources.containsKey(type);
	}

	public static boolean hasHandlerSetType(String type) {
		return handlerSets.containsKey(type);
	}

}
