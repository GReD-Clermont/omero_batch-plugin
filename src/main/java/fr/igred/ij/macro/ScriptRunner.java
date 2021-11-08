package fr.igred.ij.macro;

import ij.IJ;
import ij.gui.GenericDialog;


public class ScriptRunner {

	private final String path;
	private String arguments = "";


	public ScriptRunner(String path) {
		this.path = path;
	}


	private static boolean isSciJavaLoaded() {
		try {
			Class.forName("org.scijava.Context");
			Class.forName("org.scijava.module.ModuleException");
			Class.forName("org.scijava.module.ModuleItem");
			Class.forName("org.scijava.script.ScriptLanguage");
			Class.forName("org.scijava.script.ScriptModule");
			Class.forName("org.scijava.script.ScriptService");
			Class.forName("org.scijava.ui.swing.widget.SwingInputHarvester");
			Class.forName("org.scijava.ui.swing.widget.SwingInputPanel");
			return true;
		} catch (Exception e) {
			return false;
		}
	}


	public static ScriptRunner createScriptRunner(String path) {
		if (isSciJavaLoaded()) return new ScriptRunner2(path);
		else return new ScriptRunner(path);
	}


	public String getArguments() {
		return arguments;
	}


	public String getLanguage() {
		return path.substring(path.lastIndexOf('.'));
	}


	public void inputsDialog() {
		GenericDialog dialog = new GenericDialog("Input parameters");
		dialog.addStringField("Input parameters (separated by commas, eg: var1=x,var2=y)", arguments, 100);
		dialog.showDialog();
		if (dialog.wasOKed()) {
			arguments = dialog.getNextString();
		}
	}


	public void run() {
		try {
			int n = Integer.parseInt(arguments);
			arguments = String.valueOf(n + 1);
		} catch (NumberFormatException e) {
			if (arguments == null || arguments.isEmpty()) {
				arguments = "0";
			}
		}
		IJ.runMacroFile(path, arguments);
	}


	public void reset() {
		// NOTHING TO RESET
	}

}
