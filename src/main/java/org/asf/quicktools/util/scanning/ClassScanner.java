package org.asf.quicktools.util.scanning;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

import org.asf.cyan.fluid.Fluid;
import org.asf.cyan.fluid.bytecode.FluidClassPool;
import org.asf.cyan.fluid.bytecode.sources.LoaderClassSourceProvider;
import org.objectweb.asm.tree.ClassNode;

/**
 * 
 * Nexus class scanning utility, tool to search the classpath, jars and other
 * code sources for annotated classes and implementations of interfaces and
 * abstracts.
 * 
 * @author Sky Swimmer
 * 
 */
public class ClassScanner implements Closeable {

	private FluidClassPool pool;
	private ClassLoader loader;

	private HashMap<String, Class<?>> loadedClasses = new HashMap<String, Class<?>>();
	private HashMap<ClassLoader, Integer> classLoaders = new LinkedHashMap<ClassLoader, Integer>();

	public ClassScanner(ClassLoader loader, FluidClassPool pool) {
		this.pool = pool;
		this.loader = loader;
	}

	/**
	 * Retrieves the fallback class loader
	 * 
	 * @return ClassLoader instance
	 */
	public ClassLoader getFallbackClassLoader() {
		return loader;
	}

	/**
	 * Retrieves all added class loaders
	 * 
	 * @return Array of ClassLoader instances
	 */
	public ClassLoader[] getClassLoaders() {
		return classLoaders.keySet().stream()
				.sorted((t1, t2) -> -Integer.compare(classLoaders.get(t1), classLoaders.get(t2)))
				.toArray(t -> new ClassLoader[t]);
	}

	/**
	 * Removes class loaders from the list
	 * 
	 * @param loader Class loader to remove
	 */
	public ClassScanner removeClassLoader(ClassLoader loader) {
		classLoaders.remove(loader);
		return this;
	}

	/**
	 * Adds class loaders
	 * 
	 * @param loader Class loader to add
	 */
	public ClassScanner addClassLoader(ClassLoader loader) {
		return addClassLoader(loader, 0);
	}

	/**
	 * Adds class loaders with priority
	 * 
	 * @param loader   Class loader to add
	 * @param priority Class loader priority
	 */
	public ClassScanner addClassLoader(ClassLoader loader, int priority) {
		if (classLoaders.containsKey(loader))
			return this;
		classLoaders.put(loader, priority);
		pool.addSource(new LoaderClassSourceProvider(loader));
		return this;
	}

