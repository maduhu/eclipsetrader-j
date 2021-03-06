/*
 * Copyright (c) 2004-2013 Marco Maccaferri and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marco Maccaferri - initial API and implementation
 *     Naofumi Fukue - Yahoo! JAPAN Fx Connector
 */

package org.eclipsetrader.yahoojapanfx.internal.core.connector;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Status;
import org.eclipsetrader.core.feed.IConnectorListener;
import org.eclipsetrader.core.feed.IFeedConnector2;
import org.eclipsetrader.core.feed.IFeedIdentifier;
import org.eclipsetrader.core.feed.IFeedSubscription;
import org.eclipsetrader.core.feed.IFeedSubscription2;
import org.eclipsetrader.yahoojapanfx.internal.Activator;
import org.eclipsetrader.yahoojapanfx.internal.core.Util;
import org.eclipsetrader.yahoojapanfx.internal.core.repository.IdentifierType;
import org.eclipsetrader.yahoojapanfx.internal.core.repository.IdentifiersList;
import org.eclipsetrader.yahoojapanfx.internal.core.repository.PriceDataType;
import org.json.JSONObject;

public class SnapshotConnector implements Runnable, IFeedConnector2, IExecutableExtension, PropertyChangeListener {

    private static SnapshotConnector instance;

    private String streamingServer = "fx.yahoo.co.jp"; //$NON-NLS-1$

    private String id;
    private String name;

    private Map<String, FeedSubscription> symbolSubscriptions;
    private Map<String, FeedSubscription2> symbolSubscriptions2;
    private boolean subscriptionsChanged = false;
    private ListenerList listeners = new ListenerList(ListenerList.IDENTITY);

    private TimeZone timeZone;
//    private SimpleDateFormat df;
//    private SimpleDateFormat df2;
    private SimpleDateFormat df3;

    private Thread thread;
    private Thread notificationThread;
    private boolean stopping = false;

    private Runnable notificationRunnable = new Runnable() {

        @Override
        public void run() {
            synchronized (notificationThread) {
                while (!isStopping()) {
                    FeedSubscription[] subscriptions;
                    synchronized (symbolSubscriptions) {
                        Collection<FeedSubscription> c = symbolSubscriptions.values();
                        subscriptions = c.toArray(new FeedSubscription[c.size()]);
                    }
                    for (int i = 0; i < subscriptions.length; i++) {
                        subscriptions[i].fireNotification();
                    }

                    try {
                        notificationThread.wait();
                    } catch (InterruptedException e) {
                        // Ignore exception, not important at this time
                    }
                }
            }
        }
    };

    public SnapshotConnector() {
        symbolSubscriptions = new HashMap<String, FeedSubscription>();
        symbolSubscriptions2 = new HashMap<String, FeedSubscription2>();

        timeZone = TimeZone.getTimeZone("Asia/Tokyo"); //$NON-NLS-1$

//        df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss"); //$NON-NLS-1$
//        df.setTimeZone(timeZone);
//        df2 = new SimpleDateFormat("dd.MM.yyyy HHmmss"); //$NON-NLS-1$
//        df2.setTimeZone(timeZone);
        df3 = new SimpleDateFormat("yyyy/M/d HH:mm:ss"); //$NON-NLS-1$
        df3.setTimeZone(timeZone);
    }

