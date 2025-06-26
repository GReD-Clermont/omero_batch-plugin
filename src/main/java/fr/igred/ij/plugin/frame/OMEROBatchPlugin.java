/*
 *  Copyright (C) 2021-2023 MICA & GReD
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.

 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package fr.igred.ij.plugin.frame;


import fr.igred.ij.gui.OMEROConnectDialog;
import fr.igred.ij.gui.ProgressDialog;
import fr.igred.ij.io.BatchImage;
import fr.igred.ij.io.ROIMode;
import fr.igred.ij.macro.BatchListener;
import fr.igred.ij.macro.BatchParameters;
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
import fr.igred.omero.repository.PlateAcquisitionWrapper;
import fr.igred.omero.repository.PlateWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import fr.igred.omero.repository.ScreenWrapper;
import ij.IJ;
import ij.Prefs;
import ij.plugin.frame.PlugInFrame;
import loci.plugins.config.SpringUtilities;

import javax.swing.*;
import java.awt.Color;
import java.awt.Container;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static fr.igred.ij.io.LocalBatchImage.listImages;
import static fr.igred.ij.io.OMEROBatchImage.listImages;
import static javax.swing.JOptionPane.showMessageDialog;


/**
 * Main window for the OMERO batch plugin.
 */
public class OMEROBatchPlugin extends PlugInFrame implements BatchListener {

	/** The logger. */
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	/** The format used to display OMERO objects. */
	private static final String FORMAT = "%%-%ds (ID:%%%dd)";

	/** The minimum window size */
	private final Dimension minimumSize = new Dimension(640, 480);

	// connection management
	/** The connection status. */
	private final JLabel connectionStatus = new JLabel("Disconnected");
	/** The connection button. */
	private final JButton connect = new JButton("Connect");
	/** The disconnection button. */
	private final JButton disconnect = new JButton("Disconnect");

	// source selection
	/** The OMERO input button. */
	private final JRadioButton omero = new JRadioButton("OMERO");
	/** The local input button. */
	private final JRadioButton local = new JRadioButton("Local");
	/** The OMERO input button. */
	private final JRadioButton omeroProjects = new JRadioButton("Projects");
	/** The local input button. */
	private final JRadioButton omeroScreens = new JRadioButton("Screens");

	// group and user selection
	/** The list of groups. */
	private final JComboBox<String> groupList = new JComboBox<>();
	/** The list of users. */
	private final JComboBox<String> userList = new JComboBox<>();

	// choice of the dataSet
	/** The list of projects. */
	private final JComboBox<String> projectListIn = new JComboBox<>();
	/** The list of projects. */
	private final JComboBox<String> screenListIn = new JComboBox<>();
	/** The list of datasets. */
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	/** The list of plate acquisition. */
	private final JComboBox<String> plateAcqListIn = new JComboBox<>();
	/** The list of plates. */
	private final JComboBox<String> plateListIn = new JComboBox<>();
	/** The checkbox to delete ROIs. */
	private final JCheckBox checkDelROIs = new JCheckBox("Clear ROIs each time");
	/** The list of possible output projects. */
	private final JComboBox<ROIMode> roiMode = new JComboBox<>(ROIMode.values());

	// choice of the record
	/** The input folder. */
	private final JTextField inputFolder = new JTextField(20);
	/** The checkbox to analyse subfolders. */
	private final JCheckBox recursive = new JCheckBox("Recursive");
	/** The macro file. */
	private final JTextField macro = new JTextField(20);
	/** The macro language label. */
	private final JLabel labelLanguage = new JLabel();
	/** The macro arguments label. */
	private final JLabel labelArguments = new JLabel();
	/** The checkbox to save images. */
	private final JCheckBox checkImage = new JCheckBox("New image(s)");
	/** The checkbox to save results. */
	private final JCheckBox checkResults = new JCheckBox("Results table(s)");
	/** The checkbox to save ROIs. */
	private final JCheckBox checkROIs = new JCheckBox("ROIs");
	/** The checkbox to save the log. */
	private final JCheckBox checkLog = new JCheckBox("Log file");

