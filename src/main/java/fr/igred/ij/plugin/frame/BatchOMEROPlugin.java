package fr.igred.ij.plugin.frame;

import fr.igred.ij.gui.ConnectOMERODialog;
import fr.igred.ij.gui.ProgressDialog;
import fr.igred.ij.macro.BatchListener;
import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import ij.IJ;
import ij.plugin.frame.PlugInFrame;
import loci.plugins.config.SpringUtilities;
import fr.igred.ij.macro.BatchOMERORunner;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static javax.swing.JOptionPane.showMessageDialog;

public class BatchOMEROPlugin extends PlugInFrame implements BatchListener {
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
	private final JLabel labelGroupName = new JLabel();

	// choice of the dataSet
	private final JComboBox<String> projectListIn = new JComboBox<>();
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	private final JLabel labelInputProject = new JLabel();
	private final JLabel labelInputDataset = new JLabel();
	private final JCheckBox checkDelROIs = new JCheckBox(" Clear ROIs each time ");
	private final JCheckBox checkLoadROIs = new JCheckBox(" Load ROIs ");

	// choice of the record
	private final JTextField inputFolder = new JTextField(20);
	private final JTextField macro = new JTextField(20);
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
	private final JLabel labelOutputProject = new JLabel();
	private final JLabel labelOutputDataset = new JLabel();
	private final JButton newDatasetBtn = new JButton("New");

	// local
	private final JPanel output3b = new JPanel();
	private final JTextField outputFolder = new JTextField(20);

	// start button
	private final JButton start = new JButton("Start");

	//variables to keep
	private final transient Client client;
	private String macroChosen;
	private String directoryOut;
	private String directoryIn;
	private Long outputDatasetId;
	private Long outputProjectId;
	private transient List<GroupWrapper> groups;
	private transient List<ProjectWrapper> groupProjects;
	private transient List<ProjectWrapper> userProjects;
	private transient List<DatasetWrapper> datasets;
	private transient List<ProjectWrapper> myProjects;
	private transient List<DatasetWrapper> myDatasets;
	private transient List<ExperimenterWrapper> users;
	private transient ExperimenterWrapper exp;


