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
import com.ponysdk.core.ui.basic.PFunctionalLabel;
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

public class UISampleEntryPoint2 implements EntryPoint, UserLoggedOutHandler {

    private static final Logger log = LoggerFactory.getLogger(UISampleEntryPoint2.class);

    private PLabel mainLabel;

    // HighChartsStackedColumnAddOn highChartsStackedColumnAddOn;
    int a = 0;
    private static int counter;

    @Override
    public void start(final UIContext uiContext) {
            final PLabel l1 = Element.newPLabel("Label 1");
            final PLabel l2 = Element.newPLabel("Label 2");
            final PLabel l3 = Element.newPLabel("Label 3");
            final PLabel l4 = Element.newPLabel("Label 4");
            final PLabel l5 = Element.newPLabel("Label 5");
            final PLabel l6 = Element.newPLabel("Label 6");
            final PLabel l7 = Element.newPLabel("Label 7");
            final PLabel l8 = Element.newPLabel("Label 8");
            final PLabel l9 = Element.newPLabel("Label 9");
List<PLabel> labels = List.of(l1, l2, l3, l4, l5, l6, l7, l8, l9);

                    for(PLabel label : labels) {
                        PWindow.getMain().add(label);
                    }



              
            // Create periodic updates using PScheduler
            Runnable updateTask = new Runnable() {
     int ct=0;
                @Override
                public void run() {
                    if(ct++ % 2 == 0){

                    
                    for(int i = 0; i < 9; i++) {
                        labels.get(i).setText("Label " +(i+1));
                    }
                    }
                    else{
                        for(int i = 0; i < 9; i++) {
                            labels.get(i).setText("");
                        }
                    }
                    // Schedule next update after this one completes
                    PScheduler.schedule(UIContext.get(), this, Duration.ofMillis(2000));
                }
            };
            // Initial schedule
            PScheduler.schedule(UIContext.get(), updateTask, Duration.ofMillis(2000));
        
        
    }

    private void testScene() {
        final Scene scene1 = new AbstractScene("scene1", "Scene 1", "scene1") {

            @Override
            public PWidget buildGUI() {
                return Element.newPLabel("Scene1 Started");
            }
        };

        final Scene scene2 = new AbstractScene("scene2", "Scene 2", "scene2") {

            @Override
            public PWidget buildGUI() {
                return Element.newPLabel("Scene2 Started");
            }
        };

        final PSimplePanel layout = Element.newPSimplePanel();
        final Router router = new Router("sample");
        router.setLayout(layout);
        router.push(scene1);
        router.push(scene2);

        PWindow.getMain().add(layout);

        router.go("scene2");
    }

    private static class MyRow {

        private final int id;
        private Map<String, String> map;
        private final String format;

        public MyRow(final int id) {
            super();
            this.id = id;
            format = String.format("%09d", id);
        }

        public void putValue(final String key, final String value) {
            if (map == null) map = new ConcurrentHashMap<>();
            map.put(key, value);
        }

        public String getValue(final String key) {
            if (map != null) {
                final String v = map.get(key);
                if (v != null) return v;
            }
            return key + format;
        }

        @Override
        public MyRow clone() {
            final MyRow model = new MyRow(id);
            if (map == null) return model;
            model.map = new HashMap<>(map);
            return model;
        }

        @Override
        public String toString() {
            return "SampleModel [id=" + id + "]";
        }

    }

    private static MyRow createMyRow(final int index) {
        return new MyRow(index);
    }

