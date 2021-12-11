package fr.igred.ij.macro;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;

/**
 * Runs an ImageJ macro.
 */
public class ScriptRunner {

	private final String path;
	private String arguments = "";


	/**
	 * Creates a new object for the specified macro.
	 *
	 * @param path The path to the macro.
	 */
	public ScriptRunner(String path) {
		this.path = path;
	}


	/**
	 * Checks if SciJava is loaded.
	 *
	 * @return True if it is loaded.
	 */
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
		} catch (ClassNotFoundException e) {
			return false;
		}
	}


	/**
	 * Creates a new instance of ScriptRunner, or ScriptRunner2 if SciJava is loaded.
	 *
	 * @param path The path to the macro.
	 *
	 * @return A new ScriptRunner object.
	 */
	public static ScriptRunner createScriptRunner(String path) {
		if (isSciJavaLoaded()) return new ScriptRunner2(path);
		else return new ScriptRunner(path);
	}


	/**
	 * Sets the image to process.
	 *
	 * @param imp The image.
	 */
	public void setImage(ImagePlus imp) {
		IJ.selectWindow(imp.getID());
	}


	/**
	 * Retrieves the arguments for the macro.
	 *
	 * @return See above.
	 */
	public String getArguments() {
		return arguments;
	}


	/**
	 * Sets the arguments for the macro.
	 *
	 * @param arguments See above.
	 */
	public void setArguments(String arguments) {
		this.arguments = arguments;
	}


	/**
	 * Retrieves the script language (or the file extension).
	 *
	 * @return See above.
	 */
	public String getLanguage() {
		return path.substring(path.lastIndexOf('.'));
	}


	/**
	 * Displays an input dialog to define the input parameters.
	 */
	public void showInputDialog() {
		GenericDialog dialog = new GenericDialog("Input parameters");
		dialog.addStringField("Input parameters (separated by commas, eg: var1=x,var2=y)", arguments, 100);
		dialog.showDialog();
		if (dialog.wasOKed()) {
			this.setArguments(dialog.getNextString());
		}
	}


	/**
	 * Runs the macro.
	 */
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


	/**
	 * Resets the macro. Does nothing for macros in IJ1.
	 */
	public void reset() {
		// NOTHING TO RESET
	}

}
