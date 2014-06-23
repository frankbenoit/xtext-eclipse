/*******************************************************************************
 * Copyright (c) 2014 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.ui.imports;

import org.eclipse.osgi.util.NLS;

/**
 * @author dhuebner - Initial contribution and API
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.xtext.xbase.ui.imports.messages"; //$NON-NLS-1$
	public static String OrganizeImports;
	public static String MultiOrganizeImportsHandler_exceptionMessage;
	public static String OrganizeImportsHandler_organizeImportsErrorMessage;
	public static String TypeChooser_dialogTitle;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}