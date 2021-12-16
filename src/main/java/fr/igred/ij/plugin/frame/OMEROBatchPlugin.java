package fr.igred.ij.plugin.frame;

import fr.igred.ij.gui.OMEROConnectDialog;
import fr.igred.ij.gui.ProgressDialog;
import fr.igred.ij.macro.BatchListener;
import fr.igred.ij.macro.OMEROBatchRunner;
import fr.igred.ij.macro.ScriptRunner;
import fr.igred.omero.Client;
import fr.igred.omero.GenericObjectWrapper;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import ij.IJ;
import ij.Prefs;
import ij.plugin.frame.PlugInFrame;
import loci.plugins.config.SpringUtilities;

import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static javax.swing.JOptionPane.showMessageDialog;

/**
 * Main window for the OMERO batch plugin.
 */
public class OMEROBatchPlugin extends PlugInFrame implements BatchListener {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private static final String FORMAT = "%%-%ds (ID:%%%dd)";

	// connection management
	private final JLabel connectionStatus = new JLabel("Disconnected");
	private final JButton connect = new JButton("Connect");
	private final JButton disconnect = new JButton("Disconnect");

	// source selection
	private final JRadioButton omero = new JRadioButton("OMERO");
	private final JRadioButton local = new JRadioButton("Local");

	// choices of input images
	private final JPanel input1a = new JPanel();
	private final JPanel input1b = new JPanel();
	private final JPanel input1c = new JPanel();
	private final JPanel input2 = new JPanel();

	// group and user selection
	private final JComboBox<String> groupList = new JComboBox<>();
	private final JComboBox<String> userList = new JComboBox<>();

	// choice of the dataSet
	private final JComboBox<String> projectListIn = new JComboBox<>();
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	private final JCheckBox checkDelROIs = new JCheckBox(" Clear ROIs each time ");
	private final JCheckBox checkLoadROIs = new JCheckBox(" Load ROIs ");

	// choice of the record
	private final JTextField inputFolder = new JTextField(20);
	private final JCheckBox recursive = new JCheckBox("Recursive");
	private final JTextField macro = new JTextField(20);
	private final JLabel labelLanguage = new JLabel();
	private final JLabel labelArguments = new JLabel();
	private final JCheckBox checkImage = new JCheckBox("New image(s)");
	private final JCheckBox checkResults = new JCheckBox("Results table(s)");
	private final JCheckBox checkROIs = new JCheckBox("ROIs");
	private final JCheckBox checkLog = new JCheckBox("Log file");

	private final JPanel output1 = new JPanel();
	private final JTextField suffix = new JTextField(10);

	// Omero or local => checkbox
	private final JCheckBox onlineOutput = new JCheckBox("OMERO");
	private final JCheckBox localOutput = new JCheckBox("Local");

	// existing dataset
	private final JPanel output3a = new JPanel();
	private final JComboBox<String> projectListOut = new JComboBox<>();
	private final JComboBox<String> datasetListOut = new JComboBox<>();
	private final JButton newDatasetBtn = new JButton("New");

	// local
	private final JPanel output3b = new JPanel();
	private final JTextField outputFolder = new JTextField(20);

	// start button
	private final JButton start = new JButton("Start");

	//variables to keep
	private transient Client client;
	private transient ScriptRunner script;
	private transient List<GroupWrapper> groups;
	private transient List<ProjectWrapper> groupProjects;
	private transient List<ProjectWrapper> userProjects;
	private transient List<DatasetWrapper> datasets;
	private transient List<ProjectWrapper> myProjects;
	private transient List<DatasetWrapper> myDatasets;
	private transient List<ExperimenterWrapper> users;
	private transient ExperimenterWrapper exp;
	private String directoryOut = null;
	private String directoryIn = null;
	private Long outputDatasetId = null;
	private Long outputProjectId = null;


