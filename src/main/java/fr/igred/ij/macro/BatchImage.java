package fr.igred.ij.macro;

import fr.igred.omero.repository.ImageWrapper;
import ij.ImagePlus;

public class BatchImage {
	private ImageWrapper imageWrapper;
	private ImagePlus imagePlus;


	public BatchImage(ImageWrapper imageWrapper, ImagePlus imagePlus) {
		this.imageWrapper = imageWrapper;
		this.imagePlus = imagePlus;
	}


	public ImageWrapper getImageWrapper() {
		return imageWrapper;
	}


	public ImagePlus getImagePlus() {
		return imagePlus;
	}


	public Long getId() {
		return imageWrapper != null ? imageWrapper.getId() : null;
	}

}
