/*******************************************************************************
 * Copyright (c) 2014 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Initial API and implementation and/or initial documentation - Jay Jay Billings,
 *   Jordan H. Deyton, Dasha Gorin, Alexander J. McCaskey, Taylor Patterson,
 *   Claire Saunders, Matthew Wang, Anna Wojtowicz
 *******************************************************************************/
package org.eclipse.ice.io.hdf;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ncsa.hdf.hdf5lib.structs.H5O_info_t;

import org.eclipse.ice.datastructures.ICEObject.ICEObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This class provides a base for implementing {@link IHdfIOFactory} and
 * includes many helpful methods for writing and reading HDF5 attributes,
 * datasets, groups, and more.
 * </p>
 * <p>
 * Sub-classes <b><i>must</i></b> override the following methods (no calls to
 * the super method necessary):
 * <ul>
 * <li>{@link #getSupportedClasses()}
 * <li>{@link #getTag(Class)}
 * <li>{@link #read(int, String)}
 * <li>{@link #writeObjectData(int, Object)}
 * </ul>
 * Note that this class cannot be abstract because it references an OSGi service
 * so that sub-classes have easy access to the {@link IHdfIORegistry}
 * implementation.
 * </p>
 * 
 * @author Jordan H. Deyton
 * 
 */
public class HdfIOFactory implements IHdfIOFactory {

	/**
	 * Logger for handling event messages and other information.
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(HdfIOFactory.class);

	/**
	 * The default rank of an attribute. This means an attribute is a 1D array.
	 */
	private static final int attributeRank = 1;
	/**
	 * The default data dimension array for all attributes. This just means that
	 * an attribute is a 1D array with 1 element.
	 */
	private static final long[] attributeDims = new long[] { 1 };
	/**
	 * A buffer that contains a single double value. Helps avoid having to
	 * create a new double array for double attributes.
	 */
	private static final Double[] doubleAttributeBuffer = new Double[1];
	/**
	 * A buffer that contains a single integer value. Helps avoid having to
	 * create a new integer array for integer attributes.
	 */
	private static final Integer[] integerAttributeBuffer = new Integer[1];

	/**
	 * The IHdfIORegistry implementation that is running as an OSGi Declarative
	 * Service.
	 */
	private static IHdfIORegistry hdfIORegistry = null;

	// ---- Methods that sub-classes MUST override! ---- //
	/**
	 * Sub-classes <b>must</b> override this method.
	 */
	@Override
	public List<Class<?>> getSupportedClasses() {
		return new ArrayList<Class<?>>();
	}

	/**
	 * Sub-classes <b>must</b> override this method.
	 */
	@Override
	public String getTag(Class<?> supportedClass) {
		return null;
	}

	/**
	 * Sub-classes <b>must</b> override this method.
	 */
	@Override
	public Object read(int groupId, String tag) throws NullPointerException,
			HDF5Exception, HDF5LibraryException {
		return null;
	}

	/**
	 * Sub-classes <b>must</b> override this method. Fills out an HDF5 Group
	 * with information regarding the specified object.
	 * 
	 * @param groupId
	 *            The group for the object.
	 * @param object
	 *            The object whose information is being written to the group.
	 */
	public void writeObjectData(int groupId, Object object)
			throws NullPointerException, HDF5Exception, HDF5LibraryException {
		return;
	}

	// ------------------------------------------------- //

	// ---- HdfIORegistry operations. ---- //
	/**
	 * Sets the {@link IHdfIORegistry} implementation that is running as an OSGi
	 * Declarative Service.
	 */
	public static void setHdfIORegistry(IHdfIORegistry registry) {
		if (registry != null) {
			hdfIORegistry = registry;
			logger.info("HdfIOFactory message: " + "Registry set successfully!");
		} else {
			logger.info("HdfIOFactory message: "
					+ "Failed to set the registry.");
		}

		return;
	}

	/**
	 * Unsets the {@link IHdfIORegistry} implementation. This happens only when
	 * the bundle has shut down.
	 */
	public static void unsetHdfIORegistry(IHdfIORegistry registry) {
		hdfIORegistry = null;
	}

	/**
	 * Gets the {@link IHdfIORegistry} implementation that is running as an OSGi
	 * Declarative Service.
	 * 
	 * @return The current implementation of the IHdfIORegistry.
	 */
	public static IHdfIORegistry getHdfIORegistry() {
		return hdfIORegistry;
	}

	// ----------------------------------- //

	// ---- Intercept the write method to force writing tag Attributes. ---- //
	/**
	 * When writing an object to an HDF5 file, we must create a tag Attribute.
	 * This operation enforces this requirement. It also creates a group named
	 * with the object's toString() value. Sub-classes should instead implement
	 * {@link #writeObjectData(int, Object)}.
	 */
	@Override
	public void write(int parentGroupId, Object object)
			throws NullPointerException, HDF5Exception, HDF5LibraryException {

		if (object != null) {
			// Create the group.
			int groupId = createGroup(parentGroupId, object.toString());

			// Write the tag.
			writeTag(getTag(object.getClass()), groupId);

			// Write the object's info to the group.
			writeObjectData(groupId, object);

			// Close the group.
			closeGroup(groupId);
		}

		return;
	}

	// --------------------------------------------------------------------- //

	// ---- File writing and reading. ---- //
	/**
	 * Writes several objectss to the file specified by the URI.
	 * 
	 * @param uri
	 *            The URI of the file that will hold the objects.
	 * @param objects
	 *            The objects that will be written to the file.
	 */
	public final void writeObjects(URI uri, List<Object> objects) {

		if (uri != null && objects != null) {
			// ---- Open the file ---- //
			// Check the file associated with the URI. If it exists, delete it.
			File file = new File(uri);
			String path = file.getPath();
			logger.info("HdfIOFactory message: " + "File \"" + path
					+ "\" is being opened.");
			if (file.exists()) {
				logger.info("HdfIOFactory message: " + "File \"" + path
						+ "\" already exists and will be overwritten.");
			} else {
				// Make sure the directory containing this file exists! If we
				// can't create the directory, then quit!
				String directoryName = file.getParent();
				File directory = new File(directoryName);
				if (!directory.exists()) {
					logger.info("HdfIOFactory message: " + "Directory \""
							+ directoryName
							+ "\" does not exist. Creating directory...");
					if (!directory.mkdirs()) {
						System.err.println("HdfIOFactory error: "
								+ "Directory \"" + directoryName
								+ "\" could not be created.");
						return;
					}
				}
			}
			// ----------------------- //

			// ---- HDF5 constants, for convenience. ---- //
			// Writing out "HDF5Constants." every time is annoying.
			// Default flag.
			int H5P_DEFAULT = HDF5Constants.H5P_DEFAULT;
			// Create, open, truncate.
			int H5F_ACC_TRUNC = HDF5Constants.H5F_ACC_TRUNC;
			// ------------------------------------------ //

			// The status of the previous HDF5 operation. Generally, if it is
			// negative, there was some error.
			int status;

			// Other IDs for HDF5 components.
			int fileId;

			try {
				// Create the H5 file. This should also open it with RW-access.
				status = H5.H5Fcreate(path, H5F_ACC_TRUNC, H5P_DEFAULT,
						H5P_DEFAULT);
				if (status < 0) {
					throwException("Opening file \"" + path + "\"", status);
				}
				fileId = status;

				// Try to write each object in the list.
				for (Object object : objects) {
					// Get the IO factory for the component's type from the
					// registry. If a valid factory exists, try to write the
					// component to the file.
					IHdfIOFactory factory = hdfIORegistry
							.getHdfIOFactory(object);
					if (factory != null) {
						factory.write(fileId, object);
					}
				}

				// Close the H5file.
				status = H5.H5Fclose(fileId);
				if (status < 0) {
					throwException("Closing file \"" + path + "\"", status);
				}
			} catch (HDF5LibraryException e) {
				logger.error(getClass().getName() + " Exception!",e);
			} catch (HDF5Exception e) {
				logger.error(getClass().getName() + " Exception!",e);
			} catch (NullPointerException e) {
				logger.error(getClass().getName() + " Exception!",e);
			}
		}

		return;
	}

	/**
	 * Reads all objects from the file specified by the URI.
	 * 
	 * @param uri
	 *            The URI of the file that should contain readable objects.
	 * @return A List of objects successfully read in from the file.
	 */
	public final List<Object> readObjects(URI uri) {
		List<Object> objects = new ArrayList<Object>();

		if (uri != null) {
			// ---- Open the file. ---- //
			// Check the file associated with the URI. We need to be able to
			// read from it.
			File file = new File(uri);
			String path = file.getPath();
			logger.info("HdfIOFactory message: " + "File \"" + path
					+ "\" is being opened.");
			if (!file.canRead()) {
				System.err.println("HdfIOFactory error: " + "File \"" + path
						+ "\" cannot be read.");
				return objects;
			}
			// ------------------------ //

			// ---- HDF5 constants, for convenience. ---- //
			// Default flag.
			int H5P_DEFAULT = HDF5Constants.H5P_DEFAULT;
			// Open read-only.
			int H5F_ACC_RDONLY = HDF5Constants.H5F_ACC_RDONLY;
			int H5O_TYPE_GROUP = HDF5Constants.H5O_TYPE_GROUP;
			// ------------------------------------------ //

			// The status of the previous HDF5 operation. Generally, if it is
			// negative, there was some error.
			int status;

			// Other IDs for HDF5 components.
			int fileId;

			try {
				// Open the H5 file with read-only access.
				status = H5.H5Fopen(path, H5F_ACC_RDONLY, H5P_DEFAULT);
				if (status < 0) {
					throwException("Opening file \"" + path + "\"", status);
				}
				fileId = status;

				// Try to load each group from the file as an object.
				for (String name : getChildNames(fileId, H5O_TYPE_GROUP)) {
					// Open the group.
					int groupId = openGroup(fileId, name);

					// Pull the tag from the group. If a factory is associated
					// with the tag, use it to read the object from the group.
					String tag = readTag(groupId);
					IHdfIOFactory factory = hdfIORegistry.getHdfIOFactory(tag);
					if (factory != null) {
						Object object = factory.read(groupId, tag);
						// If the factory could read the object from the group,
						// we need to add the object to the list of objects.
						if (object != null) {
							objects.add(object);
						}
					}

					// Close the group.
					closeGroup(groupId);
				}

				// Close the H5file.
				status = H5.H5Fclose(fileId);
				if (status < 0) {
					throwException("Closing file \"" + path + "\"", status);
				}
			} catch (HDF5LibraryException e) {
				logger.error(getClass().getName() + " Exception!",e);
			} catch (HDF5Exception e) {
				logger.error(getClass().getName() + " Exception!",e);
			} catch (NullPointerException e) {
				logger.error(getClass().getName() + " Exception!",e);
			}
		}

		return objects;
	}

	// ----------------------------------- //

	// -------------------------- //
	// ---- Utility methods. ---- //
	// -------------------------- //

	/**
	 * This utility method throws an HDF5LibraryException with a custom message.
	 * 
	 * @param message
	 *            The message to append to the exception.
	 * @param status
	 *            The integer flag that indicated a problem. This is usually a
	 *            negative number.
	 */
	public final void throwException(String message, int status)
			throws HDF5LibraryException {
		throw new HDF5LibraryException("HdfIOFactory error: " + message + ": "
				+ Integer.toString(status));
	}

	// ---- Group Operations ---- //
	/**
	 * Opens an HDF5 Group.
	 * 
	 * @param parentId
	 *            The ID of the parent's Group, which should be open itself.
	 * @param name
	 *            The name of the Group to open.
	 * @return The ID of the newly-opened Group.
	 */
	public final int openGroup(int parentId, String name)
			throws HDF5LibraryException, NullPointerException {
		int status = H5.H5Gopen(parentId, name, HDF5Constants.H5P_DEFAULT);
		if (status < 0) {
			throwException("Opening group \"" + name + "\"", status);
		}
		return status;
	}

	/**
	 * Creates and opens an HDF5 Group.
	 * 
	 * @param parentId
	 *            The ID of the parent's Group, which should be open itself.
	 * @param name
	 *            The name of the Group to open.
	 * @return The ID of the newly-opened Group.
	 */
	public final int createGroup(int parentId, String name)
			throws HDF5LibraryException, NullPointerException {
		int status = H5.H5Gcreate(parentId, name, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		if (status < 0) {
			throwException("Creating group \"" + name + "\"", status);
		}
		return status;
	}

	/**
	 * Closes an HDF5 Group.
	 * 
	 * @param groupId
	 *            The ID of the Group to close.
	 */
	public final void closeGroup(int groupId) throws HDF5LibraryException {
		int status = H5.H5Gclose(groupId);
		if (status < 0) {
			throwException("Closing group with id " + groupId + ".", status);
		}
		return;
	}

	/**
	 * Gets a List of all child Objects of an HDF5 Group with the specified ID
	 * and type.
	 * 
	 * @param parentId
	 *            The ID of the parent Group.
	 * @param objectType
	 *            The type of object we are looking for, e.g., H5O_TYPE_GROUP or
	 *            H5O_TYPE_DATASET.
	 * @return A List of names of all child objects that are HDF5 Groups.
	 */
	public final List<String> getChildNames(int parentId, int objectType)
			throws HDF5LibraryException {

		// Constants used below.
		String parentGroup = ".";
		int indexType = HDF5Constants.H5_INDEX_NAME;
		int indexOrder = HDF5Constants.H5_ITER_INC;
		int lapl_id = HDF5Constants.H5P_DEFAULT;

		// Get the number of members in this group.
		int status = H5.H5Gn_members(parentId, ".");
		if (status < 0) {
			throwException("Getting number of children of group with ID "
					+ parentId + "", status);
		}
		int nMembers = status;

		// A List of group names within the parent group (which has ID groupId).
		List<String> groupNames = new ArrayList<String>(nMembers);

		// Loop over the possible indexes.
		for (int i = 0; i < nMembers; i++) {
			// Get the info for the object in this position.
			H5O_info_t info = H5.H5Oget_info_by_idx(parentId, parentGroup,
					indexType, indexOrder, i, lapl_id);

			// See if the object exists and is an HDF5 Group.
			if (info != null && info.type == objectType) {

				// Get the name and add it to the List if possible.
				String name = H5.H5Lget_name_by_idx(parentId, parentGroup,
						indexType, indexOrder, i, lapl_id);
				if (name != null) {
					groupNames.add(name);
				}
			}
		}

		return groupNames;
	}

	// -------------------------- //

	// ---- Atribute Operations ---- //
	/**
	 * Writes an Attribute for an HDF5 Object, which is typically a Group. Array
	 * Attributes are not supported.
	 * 
	 * @param objectId
	 *            The ID for the Object, which should be open, that will get the
	 *            Attribute.
	 * @param name
	 *            The name of the Attribute.
	 * @param type
	 *            The HDF5 datatype of the Attribute. Currently supported are
	 *            H5T_NATIVE_INT and H5T_NATIVE_DOUBLE.
	 * @param value
	 *            The value of the Attribute being written.
	 */
	public final void writeAttribute(int objectId, String name, int type,
			Object value) throws NullPointerException, HDF5Exception {
		int status;

		// Create the buffer that holds the data to write to the attribute.
		int rank = attributeRank;
		long[] dims = attributeDims;
		Object[] buffer = getBuffer(type);
		buffer[0] = value;

		// Create the dataspace to hold the value.
		status = H5.H5Screate_simple(rank, dims, null);
		if (status < 0) {
			throwException("Creating dataspace for attribute \"" + name + "\"",
					status);
		}
		int dataspaceId = status;

		// Create the attribute for the dataspace.
		status = H5.H5Acreate(objectId, name, type, dataspaceId,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		if (status < 0) {
			throwException("Creating attribute \"" + name + "\"", status);
		}
		int attributeId = status;

		// Write the attribute.
		status = H5.H5Awrite(attributeId, type, buffer);
		if (status < 0) {
			throwException("Writing attribute \"" + name + "\"", status);
		}
		// Close the attribute.
		status = H5.H5Aclose(attributeId);
		if (status < 0) {
			throwException("Closing attribute \"" + name + "\"", status);
		}
		// Close the dataspace.
		status = H5.H5Sclose(dataspaceId);
		if (status < 0) {
			throwException("Closing dataspace for attribute \"" + name + "\"",
					status);
		}
		return;
	}

	/**
	 * Reads an Attribute for an HDF5 Object, which is typically a Group. Array
	 * Attributes are not supported.
	 * 
	 * @param objectId
	 *            The ID for the Object, which should be open, that has the
	 *            Attribute.
	 * @param name
	 *            The name of the Attribute.
	 * @param type
	 *            The HDF5 datatype of the Attribute. Currently supported are
	 *            H5T_NATIVE_INT and H5T_NATIVE_DOUBLE.
	 * @return Returns the value of the attribute.
	 */
	public final Object readAttribute(int objectId, String name, int type)
			throws NullPointerException, HDF5Exception {
		int status;

		// Get a buffer to read the attribute.
		Object[] buffer = getBuffer(type);

		// Open the attribute.
		status = H5.H5Aopen(objectId, name, HDF5Constants.H5P_DEFAULT);
		if (status < 0) {
			throwException("Opening attribute \"" + name + "\"", status);
		}
		int attributeId = status;

		// Read the attribute.
		status = H5.H5Aread(attributeId, type, buffer);
		if (status < 0) {
			throwException("Reading attribute \"" + name + "\"", status);
		}
		// Close the attribute.
		status = H5.H5Aclose(attributeId);
		if (status < 0) {
			throwException("Closing attribute \"" + name + "\"", status);
		}
		return buffer[0];
	}

	/**
	 * Writes a String as an Attribute for an HDF5 Object, which is typically a
	 * Group. This requires a special method because the String must first be
	 * converted to a byte array.
	 * 
	 * @param objectId
	 *            The ID for the Object, which should be open, that will get the
	 *            Attribute.
	 * @param name
	 *            The name of the Attribute.
	 * @param value
	 *            The String value of the Attribute.
	 */
	public final void writeStringAttribute(int objectId, String name,
			String value) throws NullPointerException, HDF5Exception {
		int status;

		// HDF5 requires null-terminated strings. Unfortunately, Java's
		// String.getBytes() method does not return a byte array that includes a
		// null character in the last position, so we have to create a new
		// buffer that includes all bytes from the string and a null (0) byte.

		// Method 1: Create byte array of correct size, then copy string bytes
		// to byte array.

		// Methd 2: Create array from a new string that has the null character.
		byte[] buffer = (value + "\0").getBytes();

		// We have 1 string, so set rank to 1 and the length of the 1st
		// dimension is 1.
		int rank = 1;
		long[] dims = new long[] { 1 };

		// Create the dataspace to hold the value.
		status = H5.H5Screate_simple(rank, dims, null);
		if (status < 0) {
			throwException("Creating dataspace for attribute \"" + name + "\"",
					status);
		}
		int dataspaceId = status;

		// Create the datatype for the attribute. Note that we include the size
		// of the string byte buffer that includes the null character.
		status = H5.H5Tcreate(HDF5Constants.H5T_STRING, buffer.length);
		if (status < 0) {
			throwException("Creating datatype for attribute \"" + name + "\"",
					status);
		}
		int datatypeId = status;

		// FIXME I don't think this is necessary, but it may be!
		// H5.H5Tset_strpad(datatypeId, HDF5Constants.H5T_STR_NULLTERM);

		// Create the attribute for the dataspace.
		status = H5.H5Acreate(objectId, name, datatypeId, dataspaceId,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		if (status < 0) {
			throwException("Creating attribute \"" + name + "\"", status);
		}
		int attributeId = status;

		// Write the attribute.
		status = H5.H5Awrite(attributeId, datatypeId, buffer);
		if (status < 0) {
			throwException("Writing attribute \"" + name + "\"", status);
		}
		// Close the attribute.
		status = H5.H5Aclose(attributeId);
		if (status < 0) {
			throwException("Closing attribute \"" + name + "\"", status);
		}
		// Close the datatype.
		status = H5.H5Tclose(datatypeId);
		if (status < 0) {
			throwException("Closing datatype for attribute \"" + name + "\"",
					status);
		}
		// Close the dataspace.
		status = H5.H5Sclose(dataspaceId);
		if (status < 0) {
			throwException("Closing dataspace for attribute \"" + name + "\"",
					status);
		}
		return;
	}

	/**
	 * Reads a String Attribute from an HDF5 Object, which is typically a Group.
	 * This requires a special method because the String must first be converted
	 * to a byte array.
	 * 
	 * @param objectId
	 *            The ID for the Object, which should be open, that has the
	 *            Attribute.
	 * @param name
	 *            The name of the Attribute.
	 * @return The value of the String stored in the Attribute.
	 */
	public final String readStringAttribute(int objectId, String name)
			throws NullPointerException, HDF5Exception {
		int status;

		// Open the attribute.
		status = H5.H5Aopen(objectId, name, HDF5Constants.H5P_DEFAULT);
		if (status < 0) {
			throwException("Opening attribute \"" + name + "\"", status);
		}
		int attributeId = status;

		// Get the datatype for the String (H5T_STRING with a size in bytes).
		status = H5.H5Aget_type(attributeId);
		if (status < 0) {
			throwException("Reading datatype for attribute \"" + name + "\"",
					status);
		}
		int datatypeId = status;

		// Get the size of the String from the datatype.
		status = H5.H5Tget_size(datatypeId);
		if (status <= 0) {
			throwException("Reading size of datatype for attribute \"" + name
					+ "\"", status);
		}
		int size = status;

		// Initialize the buffer.
		byte[] buffer = new byte[size];

		// Read the attribute.
		status = H5.H5Aread(attributeId, datatypeId, buffer);
		if (status < 0) {
			throwException("Reading attribute \"" + name + "\"", status);
		}
		// Close the attribute.
		status = H5.H5Aclose(attributeId);
		if (status < 0) {
			throwException("Closing attribute \"" + name + "\"", status);
		}
		// Convert the buffer into a String. The null character is only required
		// inside HDF5, so strip the null character.
		return new String(buffer, 0, size - 1);
	}

	// ----------------------------- //

	// ---- Dataset Operations ---- //
	/**
	 * This method writes an HDF5 Dataset containing the data that is stored in
	 * a buffer. All of the data's properties and the buffer must be allocated
	 * before calling this method.
	 * 
	 * @param objectId
	 *            The ID for the Object, which should be open, that will get the
	 *            Dataset.
	 * @param name
	 *            The name of the Dataset.
	 * @param rank
	 *            The number of dimensions in the data.
	 * @param dims
	 *            An array containing the sizes of each dimension in the data.
	 * @param type
	 *            The HDF5 datatype of the data in the Dataset, e.g.,
	 *            H5T_NATIVE_INT or H5T_NATIVE_DOUBLE. This may also be an ID
	 *            for an opened Datatype, e.g., an array of Strings (byte
	 *            arrays).
	 * @param buffer
	 *            The buffer that contains the data to write. This needs to be
	 *            an array, e.g., a double[n] or int[n].
	 */
	public final void writeDataset(int objectId, String name, int rank,
			long[] dims, int type, Object buffer) throws NullPointerException,
			HDF5Exception {
		int status;

		// Create the dataspace.
		status = H5.H5Screate_simple(rank, dims, null);
		if (status < 0) {
			throwException("Creating dataspace for dataset \"" + name + "\"",
					status);
		}
		int dataspaceId = status;

		// Create the dataset.
		status = H5.H5Dcreate(objectId, name, type, dataspaceId,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT);
		if (status < 0) {
			throwException("Creating dataset \"" + name + "\"", status);
		}
		int datasetId = status;

		// Write the dataset.
		status = H5.H5Dwrite(datasetId, type, HDF5Constants.H5S_ALL,
				HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, buffer);
		if (status < 0) {
			throwException("Writing dataset \"" + name + "\"", status);
		}
		// Close the dataset.
		status = H5.H5Dclose(datasetId);
		if (status < 0) {
			throwException("Closing dataset \"" + name + "\"", status);
		}
		// Close the dataspace.
		status = H5.H5Sclose(dataspaceId);
		if (status < 0) {
			throwException("Closing dataspace for dataset \"" + name + "\"",
					status);
		}
		return;
	}

	/**
	 * Reads a dataset without requiring advance knowledge of its entire
	 * structure. However, the returned buffer does not have any dimensional
	 * data associated with it, just the data from the dataset.
	 * 
	 * @param groupId
	 *            The ID of the Object, which should be open, that contains the
	 *            dataset.
	 * @param name
	 *            The name of the dataset.
	 * @param type
	 *            The HDF5 datatype of the data in the dataset, e.g.,
	 *            H5T_NATIVE_INT or H5T_NATIVE_DOUBLE. This may also be an ID
	 *            for an opened datatype, e.g., an array of Strings (byte
	 *            arrays).
	 * @return A buffer containing the data from the dataset, or null if the
	 *         dataset could not be read.
	 */
	public Object readDataset(int groupId, String name, int type)
			throws NullPointerException, HDF5Exception {

		Object buffer = null;

		int status;

		// Commonly-used constants.
		int H5P_DEFAULT = HDF5Constants.H5P_DEFAULT;
		int H5S_ALL = HDF5Constants.H5S_ALL;

		// See if the dataset exists. If so, read it in.
		if (H5.H5Lexists(groupId, name, H5P_DEFAULT)) {

			// Open the dataset.
			status = H5.H5Dopen(groupId, name, H5P_DEFAULT);
			if (status < 0) {
				throwException("Could not open dataset \"" + name + "\".",
						status);
			}
			int datasetId = status;

			// Open the dataspace.
			status = H5.H5Dget_space(datasetId);
			if (status < 0) {
				throwException("Could not open dataspace for dataset \"" + name
						+ "\".", status);
			}
			int dataspaceId = status;

			// Get the rank of the dataspace.
			status = H5.H5Sget_simple_extent_ndims(dataspaceId);
			if (status < 0) {
				throwException(
						"Could not determine rank (number of dimensions) of dataspace for dataset \""
								+ name + "\".", status);
			}
			int rank = status;

			// Get the dimensions of the dataspace.
			long[] dims = new long[rank];
			status = H5.H5Sget_simple_extent_dims(dataspaceId, dims, null);
			if (status != rank) {
				throwException(
						"Could not determine dimensions of dataspace for dataset \""
								+ name + "\".", status);
			}
			// Create an appropriately sized buffer.
			buffer = getBuffer(type, dims);

			// Read in the Dataset.
			status = H5.H5Dread(datasetId, type, H5S_ALL, H5S_ALL, H5P_DEFAULT,
					buffer);
			if (status < 0) {
				throwException("Could not read dataset \"" + name + "\".",
						status);
			}
			// Close the Dataspace.
			status = H5.H5Sclose(dataspaceId);
			if (status < 0) {
				throwException("Could not close dataspace for dataset \""
						+ name + "\".", status);
			}
			// Close the Dataset.
			status = H5.H5Dclose(datasetId);
			if (status < 0) {
				throwException("Could not close dataset \"" + name + "\".",
						status);
			}
		}

		return buffer;
	}

	// ---------------------------- //

	/**
	 * Writes an ICEObject's name, description, and ID as Attributes for an HDF5
	 * Object.
	 * 
	 * @param object
	 *            The ICEObject whose info should be written in HDF5.
	 * @param objectId
	 *            The ID of the HDF5 Object receiving the ICEObject Attributes.
	 */
	public final void writeICEObjectInfo(ICEObject object, int objectId)
			throws NullPointerException, HDF5Exception {

		// Write the component's name, description, and ID.
		writeStringAttribute(objectId, "name", object.getName());
		writeStringAttribute(objectId, "description", object.getDescription());
		writeAttribute(objectId, "id", HDF5Constants.H5T_NATIVE_INT,
				object.getId());
	}

	/**
	 * Reads the ICEObject information (name, description, ID) from an HDF5
	 * Group or Object into an ICEObject.
	 * 
	 * @param object
	 *            The ICEObject that should have its information set.
	 * @param objectId
	 *            The ID of the HDF5 Group or Object that contains the
	 *            ICEObject's information.
	 */
	public final void readICEObjectInfo(ICEObject object, int objectId)
			throws NullPointerException, HDF5Exception {

		// Read the object's name, description, and ID from the HDF5 Group or
		// Object.
		object.setName(readStringAttribute(objectId, "name"));
		object.setDescription(readStringAttribute(objectId, "description"));
		object.setId((Integer) readAttribute(objectId, "id",
				HDF5Constants.H5T_NATIVE_INT));
	}

	/**
	 * Writes the tag value for the specified HDF5 Group.
	 * 
	 * @param tag
	 *            The tag string.
	 * @param objectId
	 *            The HDF5 Group that will have the specified tag.
	 */
	public final void writeTag(String tag, int objectId)
			throws NullPointerException, HDF5Exception {

		if (tag == null) {
			tag = "";
		}

		writeStringAttribute(objectId, "tag", tag);
	}

	/**
	 * Reads the value of a tag from the specified HDF5 Group.
	 * 
	 * @param objectId
	 *            The HDF5 Group that should have a tag Attribute.
	 * @return The value of the tag Attribute.
	 */
	public String readTag(int objectId) throws NullPointerException,
			HDF5Exception {
		return readStringAttribute(objectId, "tag");
	}

	/**
	 * Gets a buffer used for writing or reading an HDF5 attribute. Currently
	 * supports doubles and integers.
	 * 
	 * @param type
	 *            The data type, e.g. {@link HDF5Constants#H5T_NATIVE_DOUBLE} or
	 *            {@link HDF5Constants#H5T_NATIVE_INT}.
	 * @return The buffer as an array of Objects (of size 1). To set its value
	 *         for writing, set buffer[0].
	 */
	public final Object[] getBuffer(int type) throws HDF5LibraryException {
		Object[] buffer = null;

		if (type == HDF5Constants.H5T_NATIVE_DOUBLE) {
			buffer = doubleAttributeBuffer;
		} else if (type == HDF5Constants.H5T_NATIVE_INT) {
			buffer = integerAttributeBuffer;
		} else {
			throwException("Unsupported data type.", -1);
		}
		return buffer;
	}

	/**
	 * Gets a buffer used for writing or reading HDF5 datasets. Currently
	 * supports doubles and integers.
	 * 
	 * @param type
	 *            The data type, e.g. {@link HDF5Constants#H5T_NATIVE_DOUBLE} or
	 *            {@link HDF5Constants#H5T_NATIVE_INT}.
	 * @param dims
	 *            The sizes of each dimension of the data.
	 * @return A buffer of the appropriate size to contain the data defined by
	 *         the dimensions, e.g., 1st dimension length * 2nd dimension length
	 *         * ... * nth dimension length.
	 */
	public final Object getBuffer(int type, long[] dims)
			throws HDF5LibraryException {
		Object buffer = null;

		// Get the total size of the buffer from the dimension sizes.
		int size = 1;
		for (long dimension : dims) {
			size *= dimension;
		}
		// Create the buffer depending on the type.
		if (type == HDF5Constants.H5T_NATIVE_DOUBLE) {
			buffer = new double[size];
		} else if (type == HDF5Constants.H5T_NATIVE_INT) {
			buffer = new int[size];
		} else {
			throwException("Unsupported data type.", -1);
		}
		return buffer;
	}

}