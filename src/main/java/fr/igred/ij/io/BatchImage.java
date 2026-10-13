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


/**
 * Interface to open images and retrieve the corresponding image on OMERO, if applicable.
 */
public interface BatchImage {

	/**
	 * Returns the related ImageWrapper, or null if there is none.
	 *
	 * @return See above.
	 */
	ImageWrapper getImageWrapper();


	/**
	 * Opens the image and returns the corresponding ImagePlus.
	 *
	 * @param mode The mode used to load ROIs.
	 *
	 * @return See above.
	 */
	ImagePlus getImagePlus(ROIMode mode);


	/**
	 * Opens the image and returns the corresponding ImagePlus, with no ROI.
	 *
	 * @return See above.
	 */
	default ImagePlus getImagePlus() {
		return getImagePlus(ROIMode.DO_NOT_LOAD);
	}

}
