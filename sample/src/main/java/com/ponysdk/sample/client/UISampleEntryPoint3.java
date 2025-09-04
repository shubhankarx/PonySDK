/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *	Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *	Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ponysdk.sample.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.ponysdk.core.model.PUnit;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.server.concurrent.PScheduler;
import com.ponysdk.core.ui.basic.Element;
import com.ponysdk.core.ui.basic.IsPWidget;
import com.ponysdk.core.ui.basic.PAbsolutePanel;
import com.ponysdk.core.ui.basic.PButton;
import com.ponysdk.core.ui.basic.PCheckBox;
import com.ponysdk.core.ui.basic.PComplexPanel;
import com.ponysdk.core.ui.basic.PCookies;
import com.ponysdk.core.ui.basic.PDateBox;
import com.ponysdk.core.ui.basic.PDatePicker;
import com.ponysdk.core.ui.basic.PDockLayoutPanel;
import com.ponysdk.core.ui.basic.PElement;
import com.ponysdk.core.ui.basic.PFileUpload;
import com.ponysdk.core.ui.basic.PFlowPanel;
import com.ponysdk.core.ui.basic.PFrame;
import com.ponysdk.core.ui.basic.PFlexTable;
import com.ponysdk.core.ui.basic.PFunctionalLabel;
import com.ponysdk.core.ui.basic.PHorizontalPanel;
import com.ponysdk.core.ui.basic.PLabel;
import com.ponysdk.core.ui.basic.PListBox;
import com.ponysdk.core.ui.basic.PMenuBar;
import com.ponysdk.core.ui.basic.PRadioButton;
import com.ponysdk.core.ui.basic.PRichTextArea;
import com.ponysdk.core.ui.basic.PScript;
import com.ponysdk.core.ui.basic.PScrollPanel;
import com.ponysdk.core.ui.basic.PSimplePanel;
import com.ponysdk.core.ui.basic.PStackLayoutPanel;
import com.ponysdk.core.ui.basic.PTabLayoutPanel;
import com.ponysdk.core.ui.basic.PTextBox;
import com.ponysdk.core.ui.basic.PTree;
import com.ponysdk.core.ui.basic.PTreeItem;
import com.ponysdk.core.ui.basic.PVerticalPanel;
import com.ponysdk.core.ui.basic.PWidget;
import com.ponysdk.core.ui.basic.PWindow;
import com.ponysdk.core.ui.basic.event.PClickEvent;
import com.ponysdk.core.ui.basic.event.PKeyUpEvent;
import com.ponysdk.core.ui.basic.event.PKeyUpHandler;
import com.ponysdk.core.ui.datagrid2.adapter.DataGridAdapter;
import com.ponysdk.core.ui.datagrid2.column.ColumnDefinition;
import com.ponysdk.core.ui.datagrid2.column.DefaultColumnDefinition;
import com.ponysdk.core.ui.datagrid2.controller.DataGridController;
import com.ponysdk.core.ui.datagrid2.data.RowAction;
import com.ponysdk.core.ui.datagrid2.view.ColumnFilterFooterDataGridView;
import com.ponysdk.core.ui.datagrid2.view.ColumnVisibilitySelectorDataGridView;
import com.ponysdk.core.ui.datagrid2.view.ConfigSelectorDataGridView;
import com.ponysdk.core.ui.datagrid2.view.DataGridView;
import com.ponysdk.core.ui.datagrid2.view.DataGridView.DecodeException;
import com.ponysdk.core.ui.datagrid2.view.DefaultDataGridView;
import com.ponysdk.core.ui.datagrid2.view.RowSelectorColumnDataGridView;
import com.ponysdk.core.ui.eventbus2.EventBus.EventHandler;
import com.ponysdk.core.ui.form2.impl.formfield.ColorInputFormField;
import com.ponysdk.core.ui.form2.impl.formfield.NumberInputFormField;
import com.ponysdk.core.ui.form2.impl.formfield.StringTextBoxFormField;
import com.ponysdk.core.ui.formatter.TextFunction;
import com.ponysdk.core.ui.grid.AbstractGridWidget;
import com.ponysdk.core.ui.grid.GridTableWidget;
import com.ponysdk.core.ui.list.DataGridColumnDescriptor;
import com.ponysdk.core.ui.list.refreshable.Cell;
import com.ponysdk.core.ui.list.refreshable.RefreshableDataGrid;
import com.ponysdk.core.ui.list.renderer.cell.CellRenderer;
import com.ponysdk.core.ui.list.valueprovider.IdentityValueProvider;
import com.ponysdk.core.ui.main.EntryPoint;
import com.ponysdk.core.ui.model.PKeyCodes;
import com.ponysdk.core.ui.rich.PConfirmDialog;
import com.ponysdk.core.ui.rich.POptionPane;
import com.ponysdk.core.ui.rich.PToolbar;
import com.ponysdk.core.ui.rich.PTwinListBox;
import com.ponysdk.core.ui.scene.AbstractScene;
import com.ponysdk.core.ui.scene.Router;
import com.ponysdk.core.ui.scene.Scene;
import com.ponysdk.sample.client.event.UserLoggedOutEvent;
import com.ponysdk.sample.client.event.UserLoggedOutHandler;
import com.ponysdk.sample.client.page.addon.LoggerAddOn;

