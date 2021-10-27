package fr.igred.ij.macro;

public interface ProgressMonitor {

	void setProgress(String text);

	void setState(String text);

	void setDone();
}
