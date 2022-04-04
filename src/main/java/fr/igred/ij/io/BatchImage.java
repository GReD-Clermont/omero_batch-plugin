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
	 * @return See above.
	 */
	ImagePlus getImagePlus();

}