public class UISampleEntryPoint3 implements EntryPoint {

    private static final Logger log = LoggerFactory.getLogger(UISampleEntryPoint3.class);

    private final DecimalFormat priceFormat = new DecimalFormat("0.00000");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private final AtomicInteger updateCounter = new AtomicInteger(0);
    
    // Financial data simulation
    private double eurUsdBid = 1.08017;
    private double eurUsdAsk = 1.08096;

    @Override
    public void start(final UIContext uiContext) {
        
        // Create main control panel
        final PVerticalPanel mainPanel = Element.newPVerticalPanel();
        mainPanel.setSpacing(20);
        
        final PLabel titleLabel = Element.newPLabel("Financial Widget Latency Test Suite");
        titleLabel.addStyleName("title-header");
        mainPanel.add(titleLabel);
        
        // Create test control buttons
        final PHorizontalPanel controlPanel = Element.newPHorizontalPanel();
        controlPanel.setSpacing(10);
        
        final PButton smallTestButton = Element.newPButton("Run Small Widget Test (Price Quote)");
        final PButton mediumTestButton = Element.newPButton("Run Medium Widget Test (Trading Form)");
        final PButton largeTestButton = Element.newPButton("Run Large Widget Test (Full Dashboard)");
        final PButton identicalTestButton = Element.newPButton("🔄 Test IDENTICAL Patterns (Dictionary Test)");
        final PButton clearButton = Element.newPButton("Clear All Widgets");
        
        controlPanel.add(smallTestButton);
        controlPanel.add(mediumTestButton);
        controlPanel.add(largeTestButton);
        controlPanel.add(identicalTestButton);
        controlPanel.add(clearButton);
        
        mainPanel.add(controlPanel);
        
        // Results area
        final PVerticalPanel resultsPanel = Element.newPVerticalPanel();
        resultsPanel.setSpacing(10);
        mainPanel.add(resultsPanel);
        
        // Event handlers
        smallTestButton.addClickHandler(e -> runSmallWidgetTest(resultsPanel));
        mediumTestButton.addClickHandler(e -> runMediumWidgetTest(resultsPanel));
        largeTestButton.addClickHandler(e -> runLargeWidgetTest(resultsPanel));
        identicalTestButton.addClickHandler(e -> runIdenticalPatternTest(resultsPanel));
        clearButton.addClickHandler(e -> {
            resultsPanel.clear();
            updateCounter.set(0);
        });
        
        PWindow.getMain().add(mainPanel);
        
        // Start price simulation
        startPriceSimulation();
    }

