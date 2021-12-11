package fr.igred.ij.macro;

/**
 * Monitors the batch process progress.
 */
public interface ProgressMonitor {

	/**
	 * Sets the current progress.
	 *
	 * @param text The text for the current progress.
	 */
	void setProgress(String text);

	/**
	 * Sets the current state.
	 *
	 * @param text The text for the current state.
	 */
	void setState(String text);

	/**
	 * Signals the process is done.
	 */
	void setDone();

}
