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

package org.eclipsetrader.ui.charts.patterns;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.swt.graphics.RGB;
import org.eclipsetrader.core.charts.IDataSeries;
import org.eclipsetrader.core.feed.IOHLC;
import org.eclipsetrader.ui.charts.ChartParameters;
import org.eclipsetrader.ui.charts.GroupChartObject;
import org.eclipsetrader.ui.charts.IChartObject;
import org.eclipsetrader.ui.charts.IChartObjectFactory;
import org.eclipsetrader.ui.charts.IChartParameters;
import org.eclipsetrader.ui.charts.ILineDecorator;
import org.eclipsetrader.ui.charts.RenderStyle;
import org.eclipsetrader.ui.internal.charts.PatternBox;
import org.eclipsetrader.ui.internal.charts.indicators.IGeneralPropertiesAdapter;

public class KeyReversal implements IChartObjectFactory, IGeneralPropertiesAdapter, ILineDecorator, IExecutableExtension {

    private String id;
    private String factoryName;
    private String name;

    private RenderStyle renderStyle = RenderStyle.Line;
    private RGB color;

    public KeyReversal() {
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
     */
    @Override
    public void setInitializationData(IConfigurationElement config, String propertyName, Object data) throws CoreException {
        id = config.getAttribute("id"); //$NON-NLS-1$
        factoryName = config.getAttribute("name"); //$NON-NLS-1$
        name = config.getAttribute("name"); //$NON-NLS-1$
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.charts.ui.indicators.IChartIndicator#getId()
     */
    @Override
    public String getId() {
        return id;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.charts.ui.indicators.IChartIndicator#getName()
     */
    @Override
    public String getName() {
        return name;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.internal.charts.IGeneralPropertiesAdapter#setName(java.lang.String)
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.internal.charts.IGeneralPropertiesAdapter#getRenderStyle()
     */
    @Override
    public RenderStyle getRenderStyle() {
        return renderStyle;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.internal.charts.IGeneralPropertiesAdapter#setRenderStyle(org.eclipsetrader.ui.charts.RenderStyle)
     */
    @Override
    public void setRenderStyle(RenderStyle renderStyle) {
        this.renderStyle = renderStyle;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.charts.ILineDecorator#getColor()
     */
    @Override
    public RGB getColor() {
        return color;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.charts.ILineDecorator#setColor(org.eclipse.swt.graphics.RGB)
     */
    @Override
    public void setColor(RGB color) {
        this.color = color;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.charts.IChartObjectFactory#createObject(org.eclipsetrader.core.charts.IDataSeries)
     */
    @Override
    public IChartObject createObject(IDataSeries source) {
        if (source == null) {
            return null;
        }

        IAdaptable[] values = source.getValues();
        if (values.length < 2) {
            return null;
        }

        GroupChartObject object = new GroupChartObject();

        for (int i = values.length - 2; i >= 0; i--) {
            IOHLC[] outBars = new IOHLC[] {
                    (IOHLC) values[i].getAdapter(IOHLC.class),
                    (IOHLC) values[i + 1].getAdapter(IOHLC.class),
            };

            if (outBars[0].getHigh() < outBars[1].getHigh() && outBars[0].getLow() > outBars[1].getLow()) {
                if (outBars[1].getClose() >= outBars[0].getHigh()) {
                    object.add(new PatternBox(outBars, color, getName(), Messages.Bullish));
                    i -= outBars.length;
                }
                else if (outBars[1].getClose() <= outBars[0].getLow()) {
                    object.add(new PatternBox(outBars, color, getName(), Messages.Bearish));
                    i -= outBars.length;
                }
            }
        }

        return object;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.charts.IChartObjectFactory#getParameters()
     */
    @Override
    public IChartParameters getParameters() {
        ChartParameters parameters = new ChartParameters();

        if (!factoryName.equals(name)) {
            parameters.setParameter("name", name); //$NON-NLS-1$
        }

        parameters.setParameter("style", renderStyle.getName()); //$NON-NLS-1$
        if (color != null) {
            parameters.setParameter("color", color); //$NON-NLS-1$
        }

        return parameters;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.ui.charts.IChartObjectFactory#setParameters(org.eclipsetrader.ui.charts.IChartParameters)
     */
    @Override
    public void setParameters(IChartParameters parameters) {
        name = parameters.hasParameter("name") ? parameters.getString("name") : factoryName; //$NON-NLS-1$ //$NON-NLS-2$

        renderStyle = parameters.hasParameter("style") ? RenderStyle.getStyleFromName(parameters.getString("style")) : RenderStyle.Line; //$NON-NLS-1$ //$NON-NLS-2$
        color = parameters.getColor("color"); //$NON-NLS-1$
    }
}
