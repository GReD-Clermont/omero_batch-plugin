package fr.igred.ij.macro;

import java.util.logging.Logger;

public class ProgressLog implements ProgressMonitor {

	private final Logger logger;


	public ProgressLog(Logger logger) {
		this.logger = logger;
	}


	@Override
	public void setProgress(String text) {
		logger.info(text);
	}


	@Override
	public void setState(String text) {
		logger.fine(text);
	}


	@Override
	public void setDone() {
		setProgress("Task completed!");
	}

}
