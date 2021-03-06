package org.eclipsetrader.ui.internal.charts;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

    private static final String BUNDLE_NAME = "org.eclipsetrader.ui.internal.charts.messages"; //$NON-NLS-1$
    public static String ChartPeriodsPreferencePage_Add;
	public static String ChartPeriodsPreferencePage_Edit;
	public static String ChartPeriodsPreferencePage_Remove;
	public static String ChartsUIActivator_IndicatorErrorMessage;
    public static String DataImportJob_DataErrorMessage;
    public static String DataImportJob_DownloadDataErrorMessage;
    public static String DataImportJob_DownloadErrorMessage;
    public static String DataImportJob_Name;
    public static String DataImportJob_SecurityDownloadErrorMessage;
    public static String DataUpdateWizard_WindowTitle;
	public static String DefaultsPage_InitialBackfill;
	public static String DefaultsPage_Last;
	public static String DefaultsPage_StartDate;
	public static String DefaultsPage_Years;
    public static String ImportDataPage_AggregationLabel;
    public static String ImportDataPage_AllSecuritiesText;
    public static String ImportDataPage_DaysText;
    public static String ImportDataPage_Description;
    public static String ImportDataPage_FullIncrementalText;
    public static String ImportDataPage_FullText;
    public static String ImportDataPage_ImportLabel;
    public static String ImportDataPage_IncrementalText;
    public static String ImportDataPage_MinutesText;
    public static String ImportDataPage_PeriodLabel;
    public static String ImportDataPage_SelectedSecuritiesText;
    public static String ImportDataPage_Title;
    public static String ImportDataPage_ToLabel;
    public static String ImportDataPage_TypeLabel;
    public static String PeriodDialog_BarSize;
	public static String PeriodDialog_Description;
	public static String PeriodDialog_Last;
	public static String PeriodDialog_Period;

	public static String PatternChart_Bearish;
	public static String PatternChart_Bullish;
	
	static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
