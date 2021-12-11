package fr.igred.ij.macro;

import java.util.EventListener;

/**
 * Listens to batch runner thread.
 */
public interface BatchListener extends EventListener {

	/**
	 * Action performed when thread is finished.
	 */
	void onThreadFinished();

}