    private void testSimpleDataGridView() {
        final DefaultDataGridView<Integer, MyRow> simpleGridView = new DefaultDataGridView<>();
        simpleGridView.setRefreshOnColumnVisibilityChanged(true);

        //TODO 2 implementation (1 simple & 1 full => surrement coté core ui, recheck et discuter avec JB)

        final ColumnVisibilitySelectorDataGridView<Integer, MyRow> columnVisibilitySelectorDataGridView = new ColumnVisibilitySelectorDataGridView<>(
            simpleGridView);
        final RowSelectorColumnDataGridView<Integer, MyRow> rowSelectorColumnDataGridView = new RowSelectorColumnDataGridView<>(
            columnVisibilitySelectorDataGridView);
        final ColumnFilterFooterDataGridView<Integer, MyRow> columnFilterFooterDataGridView = new ColumnFilterFooterDataGridView<>(
            rowSelectorColumnDataGridView);
        final ConfigSelectorDataGridView<Integer, MyRow> configSelectorDataGridView = new ConfigSelectorDataGridView<>(
            columnFilterFooterDataGridView, "DEFAULT");

        final DataGridView<Integer, MyRow> gridView = configSelectorDataGridView;
        gridView.setAdapter(new DataGridAdapter<>() {

            private final List<ColumnDefinition<MyRow>> columns = new ArrayList<>();

            {
                for (char c = 'a'; c <= 'z'; c++) {
                    final String ss = c + "";
                    columns.add(new DefaultColumnDefinition<>(ss, v -> v.getValue(ss), (v, s) -> {
                        v.putValue(ss, s);
                    }));
                }
                for (char c = 'A'; c <= 'Z'; c++) {
                    final String ss = c + "";
                    columns.add(new DefaultColumnDefinition<>(ss, v -> v.getValue(ss), (v, s) -> {
                        v.putValue(ss, s);
                    }));
                }
            }

            @Override
            public void onUnselectRow(final IsPWidget rowWidget) {
                rowWidget.asWidget().removeStyleName("selected-row");
            }

            @Override
            public void onSelectRow(final IsPWidget rowWidget) {
                rowWidget.asWidget().addStyleName("selected-row");
            }

            @Override
            public boolean isAscendingSortByInsertionOrder() {
                return false;
            }

            @Override
            public Integer getKey(final MyRow v) {
                return v.id;
            }

            @Override
            public List<ColumnDefinition<MyRow>> getColumnDefinitions() {
                return columns;
            }

            @Override
            public int compareDefault(final MyRow v1, final MyRow v2) {
                return 0;
            }

            @Override
            public void onCreateHeaderRow(final IsPWidget rowWidget) {
                rowWidget.asWidget().getParent().asWidget().setStyleProperty("background", "aliceblue");
            }

            @Override
            public void onCreateFooterRow(final IsPWidget rowWidget) {
                rowWidget.asWidget().getParent().asWidget().setStyleProperty("background", "aliceblue");
            }

            @Override
            public void onCreateRow(final IsPWidget rowWidget) {
            }

            @Override
            public boolean hasHeader() {
                return true;
            }

            @Override
            public boolean hasFooter() {
                return false;
            }

            @Override
            public IsPWidget createLoadingDataWidget() {
                final PComplexPanel div = Element.newDiv();
                div.setWidth("100%");
                div.setHeight("100%");
                div.setStyleProperty("background-color", "#FFFFFF7F");
                return div;
            }

            @Override
            public void onCreateColumnResizer(final IsPWidget resizer) {
            }

            @Override
            public boolean isSelectionEnabled() {
                return true;
            }
        });
        gridView.setPollingDelayMillis(250L);

        final DataGridController<Integer, MyRow> controller = gridView.getController();
        gridView.asWidget().setHeight("500px");
        gridView.asWidget().setWidth("1000px");
        gridView.asWidget().setStyleProperty("resize", "both");
        gridView.asWidget().setStyleProperty("overflow", "hidden");
        final PTextBox pollingDelay = Element.newPTextBox();
        final PButton changePollingDelay = Element.newPButton("Change polling delay (ms)");
        PWindow.getMain().add(pollingDelay);
        PWindow.getMain().add(changePollingDelay);
        changePollingDelay.addClickHandler((e) -> {
            gridView.setPollingDelayMillis(Integer.parseInt(pollingDelay.getText().trim()));
        });
        PWindow.getMain().add(gridView);

        final PButton clearSortsButton = Element.newPButton("Clear Sorts");
        clearSortsButton.addClickHandler(e -> {
            gridView.clearSorts(false);
        });
        PWindow.getMain().add(clearSortsButton);
        PWindow.getMain().add(columnVisibilitySelectorDataGridView.getDecoratorWidget());
        PWindow.getMain().add(configSelectorDataGridView.getDecoratorWidget());

        final PTextBox addConfigTextBox = Element.newPTextBox();
        final PButton addConfigButton = Element.newPButton("Add Config");
        final PLabel addConfigLabel = Element.newPLabel();
        addConfigLabel.setStyleProperty("color", "red");

        PWindow.getMain().add(addConfigTextBox);
        PWindow.getMain().add(addConfigButton);
        addConfigButton.addClickHandler(e -> {
            final String key = addConfigTextBox.getText();
            if (!configSelectorDataGridView.addConfigEntry(key, configSelectorDataGridView.getCurrentConfig())) {
                addConfigLabel.setText(key + " config already exists");
                return;
            }
            addConfigLabel.setText("");
            addConfigTextBox.setText("");
            configSelectorDataGridView.selectConfig(key);
        });

        final PTextBox exportConfigTextBox = Element.newPTextBox();
        final PButton exportConfigButton = Element.newPButton("Export Configs");
        exportConfigButton.addClickHandler(e -> {
            exportConfigTextBox.setText(configSelectorDataGridView.encodeConfigEntries(configSelectorDataGridView.getConfigEntries()));
        });
        final PTextBox importConfigTextBox = Element.newPTextBox();
        final PButton importConfigButton = Element.newPButton("Import Configs");
        importConfigButton.addClickHandler(e -> {
            try {
                configSelectorDataGridView
                    .setConfigEntries(configSelectorDataGridView.decodeConfigEntries(importConfigTextBox.getText()));
            } catch (final DecodeException e1) {
                e1.printStackTrace();
            }
        });
        PWindow.getMain().add(exportConfigTextBox);
        PWindow.getMain().add(exportConfigButton);
        PWindow.getMain().add(importConfigTextBox);
        PWindow.getMain().add(importConfigButton);
        controller.setBound(false);
        for (int i = 0; i < 1_0000; i++) {
            if (i % 500_000 == 0) log.info("i: {}", i);
            controller.setData(createMyRow(i));
        }
        controller.setBound(true);

        gridView.addRowAction(UISampleEntryPoint2.class, new RowAction<>() {

            @Override
            public boolean testRow(final MyRow t, final int index) {
                return (index & 1) == 0;
            }

            @Override
            public void cancel(final IsPWidget row) {
                row.asWidget().removeStyleName("unpair-row");
            }

            @Override
            public void apply(final IsPWidget row) {
                row.asWidget().addStyleName("unpair-row");
            }

            @Override
            public boolean isActionApplied(final IsPWidget row) {
                return row.asWidget().hasStyleName("unpair-row");
            }
        });
    }

