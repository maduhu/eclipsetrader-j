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

package org.eclipsetrader.ui.internal.markets;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Observable;
import java.util.Observer;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.eclipsetrader.core.internal.markets.Market;
import org.eclipsetrader.core.internal.markets.MarketService;
import org.eclipsetrader.core.markets.IMarket;
import org.eclipsetrader.core.markets.IMarketStatusListener;
import org.eclipsetrader.core.markets.MarketStatusEvent;
import org.eclipsetrader.ui.SelectionProvider;
import org.eclipsetrader.ui.UIConstants;
import org.eclipsetrader.ui.internal.UIActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class MarketsView extends ViewPart {

    Display display;
    TableViewer viewer;

    private Image marketIcon;

    Action newMarketAction;
    Action deleteAction;

    MarketService marketService;

    private Runnable timedRunnable = new Runnable() {

        @Override
        public void run() {
            if (!viewer.getControl().isDisposed()) {
                viewer.update((Object[]) viewer.getInput(), null);
                int delay = (int) (60000 - System.currentTimeMillis() % 60000);
                Display.getCurrent().timerExec(delay, timedRunnable);
            }
        }
    };

    private Observer serviceObserver = new Observer() {

        @Override
        public void update(Observable o, Object arg) {
            display.asyncExec(new Runnable() {

                @Override
                public void run() {
                    if (!viewer.getControl().isDisposed()) {
                        refreshInput();
                    }
                }
            });
        }
    };

    private IMarketStatusListener marketStatusListener = new IMarketStatusListener() {

        @Override
        public void marketStatusChanged(MarketStatusEvent event) {
            final IMarket market = event.getMarket();
            display.asyncExec(new Runnable() {

                @Override
                public void run() {
                    if (!viewer.getControl().isDisposed()) {
                        viewer.update(market, null);
                    }
                }
            });
        }
    };

    private PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {

        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
            display.asyncExec(new Runnable() {

                @Override
                public void run() {
                    if (!viewer.getControl().isDisposed()) {
                        if (IMarket.PROP_NAME.equals(evt.getPropertyName())) {
                            viewer.refresh();
                        }
                        else {
                            viewer.update(evt.getSource(), null);
                        }
                    }
                }
            });
        }
    };

    public MarketsView() {
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.part.ViewPart#init(org.eclipse.ui.IViewSite)
     */
    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);

        BundleContext context = UIActivator.getDefault().getBundle().getBundleContext();
        ServiceReference serviceReference = context.getServiceReference(MarketService.class.getName());
        marketService = (MarketService) context.getService(serviceReference);
        context.ungetService(serviceReference);

        site.setSelectionProvider(new SelectionProvider());

        marketIcon = UIActivator.getDefault().getImageRegistry().get(UIConstants.MARKET_OBJECT);

        newMarketAction = new NewMarketAction(site.getShell());

        deleteAction = new Action(Messages.MarketsView_Delete) {

            @Override
            public void run() {
                IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
                for (Object obj : selection.toArray()) {
                    if (obj instanceof Market) {
                        Market market = (Market) obj;
                        marketService.deleteMarket(market);
                        PropertyChangeSupport propertyChangeSupport = (PropertyChangeSupport) market.getAdapter(PropertyChangeSupport.class);
                        if (propertyChangeSupport != null) {
                            propertyChangeSupport.removePropertyChangeListener(propertyChangeListener);
                        }
                    }
                }
            }
        };
        deleteAction.setImageDescriptor(UIActivator.getDefault().getImageRegistry().getDescriptor(UIConstants.DELETE_ICON));
        deleteAction.setDisabledImageDescriptor(UIActivator.getDefault().getImageRegistry().getDescriptor(UIConstants.DELETE_DISABLED_ICON));
        deleteAction.setEnabled(false);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
     */
    @Override
    public void createPartControl(Composite parent) {
        display = parent.getDisplay();

        createViewer(parent);
        createContextMenu();

        refreshInput();
        viewer.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                getViewSite().getSelectionProvider().setSelection(event.getSelection());
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                deleteAction.setEnabled(!selection.isEmpty());
            }
        });

        marketService.addMarketStatusListener(marketStatusListener);
        marketService.addObserver(serviceObserver);

        int delay = (int) (60000 - System.currentTimeMillis() % 60000);
        Display.getCurrent().timerExec(delay, timedRunnable);
    }

    protected void createViewer(Composite parent) {
        viewer = new TableViewer(parent, SWT.MULTI | SWT.FULL_SELECTION);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(false);

        TableColumn tableColumn = new TableColumn(viewer.getTable(), SWT.NONE);
        tableColumn.setText(Messages.MarketsView_Market);
        tableColumn.setWidth(150);
        tableColumn = new TableColumn(viewer.getTable(), SWT.NONE);
        tableColumn.setText(Messages.MarketsView_State);
        tableColumn.setWidth(150);
        tableColumn = new TableColumn(viewer.getTable(), SWT.NONE);
        tableColumn.setText(Messages.MarketsView_Message);
        tableColumn.setWidth(250);

        viewer.setLabelProvider(new MarketLabelProvider(marketIcon));
        viewer.setComparator(new ViewerComparator());
        viewer.setContentProvider(new ArrayContentProvider());
    }

    protected void refreshInput() {
        IMarket[] input = marketService.getMarkets();
        for (IMarket market : input) {
            PropertyChangeSupport propertyChangeSupport = (PropertyChangeSupport) market.getAdapter(PropertyChangeSupport.class);
            if (propertyChangeSupport != null) {
                propertyChangeSupport.removePropertyChangeListener(propertyChangeListener);
                propertyChangeSupport.addPropertyChangeListener(propertyChangeListener);
            }
        }
        viewer.setInput(input);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
     */
    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.part.WorkbenchPart#dispose()
     */
    @Override
    public void dispose() {
        Display.getCurrent().timerExec(-1, timedRunnable);

        marketService.deleteObserver(serviceObserver);
        marketService.removeMarketStatusListener(marketStatusListener);

        super.dispose();
    }

    protected void createContextMenu() {
        MenuManager menuMgr = new MenuManager("#popupMenu", "popupMenu"); //$NON-NLS-1$ //$NON-NLS-2$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {

            @Override
            public void menuAboutToShow(IMenuManager menuManager) {
                MenuManager newMenu = new MenuManager(Messages.MarketsView_New, "group.new"); //$NON-NLS-2$
                {
                    newMenu.add(new Separator("top")); //$NON-NLS-1$
                    newMenu.add(newMarketAction);
                    newMenu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
                    newMenu.add(new Separator("bottom")); //$NON-NLS-1$
                }
                menuManager.add(newMenu);
                menuManager.add(new GroupMarker("group.goto")); //$NON-NLS-1$
                menuManager.add(new Separator("group.open")); //$NON-NLS-1$
                menuManager.add(new GroupMarker("group.openWith")); //$NON-NLS-1$
                menuManager.add(new Separator("group.show")); //$NON-NLS-1$
                menuManager.add(new Separator("group.edit")); //$NON-NLS-1$
                menuManager.add(new GroupMarker("group.reorganize")); //$NON-NLS-1$
                menuManager.add(new GroupMarker("group.port")); //$NON-NLS-1$
                menuManager.add(new Separator("group.generate")); //$NON-NLS-1$
                menuManager.add(new Separator("group.search")); //$NON-NLS-1$
                menuManager.add(new Separator("group.build")); //$NON-NLS-1$
                menuManager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
                menuManager.add(new Separator("group.properties")); //$NON-NLS-1$

                menuManager.appendToGroup("group.reorganize", deleteAction); //$NON-NLS-1$
            }
        });
        viewer.getControl().setMenu(menuMgr.createContextMenu(viewer.getControl()));
        getSite().registerContextMenu(menuMgr, getSite().getSelectionProvider());
    }
}
