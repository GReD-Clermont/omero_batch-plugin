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
package fr.igred.ij.macro;


import fr.igred.ij.gui.ProgressDialog;
import fr.igred.ij.io.BatchImage;
import fr.igred.ij.io.ROIMode;
import fr.igred.omero.AnnotatableWrapper;
import fr.igred.omero.Client;
import fr.igred.omero.annotations.TableWrapper;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
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

import java.awt.Component;
import java.awt.Frame;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.file.Files.newOutputStream;


/**
 * Runs a script over multiple images retrieved from local files or from OMERO.
 */
public class OMEROBatchRunner extends Thread {

	/** The logger. */
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	/** The empty int array. */
	private static final int[] EMPTY_INT_ARRAY = new int[0];

	/** The pattern to remove the file extension from an image title. */
	private static final Pattern TITLE_AFTER_EXT = Pattern.compile("\\w+\\s?\\[?([^\\[\\]]*)]?");

	/** The images. */
	private final Map<String, List<BatchImage>> images;
	/** The script. */
	private final ScriptRunner script;
	/** The OMERO client. */
	private final Client client;
	/** The progress monitor. */
	private final ProgressMonitor progress;
	/** The parameters. */
	private final BatchParameters params;

	/** The tables. */
	private final Map<String, TableWrapper> tables = new HashMap<>(5);

	/** The ROI manager. */
	private RoiManager rm;

	/** The listener. */
	private BatchListener listener;


	/**
	 * Creates a new instance with the specified script, images and parameters.
	 *
	 * @param script The script.
	 * @param images The images.
	 * @param params The parameters.
	 * @param client The OMERO client.
	 */
	public OMEROBatchRunner(ScriptRunner script, Map<String, List<BatchImage>> images, BatchParameters params, Client client) {
		this(script, images, params, client, new ProgressLog(LOGGER));
	}


	/**
	 * Creates a new instance with the specified script, images, parameters and progress monitor.
	 *
	 * @param script   The script.
	 * @param images   The images.
	 * @param params   The parameters.
	 * @param client   The OMERO client.
	 * @param progress The progress monitor.
	 */
	public OMEROBatchRunner(ScriptRunner script,
							Map<String, List<BatchImage>> images,
							BatchParameters params,
							Client client,
							ProgressMonitor progress) {
		this.script = script;
		this.images = new HashMap<>(images);
		this.params = new BatchParameters(params);
		this.client = client;
		this.progress = progress;
		this.rm = null;
		this.listener = null;
	}


	/**
	 * Generates the timestamp for current time.
	 *
	 * @return See above.
	 */
	private static String timestamp() {
		return DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(ZonedDateTime.now());
	}


	/**
	 * Removes file extension from image title.
	 *
	 * @param title Image title.
	 *
	 * @return The title, without the extension.
	 */
	@SuppressWarnings("MagicCharacter")
	private static String removeExtension(String title) {
		if (title != null) {
			int index = title.lastIndexOf('.');
			if (index == 0 || index == -1) {
				return title;
			} else {
				String afterExt = TITLE_AFTER_EXT.matcher(title.substring(index + 1)).replaceAll("$1");
				String beforeExt = title.substring(0, index);
				if (beforeExt.toLowerCase().endsWith(".ome") && beforeExt.lastIndexOf('.') > 0) {
					beforeExt = beforeExt.substring(0, beforeExt.lastIndexOf('.'));
				}
				return afterExt.isEmpty() ? beforeExt : beforeExt + "_" + afterExt;
			}
		} else {
			return null;
		}
	}


