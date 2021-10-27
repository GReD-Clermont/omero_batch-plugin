package fr.igred.ij.process;

import java.util.EventListener;

public interface BatchListener extends EventListener {

	void onThreadFinished();

}