    public synchronized static SnapshotConnector getInstance() {
        if (instance == null) {
            instance = new SnapshotConnector();
        }
        return instance;
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
     */
    @Override
    public void setInitializationData(IConfigurationElement config, String propertyName, Object data) throws CoreException {
        id = config.getAttribute("id"); //$NON-NLS-1$
        name = config.getAttribute("name"); //$NON-NLS-1$
        instance = this;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector#getId()
     */
    @Override
    public String getId() {
        return id;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector#getName()
     */
    @Override
    public String getName() {
        return name;
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector#subscribe(org.eclipsetrader.core.feed.IFeedIdentifier)
     */
    @Override
    public IFeedSubscription subscribe(IFeedIdentifier identifier) {
        synchronized (symbolSubscriptions) {
            IdentifierType identifierType = IdentifiersList.getInstance().getIdentifierFor(identifier);
            FeedSubscription subscription = symbolSubscriptions.get(identifierType.getSymbol());
            if (subscription == null) {
                subscription = new FeedSubscription(this, identifierType);

                PropertyChangeSupport propertyChangeSupport = (PropertyChangeSupport) identifier.getAdapter(PropertyChangeSupport.class);
                if (propertyChangeSupport != null) {
                    propertyChangeSupport.addPropertyChangeListener(this);
                }

                symbolSubscriptions.put(identifierType.getSymbol(), subscription);
                subscriptionsChanged = true;
            }
            if (identifierType.getIdentifier() == null) {
                identifierType.setIdentifier(identifier);

                PropertyChangeSupport propertyChangeSupport = (PropertyChangeSupport) identifier.getAdapter(PropertyChangeSupport.class);
                if (propertyChangeSupport != null) {
                    propertyChangeSupport.addPropertyChangeListener(this);
                }
            }
            if (subscription.incrementInstanceCount() == 1) {
                subscriptionsChanged = true;
            }
            return subscription;
        }
    }

    protected void disposeSubscription(FeedSubscription subscription) {
        synchronized (symbolSubscriptions) {
            if (subscription.decrementInstanceCount() <= 0) {
                IdentifierType identifierType = subscription.getIdentifierType();

                if (subscription.getIdentifier() != null) {
                    PropertyChangeSupport propertyChangeSupport = (PropertyChangeSupport) subscription.getIdentifier().getAdapter(PropertyChangeSupport.class);
                    if (propertyChangeSupport != null) {
                        propertyChangeSupport.removePropertyChangeListener(this);
                    }
                }

                symbolSubscriptions.remove(identifierType.getSymbol());
                subscriptionsChanged = true;
            }
        }
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector2#subscribeLevel2(org.eclipsetrader.core.feed.IFeedIdentifier)
     */
    @Override
    public IFeedSubscription2 subscribeLevel2(IFeedIdentifier identifier) {
        FeedSubscription subscription;
        IdentifierType identifierType;

        synchronized (symbolSubscriptions) {
            identifierType = IdentifiersList.getInstance().getIdentifierFor(identifier);
            subscription = symbolSubscriptions.get(identifierType.getSymbol());
            if (subscription == null) {
                subscription = new FeedSubscription(this, identifierType);

                PropertyChangeSupport propertyChangeSupport = (PropertyChangeSupport) identifier.getAdapter(PropertyChangeSupport.class);
                if (propertyChangeSupport != null) {
                    propertyChangeSupport.addPropertyChangeListener(this);
                }

                symbolSubscriptions.put(identifierType.getSymbol(), subscription);
                subscriptionsChanged = true;
            }
            if (subscription.incrementInstanceCount() == 1) {
                subscriptionsChanged = true;
            }
        }

        synchronized (symbolSubscriptions2) {
            FeedSubscription2 subscription2 = symbolSubscriptions2.get(identifierType.getSymbol());
            if (subscription2 == null) {
                subscription2 = new FeedSubscription2(this, subscription);
                symbolSubscriptions2.put(identifierType.getSymbol(), subscription2);
                subscriptionsChanged = true;
            }
            if (subscription.incrementLevel2InstanceCount() == 1) {
                subscriptionsChanged = true;
            }
            return subscription;
        }
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector2#subscribeLevel2(java.lang.String)
     */
    @Override
    public IFeedSubscription2 subscribeLevel2(String symbol) {
        FeedSubscription subscription;
        IdentifierType identifierType;

        synchronized (symbolSubscriptions) {
            identifierType = IdentifiersList.getInstance().getIdentifierFor(symbol);
            subscription = symbolSubscriptions.get(identifierType.getSymbol());
            if (subscription == null) {
                subscription = new FeedSubscription(this, identifierType);

                symbolSubscriptions.put(identifierType.getSymbol(), subscription);
                subscriptionsChanged = true;
            }
            if (subscription.incrementInstanceCount() == 1) {
                subscriptionsChanged = true;
            }
        }

        synchronized (symbolSubscriptions2) {
            FeedSubscription2 subscription2 = symbolSubscriptions2.get(identifierType.getSymbol());
            if (subscription2 == null) {
                subscription2 = new FeedSubscription2(this, subscription);
                symbolSubscriptions2.put(identifierType.getSymbol(), subscription2);
                subscriptionsChanged = true;
            }
            if (subscription.incrementLevel2InstanceCount() == 1) {
                subscriptionsChanged = true;
            }
            return subscription;
        }
    }

    protected void disposeSubscription2(FeedSubscription subscription, FeedSubscription2 subscription2) {
        synchronized (symbolSubscriptions2) {
            if (subscription.decrementLevel2InstanceCount() <= 0) {
                IdentifierType identifierType = subscription.getIdentifierType();
                symbolSubscriptions2.remove(identifierType.getSymbol());
                subscriptionsChanged = true;
            }
        }
        synchronized (symbolSubscriptions) {
            if (subscription.decrementInstanceCount() <= 0) {
                IdentifierType identifierType = subscription.getIdentifierType();

                if (subscription.getIdentifier() != null) {
                    PropertyChangeSupport propertyChangeSupport = (PropertyChangeSupport) subscription.getIdentifier().getAdapter(PropertyChangeSupport.class);
                    if (propertyChangeSupport != null) {
                        propertyChangeSupport.removePropertyChangeListener(this);
                    }
                }

                symbolSubscriptions.remove(identifierType.getSymbol());
                subscriptionsChanged = true;
            }
        }
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector#connect()
     */
    @Override
    public synchronized void connect() {
        stopping = false;

        if (notificationThread == null || !notificationThread.isAlive()) {
            notificationThread = new Thread(notificationRunnable, name + " - Notification"); //$NON-NLS-1$
            notificationThread.start();
        }

        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this, name + " - Data Reader"); //$NON-NLS-1$
            thread.start();
        }
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector#disconnect()
     */
    @Override
    public synchronized void disconnect() {
        stopping = true;

        if (thread != null) {
            try {
                thread.join(30 * 1000);
            } catch (InterruptedException e) {
                Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "Error stopping thread", e); //$NON-NLS-1$
                Activator.log(status);
            }
            thread = null;
        }

        if (notificationThread != null) {
            try {
                synchronized (notificationThread) {
                    notificationThread.notify();
                }
                notificationThread.join(30 * 1000);
            } catch (InterruptedException e) {
                Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "Error stopping notification thread", e); //$NON-NLS-1$
                Activator.log(status);
            }
            notificationThread = null;
        }
    }

    public boolean isStopping() {
        return stopping;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    @SuppressWarnings({
        "rawtypes", "unchecked"
    })
    public void run() {
        try {
            HttpClient client = new HttpClient();
            client.getHttpConnectionManager().getParams().setConnectionTimeout(5000);
            Util.setupProxy(client, streamingServer);

            synchronized (thread) {
                while (!isStopping()) {
                    synchronized (symbolSubscriptions) {
                        if (symbolSubscriptions.size() != 0) {
                            String[] symbols = symbolSubscriptions.keySet().toArray(new String[symbolSubscriptions.size()]);
                            fetchLatestSnapshot(client, symbols);
//                            subscriptionsChanged = false;
                        }
                    }

                    try {
                        thread.wait(5000);
                    } catch (InterruptedException e) {
                        // Ignore exception, not important at this time
                    }
                }
            }
        } catch (Exception e) {
            Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "Error reading data", e);
            Activator.log(status);
        }

        if (!isStopping()) {
            thread = new Thread(this, name + " - Data Reader"); //$NON-NLS-1$
            try {
                Thread.sleep(2 * 1000);
            } catch (Exception e) {
                // Do nothing
            }
            thread.start();
        }
    }

    protected void fetchLatestSnapshot(HttpClient client, String[] symbols) {
        try {
            HttpMethod method = Util.getSnapshotFeedMethod(streamingServer);

            BufferedReader in = null;
            try {
                client.executeMethod(method);

                if ((method.getResponseHeader("Content-Encoding") != null) && (method.getResponseHeader("Content-Encoding").getValue().equals("gzip"))) {
                	in = new BufferedReader(new InputStreamReader(new GZIPInputStream(method.getResponseBodyAsStream())));
                } else {
                	in = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
                }
                StringBuilder sb = new StringBuilder(1000);
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    sb.append(inputLine);
                }

                Calendar c = Calendar.getInstance();
                String year = String.valueOf(c.get(Calendar.YEAR));

                JSONObject obj = new JSONObject(sb.toString());
                JSONObject arate = obj.getJSONObject("rate");
                String date = obj.getString("date");
//                String company = obj.getString("company");
                for (int i = 0; i < symbols.length; i++) {
                	String symbol = (symbols[i].length() > 6 ? symbols[i].substring(0, 6) : symbols[i]);
                	if (arate.has(symbol)) {
                        JSONObject o = arate.getJSONObject(symbol);
                        processSnapshotData(symbols[i], o, year + "/" + date);
                    }
                }

            } catch (Exception e) {
                Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "Error reading snapshot data", e); //$NON-NLS-1$
                Activator.log(status);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (method != null) {
                        method.releaseConnection();
                    }
                } catch (Exception e) {
                    Status status = new Status(IStatus.WARNING, Activator.PLUGIN_ID, 0, "Connection wasn't closed cleanly", e);
                    Activator.log(status);
                }
            }

            wakeupNotifyThread();
        } catch (Exception e) {
            Activator.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "Error reading snapshot data", e)); //$NON-NLS-1$
        }
    }

    void processSnapshotData(String symbol, JSONObject o, String datetime) {
        FeedSubscription subscription = symbolSubscriptions.get(symbol);
        if (subscription == null) {
            return;
        }

        IdentifierType identifierType = subscription.getIdentifierType();
        PriceDataType priceData = identifierType.getPriceData();

        try {
            priceData.setTime(df3.parse(datetime)); //$NON-NLS-1$
        } catch (Exception e) {
            Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, 0, "Error parsing date: " + " (DATETIME='" + datetime + "'", e); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            Activator.log(status);
        }

        priceData.setBid(Double.parseDouble(o.getJSONObject("bid").getString("value")));
        priceData.setAsk(Double.parseDouble(o.getJSONObject("ask").getString("value")));
        priceData.setLastClose(Double.parseDouble(o.getJSONObject("bid").getString("value"))
        		- Double.parseDouble(o.getJSONObject("change").getString("value")));
        priceData.setOpen(Double.parseDouble(o.getJSONObject("open").getString("value")));
        priceData.setHigh(Double.parseDouble(o.getJSONObject("high").getString("value")));
        priceData.setLow(Double.parseDouble(o.getJSONObject("low").getString("value")));

        priceData.setLast(Double.parseDouble(o.getJSONObject("bid").getString("value")));
        subscription.setTrade(priceData.getTime(), priceData.getLast(), null, null);

        if (priceData.getLast() == null || priceData.getLast() == 0.0) {
            priceData.setLast(priceData.getLastClose());
        }

        subscription.setQuote(priceData.getBid(), priceData.getAsk(), priceData.getBidSize(), priceData.getAskSize());
        if (priceData.getOpen() != 0.0 && priceData.getHigh() != 0.0 && priceData.getLow() != 0.0) {
            subscription.setTodayOHL(priceData.getOpen(), priceData.getHigh(), priceData.getLow());
        }
        subscription.setLastClose(priceData.getLastClose(), null);
    }

    public void wakeupNotifyThread() {
        if (notificationThread != null) {
            synchronized (notificationThread) {
                notificationThread.notifyAll();
            }
        }
    }

    /* (non-Javadoc)
     * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof IFeedIdentifier) {
            IFeedIdentifier identifier = (IFeedIdentifier) evt.getSource();
            synchronized (symbolSubscriptions) {
                for (FeedSubscription subscription : symbolSubscriptions.values()) {
                    if (subscription.getIdentifier() == identifier) {
                        symbolSubscriptions.remove(subscription.getIdentifierType().getSymbol());
                        IdentifierType identifierType = IdentifiersList.getInstance().getIdentifierFor(identifier);
                        subscription.setIdentifierType(identifierType);
                        symbolSubscriptions.put(identifierType.getSymbol(), subscription);
                        subscriptionsChanged = true;
                        break;
                    }
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector#addConnectorListener(org.eclipsetrader.core.feed.IConnectorListener)
     */
    @Override
    public void addConnectorListener(IConnectorListener listener) {
        listeners.add(listener);
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.feed.IFeedConnector#removeConnectorListener(org.eclipsetrader.core.feed.IConnectorListener)
     */
    @Override
    public void removeConnectorListener(IConnectorListener listener) {
        listeners.remove(listener);
    }
}
