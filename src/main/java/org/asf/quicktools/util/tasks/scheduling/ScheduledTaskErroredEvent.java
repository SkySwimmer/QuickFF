package org.asf.quicktools.util.tasks.scheduling;

import org.asf.quicktools.util.events.EventObject;

/**
 * 
 * Scheduled task error event
 * 
 * @author Sky Swimmer
 * 
 */
public class ScheduledTaskErroredEvent extends EventObject {

	private Exception error;

	public ScheduledTaskErroredEvent(Exception error) {
		this.error = error;
	}

	/**
	 * Retrieves the task exception
	 * 
	 * @return Task exception object
	 */
	public Exception getError() {
		return error;
	}

}
