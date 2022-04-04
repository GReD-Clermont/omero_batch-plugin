package fr.igred.ij.macro;

import java.util.Iterator;

public interface ImagesForBatch extends Iterable<BatchImage> {

	int size();

	interface ImageIterator extends Iterator<BatchImage> {

	}

}
