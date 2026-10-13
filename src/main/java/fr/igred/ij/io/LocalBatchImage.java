/*
 *  Copyright (C) 2021-2026 MICA & GReD
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
package fr.igred.ij.io;


import fr.igred.omero.repository.ImageWrapper;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import loci.formats.FileStitcher;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;


/**
 * Image stored in a local file.
 */
public class LocalBatchImage implements BatchImage {

	/** The logger. */
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	/** Empty file array defined once. */
	private static final File[] EMPTY_FILE_ARRAY = new File[0];

	/** The path to the image. */
	private final String path;
	/** The image index. */
	private final Integer index;


	/**
	 * Creates a new instance with the specified path and index.
	 *
	 * @param path  The path.
	 * @param index The image index.
	 */
	public LocalBatchImage(String path, Integer index) {
		this.path = path;
		this.index = index;
	}


	/**
	 * List the files contained in the directory.
	 *
	 * @param directory The directory.
	 * @param recursive Whether files should be listed recursively.
	 *
	 * @return The list of file paths.
	 */
	private static List<String> listFiles(File directory, boolean recursive) {
		File[] files = directory.listFiles();
		if (files == null) {
			files = EMPTY_FILE_ARRAY;
		}
		List<String> paths = new ArrayList<>(files.length);
		for (File file : files) {
			if (!file.isDirectory()) {
				paths.add(file.getAbsolutePath());
			} else if (recursive) {
				paths.addAll(listFiles(file, true));
			}
		}
		return paths;
	}


	/**
	 * Checks if the specified file is a ROI file.
	 *
	 * @param file The file path.
	 *
	 * @return True if it is a ROI file.
	 */
	private static boolean isRoiFile(String file) {
		return file.endsWith(".roi") || (file.endsWith(".zip") && file.contains("_RoiSet"));
	}


	/**
	 * Creates a list of images to be opened, contained in the specified directory.
	 *
	 * @param directory The directory.
	 * @param recursive Whether files should be listed recursively.
	 *
	 * @return The list of images.
	 *
	 * @throws IOException ImporterOptions could not be instantiated.
	 */
	public static List<BatchImage> listImages(String directory, boolean recursive)
	throws IOException {
		ImporterOptions options = initImporterOptions();
		List<BatchImage> batchImages = new LinkedList<>();
		File dir = new File(directory);
		List<String> files = listFiles(dir, recursive);
		List<String> used = new ArrayList<>(files.size());
		for (String file : files) {
			if (!used.contains(file) && !isRoiFile(file)) {
				// Open the image
				options.setId(file);
				ImportProcess process = new ImportProcess(options);
				try {
					process.execute();
					int n = process.getSeriesCount();
					FileStitcher fs = process.getFileStitcher();
					if (fs != null) {
						used = Arrays.asList(fs.getUsedFiles());
					} else {
						used.add(file);
					}
					for (int i = 0; i < n; i++) {
						batchImages.add(new LocalBatchImage(file, i));
					}
				} catch (IOException | FormatException e) {
					LOGGER.severe(e.getMessage());
				}
			}
		}
		return batchImages;
	}


	/**
	 * Initializes the Bio-Formats importer options.
	 *
	 * @return See above.
	 *
	 * @throws IOException If the importer options could not be initialized.
	 */
	private static ImporterOptions initImporterOptions() throws IOException {
		ImporterOptions options = new ImporterOptions();
		options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
		options.setSwapDimensions(false);
		options.setOpenAllSeries(false);
		options.setSpecifyRanges(false);
		options.setShowMetadata(false);
		options.setShowOMEXML(false);
		options.setCrop(false);
		options.setSplitChannels(false);
		options.setSplitFocalPlanes(false);
		options.setSplitTimepoints(false);
		return options;
	}


	/**
	 * Returns null.
	 *
	 * @return See above.
	 */
	@Override
	public ImageWrapper getImageWrapper() {
		return null;
	}


	/**
	 * Opens the image and returns the corresponding ImagePlus.
	 *
	 * @return See above.
	 */
	@Override
	public ImagePlus getImagePlus(ROIMode mode) {
		ImagePlus imp = null;
		boolean loadROIs = mode != ROIMode.DO_NOT_LOAD;
		try {
			ImporterOptions options = initImporterOptions();
			options.setShowROIs(loadROIs);
			if (loadROIs) {
				options.setROIsMode(mode.toString());
				loadROIs(imp, RoiManager.getInstance2(), mode);
			}
			options.setId(path);
			options.setSeriesOn(index, true);
			ImagePlus[] imps = BF.openImagePlus(options);
			imp = imps[0];
		} catch (FormatException | IOException e) {
			LOGGER.severe(e.getMessage());
		}
		return imp;
	}


	/**
	 * Returns the path to the ROI next to the image file.
	 *
	 * @return See above.
	 */
	@SuppressWarnings("MagicCharacter")
	private String getRoiPath() {
		String beforeExt = path.substring(0, path.lastIndexOf('.'));
		beforeExt = beforeExt.isEmpty() ? path : beforeExt;
		if (beforeExt.toLowerCase(Locale.ROOT).endsWith(".ome") && beforeExt.lastIndexOf('.') > 0) {
			beforeExt = beforeExt.substring(0, beforeExt.lastIndexOf('.'));
		}

		String imageIndex = index == null || index.equals(0) ? "" : "-" + index;

		boolean roiExists;
		String roiPath = beforeExt + imageIndex + ".roi";
		File roiFile = new File(roiPath);
		if (!roiFile.exists() || !roiFile.isFile()) {
			roiPath = beforeExt + imageIndex + "_RoiSet.zip";
			File zipFile = new File(roiPath);
			roiExists = zipFile.exists() && zipFile.isFile();
		} else {
			roiExists = true;
		}
		return roiExists ? roiPath : "";
	}


	/**
	 * Loads ROIs from an image in OMERO into ImageJ.
	 *
	 * @param imp     The image in ImageJ ROIs should be linked to.
	 * @param manager The ROI Manager.
	 * @param roiMode The mode used to load ROIs.
	 */
	private void loadROIs(ImagePlus imp, RoiManager manager, ROIMode roiMode) {
		RoiManager rm = manager;
		if (rm == null) {
			rm = RoiManager.getRoiManager();
		}

		String roiPath = getRoiPath();
		if (!roiPath.isEmpty() && roiMode != ROIMode.DO_NOT_LOAD) {
			rm.open(roiPath);
			Roi[] ijRois = rm.getRoisAsArray();
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
			}
			if (imp != null && roiMode == ROIMode.OVERLAY) {
				Overlay overlay = imp.getOverlay();
				for (Roi ijRoi : ijRois) {
					overlay.add(ijRoi, ijRoi.getName());
				}
				rm.reset();
			}
		}
	}


	@Override
	public String toString() {
		return "LocalBatchImage{" +
			   "path='" + path + "'" +
			   ", index=" + index +
			   "}";
	}

}
