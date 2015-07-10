/*******************************************************************************
 * Copyright (c) 2013, 2014 UT-Battelle, LLC.
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
package org.eclipse.ice.item.nuclear;

import org.eclipse.core.resources.IProject;
import org.eclipse.ice.item.Item;
import org.eclipse.ice.item.AbstractItemBuilder;
import org.eclipse.ice.item.ItemType;

/**
 * <p>
 * An ItemBuilder for building SHARP models.
 * </p>
 * 
 * @author Jay Jay Billings
 */
public class SHARPModelBuilder extends AbstractItemBuilder {
	/**
	 * The name
	 */
	public static final String name = "SHARP Model Builder";

	/**
	 * The Item type
	 */
	public static final ItemType type = ItemType.Model;

	public SHARPModelBuilder() {
		setName(name);
		setType(type);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ice.item.AbstractItemBuilder#getInstance(org.eclipse.core.resources.IProject)
	 */
	@Override
	public Item getInstance(IProject projectSpace) {

		SHARPModel model = new SHARPModel(projectSpace);
		model.setItemBuilderName(name);

		return model;
	}

}
