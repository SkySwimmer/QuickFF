package org.asf.quicktools.util.common;

import java.util.ArrayList;

public class ObjectStorageContainer {

	private ArrayList<Object> objects = new ArrayList<Object>();

	/**
	 * Retrieves objects stored in this instance
	 * 
	 * @param type Object type
	 * @return Object instance or null
	 */
	@SuppressWarnings("unchecked")
	public <T> T getObject(Class<T> type) {
		for (Object obj : objects) {
			if (type.getTypeName().equals(obj.getClass().getTypeName()))
				return (T) obj;
		}
		for (Object obj : objects) {
			if (type.isAssignableFrom(obj.getClass()))
				return (T) obj;
		}
		return null;
	}

	/**
	 * Stores objects in this instance
	 * 
	 * @param obj Object to store
	 */
	public void storeObject(Object obj) {
		if (!objects.contains(obj))
			objects.add(obj);
	}

	/**
	 * Removes stored objects
	 * 
	 * @param obj Object to remove
	 */
	public void removeObject(Object obj) {
		if (objects.contains(obj))
			objects.remove(obj);
	}

}