	public BatchOMEROPlugin() {
		super("batch-omero-plugin");
		this.client = new Client();

		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				super.windowClosing(e);
				client.disconnect();
			}
		});

		Font nameFont = new Font("Arial", Font.ITALIC, 10);

		final String projectName = "Project Name: ";
		final String datasetName = "Dataset Name: ";
		final String browse = "Browse";
		this.setSize(720, 640);
		this.setMinimumSize(this.getSize());
		this.setLocationRelativeTo(null);
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

		JPanel connection = new JPanel();
		JLabel labelConnection = new JLabel("Connection status: ");
		labelConnection.setLabelFor(connectionStatus);
		connectionStatus.setForeground(Color.RED);
		connection.add(labelConnection);
		connection.add(connectionStatus);
		connection.add(Box.createRigidArea(new Dimension(50, 0)));
		connection.add(connect);
		connection.add(disconnect);
		disconnect.setVisible(false);
		connect.addActionListener(e -> connect());
		disconnect.addActionListener(e -> disconnect());
		connection.setBorder(BorderFactory.createTitledBorder("Connection"));
		this.add(connection);

		JPanel source = new JPanel();
		JLabel labelEnterType = new JLabel("Where to get images to analyse :");
		ButtonGroup inputData = new ButtonGroup();
		inputData.add(omero);
		inputData.add(local);
		source.add(labelEnterType);
		source.add(omero);
		source.add(local);
		omero.addItemListener(this::updateInput);
		local.addItemListener(this::updateInput);
		source.setBorder(BorderFactory.createTitledBorder("Source"));
		this.add(source);

		JLabel labelGroup = new JLabel("Group Name: ");
		JLabel labelUser = new JLabel("User Name: ");
		labelGroup.setLabelFor(groupList);
		labelUser.setLabelFor(userList);
		labelGroupName.setFont(nameFont);
		input1a.add(labelGroup);
		input1a.add(groupList);
		input1a.add(labelGroupName);
		input1a.add(Box.createRigidArea(new Dimension(20, 0)));
		input1a.add(labelUser);
		input1a.add(userList);
		groupList.addItemListener(this::updateGroup);
		userList.addItemListener(this::updateUser);

		JLabel labelProjectIn = new JLabel(projectName);
		JLabel labelDatasetIn = new JLabel(datasetName);
		JButton preview = new JButton("Preview");
		labelProjectIn.setLabelFor(projectListIn);
		labelDatasetIn.setLabelFor(datasetListIn);
		labelInputProject.setLabelFor(projectListIn);
		labelInputDataset.setLabelFor(datasetListIn);
		labelInputProject.setFont(nameFont);
		labelInputDataset.setFont(nameFont);
		input1b.add(labelProjectIn);
		input1b.add(projectListIn);
		input1b.add(labelInputProject);
		input1b.add(Box.createRigidArea(new Dimension(20, 0)));
		input1b.add(labelDatasetIn);
		input1b.add(datasetListIn);
		input1b.add(labelInputDataset);
		input1b.add(Box.createRigidArea(new Dimension(20, 0)));
		input1b.add(preview);
		projectListIn.addItemListener(this::updateInputProject);
		datasetListIn.addItemListener(this::updateInputDataset);
		preview.addActionListener(e -> previewDataset());

		input1c.add(checkLoadROIs);
		input1c.add(checkDelROIs);

		JLabel inputFolderLabel = new JLabel("Images folder: ");
		JButton inputFolderBtn = new JButton(browse);
		inputFolderLabel.setLabelFor(inputFolder);
		inputFolder.setMaximumSize(new Dimension(300, 30));
		input2.add(inputFolderLabel);
		input2.add(inputFolder);
		input2.add(inputFolderBtn);
		inputFolderBtn.addActionListener(e -> chooseDirectory(inputFolder));

		JPanel panelInput = new JPanel();
		panelInput.add(input1a);
		panelInput.add(input1b);
		panelInput.add(input1c);
		panelInput.add(input2);
		local.setSelected(true);
		panelInput.setLayout(new BoxLayout(panelInput, BoxLayout.PAGE_AXIS));
		panelInput.setBorder(BorderFactory.createTitledBorder("Input"));
		this.add(panelInput);

		JPanel macro1 = new JPanel();
		JLabel macroLabel = new JLabel("Macro file: ");
		JButton macroBtn = new JButton(browse);
		macroLabel.setLabelFor(macro);
		macro.setMaximumSize(new Dimension(300, 30));
		macro1.add(macroLabel);
		macro1.add(macro);
		macro1.add(macroBtn);
		macroBtn.addActionListener(e -> chooseMacro());

		JPanel macro2 = new JPanel();
		macro2.setLayout(new BoxLayout(macro2, BoxLayout.LINE_AXIS));
		JLabel macroReturnLabel = new JLabel("The macro returns: ");
		macro2.add(macroReturnLabel);

		JPanel macro3 = new JPanel();
		macro3.setLayout(new BoxLayout(macro3, BoxLayout.LINE_AXIS));
		macro3.add(checkImage);
		macro3.add(checkResults);
		macro3.add(checkROIs);
		macro3.add(checkLog);
		checkImage.addActionListener(this::updateOutput);
		checkResults.addActionListener(this::updateOutput);
		checkROIs.addActionListener(this::updateOutput);
		checkLog.addActionListener(this::updateOutput);

		//choice of the macro
		JPanel panelMacro = new JPanel();
		panelMacro.add(macro1);
		panelMacro.add(macro2);
		panelMacro.add(macro3);
		panelMacro.setLayout(new BoxLayout(panelMacro, BoxLayout.PAGE_AXIS));
		panelMacro.setBorder(BorderFactory.createTitledBorder("Macro"));
		this.add(panelMacro);

		JLabel labelExtension = new JLabel("Suffix of output files :");
		labelExtension.setLabelFor(suffix);
		suffix.setText("_macro");
		output1.add(labelExtension);
		output1.add(suffix);
		output1.setVisible(false);

		JPanel output2 = new JPanel();
		JLabel labelRecordOption = new JLabel("Where to save results :");
		output2.add(labelRecordOption);
		output2.add(onlineOutput);
		output2.add(localOutput);
		onlineOutput.addActionListener(this::updateOutput);
		localOutput.addActionListener(this::updateOutput);


		JLabel labelProjectOut = new JLabel(projectName);
		JLabel labelDatasetOut = new JLabel(datasetName);
		labelProjectOut.setLabelFor(projectListOut);
		labelDatasetOut.setLabelFor(datasetListOut);
		labelOutputProject.setFont(nameFont);
		labelOutputDataset.setFont(nameFont);
		output3a.add(labelProjectOut);
		output3a.add(projectListOut);
		output3a.add(labelOutputProject);
		output3a.add(Box.createRigidArea(new Dimension(20, 0)));
		output3a.add(labelDatasetOut);
		output3a.add(datasetListOut);
		output3a.add(labelOutputDataset);
		output3a.add(Box.createRigidArea(new Dimension(20, 0)));
		output3a.add(newDatasetBtn);
		projectListOut.addItemListener(this::updateOutputProject);
		datasetListOut.addItemListener(this::updateOutputDataset);
		newDatasetBtn.addActionListener(this::createNewDataset);
		output3a.setVisible(false);

		JLabel outputFolderLabel = new JLabel("Output folder: ");
		JButton directoryBtn = new JButton(browse);
		outputFolderLabel.setLabelFor(outputFolder);
		outputFolder.setMaximumSize(new Dimension(300, 30));
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
		this.add(panelOutput);

		// validation button
		JPanel panelBtn = new JPanel();
		panelBtn.add(start);
		start.addActionListener(this::start);
		this.add(panelBtn);
	}


	@Override
	public void run(String arg) {
		this.setVisible(true);
	}


	private String idLabel(long id) {
		return String.format("ID = %d", id);
	}


	public void errorWindow(String message) {
		showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}


	public void warningWindow(String message) {
		showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
	}


	public void userProjectsAndDatasets(String username, long userId) {
		if (username.equals("All members")) {
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


	private void updateInputDataset(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int datasetId = ((JComboBox<?>) source).getSelectedIndex();
				DatasetWrapper dataset = datasets.get(datasetId);
				labelInputDataset.setText(idLabel(dataset.getId()));
			}
		}
		this.repack();
	}


	private void updateInputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = userProjects.get(index);
				this.datasets = project.getDatasets();
				labelInputProject.setText(idLabel(project.getId()));
				datasetListIn.removeAllItems();
				for (DatasetWrapper dataset : project.getDatasets()) {
					datasetListIn.addItem(dataset.getName());
				}
				if (!this.datasets.isEmpty()) datasetListIn.setSelectedIndex(0);
			}
		}
	}


	private void updateOutputDataset(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int datasetId = ((JComboBox<?>) source).getSelectedIndex();
				DatasetWrapper dataset = myDatasets.get(datasetId);
				labelOutputDataset.setText(idLabel(dataset.getId()));
			}
		}
		this.repack();
	}


	private void updateOutputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = myProjects.get(index);
				this.myDatasets = project.getDatasets();
				labelOutputProject.setText(idLabel(project.getId()));
				datasetListOut.removeAllItems();
				for (DatasetWrapper dataset : project.getDatasets()) {
					datasetListOut.addItem(dataset.getName());
				}
				if (!this.datasets.isEmpty()) datasetListOut.setSelectedIndex(0);
			}
		}
	}


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
		for (int i = 0; i < myDatasets.size(); i++) {
			if (myDatasets.get(i).getId() == id) {
				datasetListOut.setSelectedIndex(i);
				break;
			}
		}

		int inputProject = projectListIn.getSelectedIndex();
		projectListIn.setSelectedIndex(-1);
		projectListIn.setSelectedIndex(inputProject);

		long inputDatasetID = datasets.get(datasetListIn.getSelectedIndex()).getId();
		for (int i = 0; i < datasets.size(); i++) {
			if (datasets.get(i).getId() == inputDatasetID) {
				datasetListIn.setSelectedIndex(i);
				break;
			}
		}
	}


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
			for (ProjectWrapper project : userProjects) {
				projectListIn.addItem(project.getName());
			}
			for (ProjectWrapper project : myProjects) {
				projectListOut.addItem(project.getName());
			}
			if (!userProjects.isEmpty()) projectListIn.setSelectedIndex(0);
			if (!myProjects.isEmpty()) projectListOut.setSelectedIndex(0);
		}
	}


	private void updateGroup(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int index = groupList.getSelectedIndex();
			long id = groups.get(index).getId();
			String groupName = groups.get(index).getName();
			client.switchGroup(id);

			groupProjects = new ArrayList<>();
			try {
				groupProjects = client.getProjects();
			} catch (ServiceException | AccessException exception) {
				IJ.log(exception.getMessage());
			}

			labelGroupName.setText(idLabel(id));
			try {
				GroupWrapper group = client.getGroup(groupName);
				users = group.getExperimenters();
			} catch (ExecutionException | ServiceException | AccessException exception) {
				IJ.log(exception.getMessage());
			}
			userList.removeAllItems();

			userList.addItem("All members");
			int selected = 0;
			for (ExperimenterWrapper user : users) {
				userList.addItem(user.getUserName());
				if (user.getId() == exp.getId()) {
					selected = users.indexOf(user) + 1;
				}
			}
			userList.setSelectedIndex(selected);
		}
	}


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


	private void chooseDirectory(JTextField textField) {
		JFileChooser outputChoice = new JFileChooser();
		outputChoice.setDialogTitle("Choose the directory");
		outputChoice.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal = outputChoice.showOpenDialog(null);
		outputChoice.setAcceptAllFileFilterUsed(false); // ????
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File absDir = new File(outputChoice.getSelectedFile()
											   .getAbsolutePath());
			if (absDir.exists() && absDir.isDirectory()) {
				textField.setText(absDir.toString());
			} else {
				////find a way to prevent JFileChooser closure?
				errorWindow(String.format("Output:%nThe directory doesn't exist"));
			}
		}
	}


	private void chooseMacro() {
		JFileChooser macroChoice = new JFileChooser();
		macroChoice.setDialogTitle("Choose the macro file");
		macroChoice.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int returnVal = macroChoice.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File absFile = new File(macroChoice.getSelectedFile().getAbsolutePath());
			if (absFile.exists() && !absFile.isDirectory()) {
				macro.setText(absFile.toString());
			} else {
				//find a way to prevent JFileChooser closure?
				warningWindow(String.format("Macro:%nThe file doesn't exist"));
			}
		}
	}


	@Override
	public void onThreadFinished() {
		start.setEnabled(true);
	}


	private boolean connect() {
		boolean connected = false;
		ConnectOMERODialog connectDialog = new ConnectOMERODialog(client);
		if (!connectDialog.wasCancelled()) {

			long groupId = client.getCurrentGroupId();

			try {
				exp = client.getUser(client.getUser().getUserName());
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error(e.getCause().getMessage());
			}
			groups = exp.getGroups();
			groups.removeIf(g -> g.getId() <= 2);

			for (GroupWrapper group : groups) {
				groupList.addItem(group.getName());
			}

			connectionStatus.setText("Connected");
			connectionStatus.setForeground(new Color(0, 153, 0));
			connect.setVisible(false);
			disconnect.setVisible(true);
			omero.setSelected(true);

			int index;
			for (index = 0; index < groups.size(); index++) {
				if (groups.get(index).getId() == groupId) break;
			}
			groupList.setSelectedIndex(-1);
			groupList.setSelectedIndex(index);
			connected = true;
		}
		return connected;
	}


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
		labelGroupName.setText("");
	}


	private void previewDataset() {
		int index = datasetListIn.getSelectedIndex();
		DatasetWrapper dataset = datasets.get(index);
		try {
			JPanel panel = new JPanel(new SpringLayout());
			List<ImageWrapper> images = dataset.getImages(client);
			int nRows = Math.min(images.size(), 5);
			int missing = images.size() - nRows;
			List<ImageWrapper> truncated = images.subList(0, nRows);
			for (ImageWrapper i : truncated) {
				JLabel thumbnail = new JLabel(new ImageIcon(i.getThumbnail(client, 96)));
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
			JOptionPane.showMessageDialog(this, panel, "Preview", JOptionPane.INFORMATION_MESSAGE);
		} catch (ServiceException | AccessException | OMEROServerError | IOException e) {
			errorWindow(e.getMessage());
		}
	}


	public void start(ActionEvent e) {
		ProgressDialog progress = new ProgressDialog();
		BatchOMERORunner runner = new BatchOMERORunner(client, progress);
		runner.addListener(this);

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

		runner.setMacro(macroChosen);
		start.setEnabled(false);
		try {
			runner.start();
		} catch (Exception exception) {
			errorWindow(exception.getMessage());
		}
	}


	private void updateOutput(ActionEvent e) {
		boolean outputOnline = onlineOutput.isSelected();
		boolean outputLocal = localOutput.isSelected();
		boolean outputImage = checkImage.isSelected();
		boolean outputResults = checkResults.isSelected();
		boolean connected = disconnect.isVisible();

		if (outputOnline && !connected) {
			connected = connect();
			outputOnline = connected;
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


	private boolean getLocalInput() {
		boolean check = false;
		if (inputFolder.getText().equals("")) {
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


	private boolean getMacro() {
		boolean check = false;
		// macro file (mandatory)
		if (macro.getText().equals("")) {
			errorWindow(String.format("Macro:%nNo macro selected"));
		} else {
			macroChosen = macro.getText();
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


	private boolean getLocalOutput() {
		boolean check = false;
		if (localOutput.isSelected()) {
			if (outputFolder.getText().equals("")) {
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


	private boolean checkDeleteROIs() {
		boolean check = true;
		if (checkDelROIs.isSelected() && (!onlineOutput.isSelected() || !checkROIs.isSelected())) {
			errorWindow(String.format("ROIs:%nYou can't clear ROIs if you don't save ROIs on OMERO"));
			check = false;
		}
		return check;
	}


	private boolean checkUploadLocalInput() {
		boolean check = true;
		if (local.isSelected() && onlineOutput.isSelected() && !checkImage.isSelected()) {
			errorWindow(String.format("Output:%nYou can't upload results file or ROIs on OMERO if your image isn't in OMERO"));
			check = false;
		}
		return check;
	}


	private boolean checkSelectedOutput() {
		boolean check = true;
		if (!checkResults.isSelected() && !checkROIs.isSelected() && !checkImage.isSelected()) {
			errorWindow(String.format("Macro:%nYou have to choose at least one output"));
			check = false;
		}
		return check;
	}


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


	private void repack() {
		Dimension minSize = this.getMinimumSize();
		this.setMinimumSize(this.getSize());
		this.pack();
		this.setMinimumSize(minSize);
	}

}