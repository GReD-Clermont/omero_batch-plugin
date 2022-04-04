package fr.igred.ij.io;

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.ImageWrapper;
import ij.ImagePlus;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Image from OMERO.
 */
public class OMEROBatchImage implements BatchImage {
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private final Client client;
	private final ImageWrapper imageWrapper;


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
	public ImagePlus getImagePlus() {
		ImagePlus imp = null;
		try {
			imp = imageWrapper.toImagePlus(client);
			// Store image "annotate" permissions as a property in the ImagePlus object
			imp.setProp("Annotable", String.valueOf(imageWrapper.canAnnotate()));
		} catch (ServiceException | AccessException | ExecutionException e) {
			LOGGER.severe(e.getMessage());
		}
		return imp;
	}

}
