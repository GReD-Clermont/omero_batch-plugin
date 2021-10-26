package mica;


import ij.plugin.PlugIn;
import mica.gui.BatchWindow;


public class BatchOMERO implements PlugIn {

	@Override
	public void run(String s) {
		new BatchWindow();
	}

}