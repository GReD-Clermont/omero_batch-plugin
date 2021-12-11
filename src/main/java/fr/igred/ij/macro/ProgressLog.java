package fr.igred.ij.macro;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs the progress of the batch process.
 */
public class ProgressLog implements ProgressMonitor {

	private final Logger logger;


	/**
	 * Creates a new instance with the specified logger.
	 *
	 * @param logger The logger.
	 */
	public ProgressLog(Logger logger) {
		this.logger = logger;
	}


	/**
	 * Sets the current progress.
	 *
	 * @param text The text for the current progress.
	 */
	@Override
	public void setProgress(String text) {
		logger.info(text);
	}


	/**
	 * Sets the current state.
	 *
	 * @param text The text for the current state.
	 */
	@Override
	public void setState(String text) {
		if (logger.isLoggable(Level.FINE)) {
			logger.fine(text);
		}
	}


	/**
	 * Signals the process is done.
	 */
	@Override
	public void setDone() {
		setProgress("Task completed!");
	}

}
