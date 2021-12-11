package fr.igred.ij.macro;

import fr.igred.ij.gui.ProgressDialog;
import fr.igred.omero.Client;
import fr.igred.omero.annotations.TableWrapper;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import fr.igred.omero.roi.ROIWrapper;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.text.TextWindow;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;

import java.awt.Frame;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs a script over multiple images retrieved from local files or from OMERO.
 */
public class OMEROBatchRunner extends Thread {

	private final ScriptRunner script;
	private final Client client;
	private final ProgressMonitor progress;

	private final Map<String, TableWrapper> tables = new HashMap<>();

	private boolean inputOnOMERO;
	private boolean saveImage;
	private boolean saveROIs;
	private boolean saveResults;
	private boolean saveLog;
	private boolean loadROIs;
	private boolean clearROIs;
	private boolean outputOnOMERO;
	private boolean outputOnLocal;
	private long inputDatasetId;
	private long outputDatasetId;
	private long outputProjectId;
	private String directoryIn = "";
	private String directoryOut;
	private String suffix;

	private RoiManager rm;

	private BatchListener listener;


	public OMEROBatchRunner(ScriptRunner script, Client client) {
		super();
		this.script = script;
		this.client = client;
		this.progress = new ProgressLog(Logger.getLogger(getClass().getName()));
	}


	public OMEROBatchRunner(ScriptRunner script, Client client, ProgressMonitor progress) {
		super();
		this.script = script;
		this.client = client;
		this.progress = progress;
	}


	/**
	 * Initializes the ROI manager.
	 */
	private void initRoiManager() {
		rm = RoiManager.getInstance2();
		if (rm == null) rm = RoiManager.getRoiManager();
		rm.setVisible(false);
	}


	/**
	 * Sets the current state.
	 */
	private void setState(String text) {
		if (progress != null) progress.setState(text);
	}


	/**
	 * Sets the current progress.
	 */
	private void setProgress(String text) {
		if (progress != null) progress.setProgress(text);
	}


	/**
	 * Signals the process is done.
	 */
	private void setDone() {
		if (progress != null) progress.setDone();
	}


	/**
	 * If this thread was constructed using a separate
	 * {@code Runnable} run object, then that
	 * {@code Runnable} object's {@code run} method is called;
	 * otherwise, this method does nothing and returns.
	 * <p>
	 * Subclasses of {@code Thread} should override this method.
	 *
	 * @see #start()
	 * @see Thread#Thread(ThreadGroup, Runnable, String)
	 */
	@Override
	public void run() {
		if (progress instanceof ProgressDialog) {
			((ProgressDialog) progress).setVisible(true);
		}

		try {
			if (!outputOnLocal) {
				setState("Temporary directory creation...");
				directoryOut = Files.createTempDirectory("Fiji_analysis").toString();
			}

			if (inputOnOMERO) {
				setState("Images recovery from OMERO...");
				DatasetWrapper dataset = client.getDataset(inputDatasetId);
				List<ImageWrapper> images = dataset.getImages(client);
				setState("Macro running...");
				runMacro(images);
			} else {
				setState("Images recovery from input folder...");
				List<String> files = getImageFilesFromDirectory(getDirectoryIn());
				setState("Macro running...");
				runMacroOnLocalImages(files);
			}
			setProgress("");
			uploadTables();

			if (!outputOnLocal) {
				setState("Temporary directory deletion...");
				if (!deleteTemp(directoryOut)) {
					IJ.log("Temp directory may not be deleted.");
				}
			}
			setState("");
			setDone();
		} catch (Exception e3) {
			setDone();
			setProgress("Macro cancelled");
			if (e3.getMessage() != null && e3.getMessage().equals("Macro cancelled")) {
				IJ.run("Close");
			}
			IJ.error(e3.getMessage());
		} finally {
			if (listener != null) listener.onThreadFinished();
		}
	}