	/**
	 * Creates a new window.
	 */
	public OMEROBatchPlugin() {
		super("OMERO Batch Plugin");

		final int width = 720;
		final int height = 640;

		final String projectName = "Project Name: ";
		final String datasetName = "Dataset Name: ";
		final String browse = "Browse";

		final Font nameFont = new Font("Arial", Font.ITALIC, 10);
		final Font listFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
		final Font warnFont = new Font("Arial", Font.ITALIC + Font.BOLD, 12);

		final Color orange = new Color(250, 140, 0);

		final Dimension smallHorizontal = new Dimension(20, 0);
		final Dimension maxTextSize = new Dimension(300, 18);

		super.setSize(width, height);
		super.setMinimumSize(super.getSize());
		super.setLocationRelativeTo(null);
		super.addWindowListener(new ClientDisconnector());

		JPanel panelWarning = new JPanel();
		JLabel warning = new JLabel("Warning: all windows will be closed.");
		warning.setForeground(orange);
		warning.setFont(warnFont);
		panelWarning.add(warning);
		super.add(panelWarning);

		JPanel connection = new JPanel();
		JLabel labelConnection = new JLabel("Connection status: ");
		labelConnection.setLabelFor(connectionStatus);
		connectionStatus.setForeground(Color.RED);
		connection.add(labelConnection);
		connection.add(connectionStatus);
		connection.add(Box.createRigidArea(smallHorizontal));
		connection.add(connect);
		connection.add(disconnect);
		disconnect.setVisible(false);
		connect.addActionListener(e -> connect());
		disconnect.addActionListener(e -> disconnect());
		connection.setBorder(BorderFactory.createTitledBorder("Connection"));
		super.add(connection);

		JPanel source = new JPanel();
		JLabel labelEnterType = new JLabel("Where to get images to analyse:");
		ButtonGroup inputData = new ButtonGroup();
		inputData.add(omero);
		inputData.add(local);
		source.add(labelEnterType);
		source.add(omero);
		source.add(local);
		omero.addItemListener(this::updateInput);
		local.addItemListener(this::updateInput);
		source.setBorder(BorderFactory.createTitledBorder("Source"));
		super.add(source);

		JLabel labelGroup = new JLabel("Group Name: ");
		JLabel labelUser = new JLabel("User Name: ");
		labelGroup.setLabelFor(groupList);
		labelUser.setLabelFor(userList);
		input1a.add(labelGroup);
		input1a.add(groupList);
		input1a.add(Box.createRigidArea(smallHorizontal));
		input1a.add(labelUser);
		input1a.add(userList);
		groupList.addItemListener(this::updateGroup);
		userList.addItemListener(this::updateUser);
		groupList.setFont(listFont);
		userList.setFont(listFont);

		JLabel labelProjectIn = new JLabel(projectName);
		JLabel labelDatasetIn = new JLabel(datasetName);
		JButton preview = new JButton("Preview");
		labelProjectIn.setLabelFor(projectListIn);
		labelDatasetIn.setLabelFor(datasetListIn);
		input1b.add(labelProjectIn);
		input1b.add(projectListIn);
		input1b.add(Box.createRigidArea(smallHorizontal));
		input1b.add(labelDatasetIn);
		input1b.add(datasetListIn);
		input1b.add(Box.createRigidArea(smallHorizontal));
		input1b.add(preview);
		projectListIn.addItemListener(this::updateInputProject);
		datasetListIn.addItemListener(this::updateInputDataset);
		preview.addActionListener(e -> previewDataset());
		projectListIn.setFont(listFont);
		datasetListIn.setFont(listFont);

		input1c.add(checkLoadROIs);
		input1c.add(checkDelROIs);

		JLabel inputFolderLabel = new JLabel("Images folder: ");
		JButton inputFolderBtn = new JButton(browse);
		inputFolderLabel.setLabelFor(inputFolder);
		inputFolder.setMaximumSize(maxTextSize);
		inputFolder.setName("omero.batch.dir.input");
		input2.add(inputFolderLabel);
		input2.add(inputFolder);
		input2.add(inputFolderBtn);
		input2.add(recursive);
		inputFolderBtn.addActionListener(e -> chooseDirectory(inputFolder));

		JPanel panelInput = new JPanel();
		panelInput.add(input1a);
		panelInput.add(input1b);
		panelInput.add(input1c);
		panelInput.add(input2);
		local.setSelected(true);
		panelInput.setLayout(new BoxLayout(panelInput, BoxLayout.PAGE_AXIS));
		panelInput.setBorder(BorderFactory.createTitledBorder("Input"));
		super.add(panelInput);

		JPanel macro1 = new JPanel();
		JLabel macroLabel = new JLabel("Macro file: ");
		JButton macroBtn = new JButton(browse);
		JButton argsBtn = new JButton("Set arguments");
		macroLabel.setLabelFor(macro);
		macro.setName("omero.batch.macro");
		macro.setMaximumSize(maxTextSize);
		macro1.add(macroLabel);
		macro1.add(macro);
		macro1.add(macroBtn);
		macro1.add(argsBtn);
		macroBtn.addActionListener(e -> chooseMacro());
		argsBtn.addActionListener(e -> setArguments());

		JPanel macro2 = new JPanel();
		macro2.setLayout(new BoxLayout(macro2, BoxLayout.PAGE_AXIS));
		labelLanguage.setFont(nameFont);
		labelArguments.setFont(nameFont);
		macro2.add(labelLanguage);
		macro2.add(labelArguments);

		JPanel macro3 = new JPanel();
		macro3.setLayout(new BoxLayout(macro3, BoxLayout.LINE_AXIS));
		JLabel macroReturnLabel = new JLabel("The macro returns: ");
		macro3.add(macroReturnLabel);

		JPanel macro4 = new JPanel();
		macro4.setLayout(new BoxLayout(macro4, BoxLayout.LINE_AXIS));
		macro4.add(checkImage);
		macro4.add(checkResults);
		macro4.add(checkROIs);
		macro4.add(checkLog);
		checkImage.addActionListener(this::updateOutput);
		checkResults.addActionListener(this::updateOutput);
		checkROIs.addActionListener(this::updateOutput);
		checkLog.addActionListener(this::updateOutput);

		//choice of the macro
		JPanel panelMacro = new JPanel();
		panelMacro.add(macro1);
		panelMacro.add(macro2);
		panelMacro.add(Box.createRigidArea(smallHorizontal));
		panelMacro.add(macro3);
		panelMacro.add(macro4);
		panelMacro.setLayout(new BoxLayout(panelMacro, BoxLayout.PAGE_AXIS));
		panelMacro.setBorder(BorderFactory.createTitledBorder("Macro"));
		super.add(panelMacro);

		JLabel labelExtension = new JLabel("Suffix of output files:");
		labelExtension.setLabelFor(suffix);
		suffix.setText("_macro");
		output1.add(labelExtension);
		output1.add(suffix);
		output1.setVisible(false);

		JPanel output2 = new JPanel();
		JLabel labelRecordOption = new JLabel("Where to save results:");
		output2.add(labelRecordOption);
		output2.add(onlineOutput);
		output2.add(localOutput);
		onlineOutput.addActionListener(this::updateOutput);
		localOutput.addActionListener(this::updateOutput);


		JLabel labelProjectOut = new JLabel(projectName);
		JLabel labelDatasetOut = new JLabel(datasetName);
		labelProjectOut.setLabelFor(projectListOut);
		labelDatasetOut.setLabelFor(datasetListOut);
		output3a.add(labelProjectOut);
		output3a.add(projectListOut);
		output3a.add(Box.createRigidArea(smallHorizontal));
		output3a.add(labelDatasetOut);
		output3a.add(datasetListOut);
		output3a.add(Box.createRigidArea(smallHorizontal));
		output3a.add(newDatasetBtn);
		projectListOut.addItemListener(this::updateOutputProject);
		datasetListOut.addItemListener(this::updateOutputDataset);
		newDatasetBtn.addActionListener(this::createNewDataset);
		output3a.setVisible(false);
		projectListOut.setFont(listFont);
		datasetListOut.setFont(listFont);

		JLabel outputFolderLabel = new JLabel("Output folder: ");
		JButton directoryBtn = new JButton(browse);
		outputFolderLabel.setLabelFor(outputFolder);
		outputFolder.setMaximumSize(maxTextSize);
		outputFolder.setName("omero.batch.dir.output");
		output3b.add(outputFolderLabel);
		output3b.add(outputFolder);
		output3b.add(directoryBtn);
		directoryBtn.addActionListener(e -> chooseDirectory(outputFolder));
		output3b.setVisible(false);

		// choice of output
		JPanel panelOutput = new JPanel();
		panelOutput.add(output1);
		panelOutput.add(output2);
		panelOutput.add(output3a);
		panelOutput.add(output3b);
		panelOutput.setLayout(new BoxLayout(panelOutput, BoxLayout.PAGE_AXIS));
		panelOutput.setBorder(BorderFactory.createTitledBorder("Output"));
		super.add(panelOutput);

		// validation button
		JPanel panelBtn = new JPanel();
		panelBtn.add(start);
		start.addActionListener(this::start);
		super.add(panelBtn);
	}


