package fr.igred.ij.macro;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import org.scijava.Context;
import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.ModuleException;
import org.scijava.module.ModuleItem;
import org.scijava.script.ScriptInfo;
import org.scijava.script.ScriptLanguage;
import org.scijava.script.ScriptModule;
import org.scijava.script.ScriptService;
import org.scijava.ui.swing.widget.SwingInputHarvester;
import org.scijava.ui.swing.widget.SwingInputPanel;

import java.io.File;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs an ImageJ2 script.
 */
public class ScriptRunner2 extends ScriptRunner {

	private final boolean detectedInputs;
	protected Map<String, Object> inputs;
	private ScriptModule script;
	private String language = "";


	/**
	 * Creates a new object for the specified script.
	 *
	 * @param path The path to the script.
	 */
	public ScriptRunner2(String path) {
		super(path);
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
			IJ.error("Could not create script module from script service.");
			script = new ScriptModule(new ScriptInfo(ctx, path));
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
		detectedInputs = !inputs.isEmpty();
		if (detectedInputs) {
			script.setInputs(inputs);
		}
	}


	/**
	 * Parses a number in a string.
	 *
	 * @param s A string.
	 *
	 * @return A number if the string contains one, the string itself otherwise.
	 */
	private static Object parseString(String s) {
		try {
			return NumberFormat.getInstance().parse(s);
		} catch (ParseException e) {
			return s;
		}
	}


	@Override
	public void setImage(ImagePlus imp) {
		boolean macro = "IJ1 Macro".equals(getLanguage())
						|| ".ijm".equals(getLanguage());
		if (detectedInputs || !macro) {
			for (ModuleItem<?> input : script.getInfo().inputs()) {
				if (input.getType().equals(ImagePlus.class)) {
					String imageArg = input.getName();
					script.unresolveInput(imageArg);
					script.setInput(imageArg, IJ.getImage());
					script.resolveInput(imageArg);
				}
			}
		} else {
			super.setImage(imp);
		}
	}


	@Override
	public String getArguments() {
		if (inputs == null || inputs.isEmpty()) {
			return super.getArguments();
		} else {
			return inputs.toString();
		}
	}


	@Override
	public void setArguments(String arguments) {
		super.setArguments(arguments);
		parseArguments();
		script.getInfo().clearParameters();
		script.getInfo().parseParameters();
		addInputs();
		script.setInputs(inputs);
	}


	@Override
	public String getLanguage() {
		return language.isEmpty() ? super.getLanguage() : language;
	}


	@Override
	public void showInputDialog() {
		if (detectedInputs) {
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
		} else {
			super.showInputDialog();
		}
	}


	@Override
	public void run() {
		boolean macro = "IJ1 Macro".equals(getLanguage())
						|| ".ijm".equals(getLanguage());
		if (detectedInputs || !macro) {
			for (ModuleItem<?> input : script.getInfo().inputs()) {
				if (input.getType().equals(ImagePlus.class) && script.getInput(input.getName()) == null) {
					String imageArg = input.getName();
					script.unresolveInput(imageArg);
					script.setInput(imageArg, IJ.getImage());
					script.resolveInput(imageArg);
				}
			}
			for (Map.Entry<String, Object> input : inputs.entrySet()) {
				script.resolveInput(input.getKey());
			}
			script.run();
		} else {
			super.run();
		}
	}


	@Override
	public void reset() {
		inputs.keySet().forEach(script::unresolveInput);
	}


	private void parseArguments() {
		String args = super.getArguments();
		if (getArguments().isEmpty()) {
			inputs.clear();
		} else {
			try {
				inputs = Arrays.stream(args.split(",")).map(s -> s.split("="))
							   .collect(Collectors.toMap(s -> s[0], s -> parseString(s[1])));
			} catch (ArrayIndexOutOfBoundsException e) {
				IJ.error("Wrong format for arguments");
			}
		}
	}


	private void addInputs() {
		for (Map.Entry<String, Object> input : inputs.entrySet()) {
			ScriptInfo scriptInfo = script.getInfo();
			ModuleItem<?> item = scriptInfo.getInput(input.getKey());
			if (item == null) {
				Object value = input.getValue();
				if (value.getClass().equals(Long.class)) {
					ModuleItem<Long> newInput = new DefaultMutableModuleItem<>(scriptInfo, input.getKey(), Long.class);
					scriptInfo.registerInput(newInput);
				} else if (value.getClass().equals(Double.class)) {
					ModuleItem<Double> newInput = new DefaultMutableModuleItem<>(scriptInfo, input.getKey(), Double.class);
					scriptInfo.registerInput(newInput);
				} else {
					ModuleItem<String> newInput = new DefaultMutableModuleItem<>(scriptInfo, input.getKey(), String.class);
					scriptInfo.registerInput(newInput);
				}
			}
		}
	}

}