	/**
	 * Deletes all owned ROIs from an image on OMERO.
	 *
	 * @param image The image on OMERO.
	 */
	private void deleteROIs(ImageWrapper image) {
		setState("ROIs deletion from OMERO");
		try {
			List<ROIWrapper> rois = image.getROIs(client);
			for (ROIWrapper roi : rois) {
				if (roi.getOwner().getId() == client.getId()) {
					client.delete(roi);
				}
			}
		} catch (ExecutionException | OMEROServerError | ServiceException | AccessException exception) {
			IJ.log(exception.getMessage());
		} catch (InterruptedException e) {
			IJ.log(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}


	/**
	 * List all image files contained in a directory
	 *
	 * @param directory The folder to process
	 *
	 * @return The list of images paths.
	 */
	private List<String> getImageFilesFromDirectory(String directory) {
		File dir = new File(directory);
		File[] files = dir.listFiles();
		if (files == null) files = new File[0];
		List<String> paths = new ArrayList<>();
		for (File file : files) {
			if (!file.isDirectory()) {
				String path = file.getAbsolutePath();
				paths.add(path);
			}
		}
		return paths;
	}


	/**
	 * Retrieves the list of ROIs from an image overlay.
	 *
	 * @param imp The image ROIs are linked to.
	 *
	 * @return See above.
	 */
	private List<Roi> getOverlay(ImagePlus imp) {
		List<Roi> ijRois = new ArrayList<>(0);
		Overlay overlay = imp.getOverlay();
		if (overlay != null) {
			ijRois = Arrays.asList(overlay.toArray());
		}
		for (Roi roi : ijRois) roi.setImage(imp);
		return ijRois;
	}


	/**
	 * Retrieves the list of ROIs from the ROI manager.
	 *
	 * @param imp The image ROIs are linked to.
	 *
	 * @return See above.
	 */
	private List<Roi> getManagedRois(ImagePlus imp) {
		List<Roi> ijRois = Arrays.asList(rm.getRoisAsArray());
		for (Roi roi : ijRois) roi.setImage(imp);
		return ijRois;
	}


	/**
	 * Converts ROIs from an image overlay to OMERO ROIs.
	 *
	 * @param imp      The image ROIs are linked to.
	 * @param property The ROI property used to group shapes in OMERO.
	 *
	 * @return A list of OMERO ROIs.
	 */
	private List<ROIWrapper> getROIsFromOverlay(ImagePlus imp, String property) {
		List<ROIWrapper> rois = new ArrayList<>(0);
		if (imp != null) {
			List<Roi> ijRois = getOverlay(imp);
			rois = ROIWrapper.fromImageJ(ijRois, property);
		}

		return rois;
	}


	/**
	 * Converts ROIs from the ROI Manager to OMERO ROIs.
	 *
	 * @param imp      The image ROIs are linked to.
	 * @param property The ROI property used to group shapes in OMERO.
	 *
	 * @return A list of OMERO ROIs.
	 */
	private List<ROIWrapper> getROIsFromManager(ImagePlus imp, String property) {
		List<ROIWrapper> rois = new ArrayList<>(0);
		if (imp != null) {
			List<Roi> ijRois = getManagedRois(imp);
			rois = ROIWrapper.fromImageJ(ijRois, property);
		}

		return rois;
	}


	/**
	 * Generates the timestamp for current time.
	 *
	 * @return See above.
	 */
	private String todayDate() {
		return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
	}


	/**
	 * Runs a macro on images from OMERO and saves the results.
	 *
	 * @param images List of images on OMERO.
	 */
	void runMacro(List<ImageWrapper> images) {
		//""" Run a macro on images and save the result """
		String property = ROIWrapper.IJ_PROPERTY;
		ij.WindowManager.closeAllWindows();
		int index = 0;
		for (ImageWrapper image : images) {
			setProgress("Image " + (index + 1) + "/" + images.size());
			long inputImageId = image.getId();

			// Open image from OMERO
			ImagePlus imp = openImage(image);
			// If image could not be loaded, continue to next image.
			if (imp == null) continue;

			// Initialize ROI Manager
			initRoiManager();

			// Load ROIs
			if (loadROIs) loadROIs(image, imp, false);

			imp.show();

			// Analyse the image
			script.setImage(imp);
			script.run();

			imp.changes = false; // Prevent "Save Changes?" dialog
			save(imp, inputImageId, property);
			closeWindows();
			index++;
		}
	}


	/**
	 * Runs a macro on local files and saves the results.
	 *
	 * @param files List of image files.
	 *
	 * @throws IOException A problem occurred reading a file.
	 */
	void runMacroOnLocalImages(List<String> files) throws IOException {
		String property = ROIWrapper.IJ_PROPERTY;
		WindowManager.closeAllWindows();

		ImporterOptions options = new ImporterOptions();
		options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
		options.setSwapDimensions(false);
		options.setOpenAllSeries(false);
		options.setSpecifyRanges(false);
		options.setShowMetadata(false);
		options.setShowOMEXML(false);
		options.setShowROIs(true);
		options.setCrop(false);
		options.setSplitChannels(false);
		options.setSplitFocalPlanes(false);
		options.setSplitTimepoints(false);

		List<String> processed = new ArrayList<>(files.size());
		List<String> ignored = new ArrayList<>(files.size());
		for (String file : files) {
			if (!processed.contains(file) && !ignored.contains(file)) {
				// Open the image
				options.setId(file);
				int n = 0;
				List<String> used = new ArrayList<>(0);
				ImportProcess process = new ImportProcess(options);
				try {
					process.execute();
					n = process.getSeriesCount();
					used = Arrays.asList(process.getFileStitcher().getUsedFiles());
				} catch (FormatException e) {
					IJ.log(e.getMessage());
					ignored.add(file);
				}
				int nFile = processed.size() + used.size() + ignored.size();
				for (int i = 0; i < n; i++) {
					String msg = String.format("File %d/%d, image %d/%d", nFile, files.size(), i, n);
					setProgress(msg);
					options.setSeriesOn(i, true);
					ImagePlus[] imps;
					try {
						imps = BF.openImagePlus(options);
					} catch (FormatException e) {
						IJ.log(e.getMessage());
						continue;
					}
					ImagePlus imp = imps[0];
					imp.show();

					// Initialize ROI Manager
					initRoiManager();

					// Analyse the image
					script.setImage(imp);
					script.run();

					// Save and Close the various components
					imp.changes = false; // Prevent "Save Changes?" dialog
					save(imp, null, property);
					closeWindows();
					options.setSeriesOn(i, false);
				}
				processed.addAll(used);
			}
		}
	}


	/**
	 * Removes file extension from image title.
	 *
	 * @param title Image title.
	 *
	 * @return The title, without the extension.
	 */
	private String removeExtension(String title) {
		if (title != null && title.matches("(.*)qptiff(.*)")) {
			return title.replace(".qptiff", "_");
		} else if (title != null) {
			int index = title.lastIndexOf('.');
			if (index == 0 || index == -1) {
				return title;
			} else {
				return title.substring(0, index);
			}
		} else {
			return null;
		}
	}


	/**
	 * Opens an image from OMERO.
	 *
	 * @param image An OMERO image.
	 *
	 * @return An ImagePlus.
	 */
	private ImagePlus openImage(ImageWrapper image) {
		setState("Opening image from OMERO...");
		ImagePlus imp = null;
		try {
			imp = image.toImagePlus(client);
			// Store image "annotate" permissions as a property in the ImagePlus object
			imp.setProp("Annotable", String.valueOf(image.canAnnotate()));
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error("Could not load image: " + e.getMessage());
		}
		return imp;
	}


	/**
	 * Retrieves the list of images open after the script was run.
	 *
	 * @param inputImage The input image.
	 *
	 * @return See above.
	 */
	private List<ImagePlus> getOutputImages(ImagePlus inputImage) {
		ImagePlus outputImage = WindowManager.getCurrentImage();
		if (outputImage == null) {
			outputImage = inputImage;
		}
		int ijOutputId = outputImage.getID();

		int[] imageIds = WindowManager.getIDList();
		if (imageIds == null) {
			imageIds = new int[0];
		}
		List<Integer> idList = Arrays.stream(imageIds).boxed().collect(Collectors.toList());
		idList.removeIf(i -> i.equals(ijOutputId));
		idList.add(0, ijOutputId);
		List<ImagePlus> outputs = idList.stream()
										.map(WindowManager::getImage)
										.collect(Collectors.toList());
		if (outputs.isEmpty()) IJ.log("Warning: there is no new image.");
		return outputs;
	}


	/**
	 * Loads ROIs from an image in OMERO into ImageJ.
	 *
	 * @param image     The OMERO image.
	 * @param imp       The image in ImageJ ROIs should be linked to.
	 * @param toOverlay Whether the ROIs should be loaded to the ROI Manager (false) or the overlay (true).
	 */
	private void loadROIs(ImageWrapper image, ImagePlus imp, boolean toOverlay) {
		List<Roi> ijRois = new ArrayList<>(0);
		try {
			ijRois = ROIWrapper.toImageJ(image.getROIs(client));
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error("Could not load ROIs: " + e.getMessage());
		}
		if (toOverlay) {
			Overlay overlay = imp.getOverlay();
			if (overlay != null) {
				overlay.clear();
			} else {
				overlay = new Overlay();
			}
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				overlay.add(ijRoi, ijRoi.getName());
			}
		} else {
			rm.reset(); // Reset ROI manager to clear previous ROIs
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				rm.addRoi(ijRoi);
			}
		}
	}


