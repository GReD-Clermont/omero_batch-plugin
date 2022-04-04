package fr.igred.ij.io;

import fr.igred.omero.repository.ImageWrapper;
import ij.ImagePlus;

public interface BatchImage {


	ImageWrapper getImageWrapper();


	ImagePlus getImagePlus();


	Long getId();

}