	/** The suffix of the output files. */
	private final JTextField suffix = new JTextField(10);

	// Omero or local => checkbox
	/** The checkbox to save to OMERO. */
	private final JCheckBox onlineOutput = new JCheckBox("OMERO");
	/** The checkbox to save locally. */
	private final JCheckBox localOutput = new JCheckBox("Local");

	/** The list of possible output projects. */
	private final JComboBox<String> projectListOut = new JComboBox<>();
	/** The list of possible output screens. */
	private final JComboBox<String> screenListOut = new JComboBox<>();
	/** The list of possible output datasets. */
	private final JComboBox<String> datasetListOut = new JComboBox<>();
	/** The list of possible output datasets. */
	private final JTextField newDatasetOut = new JTextField(20);
	/** The list of possible output plates. */
	private final JComboBox<String> plateListOut = new JComboBox<>();

	/** The output folder. */
	private final JTextField outputFolder = new JTextField(20);

	/** The start button. */
	private final JButton start = new JButton("Start");

	//variables to keep
	/** The OMERO client. */
	private transient Client client;
	/** The script runner. */
	private transient ScriptRunner script;
	/** The groups. */
	private transient List<GroupWrapper> groups;
	/** The group projects. */
	private transient List<ProjectWrapper> groupProjects;
	/** The group screens. */
	private transient List<ScreenWrapper> groupScreens;
	/** The selected user's projects. */
	private transient List<ProjectWrapper> userProjects;
	/** The selected user's screens. */
	private transient List<ScreenWrapper> userScreens;
	/** The datasets. */
	private transient List<DatasetWrapper> datasets;
	/** The plates. */
	private transient List<PlateWrapper> plates;
	/** The plates acquisitions. */
	private transient List<PlateAcquisitionWrapper> plateAcquisitions;
	/** The current user's projects. */
	private transient List<ProjectWrapper> myProjects;
	/** The current user's screens. */
	private transient List<ScreenWrapper> myScreens;
	/** The current user's datasets. */
	private transient List<DatasetWrapper> myDatasets;
	/** The users. */
	private transient List<ExperimenterWrapper> users;
	/** The current user. */
	private transient ExperimenterWrapper exp;
	/** The output directory. */
	private String directoryOut = null;
	/** The input directory. */
	private String directoryIn = null;
	/** The output dataset ID. */
	private Long outputDatasetId = null;
	/** The output project ID. */
	private Long outputProjectId = null;
	/** The output project ID. */
	private Long outputScreenId = null;


