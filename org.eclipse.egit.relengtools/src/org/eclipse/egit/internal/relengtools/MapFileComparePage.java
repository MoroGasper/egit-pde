/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.egit.internal.relengtools;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class MapFileComparePage extends WizardPage {

	private final MapFileCompareEditorInput input = new MapFileCompareEditorInput();

	private String tag;

	public MapFileComparePage(String pageName, String title,
			ImageDescriptor image) {
		super(pageName, title, image);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.dialogs.IDialogPage#setVisible(boolean)
	 */
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		// Need to handle input and rebuild tree only when becoming visible
		if (visible) {
			try {
				input.updateInput(
						((ReleaseWizard) getWizard()).getSelectedProjects(),
						tag);
			} catch (final CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets
	 * .Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		final Font font = parent.getFont();
		final Composite composite = new Composite(parent, SWT.NONE);
		composite.setFont(font);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		try {
			input.run(null);
		} catch (final InterruptedException e) {
			e.printStackTrace();
		} catch (final InvocationTargetException e) {
			e.printStackTrace();
		}

		final Control c = input.createContents(composite);
		c.setLayoutData(new GridData(GridData.FILL_BOTH));
		setControl(composite);
	}

	public void setTag(String t) {
		this.tag = t;
	}

	public void updateMapProject(MapProject m) {
		input.updateMapProject(m);
	}
}
