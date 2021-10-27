package fr.igred.ij.macro;

import java.util.EventListener;

public interface BatchListener extends EventListener {

	void onThreadFinished();

}