	/**
	 * Creates a new window.
	 */
	public OMEROBatchPlugin() {
		super("OMERO Batch Plugin");
		super.setMinimumSize(minimumSize);

		final String projectName = "Project: ";
		final String datasetName = "Dataset: ";
		final String screenName = "Screen: ";
		final String plateName = "Plate: ";
		final String plateAcquisitionName = "PlateAcquisition: ";
		final String browse = "Browse";

		final Font nameFont = new Font("Arial", Font.ITALIC, 10);
		final Font listFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
		final Font warnFont = new Font("Arial", Font.ITALIC + Font.BOLD, 12);

		final Color orange = new Color(250, 140, 0);

		final Dimension smallHorizontal = new Dimension(20, 0);
		final Dimension maxTextSize = new Dimension(300, 18);

		super.setSize(minimumSize);
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

		JPanel input1a = new JPanel();
		JLabel labelGroup = new JLabel("Group: ");
		JLabel labelUser = new JLabel("User: ");
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

		JPanel omeroSource = new JPanel();
		JLabel omeroLabelEnterType = new JLabel("Where to get images from OMERO: ");
		ButtonGroup omeroInputData = new ButtonGroup();
		omeroInputData.add(omeroProjects);
		omeroInputData.add(omeroScreens);
		omeroSource.add(omeroLabelEnterType);
		omeroSource.add(omeroProjects);
		omeroSource.add(omeroScreens);
		omeroProjects.addItemListener(this::updateInput);
		omeroScreens.addItemListener(this::updateInput);
		omeroSource.setVisible(false);

		JPanel input1b = new JPanel();
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

		JPanel input1c = new JPanel();
		JLabel labelScreenIn = new JLabel(screenName);
		JLabel labelPlateIn = new JLabel(plateName);
		JLabel labelPlateAcqIn = new JLabel(plateAcquisitionName);
		JButton preview2 = new JButton("Preview");
		labelScreenIn.setLabelFor(screenListIn);
		labelPlateIn.setLabelFor(plateListIn);
		input1c.add(labelScreenIn);
		input1c.add(screenListIn);
		input1c.add(Box.createRigidArea(smallHorizontal));
		input1c.add(labelPlateIn);
		input1c.add(plateListIn);
		input1c.add(Box.createRigidArea(smallHorizontal));
		input1c.add(labelPlateAcqIn);
		input1c.add(plateAcqListIn);
		input1c.add(Box.createRigidArea(smallHorizontal));
		input1c.add(preview2);
		screenListIn.addItemListener(this::updateInputScreen);
		plateListIn.addItemListener(this::updateInputPlate);
		preview2.addActionListener(e -> previewPlate());
		screenListIn.setFont(listFont);
		plateListIn.setFont(listFont);
		plateAcqListIn.setFont(listFont);


		JPanel input2 = new JPanel();
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

		JPanel input3 = new JPanel();
		JLabel labelROIMode = new JLabel("Load ROIs: ");
		labelROIMode.setLabelFor(roiMode);
		input3.add(labelROIMode);
		input3.add(roiMode);
		input3.add(checkDelROIs);

		JPanel panelInput = new JPanel();
		panelInput.add(input1a);
		panelInput.add(omeroSource);
		panelInput.add(input1b);
		panelInput.add(input1c);
		panelInput.add(input2);
		panelInput.add(input3);
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
		JPanel macro2i = new JPanel();
		macro2i.setLayout(new BoxLayout(macro2i, BoxLayout.PAGE_AXIS));
		labelLanguage.setText("L");
		labelArguments.setText("A");
		labelLanguage.setFont(nameFont);
		labelArguments.setFont(nameFont);
		macro2i.add(labelLanguage);
		macro2i.add(labelArguments);
		macro2.add(macro2i);

		JPanel macro3 = new JPanel();
		JLabel macroReturnLabel = new JLabel("The macro returns: ");
		macro3.add(macroReturnLabel);

		JPanel macro4 = new JPanel();
		JPanel macro4i = new JPanel();
		macro4i.setLayout(new BoxLayout(macro4i, BoxLayout.LINE_AXIS));
		macro4i.add(checkImage);
		macro4i.add(checkResults);
		macro4i.add(checkROIs);
		macro4i.add(checkLog);
		checkImage.addActionListener(this::updateOutput);
		checkResults.addActionListener(this::updateOutput);
		checkROIs.addActionListener(this::updateOutput);
		checkLog.addActionListener(this::updateOutput);
		macro4.add(macro4i);

		//choice of the macro
		JPanel panelMacro = new JPanel();
		panelMacro.add(macro1);
		panelMacro.add(macro2);
		panelMacro.add(macro3);
		panelMacro.add(macro4);
		panelMacro.setLayout(new BoxLayout(panelMacro, BoxLayout.PAGE_AXIS));
		panelMacro.setBorder(BorderFactory.createTitledBorder("Macro"));
		super.add(panelMacro);

		JPanel output1 = new JPanel();
		JLabel labelRecordOption = new JLabel("Where to save results:");
		output1.add(labelRecordOption);
		output1.add(onlineOutput);
		output1.add(localOutput);
		onlineOutput.addActionListener(this::updateOutput);
		localOutput.addActionListener(this::updateOutput);

		JPanel output2 = new JPanel();
		JLabel labelExtension = new JLabel("Suffix of output files:");
		labelExtension.setLabelFor(suffix);
		suffix.setText("_macro");
		output2.add(labelExtension);
		output2.add(suffix);

		JPanel output3a = new JPanel();
		JPanel output3a1 = new JPanel();
		JButton newDatasetBtn = new JButton("New");
		JLabel labelProjectOut = new JLabel(projectName);
		JLabel labelDatasetOut = new JLabel(datasetName);
		labelProjectOut.setLabelFor(projectListOut);
		labelDatasetOut.setLabelFor(datasetListOut);
		output3a.add(labelProjectOut);
		output3a.add(projectListOut);
		output3a1.add(labelDatasetOut);
		output3a1.add(datasetListOut);
		output3a1.add(Box.createRigidArea(smallHorizontal));
		output3a1.add(newDatasetBtn);
		output3a.add(output3a1);
		projectListOut.addItemListener(this::updateOutputProject);
		datasetListOut.addItemListener(this::updateOutputDataset);
		newDatasetBtn.addActionListener(this::createNewDataset);
		projectListOut.setFont(listFont);
		datasetListOut.setFont(listFont);

		JPanel output3b = new JPanel();
		JPanel output3b1 = new JPanel();
		JLabel labelScreenOut = new JLabel(screenName);
		labelProjectOut.setLabelFor(screenListOut);
		output3b.add(labelScreenOut);
		output3b.add(screenListOut);
		JLabel labelNewDatasetForPlate = new JLabel("Dataset to create:");
		labelExtension.setLabelFor(newDatasetOut);
		newDatasetOut.setText("");
		output3b1.add(labelNewDatasetForPlate);
		output3b1.add(newDatasetOut);
		output3b.add(output3b1);
		screenListOut.addItemListener(this::updateOutputScreen);
		screenListOut.setFont(listFont);

		JPanel output3c = new JPanel();
		JLabel outputFolderLabel = new JLabel("Output folder: ");
		JButton directoryBtn = new JButton(browse);
		outputFolderLabel.setLabelFor(outputFolder);
		outputFolder.setMaximumSize(maxTextSize);
		outputFolder.setName("omero.batch.dir.output");
		output3c.add(outputFolderLabel);
		output3c.add(outputFolder);
		output3c.add(directoryBtn);
		directoryBtn.addActionListener(e -> chooseDirectory(outputFolder));

		// choice of output
		JPanel panelOutput = new JPanel();
		panelOutput.add(output1);
		panelOutput.add(output2);
		panelOutput.add(output3a);
		panelOutput.add(output3b);
		panelOutput.add(output3c);
		panelOutput.setLayout(new BoxLayout(panelOutput, BoxLayout.PAGE_AXIS));
		panelOutput.setBorder(BorderFactory.createTitledBorder("Output"));
		super.add(panelOutput);

		// validation button
		JPanel panelBtn = new JPanel();
		panelBtn.add(start);
		start.addActionListener(this::start);
		super.add(panelBtn);

		// Adjust input panel sizes
		input2.setVisible(false);
		input1a.setVisible(true);
		input1b.setVisible(true);
		input1c.setVisible(false);
		input3.setVisible(true);
		super.pack();

		input1a.setMaximumSize(new Dimension(input1a.getMaximumSize().width, input1a.getHeight()));
		input1b.setMaximumSize(new Dimension(input1b.getMaximumSize().width, input1b.getHeight()));
		input1c.setMaximumSize(new Dimension(input1c.getMaximumSize().width, input1c.getHeight()));
		input3.setMaximumSize(new Dimension(input3.getMaximumSize().width, input3.getHeight()));

		local.setSelected(true);

		// Adjust other panel sizes
		super.pack();

		panelWarning.setMaximumSize(new Dimension(panelWarning.getMaximumSize().width, warning.getHeight()));
		connection.setMaximumSize(new Dimension(connection.getMaximumSize().width, connection.getHeight()));
		source.setMaximumSize(new Dimension(connection.getMaximumSize().width, source.getHeight()));
		input2.setMaximumSize(new Dimension(input2.getMaximumSize().width, input2.getHeight()));
		panelInput.setMaximumSize(new Dimension(panelInput.getMaximumSize().width, panelInput.getHeight()));

		macro1.setMaximumSize(new Dimension(macro1.getMaximumSize().width, macro1.getHeight()));
		macro2.setMaximumSize(new Dimension(macro2.getMaximumSize().width, macro2.getHeight()));
		macro3.setMaximumSize(new Dimension(macro3.getMaximumSize().width, macro3.getHeight()));
		macro4.setPreferredSize(new Dimension(macro4.getPreferredSize().width, macro4.getHeight()));
		panelMacro.setMaximumSize(new Dimension(panelMacro.getMaximumSize().width, panelMacro.getHeight()));

		labelLanguage.setText("");
		labelArguments.setText("");
		output2.setVisible(false);
		output3a.setVisible(false);
		output3b.setVisible(false);
		output3b1.setVisible(false);
		output3c.setVisible(false);

		// Initial pack
		super.pack();
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
	 * Shows an error window.
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
		if (pref.isEmpty()) {
			pref = "omero.batch." + Prefs.DIR_IMAGE;
		}
		String previousDir = textField.getText();
		if (previousDir.isEmpty()) {
			previousDir = Prefs.get(pref, previousDir);
		}

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
	 * Retrieves user projects
	 *
	 * @param username The OMERO user.
	 * @param userId   The user ID.
	 */
	private void userProjects(String username, long userId) {
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
	 * Retrieves user screens
	 *
	 * @param username The OMERO user.
	 * @param userId   The user ID.
	 */
	private void userScreens(String username, long userId) {
		if ("All members".equals(username)) {
			userScreens = groupScreens;
		} else {
			userScreens = groupScreens.stream()
									  .filter(screen -> screen.getOwner().getId() == userId)
									  .collect(Collectors.toList());
		}
		myScreens = groupScreens.stream()
								.filter(screen -> screen.getOwner().getId() == exp.getId())
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
				if (!this.datasets.isEmpty()) {
					datasetListIn.setSelectedIndex(0);
				}

				this.repack();
			}
		}
	}