	/**
	 * Verifies if specific classes are known
	 * 
	 * @param name Class name
	 * @return True if known, false otherwise
	 */
	public boolean isClassKnown(String name) {
		try {
			return pool.getClassNode(name) != null;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/**
	 * Finds classes extending another type
	 * 
	 * @param type Supertype or interface
	 * @return Array of Class instances
	 */
	public <T> Class<? extends T>[] findClassInstancesExtending(Class<T> type) {
		return findClassInstancesExtending(type.getTypeName(), type);
	}

	/**
	 * Finds classes extending another type
	 * 
	 * @param type     Supertype or interface
	 * @param castType Type to cast to
	 * @return Array of Class instances
	 */
	public <T> Class<? extends T>[] findClassInstancesExtending(ClassNode type, Class<T> castType) {
		return findClassInstancesExtending(type.name.replace("/", "."), castType);
	}

	/**
	 * Finds classes extending another type
	 * 
	 * @param type     Supertype or interface name
	 * @param castType Type to cast to
	 * @return Array of Class instances
	 */
	@SuppressWarnings("unchecked")
	public <T> Class<? extends T>[] findClassInstancesExtending(String type, Class<T> castType) {
		return (Class<? extends T>[]) Stream.of(findClassNodesExtending(type)).map(t -> {
			try {
				return getClassInstance(t.name.replace("/", "."));
			} catch (ClassNotFoundException e) {
				return null;
			}
		}).filter(t -> t != null && castType.isAssignableFrom(t)).toArray(t -> new Class<?>[t]);
	}

	/**
	 * Finds classes extending another type
	 * 
	 * @param type Supertype or interface
	 * @return Array of class names
	 */
	public String[] findClassNamesExtending(Class<?> type) {
		return findClassNamesExtending(type.getTypeName());
	}

	/**
	 * Finds classes extending another type
	 * 
	 * @param type Supertype or interface
	 * @return Array of class names
	 */
	public String[] findClassNamesExtending(ClassNode type) {
		return findClassNamesExtending(type.name.replace("/", "."));
	}

	/**
	 * Finds classes extending another type
	 * 
	 * @param type Supertype or interface name
	 * @return Array of class names
	 */
	public String[] findClassNamesExtending(String type) {
		return Stream.of(findClassNodesExtending(type)).map(t -> t.name.replace("/", ".")).toArray(t -> new String[t]);
	}

	/**
	 * Finds class nodes extending another type
	 * 
	 * @param type Supertype or interface
	 * @return Array of ClassNode instances
	 */
	public ClassNode[] findClassNodesExtending(Class<?> type) {
		return findClassNodesExtending(type.getTypeName());
	}

	/**
	 * Finds class nodes extending another type
	 * 
	 * @param type Supertype or interface
	 * @return Array of ClassNode instances
	 */
	public ClassNode[] findClassNodesExtending(ClassNode type) {
		return findClassNodesExtending(type.name.replace("/", "."));
	}

	/**
	 * Finds class nodes extending another type
	 * 
	 * @param type Supertype name or interface name
	 * @return Array of ClassNode instances
	 */
	public ClassNode[] findClassNodesExtending(String type) {
		// Find all
		type = type.replace("/", ".");
		ArrayList<ClassNode> res = new ArrayList<ClassNode>();
		for (ClassNode node : getAllClassNodes()) {
			// Verify
			if (verifyTypeExtends(node, type)) {
				// Check modifier
				if (Modifier.isAbstract(node.access) && Modifier.isInterface(node.access))
					continue;
				res.add(node);
			}
		}
		return res.toArray(t -> new ClassNode[t]);
	}

	/**
	 * Finds annotated classes
	 * 
	 * @param annotation The annotation to search for
	 * @param castType   Type to cast to
	 * @return Array of Class instances
	 */
	public <T> Class<? extends T>[] findAnnotatedClassInstances(Class<? extends Annotation> annotation,
			Class<T> castType) {
		return findAnnotatedClassInstances(annotation.getTypeName(), castType);
	}

	/**
	 * Finds annotated classes
	 * 
	 * @param annotation The annotation to search for
	 * @param castType   Type to cast to
	 * @return Array of Class instances
	 */
	public <T> Class<? extends T>[] findAnnotatedClassInstances(ClassNode annotation, Class<T> castType) {
		return findAnnotatedClassInstances(annotation.name.replace("/", "."), castType);
	}

	/**
	 * Finds annotated classes
	 * 
	 * @param annotation The annotation to search for
	 * @param castType   Type to cast to
	 * @return Array of Class instances
	 */
	@SuppressWarnings("unchecked")
	public <T> Class<? extends T>[] findAnnotatedClassInstances(String annotation, Class<T> castType) {
		return (Class<? extends T>[]) Stream.of(findAnnotatedClassNodes(annotation)).map(t -> {
			try {
				return getClassInstance(t.name.replace("/", "."));
			} catch (ClassNotFoundException e) {
				return null;
			}
		}).filter(t -> t != null && castType.isAssignableFrom(t)).toArray(t -> new Class<?>[t]);
	}

	/**
	 * Finds annotated class names
	 * 
	 * @param annotation The annotation to search for
	 * @return Array of class names
	 */
	public String[] findAnnotatedClassNames(Class<? extends Annotation> annotation) {
		return findAnnotatedClassNames(annotation.getTypeName());
	}

	/**
	 * Finds annotated class names
	 * 
	 * @param annotation The annotation to search for
	 * @return Array of class names
	 */
	public String[] findAnnotatedClassNames(ClassNode annotation) {
		return findAnnotatedClassNames(annotation.name.replace("/", "."));
	}

	/**
	 * Finds annotated class names
	 * 
	 * @param annotation The annotation to search for
	 * @return Array of class names
	 */
	public String[] findAnnotatedClassNames(String annotation) {
		return Stream.of(findAnnotatedClassNodes(annotation)).map(t -> t.name.replace("/", "."))
				.toArray(t -> new String[t]);
	}

	/**
	 * Finds annotated class nodes
	 * 
	 * @param annotation The annotation to search for
	 * @return Array of ClassNode instances
	 */
	public ClassNode[] findAnnotatedClassNodes(Class<? extends Annotation> annotation) {
		return findAnnotatedClassNodes(annotation.getTypeName());
	}

	/**
	 * Finds annotated class nodes
	 * 
	 * @param annotation The annotation to search for
	 * @return Array of ClassNode instances
	 */
	public ClassNode[] findAnnotatedClassNodes(ClassNode annotation) {
		return findAnnotatedClassNodes(annotation.name.replace("/", "."));
	}

	/**
	 * Finds annotated class nodes
	 * 
	 * @param annotation The annotation to search for
	 * @return Array of ClassNode instances
	 */
	public ClassNode[] findAnnotatedClassNodes(String annotation) {
		// Find all
		annotation = annotation.replace("/", ".");
		ArrayList<ClassNode> res = new ArrayList<ClassNode>();
		for (ClassNode node : getAllClassNodes()) {
			// Verify
			if (verifyAnnotation(node, annotation)) {
				// Check modifier
				if (Modifier.isAbstract(node.access) && Modifier.isInterface(node.access))
					continue;
				res.add(node);
			}
		}
		return res.toArray(t -> new ClassNode[t]);
	}

	/**
	 * Checks if the given class node has the given annotation
	 * 
	 * @param node       Class node
	 * @param annotation Annotation to check
	 * @return True if present, false otherwise
	 */
	public boolean verifyAnnotation(ClassNode node, String annotation) {
		// Check node
		annotation = annotation.replace("/", ".");
		String annotationF = annotation;
		if (node.invisibleAnnotations != null && node.invisibleAnnotations.stream()
				.anyMatch(t -> Fluid.parseDescriptor(t.desc).replace("/", ".").equals(annotationF)))
			return true;
		if (node.visibleAnnotations != null && node.visibleAnnotations.stream()
				.anyMatch(t -> Fluid.parseDescriptor(t.desc).replace("/", ".").equals(annotationF)))
			return true;

		// Check interfaces
		if (node.interfaces != null) {
			for (String inter : node.interfaces) {
				// Check interface
				inter = inter.replace("/", ".");
				ClassNode interNode;
				try {
					interNode = getClassNode(inter);
				} catch (ClassNotFoundException e) {
					continue;
				}
				if (verifyAnnotation(interNode, annotation))
					return true;
			}
		}

		// Check supertype
		if (node.superName != null && !node.superName.equals(Object.class.getTypeName().replace(".", "/"))) {
			ClassNode interNode;
			try {
				interNode = getClassNode(node.superName);
			} catch (ClassNotFoundException e) {
				return false;
			}
			if (verifyAnnotation(interNode, annotation))
				return true;
		}

		// No match
		return false;
	}

	/**
	 * Checks if the given class node extends the given type
	 * 
	 * @param node Class node
	 * @param type Target type
	 * @return True if the given node extends the target type, false otherwise
	 */
	public boolean verifyTypeExtends(ClassNode node, String type) {
		// Check node
		type = type.replace("/", ".");
		if (node.name.replace("/", ".").equals(type))
			return true;

		// Check interfaces
		if (node.interfaces != null) {
			for (String inter : node.interfaces) {
				// Check interface
				inter = inter.replace("/", ".");
				ClassNode interNode;
				try {
					interNode = getClassNode(inter);
				} catch (ClassNotFoundException e) {
					continue;
				}
				if (verifyTypeExtends(interNode, type))
					return true;
			}
		}

		// Check supertype
		if (node.superName != null && !node.superName.equals(Object.class.getTypeName().replace(".", "/"))) {
			ClassNode interNode;
			try {
				interNode = getClassNode(node.superName);
			} catch (ClassNotFoundException e) {
				return false;
			}
			if (verifyTypeExtends(interNode, type))
				return true;
		}

		// No match
		return false;
	}

	/**
	 * Retrieves all discovered class names
	 * 
	 * @return Array of discovered class names
	 */
	public String[] getAllClassNames() {
		return Stream.of(pool.getLoadedClasses()).map(t -> t.name.replace("/", ".")).toArray(t -> new String[t]);
	}

	/**
	 * Retrieves all discovered class nodes
	 * 
	 * @return Array of ClassNode instances
	 */
	public ClassNode[] getAllClassNodes() {
		return pool.getLoadedClasses();
	}

	/**
	 * Retrieves all discovered class nodes
	 * 
	 * @return Array of Class instances
	 */
	public Class<?>[] getAllClassInstances() {
		return Stream.of(pool.getLoadedClasses()).map(t -> {
			try {
				return getClassInstance(t.name.replace("/", "."));
			} catch (ClassNotFoundException e) {
				return null;
			}
		}).filter(t -> t != null).toArray(t -> new Class<?>[t]);
	}

	/**
	 * Retrieves loaded class nodes
	 * 
	 * @param name Class name
	 * @return ClassNode object
	 * @throws ClassNotFoundException If the class cannot be found
	 */
	public ClassNode getClassNode(String name) throws ClassNotFoundException {
		return pool.getClassNode(name);
	}

	/**
	 * Retrieves classes by name
	 * 
	 * @param <T>  Class type
	 * @param name Class name
	 * @return Class object
	 * @throws ClassNotFoundException If the class cannot be found
	 */
	@SuppressWarnings("unchecked")
	public <T> Class<T> getClassInstance(String name) throws ClassNotFoundException {
		if (loadedClasses.containsKey(name))
			return (Class<T>) loadedClasses.get(name);

		// Verify
		pool.getClassNode(name);

		// Load class
		for (ClassLoader loader : getClassLoaders()) {
			try {
				Class<T> cls = (Class<T>) loader.loadClass(name);
				loadedClasses.put(name, cls);
				return cls;
			} catch (Throwable e) {
			}
		}

		// Try with fallback
		try {
			Class<T> cls = (Class<T>) loader.loadClass(name);
			loadedClasses.put(name, cls);
			return cls;
		} catch (Throwable e) {
		}

		// Not found
		throw new ClassNotFoundException(name);
	}

	@Override
	public void close() {
		loadedClasses.clear();
		classLoaders.clear();
	}

}