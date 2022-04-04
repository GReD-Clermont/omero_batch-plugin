package fr.igred.ij.macro;

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.ImageWrapper;
import ij.ImagePlus;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;


public class OMEROImagesForBatch implements ImagesForBatch {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private List<ImageWrapper> images;
	private Client client;


	public OMEROImagesForBatch(Client client, List<ImageWrapper> images) {
		this.client = client;
		this.images = images;
	}


	@Override
	public int size() {
		return images.size();
	}


	/**
	 * Returns an iterator over elements of type {@code T}.
	 *
	 * @return an Iterator.
	 */
	@Override
	public Iterator<BatchImage> iterator() {
		return new OMEROImageIterator();
	}


	private class OMEROImageIterator implements ImageIterator {

		private Iterator<ImageWrapper> iterator = images.iterator();


		/**
		 * Returns {@code true} if the iteration has more elements. (In other words, returns {@code true} if {@link
		 * #next} would return an element rather than throwing an exception.)
		 *
		 * @return {@code true} if the iteration has more elements
		 */
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}


		/**
		 * Returns the next element in the iteration.
		 *
		 * @return the next element in the iteration
		 *
		 * @throws NoSuchElementException if the iteration has no more elements
		 */
		@Override
		public BatchImage next() {
			ImageWrapper image = iterator.next();
			ImagePlus imp = null;
			try {
				imp = image.toImagePlus(client);
				// Store image "annotate" permissions as a property in the ImagePlus object
				imp.setProp("Annotable", String.valueOf(image.canAnnotate()));
			} catch (ServiceException | AccessException | ExecutionException e) {
				LOGGER.warning(e.getMessage());
			}
			return new BatchImage(image, imp);
		}


		/**
		 * Removes from the underlying collection the last element returned by this iterator (optional operation).  This
		 * method can be called only once per call to {@link #next}.
		 * <p>
		 * The behavior of an iterator is unspecified if the underlying collection is modified while the iteration is in
		 * progress in any way other than by calling this method, unless an overriding class has specified a concurrent
		 * modification policy.
		 * <p>
		 * The behavior of an iterator is unspecified if this method is called after a call to the {@link
		 * #forEachRemaining forEachRemaining} method.
		 *
		 * @throws UnsupportedOperationException if the {@code remove} operation is not supported by this iterator
		 * @throws IllegalStateException         if the {@code next} method has not yet been called, or the {@code
		 *                                       remove} method has already been called after the last call to the
		 *                                       {@code next} method
		 * @implSpec The default implementation throws an instance of {@link UnsupportedOperationException} and performs
		 * no other action.
		 */
		@Override
		public void remove() {
			iterator.remove();
		}

	}

}