    /**
     * Small Widget Test: Simple EUR/USD price quote display
     * Minimal payload - single price update pattern
     */
    private void runSmallWidgetTest(final PVerticalPanel resultsPanel) {
        final PLabel testLabel = Element.newPLabel("=== Small Widget Test (Price Quote) ===");
        testLabel.addStyleName("test-section-header");
        resultsPanel.add(testLabel);
        
        // Create simple price display
        final PHorizontalPanel pricePanel = Element.newPHorizontalPanel();
        pricePanel.addStyleName("price-quote-panel");
        pricePanel.setSpacing(10);
        
        final PLabel symbolLabel = Element.newPLabel("EUR/USD:");
        final PLabel bidLabel = Element.newPLabel("Bid: " + priceFormat.format(eurUsdBid));
        final PLabel askLabel = Element.newPLabel("Ask: " + priceFormat.format(eurUsdAsk));
        
        pricePanel.add(symbolLabel);
        pricePanel.add(bidLabel);
        pricePanel.add(askLabel);
        
        resultsPanel.add(pricePanel);
        
        // Update prices repeatedly to create dictionary patterns
        final AtomicInteger smallTestCounter = new AtomicInteger(0);
        
        final Runnable updatePrices = new Runnable() {
            @Override
            public void run() {
                final int count = smallTestCounter.incrementAndGet();
                
                // Small random price movements
                eurUsdBid += (Math.random() - 0.5) * 0.00001;
                eurUsdAsk += (Math.random() - 0.5) * 0.00001;
                
                bidLabel.setText("Bid: " + priceFormat.format(eurUsdBid));
                askLabel.setText("Ask: " + priceFormat.format(eurUsdAsk));
                
                // Log latency tracking for small payloads
                System.out.println("Small Widget Update #" + count + " - Bid: " + eurUsdBid + ", Ask: " + eurUsdAsk);
                
                if (count < 10) {
                    PScheduler.schedule(UIContext.get(), this, Duration.ofMillis(500));
                }
            }
        };
        
        PScheduler.schedule(UIContext.get(), updatePrices, Duration.ofMillis(500));
    }
    
    /**
     * Medium Widget Test: Trading form based on dealer intervention interface
     * Medium payload - form with multiple fields and validation
     */
    private void runMediumWidgetTest(final PVerticalPanel resultsPanel) {
        final PLabel testLabel = Element.newPLabel("=== Medium Widget Test (Trading Form) ===");
        testLabel.addStyleName("test-section-header");
        resultsPanel.add(testLabel);
        
        // Create vertical form layout
        final PVerticalPanel formPanel = Element.newPVerticalPanel();
        formPanel.addStyleName("trading-form");
        formPanel.setSpacing(5);
        
        // Form fields based on cockpit 1 screenshot
        final PLabel quoteReqIdLabel = Element.newPLabel("Quote ReqID: R20250814dd010000000002");
        final PLabel requesterLabel = Element.newPLabel("Requester: Sales 1 OBO AMTBK | Antrak Bank");
        final PLabel accountLabel = Element.newPLabel("Account: 022200-001");
        final PLabel creationDateLabel = Element.newPLabel("Creation Date: " + dateFormat.format(new Date()));
        final PLabel statusLabel = Element.newPLabel("Status: Quoting");
        final PLabel securityClassLabel = Element.newPLabel("Security Class: EUR/USD");
        final PLabel quantityLabel = Element.newPLabel("Quantity: 1M EUR");
        final PLabel priceLabel = Element.newPLabel("Market Price: " + priceFormat.format(eurUsdBid));
        
        formPanel.add(quoteReqIdLabel);
        formPanel.add(requesterLabel);
        formPanel.add(accountLabel);
        formPanel.add(creationDateLabel);
        formPanel.add(statusLabel);
        formPanel.add(securityClassLabel);
        formPanel.add(quantityLabel);
        formPanel.add(priceLabel);
        
        resultsPanel.add(formPanel);
        
        // Action buttons
        final PHorizontalPanel actionPanel = Element.newPHorizontalPanel();
        actionPanel.setSpacing(10);
        
        final PButton acceptButton = Element.newPButton("Accept Quote");
        final PButton rejectButton = Element.newPButton("Reject");
        final PButton deferButton = Element.newPButton("Defer");
        
        actionPanel.add(acceptButton);
        actionPanel.add(rejectButton);
        actionPanel.add(deferButton);
        
        resultsPanel.add(actionPanel);
        
        // Update form fields periodically to generate medium-sized patterns
        final AtomicInteger mediumTestCounter = new AtomicInteger(0);
        final String[] statuses = {"Quoting", "Pending", "Executed", "Rejected"};
        
        final Runnable updateForm = new Runnable() {
            @Override
            public void run() {
                final int count = mediumTestCounter.incrementAndGet();
                
                // Update multiple labels
                priceLabel.setText("Market Price: " + priceFormat.format(eurUsdBid + (Math.random() - 0.5) * 0.0001));
                statusLabel.setText("Status: " + statuses[count % 4]);
                creationDateLabel.setText("Creation Date: " + dateFormat.format(new Date()));
                
                System.out.println("Medium Widget Update #" + count + " - Form fields updated");
                
                if (count < 8) {
                    PScheduler.schedule(UIContext.get(), this, Duration.ofMillis(750));
                }
            }
        };
        
        PScheduler.schedule(UIContext.get(), updateForm, Duration.ofMillis(750));
    }
    