	/**
	 * Formats the object name using its ID and some padding.
	 *
	 * @param name    Object name.
	 * @param id      Object ID.
	 * @param padName Padding used for the name.
	 * @param padId   Padding used for the ID.
	 *
	 * @return The formatted object qualifier.
	 */
	private static String format(String name, long id, int padName, int padId) {
		String format = String.format(FORMAT, padName, padId);
		return String.format(format, name, id);
	}


	/**
	 * Gets the padding value for a list of OMERO objects.
	 *
	 * @param objects The OMERO objects.
	 * @param mapper  The function applied to these objects.
	 * @param <T>     The type of object.
	 *
	 * @return The padding required.
	 */
	private static <T extends GenericObjectWrapper<?>>
	int getListPadding(Collection<T> objects, Function<? super T, ? extends Number> mapper) {
		return objects.stream()
					  .map(mapper)
					  .mapToInt(Number::intValue)
					  .max()
					  .orElse(0);
	}


	/**
	 * Shows a warning window.
	 *
	 * @param message The warning message.
	 */
	public static void warningWindow(String message) {
		showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
	}


	/**
	 * Shows a error window.
	 *
	 * @param message The error message.
	 */
	public static void errorWindow(String message) {
		showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}


	/**
	 * Displays a window to choose a folder.
	 *
	 * @param textField The related text field.
	 */
	private static void chooseDirectory(JTextField textField) {
		String pref = textField.getName();
		if(pref.isEmpty()) pref = "omero.batch." + Prefs.DIR_IMAGE;
		String previousDir = textField.getText();
		if(previousDir.isEmpty()) previousDir = Prefs.get(pref, previousDir);

		JFileChooser outputChoice = new JFileChooser(previousDir);
		outputChoice.setDialogTitle("Choose the directory");
		outputChoice.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal = outputChoice.showOpenDialog(null);
		outputChoice.setAcceptAllFileFilterUsed(false); // ????
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File absDir = new File(outputChoice.getSelectedFile()
											   .getAbsolutePath());
			if (absDir.exists() && absDir.isDirectory()) {
				textField.setText(absDir.toString());
				Prefs.set(pref, absDir.toString());
			} else {
				////find a way to prevent JFileChooser closure?
				errorWindow(String.format("Output:%nThe directory doesn't exist"));
			}
		}
	}


	/**
	 * Starts the plugin and displays the window.
	 *
	 * @param arg The arguments.
	 */
	@Override
	public void run(String arg) {
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		this.setVisible(true);
	}


	/**
	 * Retrieves user projects and datasets.
	 *
	 * @param username The OMERO user.
	 * @param userId   The user ID.
	 */
	public void userProjectsAndDatasets(String username, long userId) {
		if ("All members".equals(username)) {
			userProjects = groupProjects;
		} else {
			userProjects = groupProjects.stream()
										.filter(project -> project.getOwner().getId() == userId)
										.collect(Collectors.toList());
		}
		myProjects = groupProjects.stream()
								  .filter(project -> project.getOwner().getId() == exp.getId())
								  .collect(Collectors.toList());
	}


	/**
	 * Updates the display when the input dataset is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateInputDataset(ItemEvent e) {
		this.repack();
	}


	/**
	 * Updates the display when the input project is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateInputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = userProjects.get(index);
				this.datasets = project.getDatasets();
				this.datasets.sort(Comparator.comparing(DatasetWrapper::getName,
														String.CASE_INSENSITIVE_ORDER));
				datasetListIn.removeAllItems();
				int padName = getListPadding(datasets, d -> d.getName().length());
				int padId = getListPadding(datasets, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				for (DatasetWrapper d : this.datasets) {
					datasetListIn.addItem(format(d.getName(), d.getId(), padName, padId));
				}
				if (!this.datasets.isEmpty()) datasetListIn.setSelectedIndex(0);
			}
		}
	}


	/**
	 * Updates the display when the output dataset is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateOutputDataset(ItemEvent e) {
		this.repack();
	}


	/**
	 * Updates the display when the output project is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateOutputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = myProjects.get(index);
				this.myDatasets = project.getDatasets();
				this.myDatasets.sort(Comparator.comparing(DatasetWrapper::getName, String.CASE_INSENSITIVE_ORDER));
				datasetListOut.removeAllItems();
				int padName = getListPadding(myDatasets, d -> d.getName().length());
				int padId = getListPadding(myDatasets, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				for (DatasetWrapper d : this.myDatasets) {
					datasetListOut.addItem(format(d.getName(), d.getId(), padName, padId));
				}
				if (!this.datasets.isEmpty()) datasetListOut.setSelectedIndex(0);
			}
		}
	}


	/**
	 * Creates a new dataset and updates the display.
	 *
	 * @param e The event triggering this.
	 */
	private void createNewDataset(ActionEvent e) {
		int index = projectListOut.getSelectedIndex();
		ProjectWrapper project = myProjects.get(index);
		long id = -1;
		String name = (String) JOptionPane.showInputDialog(this,
														   "New dataset name:",
														   "Create a new dataset",
														   JOptionPane.QUESTION_MESSAGE,
														   null,
														   null,
														   null);
		if (name == null) return;
		try {
			DatasetWrapper newDataset = project.addDataset(client, name, "");
			id = newDataset.getId();
		} catch (ExecutionException | ServiceException | AccessException exception) {
			warningWindow("Could not create dataset: " + exception.getMessage());
		}
		projectListOut.setSelectedIndex(-1);
		projectListOut.setSelectedIndex(index);
		boolean searchOut = true;
		for (int i = 0; searchOut && i < myDatasets.size(); i++) {
			if (myDatasets.get(i).getId() == id) {
				datasetListOut.setSelectedIndex(i);
				searchOut = false;
			}
		}

		int inputProject = projectListIn.getSelectedIndex();
		projectListIn.setSelectedIndex(-1);
		projectListIn.setSelectedIndex(inputProject);

		boolean searchIn = true;
		long inputDatasetID = datasets.get(datasetListIn.getSelectedIndex()).getId();
		for (int i = 0; searchIn && i < datasets.size(); i++) {
			if (datasets.get(i).getId() == inputDatasetID) {
				datasetListIn.setSelectedIndex(i);
				searchIn = false;
			}
		}
	}


	/**
	 * Updates the display when the selected user is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateUser(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int index = userList.getSelectedIndex();
			String username = userList.getItemAt(index);
			long userId = -1;
			if (index >= 1) userId = users.get(index - 1).getId();
			userProjectsAndDatasets(username, userId);
			projectListIn.removeAllItems();
			projectListOut.removeAllItems();
			datasetListIn.removeAllItems();
			datasetListOut.removeAllItems();
			int padName = getListPadding(userProjects, p -> p.getName().length());
			int padId = getListPadding(userProjects, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (ProjectWrapper project : userProjects) {
				projectListIn.addItem(format(project.getName(), project.getId(), padName, padId));
			}
			int padMyName = getListPadding(myProjects, p -> p.getName().length());
			int padMyId = getListPadding(myProjects, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (ProjectWrapper project : myProjects) {
				projectListOut.addItem(format(project.getName(), project.getId(), padMyName, padMyId));
			}
			if (!userProjects.isEmpty()) projectListIn.setSelectedIndex(0);
			if (!myProjects.isEmpty()) projectListOut.setSelectedIndex(0);
		}
	}


	/**
	 * Updates the display when the selected group is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateGroup(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int index = groupList.getSelectedIndex();
			long id = groups.get(index).getId();
			String groupName = groups.get(index).getName();
			client.switchGroup(id);

			groupProjects = new ArrayList<>(0);
			try {
				groupProjects = client.getProjects();
			} catch (ServiceException | ExecutionException | AccessException exception) {
				LOGGER.warning(exception.getMessage());
			}
			groupProjects.sort(Comparator.comparing(ProjectWrapper::getName,
													String.CASE_INSENSITIVE_ORDER));
			try {
				GroupWrapper group = client.getGroup(groupName);
				users = group.getExperimenters();
			} catch (ExecutionException | ServiceException | AccessException exception) {
				LOGGER.warning(exception.getMessage());
			}
			users.sort(Comparator.comparing(ExperimenterWrapper::getUserName));
			userList.removeAllItems();

			userList.addItem("All members");
			int padName = getListPadding(users, u -> u.getUserName().length());
			int padId = getListPadding(users, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			int selected = 0;
			for (ExperimenterWrapper user : users) {
				userList.addItem(format(user.getUserName(), user.getId(), padName, padId));
				if (user.getId() == exp.getId()) {
					selected = users.indexOf(user) + 1;
				}
			}
			userList.setSelectedIndex(selected);
		}
	}


	/**
	 * Updates the display when the input selection is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateInput(ItemEvent e) {
		if (omero.isSelected()) {
			boolean connected = disconnect.isVisible();
			if (!connected) {
				connected = connect();
			}
			if (connected) {
				input1a.setVisible(true);
				input1b.setVisible(true);
				input1c.setVisible(true);
				input2.setVisible(false);
			} else {
				local.setSelected(true);
			}
		} else { //local.isSelected()
			input2.setVisible(true);
			checkDelROIs.setSelected(false);
			checkLoadROIs.setSelected(false);
			input1c.setVisible(false);
			input1b.setVisible(false);
			input1a.setVisible(false);
		}
		this.repack();
	}


	/**
	 * Displays a dialog to select the macro to run.
	 */
	private void chooseMacro() {
		String pref = macro.getName().isEmpty() ? "omero.batch.macro" : macro.getName();
		String previousMacro = macro.getText();
		if(previousMacro.isEmpty()) {
			previousMacro = Prefs.get(pref, previousMacro);
		}
		JFileChooser macroChoice = new JFileChooser(previousMacro);
		macroChoice.setDialogTitle("Choose the macro file");
		macroChoice.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int returnVal = macroChoice.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File absFile = new File(macroChoice.getSelectedFile().getAbsolutePath());
			if (absFile.exists() && !absFile.isDirectory()) {
				macro.setText(absFile.toString());
				Prefs.set(pref, absFile.toString());
			} else {
				//find a way to prevent JFileChooser closure?
				warningWindow(String.format("Macro:%nThe file doesn't exist"));
			}
		}
		if (!macro.getText().isEmpty()) {
			script = ScriptRunner.createScriptRunner(macro.getText());
			labelLanguage.setText("Language: " + script.getLanguage());
			labelArguments.setText("Arguments: " + script.getArguments());
		}
	}


	/**
	 * Displays a dialog to set input arguments.
	 */
	private void setArguments() {
		if (script != null) {
			script.showInputDialog();
			labelLanguage.setText("Language: " + script.getLanguage());
			labelArguments.setText("Arguments: " + script.getArguments());
		}
	}


	/**
	 * Re-enables start button and resets script state when batch process is over.
	 */
	@Override
	public void onThreadFinished() {
		start.setEnabled(true);
		script.reset();
	}


	/**
	 * Displays a connection dialog to connect to OMERO.
	 *
	 * @return True if the connection was successful.
	 */
	private boolean connect() {
		final Color green = new Color(0, 153, 0);
		boolean connected = false;
		if(client == null) client = new Client();
		OMEROConnectDialog connectDialog = new OMEROConnectDialog();
		connectDialog.connect(client);
		if (!connectDialog.wasCancelled()) {

			long groupId = client.getCurrentGroupId();

			try {
				exp = client.getUser(client.getUser().getUserName());
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error(e.getCause().getMessage());
			} catch (NoSuchElementException e) {
				IJ.error(e.getMessage());
				return false;
			}
			groups = exp.getGroups();
			groups.removeIf(g -> g.getId() <= 2);

			int padName = getListPadding(groups, g -> g.getName().length());
			int padId = getListPadding(groups, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (GroupWrapper group : groups) {
				groupList.addItem(format(group.getName(), group.getId(), padName, padId));
			}

			connectionStatus.setText("Connected");
			connectionStatus.setForeground(green);
			connect.setVisible(false);
			disconnect.setVisible(true);
			omero.setSelected(true);

			int index = -1;
			for (int i = 0; index < 0 && i < groups.size(); i++) {
				if (groups.get(i).getId() == groupId) index = i;
			}
			groupList.setSelectedIndex(-1);
			groupList.setSelectedIndex(index);
			connected = true;
		}
		return connected;
	}


	/**
	 * Disconnects from OMERO.
	 */
	private void disconnect() {
		client.disconnect();
		local.setSelected(true);
		onlineOutput.setSelected(false);
		updateOutput(null);
		connectionStatus.setText("Disconnected");
		connectionStatus.setForeground(Color.RED);
		connect.setVisible(true);
		disconnect.setVisible(false);
		groupList.removeAllItems();
		userList.removeAllItems();
	}


	/**
	 * Shows a new window to preview the current dataset.
	 */
	private void previewDataset() {
		final int thumbnailSize = 96;

		int index = datasetListIn.getSelectedIndex();
		DatasetWrapper dataset = datasets.get(index);
		try {
			JPanel panel = new JPanel(new SpringLayout());
			List<ImageWrapper> images = dataset.getImages(client);
			int nRows = Math.min(images.size(), 5);
			int missing = images.size() - nRows;
			List<ImageWrapper> truncated = images.subList(0, nRows);
			for (ImageWrapper i : truncated) {
				JLabel thumbnail = new JLabel(new ImageIcon(i.getThumbnail(client, thumbnailSize)));
				panel.add(thumbnail);

				JLabel name = new JLabel(i.getName());
				name.setLabelFor(thumbnail);
				panel.add(name);
			}
			if (missing != 0) {
				panel.add(new JLabel());
				JLabel etc = new JLabel("+ " + missing + " images not shown...");
				panel.add(etc);
				nRows++;
			}
			SpringUtilities.makeCompactGrid(panel, //parent
											nRows, 2,
											5, 5,  //initX, initY
											10, 10); //xPad, yPad
			showMessageDialog(this, panel, "Preview", JOptionPane.INFORMATION_MESSAGE);
		} catch (ServiceException | AccessException | OMEROServerError | ExecutionException | IOException e) {
			errorWindow(e.getMessage());
		}
	}


	/**
	 * Starts the batch processing.
	 *
	 * @param e The event triggering this.
	 */
	public void start(ActionEvent e) {
		ProgressDialog progress = new ProgressDialog();
		OMEROBatchRunner runner = new OMEROBatchRunner(script, client, progress);
		runner.setListener(this);

		// initiation of success variables
		boolean checkInput;
		boolean checkMacro = getMacro();
		boolean checkOutput = getOutput();

		// input data
		if (omero.isSelected()) {
			runner.setInputOnOMERO(true);
			int index = datasetListIn.getSelectedIndex();
			DatasetWrapper dataset = datasets.get(index);
			long inputDatasetId = dataset.getId();
			runner.setInputDatasetId(inputDatasetId);
			runner.setOutputDatasetId(inputDatasetId);
			checkInput = true;
		} else { // local.isSelected()
			runner.setInputOnOMERO(false);
			checkInput = getLocalInput();
			runner.setDirectoryIn(directoryIn);
			runner.setRecursive(recursive.isSelected());
		}

		if (!checkInput || !checkMacro || !checkOutput) {
			return;
		}

		// suffix
		runner.setSuffix(suffix.getText());

		runner.setLoadROIS(checkLoadROIs.isSelected());
		runner.setClearROIS(checkDelROIs.isSelected());
		runner.setSaveImage(checkImage.isSelected());
		runner.setSaveResults(checkResults.isSelected());
		runner.setSaveROIs(checkROIs.isSelected());
		runner.setSaveLog(checkLog.isSelected());
		if (onlineOutput.isSelected()) {
			runner.setOutputOnOMERO(true);
			if (checkResults.isSelected()) {
				runner.setOutputProjectId(outputProjectId);
			}
			if (checkImage.isSelected()) {
				runner.setOutputDatasetId(outputDatasetId);
			}
		}
		if (localOutput.isSelected()) {
			runner.setOutputOnLocal(true);
			runner.setDirectoryOut(directoryOut);
		}

		start.setEnabled(false);
		try {
			runner.start();
		} catch (RuntimeException exception) {
			errorWindow(exception.getMessage());
		}
	}


	/**
	 * Updates the display when the output selection is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateOutput(ActionEvent e) {
		boolean outputOnline = onlineOutput.isSelected();
		boolean outputLocal = localOutput.isSelected();
		boolean outputImage = checkImage.isSelected();
		boolean outputResults = checkResults.isSelected();

		if (outputOnline && !disconnect.isVisible()) {
			outputOnline = connect();
			onlineOutput.setSelected(outputOnline);
		}

		output1.setVisible(outputImage);
		output3a.setVisible(outputOnline && (outputImage || outputResults));
		datasetListOut.setVisible(outputOnline && outputImage);
		newDatasetBtn.setVisible(outputOnline && outputImage);
		if (outputOnline && userProjects.equals(myProjects)) {
			projectListOut.setSelectedIndex(projectListIn.getSelectedIndex());
		}
		output3b.setVisible(outputLocal);
	}


	/**
	 * Retrieves the local input from the text field and stores it in the corresponding attribute.
	 *
	 * @return True if the input is ok.
	 */
	private boolean getLocalInput() {
		boolean check = false;
		if (inputFolder.getText().isEmpty()) {
			errorWindow(String.format("Input:%nNo directory selected"));
		} else {
			directoryIn = inputFolder.getText();
			File directoryInF = new File(directoryIn);
			if (directoryInF.exists() && directoryInF.isDirectory()) {
				check = true;
			} else {
				String msg = String.format("Input:%n The directory %s does not exist", directoryIn);
				errorWindow(msg);
			}
		}
		return check;
	}


	/**
	 * Retrieves the selected macro from the text field and stores it in the corresponding attribute.
	 *
	 * @return True if the file exists.
	 */
	private boolean getMacro() {
		boolean check = false;
		// macro file (mandatory)
		if (macro.getText().isEmpty()) {
			errorWindow(String.format("Macro:%nNo macro selected"));
		} else {
			String macroChosen = macro.getText();
			File macroFile = new File(macroChosen);
			if (macroFile.exists() && !macroFile.isDirectory()) {
				check = true;
			} else {
				String msg = String.format("Macro:%n The file %s does not exist", macroChosen);
				errorWindow(msg);
			}
		}
		return check;
	}


	/**
	 * Retrieves the selected OMERO output and stores it in the corresponding attribute.
	 *
	 * @return True if the selection is ok.
	 */
	private boolean getOMEROOutput() {
		boolean check = false;
		if (onlineOutput.isSelected()) {
			int projIndex = projectListOut.getSelectedIndex();
			int datIndex = datasetListOut.getSelectedIndex();
			if (projIndex == -1 || projIndex > myProjects.size()) {
				errorWindow(String.format("Output:%nNo project selected"));
			} else if (datIndex == -1 || datIndex > myDatasets.size()) {
				errorWindow(String.format("Output:%nNo dataset selected"));
			} else {
				check = true;
				DatasetWrapper dataset = myDatasets.get(datIndex);
				ProjectWrapper project = myProjects.get(projIndex);
				outputDatasetId = dataset.getId();
				outputProjectId = project.getId();
			}
		}
		return check;
	}


	/**
	 * Retrieves the selected local output from the text field and stores it in the corresponding attribute.
	 *
	 * @return True if the selection is ok.
	 */
	private boolean getLocalOutput() {
		boolean check = false;
		if (localOutput.isSelected()) {
			if (outputFolder.getText().isEmpty()) {
				errorWindow(String.format("Output:%nNo directory selected"));
			} else {
				directoryOut = outputFolder.getText();
				File directoryOutFile = new File(directoryOut);
				if (directoryOutFile.exists() && directoryOutFile.isDirectory()) {
					check = true;
				} else {
					String msg = String.format("Output:%n The directory %s does not exist", directoryOut);
					errorWindow(msg);
				}
			}
		}
		return check;
	}


	/**
	 * Checks if "delete ROIs" can be selected.
	 *
	 * @return See above.
	 */
	private boolean checkDeleteROIs() {
		boolean check = true;
		if (checkDelROIs.isSelected() && (!onlineOutput.isSelected() || !checkROIs.isSelected())) {
			errorWindow(String.format("ROIs:%nYou can't clear ROIs if you don't save ROIs on OMERO"));
			check = false;
		}
		return check;
	}


	/**
	 * Checks if uploads to OMERO can be selected (tables or ROIs).
	 *
	 * @return See above.
	 */
	private boolean checkUploadLocalInput() {
		boolean check = true;
		if (local.isSelected() && onlineOutput.isSelected() && !checkImage.isSelected()) {
			errorWindow(String.format("Output:%nYou can't upload results file or ROIs on OMERO if your image isn't in OMERO"));
			check = false;
		}
		return check;
	}


	/**
	 * Checks if at least one output is selected.
	 *
	 * @return See above.
	 */
	private boolean checkSelectedOutput() {
		boolean check = true;
		if (!checkResults.isSelected() && !checkROIs.isSelected() && !checkImage.isSelected()) {
			errorWindow(String.format("Macro:%nYou have to choose at least one output"));
			check = false;
		}
		return check;
	}


	/**
	 * Checks the output selections and stores it in the appropriate fields.
	 *
	 * @return True if the selected parameters are ok.
	 */
	private boolean getOutput() {
		boolean omeroCheck = getOMEROOutput();
		boolean localCheck = getLocalOutput();
		boolean check = omeroCheck || localCheck;

		if (!onlineOutput.isSelected() && !localOutput.isSelected()) {
			errorWindow(String.format("Output:%nYou have to choose the localisation to save the results"));
			check = false;
		}

		check &= checkSelectedOutput();
		check &= checkUploadLocalInput();
		check &= checkDeleteROIs();

		return check;
	}


	/**
	 * Repacks this window.
	 */
	private void repack() {
		Dimension minSize = this.getMinimumSize();
		this.setMinimumSize(this.getSize());
		this.pack();
		this.setMinimumSize(minSize);
	}


	private class ClientDisconnector extends WindowAdapter {

		ClientDisconnector() {
			super();
		}


		@Override
		public void windowClosing(WindowEvent e) {
			super.windowClosing(e);
			Client c = client;
			if(c != null) c.disconnect();
		}

	}

}