	/**
	 * Saves the results.
	 *
	 * @param inputImage   The input image in ImageJ.
	 * @param omeroInputId The OMERO image input ID.
	 * @param property     The ROI property used to group shapes in OMERO.
	 */
	private void save(ImagePlus inputImage, Long omeroInputId, String property) {
		String inputTitle = removeExtension(inputImage.getTitle());

		Long omeroOutputId = omeroInputId;
		List<ImagePlus> outputs = getOutputImages(inputImage);

		ImagePlus outputImage = inputImage;
		if (!outputs.isEmpty()) outputImage = outputs.get(0);

		// If input image is expected as output for ROIs on OMERO but is not annotable, import it.
		boolean annotable = Boolean.parseBoolean(inputImage.getProp("Annotable"));
		boolean outputIsNotInput = !inputImage.equals(outputImage);
		if (!outputOnOMERO || !saveROIs || annotable || outputIsNotInput) {
			outputs.removeIf(i -> i.equals(inputImage));
		}

		if (saveImage) {
			List<Long> outputIds = new ArrayList<>();
			for (ImagePlus imp : outputs) {
				List<Long> ids = saveImage(imp, property);
				outputIds.addAll(ids);
			}
			if (!outputIds.isEmpty() && outputIsNotInput) {
				omeroOutputId = outputIds.get(0);
			}
		}

		if (saveROIs) {
			if (!saveImage) saveOverlay(outputImage, omeroOutputId, inputTitle, property);
			saveROIManager(outputImage, omeroOutputId, inputTitle, property);
		}
		if (saveResults) saveResults(outputImage, omeroOutputId, inputTitle, property);
		if (saveLog) saveLog(omeroOutputId, inputTitle);

		for (ImagePlus imp : outputs) {
			imp.changes = false;
			imp.close();
		}
	}


