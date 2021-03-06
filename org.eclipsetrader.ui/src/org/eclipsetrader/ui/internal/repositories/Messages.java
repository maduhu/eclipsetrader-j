/*
 * Copyright (c) 2004-2011 Marco Maccaferri and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marco Maccaferri - initial API and implementation
 */

package org.eclipsetrader.ui.internal.repositories;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

    private static final String BUNDLE_NAME = "org.eclipsetrader.ui.internal.repositories.messages"; //$NON-NLS-1$
    public static String Navigator_CollapseAll;
	public static String Navigator_Delete;
	public static String Navigator_ExpandAll;
	public static String RepositoryExplorer_CollapseAll;
    public static String RepositoryExplorer_Copy;
    public static String RepositoryExplorer_Delete;
    public static String RepositoryExplorer_DeleteConfirmMessage;
    public static String RepositoryExplorer_ExpandAll;
    public static String RepositoryExplorer_Paste;
    public static String RepositoryExplorer_Refresh;
    public static String RepositoryExplorer_RefreshJobName;
	public static String RepositoryMoveJob_RepositoryMove;
    public static String RepositoryTree_Instruments;
	public static String RepositoryTree_Others;
    public static String RepositoryTree_Watchlists;
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