	/**
	 * Deletes the temp folder.
	 *
	 * @param tmpDir The temp folder.
	 *
	 * @return True if the deletion was successful.
	 */
	private static boolean deleteTemp(String tmpDir) {
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
	 * Retrieves the list of images open after the script was run.
	 *
	 * @param inputImage The input image.
	 *
	 * @return See above.
	 */
	private static List<ImagePlus> getOutputImages(ImagePlus inputImage) {
		ImagePlus outputImage = WindowManager.getCurrentImage();
		if (outputImage == null) {
			outputImage = inputImage;
		}
		int ijOutputId = outputImage.getID();

		int[] imageIds = WindowManager.getIDList();
		if (imageIds == null) {
			imageIds = EMPTY_INT_ARRAY;
		}
		List<Integer> idList = Arrays.stream(imageIds).boxed().collect(Collectors.toList());
		idList.removeIf(i -> i.equals(ijOutputId));
		idList.add(0, ijOutputId);
		return idList.stream()
					 .map(WindowManager::getImage)
					 .filter(Objects::nonNull)
					 .collect(Collectors.toList());
	}


	/**
	 * Retrieves the list of ROIs from an image overlay.
	 *
	 * @param imp The image ROIs are linked to.
	 *
	 * @return See above.
	 */
	private static List<Roi> getOverlay(ImagePlus imp) {
		Overlay overlay = imp.getOverlay();
		List<Roi> ijRois = new ArrayList<>(0);
		if (overlay != null) {
			ijRois = new ArrayList<>(Arrays.asList(overlay.toArray()));
		}
		for (Roi roi : ijRois) {
			roi.setImage(imp);
		}
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
	private static List<ROIWrapper> getROIsFromOverlay(ImagePlus imp, String property) {
		List<ROIWrapper> rois = new ArrayList<>(0);
		if (imp != null) {
			List<Roi> ijRois = getOverlay(imp);
			rois = ROIWrapper.fromImageJ(ijRois, property);
		}

		return rois;
	}


	/**
	 * Saves ImageJ ROIs to a file.
	 *
	 * @param ijRois The ROIs.
	 * @param path   The path to the file.
	 */
	private static void saveRoiFile(List<? extends Roi> ijRois, String path) {
		try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(newOutputStream(Paths.get(path))));
			 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(zos))) {
			RoiEncoder re = new RoiEncoder(dos);
			for (int i = 0; i < ijRois.size(); i++) {
				if (ijRois.get(i) != null) {
					// WARNING: Prepending index does not ensure label is unique.
					String label = i + "-" + ijRois.get(i).getName() + ".roi";
					zos.putNextEntry(new ZipEntry(label));
					re.write(ijRois.get(i));
					dos.flush();
				}
			}
		} catch (IOException e) {
			IJ.error("Error while saving ROI file: " + e.getMessage());
		}
	}


	/**
	 * Adds a timestamp to the table name.
	 *
	 * @param table The table.
	 * @param name  The table name.
	 */
	private static String renameTable(TableWrapper table, String name) {
		String newName;
		if (name == null || name.isEmpty()) {
			newName = timestamp() + "_" + table.getName();
		} else {
			newName = timestamp() + "_" + name;
		}
		table.setName(newName);
		return newName;
	}


	/**
	 * Saves a table as a text file.
	 *
	 * @param table The table.
	 * @param path  The path to the file.
	 */
	private static void saveTable(TableWrapper table, String path) {
		try {
			//noinspection MagicCharacter
			table.saveAs(path, '\t');
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			IJ.error("Could not save table as file: " + e.getMessage());
		}
	}


	/**
	 * Initializes the ROI manager.
	 */
	private void initRoiManager() {
		rm = RoiManager.getInstance2();
		if (rm == null) {
			rm = RoiManager.getRoiManager();
		}
		rm.setVisible(false);
	}


	/**
	 * Sets the current state.
	 *
	 * @param text The text for the current state.
	 */
	private void setState(String text) {
		if (progress != null) {
			progress.setState(text);
		}
	}


	/**
	 * Sets the current progress.
	 *
	 * @param text The text for the current progress.
	 */
	private void setProgress(String text) {
		if (progress != null) {
			progress.setProgress(text);
		}
	}


	/**
	 * Signals the process is done.
	 */
	private void setDone() {
		if (progress != null) {
			progress.setDone();
		}
	}


	/**
	 * If this thread was constructed using a separate {@code Runnable} run object, then that {@code Runnable} object's
	 * {@code run} method is called; otherwise, this method does nothing and returns.
	 * <p>
	 * Subclasses of {@code Thread} should override this method.
	 *
	 * @see #start()
	 * @see Thread#Thread(ThreadGroup, Runnable, String)
	 */
	@Override
	public void run() {
		boolean running = true;
		if (progress instanceof ProgressDialog) {
			((Component) progress).setVisible(true);
		}

		try {
			if (!params.isOutputOnLocal()) {
				setState("Temporary directory creation...");
				params.setDirectoryOut(Files.createTempDirectory("Fiji_analysis").toString());
			}

			for (Entry<String, List<BatchImage>> entry : images.entrySet()) {
				setState("Macro running...");
				runMacro(entry);
				setProgress("");
				uploadTables(entry.getKey());
				tables.clear();
			}

			if (!params.isOutputOnLocal()) {
				setState("Temporary directory deletion...");
				if (!deleteTemp(params.getDirectoryOut())) {
					LOGGER.warning("Temp directory may not be deleted.");
				}
			}
			running = false;
			setState("");
			setDone();
		} catch (IOException e) {
			running = false;
			setDone();
			setProgress("Macro cancelled");
			if ("Macro cancelled".equals(e.getMessage())) {
				IJ.run("Close");
			}
			IJ.error(e.getMessage());
		} finally {
			if (running) {
				setDone();
				setProgress("An unexpected error occurred.");
			}
			if (listener != null) {
				listener.onThreadFinished();
			}
			if (rm != null) {
				rm.setVisible(true);
				rm.close();
			}
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
			rois.removeIf(roi -> roi.getOwner().getId() != client.getId());
			client.delete(rois);
		} catch (ExecutionException | OMEROServerError | ServiceException | AccessException exception) {
			LOGGER.warning(exception.getMessage());
		} catch (InterruptedException e) {
			LOGGER.warning(e.getMessage());
			currentThread().interrupt();
		}
	}


	/**
	 * Retrieves the list of ROIs from the ROI manager.
	 *
	 * @param imp The image ROIs are linked to.
	 *
	 * @return See above.
	 */
	private List<Roi> getManagedRois(ImagePlus imp) {
		List<Roi> ijRois = new ArrayList<>(Arrays.asList(rm.getRoisAsArray()));
		for (Roi roi : ijRois) {
			roi.setImage(imp);
		}
		return ijRois;
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
	 * Runs a macro on images and saves the results.
	 */
	private void runMacro(Entry<String, ? extends List<BatchImage>> imgList) {
		String property = ROIWrapper.IJ_PROPERTY;
		WindowManager.closeAllWindows();

		int index = 0;
		for (BatchImage image : imgList.getValue()) {
			// Initialize ROI Manager
			initRoiManager();

			//noinspection HardcodedFileSeparator
			String prog = String.format("Processing %s: %n Image %d/%d",
										imgList.getKey(),
										index + 1,
										imgList.getValue().size());

			setProgress(prog);
			setState("Opening image...");
			ImagePlus imp = image.getImagePlus(params.getROIMode());
			// If image could not be loaded, continue to next image.
			if (imp != null) {
				ImageWrapper imageWrapper = image.getImageWrapper();
				Long inputImageId = imageWrapper != null ? imageWrapper.getId() : null;
				imp.show();

				// Process the image
				setState("Processing image...");
				script.setImage(imp);
				script.run();

				imp.changes = false; // Prevent "Save Changes?" dialog
				save(imp, inputImageId, property);
			}
			closeWindows();
			index++;
		}
	}


	/**
	 * Loads ROIs from an image in OMERO into ImageJ.
	 *
	 * @param image   The OMERO image.
	 * @param imp     The image in ImageJ ROIs should be linked to.
	 * @param roiMode The mode used to load ROIs.
	 */
	private void loadROIs(ImageWrapper image, ImagePlus imp, ROIMode roiMode) {
		List<Roi> ijRois = new ArrayList<>(0);
		try {
			ijRois = ROIWrapper.toImageJ(image.getROIs(client));
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error("Could not load ROIs: " + e.getMessage());
		}
		if (roiMode == ROIMode.OVERLAY) {
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
		} else if (roiMode == ROIMode.MANAGER) {
			rm.reset(); // Reset ROI manager to clear previous ROIs
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				rm.addRoi(ijRoi);
			}
		}
	}


	/**
	 * Saves the images, results and ROIs.
	 *
	 * @param inputImage   The input image in ImageJ.
	 * @param omeroInputId The OMERO image input ID.
	 * @param property     The ROI property used to group shapes in OMERO.
	 */
	private void save(ImagePlus inputImage, Long omeroInputId, String property) {
		String inputTitle = removeExtension(inputImage.getTitle());

		Long omeroOutputId = omeroInputId;
		List<ImagePlus> outputs = getOutputImages(inputImage);

		ImagePlus outputImage = outputs.isEmpty() ? inputImage : outputs.get(0);

		boolean annotatable = Boolean.parseBoolean(inputImage.getProp("Annotatable"));
		boolean outputIsNotInput = !inputImage.equals(outputImage);
		if (!params.isOutputOnOMERO() || !params.shouldSaveROIs() || annotatable || outputIsNotInput) {
			outputs.removeIf(inputImage::equals);
		}

		if (params.shouldSaveImages()) {
			List<Long> outputIds = saveImages(outputs, property);
			if (!outputIds.isEmpty() && outputIsNotInput) {
				omeroOutputId = outputIds.get(0);
			}
		}

		if (params.shouldSaveROIs()) {
			if (!params.shouldSaveImages()) {
				saveOverlay(outputImage, omeroOutputId, inputTitle, property);
			}
			saveROIManager(outputImage, omeroOutputId, inputTitle, property);
		}
		if (params.shouldSaveResults()) {
			saveResults(outputImage, omeroOutputId, inputTitle, property);
		}
		if (params.shouldSaveLog()) {
			saveLog(omeroOutputId, inputTitle);
		}

		for (ImagePlus imp : outputs) {
			imp.changes = false;
			imp.close();
		}
	}


	/**
	 * Saves images.
	 *
	 * @param outputs  The images to save.
	 * @param property The ROI property used to group shapes in OMERO.
	 *
	 * @return The OMERO IDs of the (possibly) uploaded images.
	 */
	private List<Long> saveImages(Collection<? extends ImagePlus> outputs, String property) {
		if (outputs.isEmpty()) {
			LOGGER.info("Warning: there is no new image.");
		}
		List<Long> outputIds = new ArrayList<>(outputs.size());
		outputs.forEach(imp -> outputIds.addAll(saveImage(imp, property)));
		return outputIds;
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
		List<Long> ids = new ArrayList<>(0);
		String title = removeExtension(image.getTitle());
		String path = params.getDirectoryOut() + File.separator +
					  title + params.getSuffix() + ".tif";
		IJ.saveAsTiff(image, path);
		if (params.isOutputOnOMERO()) {
			try {
				setState("Import on OMERO...");
				DatasetWrapper dataset = client.getDataset(params.getOutputDatasetId());
				ids = dataset.importImage(client, path);
				if (params.shouldSaveROIs() && !ids.isEmpty()) {
					saveOverlay(image, ids.get(0), title, property);
				}
			} catch (AccessException | ServiceException | OMEROServerError | ExecutionException e) {
				IJ.error("Could not import image: " + e.getMessage());
			}
		}
		return ids;
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
		if (params.isOutputOnLocal()) {  //  local save
			setState("Saving overlay ROIs...");
			String timestamp = params.shouldClearROIs() ? "" : timestamp() + "_";
			String path = params.getDirectoryOut() + File.separator +
						  title + "_" + timestamp + "RoiSet.zip";
			List<Roi> ijRois = getOverlay(imp);
			saveRoiFile(ijRois, path);
		}
		if (params.isOutputOnOMERO() && imageId != null) { // save on Omero
			List<ROIWrapper> rois = getROIsFromOverlay(imp, property);
			try {
				ImageWrapper image = client.getImage(imageId);
				if (params.shouldClearROIs()) {
					deleteROIs(image);
				}
				setState("Saving overlay ROIs on OMERO...");
				image.saveROIs(client, rois);
				loadROIs(image, imp, ROIMode.OVERLAY); // reload ROIs
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
		if (params.isOutputOnLocal()) {  //  local save
			setState("Saving ROIs...");
			String timestamp = params.shouldClearROIs() ? "" : timestamp() + "_";
			String path = params.getDirectoryOut() + File.separator +
						  title + "_" + timestamp + "RoiSet.zip";
			List<Roi> ijRois = getManagedRois(imp);
			saveRoiFile(ijRois, path);
		}
		if (params.isOutputOnOMERO() && imageId != null) { // save on Omero
			List<ROIWrapper> rois = getROIsFromManager(imp, property);
			try {
				ImageWrapper image = client.getImage(imageId);
				if (params.shouldClearROIs()) {
					deleteROIs(image);
				}
				setState("Saving ROIs on OMERO...");
				image.saveROIs(client, rois);
				loadROIs(image, imp, ROIMode.MANAGER); // reload ROIs
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
		List<Roi> ijRois = getOverlay(imp);
		ijRois.addAll(getManagedRois(imp));

		setState("Saving results files...");
		String[] candidates = WindowManager.getNonImageTitles();
		List<ResultsTable> results = Arrays.stream(candidates)
										   .map(ResultsTable::getResultsTable)
										   .collect(Collectors.toList());
		results.add(0, ResultsTable.getResultsTable());
		Map<String, Boolean> processed = new HashMap<>(results.size());
		for (ResultsTable rt : results) {
			if (rt != null) {
				String name = rt.getTitle();
				if (!Boolean.TRUE.equals(processed.get(name)) && rt.getHeadings().length > 0) {
					String path = params.getDirectoryOut() +
								  File.separator +
								  name + "_" +
								  title + "_" +
								  timestamp() + ".csv";
					rt.save(path);
					appendTable(rt, imageId, ijRois, property);
					uploadFileToImage(imageId, path);
					rt.reset();
					processed.put(name, true);
				}
			}
		}
	}


	/**
	 * Saves the log.
	 *
	 * @param imageId The image ID on OMERO.
	 * @param title   The image title used to name the file when saving locally.
	 */
	private void saveLog(Long imageId, String title) {
		String path = params.getDirectoryOut() + File.separator + title + "_log.txt";
		IJ.selectWindow("Log");
		IJ.saveAs("txt", path);
		uploadFileToImage(imageId, path);
	}


	/**
	 * Uploads a file to an image on OMERO.
	 *
	 * @param imageId The image ID on OMERO.
	 * @param path    The path to the file.
	 */
	private void uploadFileToImage(Long imageId, String path) {
		if (imageId != null && params.isOutputOnOMERO()) {
			ImageWrapper image = null;
			try {
				setState("Uploading results files...");
				image = client.getImage(imageId);
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Error retrieving image:" + e.getMessage());
			}
			uploadFile(image, path);
		}
	}


	/**
	 * Uploads a file to an annotatable object on OMERO.
	 *
	 * @param object The object on OMERO.
	 * @param path   The path to the file.
	 */
	private void uploadFile(AnnotatableWrapper<?> object, String path) {
		if (object != null && params.isOutputOnOMERO()) {
			try {
				object.addFile(client, new File(path));
			} catch (ExecutionException e) {
				IJ.error("Error adding file to object:" + e.getMessage());
			} catch (InterruptedException e) {
				IJ.error("Error adding file to object:" + e.getMessage());
				currentThread().interrupt();
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
	private void appendTable(ResultsTable results, Long imageId, List<? extends Roi> ijRois, String property) {
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
	 * Uploads a table to a project, if required.
	 *
	 * @param repoWrapper The project the table belongs to.
	 * @param table       The table.
	 */
	private void uploadTable(AnnotatableWrapper<?> repoWrapper, TableWrapper table) {
		if (repoWrapper != null && params.isOutputOnOMERO()) {
			try {
				repoWrapper.addTable(client, table);
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Could not upload table: " + e.getMessage());
			}
		}
	}


	/**
	 * Upload the tables to OMERO.
	 */
	private void uploadTables(String parentName) {
		AnnotatableWrapper<?> repoWrapper = null;
		if (params.shouldSaveResults()) {
			setState("Uploading tables...");
			if (params.isOutputOnOMERO()) {
				if (params.getOutputProjectId() > 0) {
					try {
						repoWrapper = client.getProject(params.getOutputProjectId());
					} catch (ExecutionException | ServiceException | AccessException e) {
						IJ.error("Could not retrieve project: " + e.getMessage());
					}
				} else {
					try {
						repoWrapper = client.getScreen(params.getOutputScreenId());
					} catch (ExecutionException | ServiceException | AccessException e) {
						IJ.error("Could not retrieve screen: " + e.getMessage());
					}
				}
			}
			for (Entry<String, TableWrapper> entry : tables.entrySet()) {
				String name = entry.getKey() + "_" + parentName;
				TableWrapper table = entry.getValue();
				String newName = renameTable(table, name);
				uploadTable(repoWrapper, table);
				String path = params.getDirectoryOut() + File.separator + newName + ".csv";
				saveTable(table, path);
				uploadFile(repoWrapper, path);
			}
		}
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


	/**
	 * Returns the client.
	 *
	 * @return See above.
	 */
	public Client getClient() {
		return client;
	}


	/**
	 * Sets the listener.
	 *
	 * @param listener The listener.
	 */
	public void setListener(BatchListener listener) {
		this.listener = listener;
	}

}