	/**
	 * Saves an image.
	 *
	 * @param image    The image to save.
	 * @param property The ROI property to group shapes in OMERO.
	 *
	 * @return The OMERO IDs of the (possibly) uploaded image. Should be empty or contain one value.
	 */
	private List<Long> saveImage(ImagePlus image, String property) {
		List<Long> ids = new ArrayList<>();
		String title = removeExtension(image.getTitle());
		String path = directoryOut + File.separator + title + suffix + ".tif";
		IJ.saveAsTiff(image, path);
		if (outputOnOMERO) {
			try {
				setState("Import on OMERO...");
				DatasetWrapper dataset = client.getDataset(outputDatasetId);
				ids = dataset.importImage(client, path);
				if (saveROIs && !ids.isEmpty()) {
					saveOverlay(image, ids.get(0), title, property);
				}
			} catch (Exception e) {
				IJ.error("Could not import image: " + e.getMessage());
			}
		}
		return ids;
	}


	/**
	 * Saves ImageJ ROIs to a file.
	 *
	 * @param ijRois The ROIs.
	 * @param path   The path to the file.
	 */
	private void saveRoiFile(List<Roi> ijRois, String path) {
		try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(zos))) {
			RoiEncoder re = new RoiEncoder(dos);
			for (int i = 0; i < ijRois.size(); i++) {
				// WARNING: Prepending index does not ensure label is unique.
				String label = i + "-" + ijRois.get(i).getName() + ".roi";
				if (ijRois.get(i) == null) continue;
				zos.putNextEntry(new ZipEntry(label));
				re.write(ijRois.get(i));
				dos.flush();
			}
		} catch (IOException e) {
			IJ.error("Error while saving ROI file: " + e.getMessage());
		}
	}


	/**
	 * Saves the ROIs from an image overlay in ImageJ.
	 *
	 * @param imp      The image.
	 * @param imageId  The image ID on OMERO.
	 * @param title    The image title used to name the file when saving locally.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void saveOverlay(ImagePlus imp, Long imageId, String title, String property) {
		if (outputOnLocal) {  //  local save
			setState("Saving overlay ROIs...");
			String path = directoryOut + File.separator + title + "_" + todayDate() + "_RoiSet.zip";
			List<Roi> ijRois = getOverlay(imp);
			saveRoiFile(ijRois, path);
		}
		if (outputOnOMERO && imageId != null) { // save on Omero
			List<ROIWrapper> rois = getROIsFromOverlay(imp, property);
			try {
				ImageWrapper image = client.getImage(imageId);
				if (clearROIs) {
					deleteROIs(image);
				}
				setState("Saving overlay ROIs on OMERO...");
				for (ROIWrapper roi : rois) {
					roi.setImage(image);
					image.saveROI(client, roi);
				}
				loadROIs(image, imp, true); // reload ROIs
			} catch (ServiceException | AccessException | ExecutionException e) {
				IJ.error("Could not import overlay ROIs to OMERO: " + e.getMessage());
			}
		}
	}


	/**
	 * Saves the ROIs from the ROI Manager (for an image).
	 *
	 * @param imp      The image.
	 * @param imageId  The image ID on OMERO.
	 * @param title    The image title used to name the file when saving locally.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void saveROIManager(ImagePlus imp, Long imageId, String title, String property) {
		if (outputOnLocal) {  //  local save
			setState("Saving ROIs...");
			String path = directoryOut + File.separator + title + "_" + todayDate() + "_RoiSet.zip";
			List<Roi> ijRois = getManagedRois(imp);
			saveRoiFile(ijRois, path);
		}
		if (outputOnOMERO && imageId != null) { // save on Omero
			List<ROIWrapper> rois = getROIsFromManager(imp, property);
			try {
				ImageWrapper image = client.getImage(imageId);
				if (clearROIs) {
					deleteROIs(image);
				}
				setState("Saving ROIs on OMERO...");
				for (ROIWrapper roi : rois) {
					roi.setImage(image);
					image.saveROI(client, roi);
				}
				loadROIs(image, imp, false); // reload ROIs
			} catch (ServiceException | AccessException | ExecutionException e) {
				IJ.error("Could not import ROIs to OMERO: " + e.getMessage());
			}
		}
	}


	/**
	 * Saves the results (linked to an image).
	 *
	 * @param imp      The image.
	 * @param imageId  The image ID on OMERO.
	 * @param title    The image title used to name the file when saving locally.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void saveResults(ImagePlus imp, Long imageId, String title, String property) {
		String resultsName = null;
		List<Roi> ijRois = getOverlay(imp);
		ijRois.addAll(getManagedRois(imp));
		setState("Saving results files...");
		ResultsTable rt = ResultsTable.getResultsTable();
		if (rt != null && rt.getHeadings().length > 0) {
			resultsName = rt.getTitle();
			String path = directoryOut + File.separator + resultsName + "_" + title + "_" + todayDate() + ".csv";
			rt.save(path);
			if (outputOnOMERO) {
				appendTable(rt, imageId, ijRois, property);
				uploadFile(imageId, path);
			}
			rt.reset();
		}
		String[] candidates = WindowManager.getNonImageTitles();
		for (String candidate : candidates) {
			rt = ResultsTable.getResultsTable(candidate);

			// Skip if rt is null or if results already processed
			if (rt == null || rt.getTitle().equals(resultsName)) continue;

			String path = directoryOut + File.separator + candidate + "_" + title + "_" + todayDate() + ".csv";
			rt.save(path);
			if (outputOnOMERO) {
				appendTable(rt, imageId, ijRois, property);
				uploadFile(imageId, path);
			}
			rt.reset();
		}
	}


	/**
	 * Saves the log.
	 *
	 * @param imageId The image ID on OMERO.
	 * @param title   The image title used to name the file when saving locally.
	 */
	private void saveLog(Long imageId, String title) {
		String path = directoryOut + File.separator + title + "_log.txt";
		IJ.selectWindow("Log");
		IJ.saveAs("txt", path);
		if (outputOnOMERO) uploadFile(imageId, path);
	}


	/**
	 * Uploads a file to an image on OMERO.
	 *
	 * @param imageId The image ID on OMERO.
	 * @param path    The path to the file.
	 */
	private void uploadFile(Long imageId, String path) {
		if (imageId != null) {
			try {
				setState("Uploading results files...");
				ImageWrapper image = client.getImage(imageId);
				image.addFile(client, new File(path));
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Error adding file to image:" + e.getMessage());
			} catch (InterruptedException e) {
				IJ.error("Error adding file to image:" + e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
	}


	/**
	 * Adds the current results to the corresponding table.
	 *
	 * @param results  The results table.
	 * @param imageId  The image ID on OMERO.
	 * @param ijRois   The ROIs in ImageJ.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void appendTable(ResultsTable results, Long imageId, List<Roi> ijRois, String property) {
		String resultsName = results.getTitle();
		TableWrapper table = tables.get(resultsName);
		try {
			if (table == null) {
				tables.put(resultsName, new TableWrapper(client, results, imageId, ijRois, property));
			} else {
				table.addRows(client, results, imageId, ijRois, property);
			}
		} catch (ServiceException | AccessException | ExecutionException e) {
			IJ.error("Could not create or append table: " + e.getMessage());
		}
	}


	/**
	 * Upload the tables to OMERO.
	 */
	private void uploadTables() {
		if (outputOnOMERO && saveResults) {
			setState("Uploading tables...");
			try {
				ProjectWrapper project = client.getProject(outputProjectId);
				for (Map.Entry<String, TableWrapper> entry : tables.entrySet()) {
					String name = entry.getKey();
					TableWrapper table = entry.getValue();
					String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
					String newName;
					if (name == null || name.equals("")) newName = timestamp + "_" + table.getName();
					else newName = timestamp + "_" + name;
					table.setName(newName);
					project.addTable(client, table);
				}
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Could not save table: " + e.getMessage());
			}
		}
	}


	/**
	 * Deletes the temp folder.
	 *
	 * @param tmpDir The temp folder.
	 *
	 * @return True if the deletion was successful.
	 */
	private boolean deleteTemp(String tmpDir) {
		//""" Delete the local copy of temporary files and directory """
		boolean deleted = true;
		File dir = new File(tmpDir);
		File[] entries = dir.listFiles();
		if (entries != null) {
			try {
				for (File entry : entries) {
					deleted &= Files.deleteIfExists(entry.toPath());
				}
				deleted &= Files.deleteIfExists(dir.toPath());
			} catch (IOException e) {
				IJ.error("Could not delete files: " + e.getMessage());
			}
		}
		return deleted;
	}


	/**
	 * Closes all open windows in ImageJ.
	 */
	private void closeWindows() {
		for (Frame frame : WindowManager.getNonImageWindows()) {
			if (frame instanceof TextWindow) {
				((TextWindow) frame).close(false);
			}
		}
		rm.reset();
		WindowManager.closeAllWindows();
	}


	public Client getClient() {
		return client;
	}


	public long getOutputProjectId() {
		return outputProjectId;
	}


	public void setOutputProjectId(Long outputProjectId) {
		if (outputProjectId != null) this.outputProjectId = outputProjectId;
	}


	public long getOutputDatasetId() {
		return outputDatasetId;
	}


	public void setOutputDatasetId(Long outputDatasetId) {
		if (outputDatasetId != null) this.outputDatasetId = outputDatasetId;
	}


	public long getInputDatasetId() {
		return inputDatasetId;
	}


	public void setInputDatasetId(Long inputDatasetId) {
		if (inputDatasetId != null) this.inputDatasetId = inputDatasetId;
	}


	public boolean shouldSaveROIs() {
		return saveROIs;
	}


	public void setSaveROIs(boolean saveROIs) {
		this.saveROIs = saveROIs;
	}


	public boolean shouldSaveResults() {
		return saveResults;
	}


	public void setSaveResults(boolean saveResults) {
		this.saveResults = saveResults;
	}


	public boolean isInputOnOMERO() {
		return inputOnOMERO;
	}


	public void setInputOnOMERO(boolean inputOnOMERO) {
		this.inputOnOMERO = inputOnOMERO;
	}


	public boolean shouldSaveImage() {
		return saveImage;
	}


	public void setSaveImage(boolean saveImage) {
		this.saveImage = saveImage;
	}


	public boolean shouldLoadROIs() {
		return loadROIs;
	}


	public void setLoadROIS(boolean loadROIs) {
		this.loadROIs = loadROIs;
	}


	public boolean shouldClearROIs() {
		return clearROIs;
	}


	public void setClearROIS(boolean clearROIs) {
		this.clearROIs = clearROIs;
	}


	public String getDirectoryIn() {
		return directoryIn;
	}


	public void setDirectoryIn(String directoryIn) {
		this.directoryIn = directoryIn;
	}


	public String getDirectoryOut() {
		return directoryOut;
	}


	public void setDirectoryOut(String directoryOut) {
		this.directoryOut = directoryOut;
	}


	public String getSuffix() {
		return suffix;
	}


	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}


	public boolean isOutputOnOMERO() {
		return outputOnOMERO;
	}


	public void setOutputOnOMERO(boolean outputOnOMERO) {
		this.outputOnOMERO = outputOnOMERO;
	}


	public boolean isOutputOnLocal() {
		return outputOnLocal;
	}


	public void setOutputOnLocal(boolean outputOnLocal) {
		this.outputOnLocal = outputOnLocal;
	}


	public void setSaveLog(boolean saveLog) {
		this.saveLog = saveLog;
	}


	public void addListener(BatchListener listener) {
		this.listener = listener;
	}

}
