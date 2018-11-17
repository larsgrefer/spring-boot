/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.loader.jar;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;

import org.springframework.boot.loader.data.RandomAccessData;

/**
 * Provides access to entries from a {@link JarFile}. In order to reduce memory
 * consumption entry details are stored using int arrays. The {@code hashCodes} array
 * stores the hash code of the entry name, the {@code centralDirectoryOffsets} provides
 * the offset to the central directory record and {@code positions} provides the original
 * order position of the entry. The arrays are stored in hashCode order so that a binary
 * search can be used to find a name.
 * <p>
 * A typical Spring Boot application will have somewhere in the region of 10,500 entries
 * which should consume about 122K.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
class JarFileEntries implements CentralDirectoryVisitor, Iterable<JarEntry> {

	private static final long LOCAL_FILE_HEADER_SIZE = 30;

	private static final char SLASH = '/';

	private static final char NO_SUFFIX = 0;

	private final JarFile jarFile;

	private final JarEntryFilter filter;

	private final Map<AsciiBytes, FileHeader> entriesCache = Collections
			.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false));

	JarFileEntries(JarFile jarFile, JarEntryFilter filter) {
		this.jarFile = jarFile;
		this.filter = filter;
	}

	@Override
	public void visitStart(CentralDirectoryEndRecord endRecord,
						   RandomAccessData centralDirectoryData) {
	}

	@Override
	public void visitFileHeader(CentralDirectoryFileHeader fileHeader, int dataOffset) {
		AsciiBytes name = applyFilter(fileHeader.getName());
		if (name != null) {
			this.entriesCache.put(name, fileHeader);
		}
	}

	@Override
	public void visitEnd() {

	}

	int getSize() {
		return this.entriesCache.size();
	}

	@Override
	public Iterator<JarEntry> iterator() {
		return new EntryIterator(this.entriesCache.keySet().iterator());
	}

	public boolean containsEntry(CharSequence name) {
		return getEntry(name, FileHeader.class) != null;
	}

	public JarEntry getEntry(CharSequence name) {
		return getEntry(name, JarEntry.class);
	}

	public InputStream getInputStream(String name) throws IOException {
		FileHeader entry = getEntry(name, FileHeader.class);
		return getInputStream(entry);
	}

	public InputStream getInputStream(FileHeader entry) throws IOException {
		if (entry == null) {
			return null;
		}
		InputStream inputStream = getEntryData(entry).getInputStream();
		if (entry.getMethod() == ZipEntry.DEFLATED) {
			inputStream = new ZipInflaterInputStream(inputStream, (int) entry.getSize());
		}
		return inputStream;
	}

	public RandomAccessData getEntryData(String name) throws IOException {
		FileHeader entry = getEntry(name, FileHeader.class);
		if (entry == null) {
			return null;
		}
		return getEntryData(entry);
	}

	private RandomAccessData getEntryData(FileHeader entry) throws IOException {
		// aspectjrt-1.7.4.jar has a different ext bytes length in the
		// local directory to the central directory. We need to re-read
		// here to skip them
		RandomAccessData data = this.jarFile.getData();
		byte[] localHeader = data.read(entry.getLocalHeaderOffset(),
				LOCAL_FILE_HEADER_SIZE);
		long nameLength = Bytes.littleEndianValue(localHeader, 26, 2);
		long extraLength = Bytes.littleEndianValue(localHeader, 28, 2);
		return data.getSubsection(entry.getLocalHeaderOffset() + LOCAL_FILE_HEADER_SIZE
				+ nameLength + extraLength, entry.getCompressedSize());
	}

	private <T extends FileHeader> T getEntry(CharSequence name, Class<T> type) {
		AsciiBytes hashCode = new AsciiBytes(name.toString());
		T entry = getEntry(hashCode, name, NO_SUFFIX, type);
		if (entry == null) {
			hashCode = new AsciiBytes(name.toString() + SLASH);
			entry = getEntry(hashCode, name, SLASH, type);
		}
		return entry;
	}

	private <T extends FileHeader> T getEntry(AsciiBytes hashCode, CharSequence name,
											  char suffix, Class<T> type) {
		T entry = getEntry(hashCode, type);
		if (entry.hasName(name, suffix)) {
			return entry;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private <T extends FileHeader> T getEntry(AsciiBytes name, Class<T> type) {
		FileHeader entry = this.entriesCache.get(name);
		if (CentralDirectoryFileHeader.class.equals(entry.getClass())
				&& type.equals(JarEntry.class)) {
			entry = new JarEntry(this.jarFile, (CentralDirectoryFileHeader) entry);
		}
		return (T) entry;
	}

	public void clearCache() {
	}

	private AsciiBytes applyFilter(AsciiBytes name) {
		return (this.filter != null) ? this.filter.apply(name) : name;
	}

	/**
	 * Iterator for contained entries.
	 */
	private class EntryIterator implements Iterator<JarEntry> {

		private Iterator<AsciiBytes> delegate;

		public EntryIterator(Iterator<AsciiBytes> iterator) {
			this.delegate = iterator;
		}

		@Override
		public boolean hasNext() {
			return delegate.hasNext();
		}

		@Override
		public JarEntry next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			AsciiBytes next = delegate.next();
			return getEntry(next, JarEntry.class);
		}

	}

}