    private void testVisibilityHandler(final PWindow window) {
        final PLabel liveVisibility = Element.newPLabel("Live Visibility : Unknown");
        window.add(liveVisibility);

        final PButton button = Element.newPButton("Check visibility");
        window.add(button);

        final PLabel visibilityLabel = Element.newPLabel("Visibility : Unknown");
        window.add(visibilityLabel);

        final PScrollPanel frame = Element.newPScrollPanel();
        frame.setHeight("200px");
        frame.setWidth("300px");
        window.add(frame);

        final PFlowPanel panel = Element.newPFlowPanel();
        panel.setHeight("2000px");
        frame.add(panel);

        final PFlowPanel subPanel = Element.newPFlowPanel();
        subPanel.setStyleProperty("backgroundColor", "red");
        subPanel.setHeight("125px");
        subPanel.setWidth("200px");
        panel.add(subPanel);

        final PLabel label = Element.newPLabel("Increment : " + a++);
        subPanel.add(label);

        PScheduler.scheduleAtFixedRate(() -> {
            a++;
            if (subPanel.isShown() && subPanel.getWindow().isShown()) updateLabel(label, String.valueOf(a));
        }, Duration.ofSeconds(1));

        subPanel.getWindow().addVisibilityHandler(event -> {
            if (event.getData()) {
                System.err.println("Force refresh, because window became visible");
                updateLabel(label, String.valueOf(a));
            } else {
                System.err.println("Window became not visible");
            }
        });

        liveVisibility.setText("Live Visibility : " + subPanel.isShown());
        visibilityLabel.setText("Visibility : " + subPanel.isShown());
        subPanel.addVisibilityHandler(event -> {
            liveVisibility.setText("Live Visibility : " + event.getData());
            if (event.getData()) {
                System.err.println("Force refresh, because panel became visible");
                updateLabel(label, String.valueOf(a));
            }
        });
        button.addClickHandler(event -> {
            visibilityLabel.setText("Visibility : " + subPanel.isShown());
        });
    }

