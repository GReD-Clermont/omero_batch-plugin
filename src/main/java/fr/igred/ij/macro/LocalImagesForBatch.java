package fr.igred.ij.macro;

import ij.IJ;
import ij.ImagePlus;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

public class LocalImagesForBatch implements ImagesForBatch {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private static final File[] EMPTY_FILE_ARRAY = new File[0];

	private final ImporterOptions options = new ImporterOptions();
	private final Map<String, Integer> imageFiles;


	public LocalImagesForBatch(String directory, boolean recursive) throws IOException {
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
		File dir = new File(directory);
		List<String> files = listFiles(dir, recursive);
		imageFiles = new LinkedHashMap<>(files.size());
		List<String> used = new ArrayList<>(files.size());
		for (String file : files) {
			if (!used.contains(file)) {
				// Open the image
				options.setId(file);
				ImportProcess process = new ImportProcess(options);
				try {
					process.execute();
					int n = process.getSeriesCount();
					FileStitcher fs = process.getFileStitcher();
					if (fs != null) used = Arrays.asList(fs.getUsedFiles());
					else used.add(file);
					for (int i = 0; i < n; i++)
						imageFiles.put(file, i);
				} catch (IOException | FormatException e) {
					LOGGER.info(e.getMessage());
				}
			}
		}
	}


	private static List<String> listFiles(File directory, boolean recursive) {
		File[] files = directory.listFiles();
		if (files == null) files = EMPTY_FILE_ARRAY;
		List<String> paths = new ArrayList<>(files.length);
		for (File file : files) {
			if (!file.isDirectory()) {
				String path = file.getAbsolutePath();
				paths.add(path);
			} else if (recursive) {
				listFiles(file, true);
			}
		}
		return paths;
	}


	public void setLoadROIs(boolean loadROIs) {
		options.setShowROIs(loadROIs);
	}


	@Override
	public int size() {
		return imageFiles.size();
	}


	/**
	 * Returns an iterator over elements of type {@code T}.
	 *
	 * @return an Iterator.
	 */
	@Override
	public Iterator<BatchImage> iterator() {
		return new LocalImageIterator();
	}


	private class LocalImageIterator implements ImageIterator {

		private Iterator<Map.Entry<String, Integer>> iterator = imageFiles.entrySet().iterator();


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
			Map.Entry<String, Integer> entry = iterator.next();
			options.setId(entry.getKey());
			options.setSeriesOn(entry.getValue(), true);
			ImagePlus imp = null;
			try {
				ImagePlus[] imps = BF.openImagePlus(options);
				imp = imps[0];
			} catch (FormatException | IOException e) {
				IJ.error(e.getMessage());
			}
			return new BatchImage(null, imp);
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
