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
package fr.igred.ij.io;


import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.roi.ROIWrapper;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Image from OMERO.
 */
public class OMEROBatchImage implements BatchImage {

	/** The logger. */
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	/** The OMERO client. */
	private final Client client;
	/** The OMERO image. */
	private final ImageWrapper imageWrapper;


	/**
	 * Creates a new instance with the specified client and image.
	 *
	 * @param client       The OMERO client.
	 * @param imageWrapper The OMERO image.
	 */
	public OMEROBatchImage(Client client, ImageWrapper imageWrapper) {
		this.client = client;
		this.imageWrapper = imageWrapper;
	}


	/**
	 * Creates a list of OMERO images to be opened.
	 *
	 * @param client The OMERO client.
	 * @param images The list of ImageWrappers.
	 *
	 * @return The list of images.
	 */
	public static List<BatchImage> listImages(Client client, Collection<? extends ImageWrapper> images) {
		return images.stream().map(i -> new OMEROBatchImage(client, i)).collect(Collectors.toList());
	}


	/**
	 * Returns the related ImageWrapper, or null if there is none.
	 *
	 * @return See above.
	 */
	@Override
	public ImageWrapper getImageWrapper() {
		return imageWrapper;
	}


	/**
	 * Opens the image and returns the corresponding ImagePlus.
	 *
	 * @return See above.
	 */
	@Override
	public ImagePlus getImagePlus(ROIMode mode) {
		ImagePlus imp = null;
		try {
			imp = imageWrapper.toImagePlus(client);
			// Store image "annotate" permissions as a property in the ImagePlus object
			imp.setProp("Annotatable", String.valueOf(imageWrapper.canAnnotate()));
			if (mode != ROIMode.DO_NOT_LOAD) {
				loadROIs(imp, RoiManager.getInstance2(), mode);
			}
		} catch (ExecutionException | ServiceException | AccessException e) {
			LOGGER.severe("Could not load image: " + e.getMessage());
		}
		return imp;
	}


	/**
	 * Loads ROIs from an image in OMERO into ImageJ (removes previous ROIs).
	 *
	 * @param imp     The image in ImageJ ROIs should be linked to.
	 * @param manager The ROI Manager.
	 * @param roiMode The mode used to load ROIs.
	 */
	private void loadROIs(ImagePlus imp, RoiManager manager, ROIMode roiMode) {
		List<Roi> ijRois = new ArrayList<>(0);
		RoiManager rm = manager;
		if (rm == null) {
			rm = RoiManager.getRoiManager();
		}
		try {
			ijRois = ROIWrapper.toImageJ(imageWrapper.getROIs(client));
		} catch (ExecutionException | ServiceException | AccessException e) {
			LOGGER.severe("Could not load ROIs: " + e.getMessage());
		}
		if (roiMode == ROIMode.OVERLAY && imp != null) {
			Overlay overlay = imp.getOverlay();
			if (overlay != null) {
				overlay.clear();
			} else {
				imp.setOverlay(new Overlay());
				overlay = imp.getOverlay();
			}
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				overlay.add(ijRoi, ijRoi.getName());
			}
		} else if (roiMode == ROIMode.MANAGER && rm != null) {
			rm.reset(); // Reset ROI manager to clear previous ROIs
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				rm.addRoi(ijRoi);
			}
		}
	}


	@Override
	public String toString() {
		return "OMEROBatchImage{" +
			   "client=" + client +
			   ", imageWrapper=" + imageWrapper +
			   "}";
	}

}
