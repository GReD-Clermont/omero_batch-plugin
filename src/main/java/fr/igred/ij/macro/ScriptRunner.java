package fr.igred.ij.macro;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import org.scijava.Context;
import org.scijava.module.ModuleException;
import org.scijava.module.ModuleItem;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptModule;
import org.scijava.script.ScriptService;
import org.scijava.ui.swing.widget.SwingInputHarvester;
import org.scijava.ui.swing.widget.SwingInputPanel;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;


public class ScriptRunner {

	private final String path;

	private String arguments = "";

	private Map<String, Object> inputs;

	private ScriptModule script;

	private String language = "";

	private boolean detectedInputs;


	public ScriptRunner(String path) {
		this.path = path;
		setSciJavaScript(path);
	}


	private boolean isSciJavaLoaded() {
		try {
			Class.forName("org.scijava.Context");
			Class.forName("org.scijava.module.ModuleException");
			Class.forName("org.scijava.module.ModuleItem");
			Class.forName("org.scijava.script.ScriptModule");
			Class.forName("org.scijava.script.ScriptService");
			Class.forName("org.scijava.ui.swing.widget.SwingInputHarvester");
			Class.forName("org.scijava.ui.swing.widget.SwingInputPanel");
			return true;
		} catch (Exception e) {
			return false;
		}
	}


	public Map<String, Object> getInputs() {
		return inputs;
	}


	public String getArguments() {
		if (inputs == null || inputs.isEmpty()) {
			return arguments;
		} else {
			return getInputs().toString();
		}
	}


	public String getLanguage() {
		return language;
	}


	public void inputsDialog() {
		if (isSciJavaLoaded() && detectedInputs) {
			scijavaInputsDialog();
		} else {
			GenericDialog dialog = new GenericDialog("Input parameters");
			dialog.addStringField("Input parameters (separated by commas, eg: var1=x,var2=y)", arguments, 100);
			dialog.showDialog();
			if (dialog.wasOKed()) {
				arguments = dialog.getNextString();
				try {
					inputs = Arrays.stream(arguments.split(",")).map(s -> s.split("="))
								   .collect(Collectors.toMap(s -> s[0], s -> s[1]));
				} catch (ArrayIndexOutOfBoundsException e) {
					IJ.error("Wrong format for arguments");
				}
			}
		}
	}


	public void run() {
		boolean macro = getLanguage().equals("IJ1 Macro");
		if (isSciJavaLoaded() && detectedInputs && !macro) {
			scijavaRun();
		} else {
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
	}


	public void reset() {
		inputs.keySet().forEach(script::unresolveInput);
	}


	private void scijavaRun() {
		for (ModuleItem<?> input : script.getInfo().inputs()) {
			if (input.getType().equals(ImagePlus.class)) {
				String imageArg = input.getName();
				script.unresolveInput(imageArg);
				script.setInput(imageArg, IJ.getImage());
				script.resolveInput(imageArg);
			}
		}
		for (String key : inputs.keySet()) {
			script.resolveInput(key);
		}
		script.run();
	}


	private void scijavaInputsDialog() {
		SwingInputHarvester inputHarvester = new SwingInputHarvester();
		script.getContext().inject(inputHarvester);
		SwingInputPanel inputPanel = inputHarvester.createInputPanel();
		try {
			inputHarvester.buildPanel(inputPanel, script);
		} catch (ModuleException e) {
			IJ.error(e.getMessage());
		}
		boolean harvested = inputHarvester.harvestInputs(inputPanel, script);
		if (harvested) {
			inputs = script.getInputs();
			for (ModuleItem<?> input : script.getInfo().inputs()) {
				if (input.getType().equals(ImagePlus.class)) {
					inputs.remove(input.getName());
				}
			}
		}
	}


	private void setSciJavaScript(String path) {
		if (isSciJavaLoaded()) {
			final Context ctx = (Context) IJ.runPlugIn("org.scijava.Context", "");
			ScriptService scriptService = ctx.getService(ScriptService.class);
			scriptService.addAlias(ImagePlus.class);
			scriptService.addAlias("IJ1Overlay", Overlay.class);
			scriptService.addAlias(ResultsTable.class);
			scriptService.addAlias(RoiManager.class);

			try {
				script = scriptService.getScript(new File(path)).createModule();
				script.setContext(ctx);
			} catch (ModuleException e) {
				IJ.error("Could not create script module");
			}
			ScriptLanguage lang = script.getInfo().getLanguage();
			if (lang != null) {
				language = lang.getLanguageName();
			}

			inputs = new LinkedHashMap<>(script.getInputs().size());
			for (ModuleItem<?> input : script.getInfo().inputs()) {
				if (input.getType().equals(ImagePlus.class)) {
					script.resolveInput(input.getName());
				} else {
					inputs.put(input.getName(), input.getDefaultValue());
				}
			}
			detectedInputs = inputs.size() > 0;
			if(detectedInputs) {
				script.setInputs(inputs);
			}
		}
	}

}
