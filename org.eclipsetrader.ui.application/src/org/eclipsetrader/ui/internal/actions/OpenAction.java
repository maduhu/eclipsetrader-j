/*
 * Copyright (c) 2004-2006 Marco Maccaferri and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marco Maccaferri - initial API and implementation
 */

package org.eclipsetrader.ui.internal.actions;

import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.RetargetAction;

/**
 * Retargettable action used to open a resource.
 *
 * @since 1.0
 */
public class OpenAction extends RetargetAction {

    public OpenAction(IWorkbenchWindow window) {
        super("open", "&Open");
        window.getPartService().addPartListener(this);
        setActionDefinitionId("org.eclipse.ui.file.open"); //$NON-NLS-1$
    }
}