	/**
	 * Updates the display when the input screen is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateInputScreen(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ScreenWrapper screen = userScreens.get(index);
				this.plates = screen.getPlates();
				this.plates.sort(Comparator.comparing(PlateWrapper::getName,
													  String.CASE_INSENSITIVE_ORDER));
				plateListIn.removeAllItems();
				int padName = getListPadding(plates, d -> d.getName().length());
				int padId = getListPadding(plates, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				for (PlateWrapper d : this.plates) {
					plateListIn.addItem(format(d.getName(), d.getId(), padName, padId));
				}
				if (!this.plates.isEmpty()) {
					plateListIn.setSelectedIndex(0);
				}

				this.repack();
			}
		}
	}


	/**
	 * Updates the display when the input plate is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateInputPlate(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				PlateWrapper plate = this.plates.get(index);
				this.plateAcquisitions = plate.getPlateAcquisitions();
				this.plateAcquisitions.sort(Comparator.comparing(PlateAcquisitionWrapper::getName,
																 String.CASE_INSENSITIVE_ORDER));
				plateAcqListIn.removeAllItems();
				int padName = getListPadding(plateAcquisitions, n -> n.getName().length());
				int padId = getListPadding(plateAcquisitions, g -> (int) (StrictMath.log10(g.getId()))) + 1;

				for (PlateAcquisitionWrapper d : this.plateAcquisitions) {
					String name = d.getName();
					if (padName == 0) {
						padName = 4 + padId;
					}
					if (name.isEmpty()) {
						name = "Run " + d.getId();
					}
					plateAcqListIn.addItem(format(name, d.getId(), padName, padId));
				}
				if (!this.plateAcquisitions.isEmpty()) {
					plateAcqListIn.insertItemAt("All acquisitions", 0);
					plateAcqListIn.setSelectedIndex(0);
				}

				this.repack();
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
				if (!this.datasets.isEmpty()) {
					datasetListOut.setSelectedIndex(0);
				}

				this.repack();
			}
		}
	}


	/**
	 * Updates the display when the output screen is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateOutputScreen(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ScreenWrapper screen = myScreens.get(index);
				/* The current user's plates. */
				List<PlateWrapper> myPlates = screen.getPlates();
				myPlates.sort(Comparator.comparing(PlateWrapper::getName, String.CASE_INSENSITIVE_ORDER));
				plateListOut.removeAllItems();
				int padName = getListPadding(myPlates, d -> d.getName().length());
				int padId = getListPadding(myPlates, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				for (PlateWrapper d : myPlates) {
					plateListOut.addItem(format(d.getName(), d.getId(), padName, padId));
				}
				if (!myPlates.isEmpty()) {
					plateListOut.setSelectedIndex(0);
				}

				this.repack();
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
		if (name == null) {
			return;
		}
		try {
			DatasetWrapper newDataset = project.addDataset(client, name, "");
			project.reload(client);
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
			if (index >= 1) {
				userId = users.get(index - 1).getId();
			}

			userProjects(username, userId);
			projectListIn.removeAllItems();
			projectListOut.removeAllItems();
			datasetListIn.removeAllItems();
			datasetListOut.removeAllItems();
			for (ProjectWrapper project : userProjects) {
				int padName = getListPadding(userProjects, p -> p.getName().length());
				int padId = getListPadding(userProjects, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				projectListIn.addItem(format(project.getName(), project.getId(), padName, padId));
			}
			int padMyName = getListPadding(myProjects, p -> p.getName().length());
			int padMyId = getListPadding(myProjects, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (ProjectWrapper project : myProjects) {
				projectListOut.addItem(format(project.getName(), project.getId(), padMyName, padMyId));
			}
			if (!userProjects.isEmpty()) {
				projectListIn.setSelectedIndex(0);
			}
			if (!myProjects.isEmpty()) {
				projectListOut.setSelectedIndex(0);
			}

			userScreens(username, userId);
			screenListIn.removeAllItems();
			screenListOut.removeAllItems();
			plateListIn.removeAllItems();
			plateAcqListIn.removeAllItems();
			plateListOut.removeAllItems();
			for (ScreenWrapper project : userScreens) {
				int padName = getListPadding(userScreens, p -> p.getName().length());
				int padId = getListPadding(userScreens, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				screenListIn.addItem(format(project.getName(), project.getId(), padName, padId));
			}
			padMyName = getListPadding(myScreens, p -> p.getName().length());
			padMyId = getListPadding(myScreens, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (ScreenWrapper screen : myScreens) {
				screenListOut.addItem(format(screen.getName(), screen.getId(), padMyName, padMyId));
			}
			if (!userScreens.isEmpty()) {
				screenListIn.setSelectedIndex(0);
			}
			if (!myScreens.isEmpty()) {
				screenListOut.setSelectedIndex(0);
			}
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

			groupScreens = new ArrayList<>(0);
			try {
				groupScreens = client.getScreens();
			} catch (ServiceException | ExecutionException | AccessException exception) {
				LOGGER.warning(exception.getMessage());
			}
			groupScreens.sort(Comparator.comparing(ScreenWrapper::getName,
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
				omeroProjects.setSelected(true);
			}
			if (connected) {
				groupList.getParent().setVisible(true);
				projectListIn.getParent().setVisible(true);
				omeroProjects.getParent().setVisible(true);
				inputFolder.getParent().setVisible(false);
				if (omeroProjects.isSelected()) {
					projectListIn.getParent().setVisible(true);
					screenListIn.getParent().setVisible(false);
					updateOutput();
				} else {
					projectListIn.getParent().setVisible(false);
					screenListIn.getParent().setVisible(true);
					updateOutput();
				}
			} else {
				local.setSelected(true);
			}
		} else { //local.isSelected()
			inputFolder.getParent().setVisible(true);
			projectListIn.getParent().setVisible(false);
			screenListIn.getParent().setVisible(false);
			omeroProjects.getParent().setVisible(false);
			groupList.getParent().setVisible(false);
		}
		this.repack();
	}


	/**
	 * Displays a dialog to select the macro to run.
	 */
	private void chooseMacro() {
		String pref = macro.getName().isEmpty() ? "omero.batch.macro" : macro.getName();
		String previousMacro = macro.getText();
		if (previousMacro.isEmpty()) {
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
		repack();
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
		if (client == null) {
			client = new Client();
		}
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
			omeroProjects.setSelected(true);

			int index = -1;
			for (int i = 0; index < 0 && i < groups.size(); i++) {
				if (groups.get(i).getId() == groupId) {
					index = i;
				}
			}
			groupList.setSelectedIndex(-1);
			groupList.setSelectedIndex(index);
			connected = true;
		}
		repack();
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
		repack();
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
	 * Shows a new window to preview the current dataset.
	 */
	private void previewPlate() {
		final int thumbnailSize = 96;

		int plateIndex = plateListIn.getSelectedIndex();
		int plateAcquisitionIndex = plateAcqListIn.getSelectedIndex();

		try {
			JPanel panel = new JPanel(new SpringLayout());
			List<ImageWrapper> images;
			if (plateAcquisitionIndex <= 0) {
				PlateWrapper plate = plates.get(plateIndex);
				images = plate.getImages(client);
			} else {
				PlateAcquisitionWrapper acquisition = plateAcquisitions.get(plateAcquisitionIndex - 1);
				images = acquisition.getImages(client);
			}

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
		BatchParameters params = new BatchParameters();

		// initialization of success variables
		boolean badInput;
		boolean badMacro = !getMacro();
		boolean badOutput = !getOutput();

		// input data
		params.setSuffix(suffix.getText());
		params.setROIMode(roiMode.getItemAt(roiMode.getSelectedIndex()));
		params.setClearROIS(checkDelROIs.isSelected());
		params.setSaveImages(checkImage.isSelected());
		params.setSaveResults(checkResults.isSelected());
		params.setSaveROIs(checkROIs.isSelected());
		params.setSaveLog(checkLog.isSelected());

		Map<String, List<BatchImage>> images = new HashMap<>();
		long inputDatasetId = -1L;
		try {
			if (omero.isSelected()) {
				if (omeroProjects.isSelected()) {
					int index = datasetListIn.getSelectedIndex();
					DatasetWrapper dataset = datasets.get(index);
					inputDatasetId = dataset.getId();
					List<ImageWrapper> imageWrappers = dataset.getImages(client);
					images.put(dataset.getName(), listImages(client, imageWrappers));
					badInput = false;
				} else {
					int plateIndex = plateListIn.getSelectedIndex();
					int plateAcqIndex = plateAcqListIn.getSelectedIndex();
					List<ImageWrapper> imageWrappers;
					PlateWrapper plate = plates.get(plateIndex);

					if (plateAcqIndex <= 0) {
						for (PlateAcquisitionWrapper acquisition : plateAcquisitions) {
							imageWrappers = acquisition.getImages(client);
							images.put(plate.getName() + "_" + acquisition.getName(),
									   listImages(client, imageWrappers));
						}
					} else {
						PlateAcquisitionWrapper acquisition = plateAcquisitions.get(plateAcqIndex - 1);
						imageWrappers = acquisition.getImages(client);
						images.put(plate.getName() + "_" + acquisition.getName(), listImages(client, imageWrappers));
					}
					badInput = false;
				}
			} else { // local.isSelected()
				badInput = !getLocalInput();
				images.put(new File(directoryIn).getName(), listImages(directoryIn, recursive.isSelected()));
			}
			params.setOutputDatasetId(inputDatasetId);
		} catch (ServiceException | AccessException | ExecutionException | IOException exception) {
			IJ.error(exception.getMessage());
			return;
		}

		if (badInput || badMacro || badOutput) {
			return;
		}

		if (onlineOutput.isSelected()) {
			params.setOutputOnOMERO(true);
			if (checkResults.isSelected()) {
				if (omeroProjects.isSelected()) {
					params.setOutputProjectId(outputProjectId);
				} else {
					params.setOutputScreenId(outputScreenId);
				}
			}
			if (checkImage.isSelected()) {
				params.setOutputDatasetId(outputDatasetId);
			}
		}
		if (localOutput.isSelected()) {
			params.setOutputOnLocal(true);
			params.setDirectoryOut(directoryOut);
		}

		OMEROBatchRunner runner = new OMEROBatchRunner(script, images, params, client, progress);
		runner.setListener(this);
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
		updateOutput();
	}


	private void updateOutput() {
		boolean outputOnline = onlineOutput.isSelected();
		boolean outputLocal = localOutput.isSelected();
		boolean outputImage = checkImage.isSelected();
		boolean outputResults = checkResults.isSelected();

		if (outputOnline && !disconnect.isVisible()) {
			outputOnline = connect();
			onlineOutput.setSelected(outputOnline);
		}

		suffix.getParent().setVisible(outputImage);
		projectListOut.getParent()
					  .setVisible(outputOnline && (outputImage || outputResults) && omeroProjects.isSelected());
		screenListOut.getParent()
					 .setVisible(outputOnline && (outputImage || outputResults) && omeroScreens.isSelected());
		datasetListOut.getParent().setVisible(outputOnline && outputImage && omeroProjects.isSelected());
		newDatasetOut.getParent().setVisible(outputOnline && outputImage && omeroScreens.isSelected());
		if (outputOnline && userProjects.equals(myProjects)) {
			projectListOut.setSelectedIndex(projectListIn.getSelectedIndex());
		}
		outputFolder.getParent().setVisible(outputLocal);
		repack();
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
			int screenIndex = screenListOut.getSelectedIndex();
			String newDatasetName = newDatasetOut.getText();
			if (projIndex == -1 || projIndex > myProjects.size()) {
				errorWindow(String.format("Output:%nNo project selected"));
			} else if (datIndex == -1 || datIndex > myDatasets.size()) {
				errorWindow(String.format("Output:%nNo dataset selected"));
			} else if (screenIndex == -1 || screenIndex > myScreens.size()) {
				errorWindow(String.format("Output:%nNo screen selected"));
			} else if (newDatasetOut.getParent().isVisible() && newDatasetName.isEmpty()) {
				errorWindow(String.format("Output:%nNo name set for the output dataset"));
			} else {
				check = true;
				if (omeroProjects.isSelected()) {
					DatasetWrapper dataset = myDatasets.get(datIndex);
					ProjectWrapper project = myProjects.get(projIndex);
					outputDatasetId = dataset.getId();
					outputProjectId = project.getId();
				} else {
					try {
						DatasetWrapper dataset = new DatasetWrapper(newDatasetName, "");
						dataset.saveAndUpdate(client);
						outputDatasetId = dataset.getId();
					} catch (AccessException | ExecutionException | ServiceException e) {
						check = false;
						String msg = "Error; cannot create the new dataset " + newDatasetName;
						errorWindow(msg);
					}
					ScreenWrapper screen = myScreens.get(screenIndex);
					outputScreenId = screen.getId();
				}
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
		if (checkDelROIs.isSelected() && !checkROIs.isSelected()) {
			errorWindow(String.format("ROIs:%nYou can't clear ROIs if you don't save ROIs"));
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
			errorWindow(String.format(
					"Output:%nYou can't upload results file or ROIs on OMERO if your image isn't in OMERO"));
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
		Dimension size = this.getSize();
		this.setMinimumSize(minimumSize);
		this.pack();

		Dimension bestSize = this.getSize();
		this.setSize(size);
		int width = Math.max(size.width, bestSize.width);
		this.setMinimumSize(new Dimension(width, bestSize.height));
		this.pack();
		this.setMinimumSize(bestSize);

		Container inputPanel = projectListIn.getParent().getParent();
		inputPanel.setMinimumSize(inputPanel.getPreferredSize());
		inputPanel.setMaximumSize(
				new Dimension(inputPanel.getMaximumSize().width, inputPanel.getPreferredSize().height));

		Container outputPanel = onlineOutput.getParent().getParent();
		outputPanel.setMinimumSize(outputPanel.getPreferredSize());
		outputPanel.setMaximumSize(new Dimension(outputPanel.getMaximumSize().width, outputPanel.getHeight()));
	}


	private class ClientDisconnector extends WindowAdapter {

		ClientDisconnector() {}


		@Override
		public void windowClosing(WindowEvent e) {
			super.windowClosing(e);
			Client c = client;
			if (c != null) {
				c.disconnect();
			}
		}

	}

}