    private static void updateLabel(final PLabel label, final String text) {
        System.out.println("Update label " + text);
        label.setText("Increment : " + text);
    }

    private void createFunctionalLabel() {
        final TextFunction textFunction = new TextFunction(args -> {
            System.out.println(args[0] + " " + args[1]);
            return (String) args[0];
        }, "console.log(args[0] + \" \" + args[1]); return args[0];");
        final PFunctionalLabel newPFunctionalLabel = Element.newPFunctionalLabel(textFunction);
        PWindow.getMain().add(newPFunctionalLabel);
        newPFunctionalLabel.setArgs("A", "B");
    }

    public PFlowPanel createPFileUpload() {
        final PFlowPanel panel = Element.newPFlowPanel();
        final PFileUpload fileUpload = Element.newPFileUpload();
        fileUpload.setName("file");
        panel.add(fileUpload);
        fileUpload.addChangeHandler(event -> {
            final PFileUpload pFileUpload = (PFileUpload) event.getSource();
            System.out.println("File name : " + pFileUpload.getFileName());
            System.out.println("File size : " + pFileUpload.getFileSize() + " bytes");
        });
        fileUpload.addStreamHandler((request, response, context) -> {
            try {
                final List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
                for (final FileItem item : items) {
                    if (!item.isFormField()) readFileItem(item);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        });
        final PButton button = Element.newPButton("Submit");
        button.addClickHandler(event -> fileUpload.submit());
        panel.add(button);
        return panel;
    }

    private void readFileItem(final FileItem item) throws IOException, FileNotFoundException {
        // Store the uploaded file on the server (don't forget to remove)
        final String fileName = FilenameUtils.getName(item.getName());
        final InputStream fileContent = item.getInputStream();
        final File uploadedFile = File.createTempFile(fileName, "fileUpload");
        IOUtils.copy(fileContent, new FileOutputStream(uploadedFile));
        uploadedFile.deleteOnExit();

        // Read directly the input stream
        final BufferedReader reader = new BufferedReader(new InputStreamReader(item.getInputStream(), "UTF-8"));
        final StringBuilder value = new StringBuilder();
        final char[] buffer = new char[1024];
        for (int length = 0; (length = reader.read(buffer)) > 0;) {
            value.append(buffer, 0, length);
        }
        System.out.println(value.toString());
    }

    private void testPerf() {
        final PWindow w = Element.newPWindow("Window 1", "resizable=yes,location=0,status=0,scrollbars=0");
        final List<PLabel> labels = new ArrayList<>(1000);

        final PButton start = Element.newPButton("Start");
        w.add(start);
        start.addClickHandler(event -> scheduleUpdate(labels));

        for (int i = 0; i < 1000; i++) {
            final PLabel label = Element.newPLabel(counter + "-" + i);
            labels.add(label);
            w.add(label);
        }

        w.open();
    }

    private void scheduleUpdate(final List<PLabel> labels) {
        PScheduler.schedule(() -> {
            int i = 0;
            counter++;
            for (final PLabel label : labels) {
                label.setText(counter + "-" + i);
                i++;
            }
            if (counter < 20) scheduleUpdate(labels);
            else counter = 0;
        }, Duration.ofMillis(20));
    }

    private void createReconnectingPanel() {
        final PSimplePanel reconnectionPanel = Element.newPSimplePanel();
        reconnectionPanel.setAttribute("id", "reconnection");
        final PSimplePanel reconnectingPanel = Element.newPSimplePanel();
        reconnectingPanel.setAttribute("id", "reconnecting");
        reconnectionPanel.setWidget(reconnectingPanel);
        PWindow.getMain().add(reconnectionPanel);
    }

    private void downloadFile() {
        final PButton downloadImageButton = Element.newPButton("Download Pony image");
        downloadImageButton.addClickHandler(event -> UIContext.get().stackStreamRequest((request, response, uiContext1) -> {
            response.reset();
            response.setContentType("image/png");
            response.setHeader("Content-Disposition", "attachment; filename=pony_image.png");

            try {
                final OutputStream output = response.getOutputStream();
                final InputStream input = getClass().getClassLoader().getResourceAsStream("images/pony.png");

                final byte[] buff = new byte[1024];
                while (input.read(buff) != -1) {
                    output.write(buff);
                }

                output.flush();
                output.close();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }));
        PWindow.getMain().add(downloadImageButton);
    }

    private void testPAddon() {
        final LoggerAddOn addon = createPAddOn();
        addon.attach(PWindow.getMain());

        // final PElementAddOn elementAddOn = new PElementAddOn();
        // elementAddOn.setInnerText("Coucou");
        // flowPanel.add(elementAddOn);

        // highChartsStackedColumnAddOn = new HighChartsStackedColumnAddOn();
        // PWindow.getMain().add(highChartsStackedColumnAddOn);
        // highChartsStackedColumnAddOn.setSeries("");

        // final HighChartsStackedColumnAddOn h2 = new
        // HighChartsStackedColumnAddOn();
        // a.add(h2);
        // h2.setSeries("");
        // final PElementAddOn elementAddOn2 = new PElementAddOn();
        // elementAddOn2.setInnerText("Coucou dans window");
        // a.add(elementAddOn2);
    }

    private void createNewEvent() {
        final EventHandler<PClickEvent> handler = UIContext.get().getNewEventBus().subscribe(PClickEvent.class,
            event -> System.err.println("B " + event));
        UIContext.get().getNewEventBus().post(new PClickEvent(this));
        UIContext.get().getNewEventBus().post(new PClickEvent(this));
        UIContext.get().getNewEventBus().unsubscribe(handler);
        UIContext.get().getNewEventBus().post(new PClickEvent(this));
    }

    private static final class Data {

        protected Integer key;
        protected String value;

        public Data(final Integer key, final String value) {
            this.key = key;
            this.value = value;
        }
    }

    
    @Override
    public void onUserLoggedOut(final UserLoggedOutEvent event) {
        UIContext.get().close();
    }

    private static final PStackLayoutPanel createStackLayoutPanel() {
        final PStackLayoutPanel child = Element.newPStackLayoutPanel(PUnit.CM);
        child.add(Element.newPLabel("Text"), "Text", false, 1.0);
        return child;
    }

    private static final PListBox createListBox() {
        final PListBox pListBox = Element.newPListBox(true);
        pListBox.addItem("A");
        pListBox.addItem("B");
        pListBox.insertItem("C", 1);
        pListBox.addItemsInGroup("sport", "Baseball", "Basketball", "Football", "Hockey", "Water Polo");
        return pListBox;
    }

    private static final PTabLayoutPanel createTabLayoutPanel() {
        final PTabLayoutPanel child = Element.newPTabLayoutPanel();
        child.add(Element.newPLabel("text"), "text");
        return child;
    }

    private static final PMenuBar createMenu() {
        final PMenuBar pMenuBar = Element.newPMenuBar(true);
        pMenuBar.addItem(Element.newPMenuItem("Menu 1", Element.newPMenuBar()));
        pMenuBar.addItem(Element.newPMenuItem("Menu 2", true, Element.newPMenuBar()));
        pMenuBar.addItem(Element.newPMenuItem("Menu 3", () -> System.err.println("Menu click")));
        pMenuBar.addSeparator();
        return pMenuBar;
    }

    private static final PFlowPanel createPFlowPanel() {
        final PFlowPanel panel1 = Element.newPFlowPanel();
        panel1.setAttribute("id", "panel1");
        final PFlowPanel panel2_1 = Element.newPFlowPanel();
        panel2_1.setAttribute("id", "panel2_1");
        final PFlowPanel panel3_1_1 = Element.newPFlowPanel();
        panel3_1_1.setAttribute("id", "panel3_1_1");
        final PFlowPanel panel3_1_2 = Element.newPFlowPanel();
        panel3_1_2.setAttribute("id", "panel3_1_2");
        final PFlowPanel panel2_2 = Element.newPFlowPanel();
        panel2_2.setAttribute("id", "panel2_2");
        final PFlowPanel panel3_2_1 = Element.newPFlowPanel();
        panel3_2_1.setAttribute("id", "panel3_2_1");
        final PFlowPanel panel3_2_2 = Element.newPFlowPanel();
        panel3_2_2.setAttribute("id", "panel3_2_2");

        panel1.add(panel2_1);
        panel2_1.add(panel3_1_1);
        panel2_1.add(panel3_1_2);
        panel1.add(panel2_2);
        panel2_2.add(panel3_2_1);
        panel2_2.add(panel3_2_2);

        return panel1;
    }

    private static final PWidget createBlock(final PWidget child) {
        final PFlowPanel panel = Element.newPFlowPanel();
        panel.add(child);
        return panel;
    }

    private static final PDockLayoutPanel createDockLayoutPanel() {
        final PDockLayoutPanel pDockLayoutPanel = Element.newPDockLayoutPanel(PUnit.CM);
        pDockLayoutPanel.addNorth(Element.newPLabel("LabelDock"), 1.5);
        return pDockLayoutPanel;
    }

    private static final PFlowPanel createDateBox() {
        final PFlowPanel flowPanel = Element.newPFlowPanel();

        final PDatePicker datePicker = Element.newPDatePicker();
        final Date a = new Date();
        System.err.println(new Date(a.getYear(), a.getMonth(), 26));
        System.err.println(new Date());

        datePicker.setTransientEnabledOnDates(false, List.of(new Date(), new Date(a.getYear(), a.getMonth(), 26)));
        datePicker.addStyleToDates("toto", List.of(new Date()));

        final PDateBox dateBox = Element.newPDateBox(datePicker, new SimpleDateFormat("dd/MM/yyyy"));
        //dateBox.setValue(new Date(0));
        flowPanel.add(dateBox);
        datePicker.addShowRangeHandler(e -> {
            datePicker.setTransientEnabledOnDates(false, List.of(new Date(), new Date(a.getYear(), a.getMonth(), 26)));
            datePicker.addStyleToDates("toto", List.of(new Date()));
        });

        final PButton button = Element.newPButton("reset");
        button.addClickHandler(event -> dateBox.setValue(null));
        flowPanel.add(button);
        return flowPanel;
    }

    private static final LoggerAddOn createPAddOn() {
        final LoggerAddOn labelPAddOn = new LoggerAddOn();
        labelPAddOn.log("addon logger test");

        labelPAddOn.setAjaxHandler((req, resp) -> {
            final String header = req.getHeader("info");

            if (header.equals("Get Data")) {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType("application/json");
                resp.getWriter().print("{\"response\": \"" + header + "\"}");
                resp.getWriter().flush();
            } else {
                resp.sendError(500);
            }
        });

        labelPAddOn.setTerminalHandler(event -> System.err.println(event.toString()));

        return labelPAddOn;
    }

    private static final PTextBox createPTextBox() {
        final PTextBox pTextBox = Element.newPTextBox();

        pTextBox.addKeyUpHandler(new PKeyUpHandler() {

            @Override
            public void onKeyUp(final PKeyUpEvent keyUpEvent) {
                PScript.execute(PWindow.getMain(), "alert('" + keyUpEvent + "');");
            }

            @Override
            public PKeyCodes[] getFilteredKeys() {
                return new PKeyCodes[] { PKeyCodes.ENTER };
            }
        });
        return pTextBox;
    }

    private static final PTree createTree() {
        final PTree tree = Element.newPTree();

        final PTreeItem firstFolder = tree.add("First");
        firstFolder.add("2");
        firstFolder.add(0, Element.newPTreeItem("1"));

        firstFolder.setState(true);

        final PTreeItem secondFolder = Element.newPTreeItem("Second");
        final PTreeItem subItem = secondFolder.add(Element.newPTreeItem());
        subItem.setText("3");
        secondFolder.add(Element.newPTreeItem(Element.newPLabel("4")));
        tree.add(secondFolder);

        secondFolder.setSelected(true);

        return tree;
    }

    private static final PAbsolutePanel createAbsolutePanel() {
        final PAbsolutePanel pAbsolutePanel = Element.newPAbsolutePanel();
        pAbsolutePanel.add(Element.newDiv());
        pAbsolutePanel.add(Element.newP());
        return pAbsolutePanel;
    }

    private static final PButton createButton() {
        final PButton pButton = Element.newPButton("Button 1");
        pButton.addClickHandler(handler -> pButton.setText("Button 1 clicked"));
        return pButton;
    }

    class Pojo {

        public String security;
        public String classe;
        public Double bid;
        public Double offer;
        public Double spread;
        public String coucou;
        public String coucou1;
        public String coucou2;
        public String coucou3;
        public String coucou4;
        public String coucou5;
        public String coucou6;
        public String coucou7;
        public String coucou8;
        public String coucou9;
        public String coucou10;

        /**
         * @return the security
         */
        public String getSecurity() {
            return security;
        }

        /**
         * @param security the security to set
         */
        public void setSecurity(final String security) {
            this.security = security;
        }

        /**
         * @return the classe
         */
        public String getClasse() {
            return classe;
        }

        /**
         * @param classe the classe to set
         */
        public void setClasse(final String classe) {
            this.classe = classe;
        }

        /**
         * @return the bid
         */
        public Double getBid() {
            return bid;
        }

        /**
         * @param bid the bid to set
         */
        public void setBid(final Double bid) {
            this.bid = bid;
        }

        /**
         * @return the offer
         */
        public Double getOffer() {
            return offer;
        }

        /**
         * @param offer the offer to set
         */
        public void setOffer(final Double offer) {
            this.offer = offer;
        }

        /**
         * @return the spread
         */
        public Double getSpread() {
            return spread;
        }

        /**
         * @param spread the spread to set
         */
        public void setSpread(final Double spread) {
            this.spread = spread;
        }

        /**
         * @return the coucou
         */
        public String getCoucou() {
            return coucou;
        }

        /**
         * @param coucou the coucou to set
         */
        public void setCoucou(final String coucou) {
            this.coucou = coucou;
        }

        /**
         * @return the coucou1
         */
        public String getCoucou1() {
            return coucou1;
        }

        /**
         * @param coucou1 the coucou1 to set
         */
        public void setCoucou1(final String coucou1) {
            this.coucou1 = coucou1;
        }

        /**
         * @return the coucou2
         */
        public String getCoucou2() {
            return coucou2;
        }

        /**
         * @param coucou2 the coucou2 to set
         */
        public void setCoucou2(final String coucou2) {
            this.coucou2 = coucou2;
        }

        /**
         * @return the coucou3
         */
        public String getCoucou3() {
            return coucou3;
        }

        /**
         * @param coucou3 the coucou3 to set
         */
        public void setCoucou3(final String coucou3) {
            this.coucou3 = coucou3;
        }

        /**
         * @return the coucou4
         */
        public String getCoucou4() {
            return coucou4;
        }

        /**
         * @param coucou4 the coucou4 to set
         */
        public void setCoucou4(final String coucou4) {
            this.coucou4 = coucou4;
        }

    }

}