    /**
     * Large Widget Test: Complex dashboard with multiple panels
     * Large payload - multiple grids, charts simulation, and data tables
     */
    private void runLargeWidgetTest(final PVerticalPanel resultsPanel) {
        final PLabel testLabel = Element.newPLabel("=== Large Widget Test (Full Dashboard) ===");
        testLabel.addStyleName("test-section-header");
        resultsPanel.add(testLabel);
        
        // Create complex dashboard layout
        final PVerticalPanel dashboardPanel = Element.newPVerticalPanel();
        dashboardPanel.addStyleName("financial-dashboard");
        dashboardPanel.setSpacing(10);
        
        // Create many label widgets for large payload
        final PLabel[] priceLabels = new PLabel[20];  // 20 price labels
        final PLabel[] tradingLabels = new PLabel[15]; // 15 trading activity labels
        final PLabel[] positionLabels = new PLabel[10]; // 10 position labels
        final PLabel[] riskLabels = new PLabel[8];      // 8 risk labels
        
        // Initialize all labels
        for (int i = 0; i < priceLabels.length; i++) {
            priceLabels[i] = Element.newPLabel("Price " + (i+1) + ": " + priceFormat.format(1.0 + Math.random()));
            dashboardPanel.add(priceLabels[i]);
        }
        
        for (int i = 0; i < tradingLabels.length; i++) {
            tradingLabels[i] = Element.newPLabel("Trade " + (i+1) + ": EUR/USD " + (Math.random() > 0.5 ? "BUY" : "SELL") + " " + (int)(Math.random() * 10) + "M");
            dashboardPanel.add(tradingLabels[i]);
        }
        
        for (int i = 0; i < positionLabels.length; i++) {
            positionLabels[i] = Element.newPLabel("Position " + (i+1) + ": " + (Math.random() > 0.5 ? "+" : "-") + (int)(Math.random() * 100) + "K");
            dashboardPanel.add(positionLabels[i]);
        }
        
        for (int i = 0; i < riskLabels.length; i++) {
            riskLabels[i] = Element.newPLabel("Risk " + (i+1) + ": " + (Math.random() > 0.7 ? "HIGH" : "LOW"));
            dashboardPanel.add(riskLabels[i]);
        }
        
        resultsPanel.add(dashboardPanel);
        
        // Status panel
        final PLabel statusPanel = Element.newPLabel("Dashboard Status: Active | Updates: 0");
        statusPanel.addStyleName("status-panel");
        resultsPanel.add(statusPanel);
        
        // Update entire dashboard periodically (large payload updates)
        final AtomicInteger largeTestCounter = new AtomicInteger(0);
        
        final Runnable updateDashboard = new Runnable() {
            @Override
            public void run() {
                final int count = largeTestCounter.incrementAndGet();
                
                // Update all labels - this creates large, complex patterns
                for (int i = 0; i < priceLabels.length; i++) {
                    priceLabels[i].setText("Price " + (i+1) + ": " + priceFormat.format(1.0 + Math.random()));
                }
                
                for (int i = 0; i < tradingLabels.length; i++) {
                    tradingLabels[i].setText("Trade " + (i+1) + ": EUR/USD " + (Math.random() > 0.5 ? "BUY" : "SELL") + " " + (int)(Math.random() * 10) + "M");
                }
                
                for (int i = 0; i < positionLabels.length; i++) {
                    positionLabels[i].setText("Position " + (i+1) + ": " + (Math.random() > 0.5 ? "+" : "-") + (int)(Math.random() * 100) + "K");
                }
                
                for (int i = 0; i < riskLabels.length; i++) {
                    riskLabels[i].setText("Risk " + (i+1) + ": " + (Math.random() > 0.7 ? "HIGH" : "LOW"));
                }
                
                // Update status
                statusPanel.setText("Dashboard Status: Active | Updates: " + count + " | Last: " + new Date());
                
                System.out.println("Large Widget Update #" + count + " - Full dashboard refresh (" + (priceLabels.length + tradingLabels.length + positionLabels.length + riskLabels.length) + " widgets)");
                
                if (count < 6) {
                    PScheduler.schedule(UIContext.get(), this, Duration.ofMillis(1000));
                }
            }
        };
        
        PScheduler.schedule(UIContext.get(), updateDashboard, Duration.ofMillis(1000));
    }
    
    /**
     * IDENTICAL Pattern Test: Tests dictionary compression by sending identical patterns
     * This should demonstrate actual dictionary reuse since values don't change
     */
    private void runIdenticalPatternTest(final PVerticalPanel resultsPanel) {
        final PLabel testLabel = Element.newPLabel("=== 🔄 IDENTICAL Pattern Dictionary Test ===");
        testLabel.addStyleName("test-section-header");
        resultsPanel.add(testLabel);
        
        final PLabel instructionLabel = Element.newPLabel("This test sends IDENTICAL patterns repeatedly to test dictionary compression.");
        resultsPanel.add(instructionLabel);
        
        // Create test widgets with FIXED values
        final PLabel priceLabel = Element.newPLabel("EUR/USD: 1.08500");
        final PLabel statusLabel = Element.newPLabel("Status: ACTIVE");
        final PLabel volumeLabel = Element.newPLabel("Volume: 1M EUR");
        
        resultsPanel.add(priceLabel);
        resultsPanel.add(statusLabel);
        resultsPanel.add(volumeLabel);
        
        // Counter to track updates
        final AtomicInteger identicalTestCounter = new AtomicInteger(0);
        
        final Runnable updateIdentical = new Runnable() {
            @Override
            public void run() {
                final int count = identicalTestCounter.incrementAndGet();
                
                // SET IDENTICAL VALUES EVERY TIME (no Math.random()!)
                priceLabel.setText("EUR/USD: 1.08500");  // ← SAME VALUE
                statusLabel.setText("Status: ACTIVE");   // ← SAME VALUE  
                volumeLabel.setText("Volume: 1M EUR");   // ← SAME VALUE
                
                System.out.println("🔄 IDENTICAL Pattern Test #" + count + " - Should create dictionary compression after 2nd occurrence!");
                
                if (count < 10) {
                    PScheduler.schedule(UIContext.get(), this, Duration.ofMillis(800));
                }
            }
        };
        
        PScheduler.schedule(UIContext.get(), updateIdentical, Duration.ofMillis(800));
    }
    
    private void startPriceSimulation() {
        // Continuous price updates to simulate real market data
        PScheduler.scheduleAtFixedRate(() -> {
            eurUsdBid += (Math.random() - 0.5) * 0.00001;
            eurUsdAsk += (Math.random() - 0.5) * 0.00001;
        }, Duration.ofMillis(100));
    }

}
