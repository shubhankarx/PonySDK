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

package com.ponysdk.sample.client.page;

import java.time.Duration;
import java.util.Arrays;

import com.ponysdk.core.model.PUnit;
import com.ponysdk.core.model.PVerticalAlignment;
import com.ponysdk.core.ui.basic.Element;
import com.ponysdk.core.ui.basic.PButton;
import com.ponysdk.core.ui.basic.PDockLayoutPanel;
import com.ponysdk.core.ui.basic.PHorizontalPanel;
import com.ponysdk.core.ui.basic.PLabel;
import com.ponysdk.core.ui.basic.PSimpleLayoutPanel;
import com.ponysdk.core.ui.basic.PSimplePanel;
import com.ponysdk.core.ui.basic.PTabLayoutPanel;
import com.ponysdk.core.ui.basic.PTextBox;
import com.ponysdk.core.ui.rich.PNotificationManager;

public class TabLayoutPanelPageActivity extends SamplePageActivity {

    private static final String[] colors = { "#F34A53", "#FAE3B4", "#AAC789", "#437356", "#1E4147" };
    protected int tabCount = 0;

    public TabLayoutPanelPageActivity() {
        super("Tab Layout Panel", Arrays.asList("Panels", "Tab"));
    }

    @Override
    protected void onFirstShowPage() {
        super.onFirstShowPage();

        final PDockLayoutPanel dockLayoutPanel = Element.newPDockLayoutPanel(PUnit.PX);
        dockLayoutPanel.setSizeFull();

        final PTabLayoutPanel tabPanel = Element.newPTabLayoutPanel();
        tabPanel.setSizeFull();
        tabPanel.setAnimationVertical(false);
        tabPanel.setAnimationDuration(Duration.ofMillis(1000));

        tabPanel.addBeforeSelectionHandler(event -> PNotificationManager.showTrayNotification(getView().asWidget().getWindow(),
            "onBeforeSelection, tab index : " + event.getSelectedItem()));
        tabPanel.addSelectionHandler(event -> PNotificationManager.showTrayNotification(getView().asWidget().getWindow(),
            "onSelection, tab index : " + event.getSelectedItem()));

        final PButton button = Element.newPButton("Add Tab");
        button.setStyleProperty("margin", "10px");
        button.addClickHandler(clickEvent -> addTabContent(tabPanel));

        final PButton addCustomTabButton = Element.newPButton("Add Tab (custom tab)");
        addCustomTabButton.setStyleProperty("margin", "10px");
        addCustomTabButton.addClickHandler(clickEvent -> addCustomTabContent(tabPanel));

        final PTextBox indexTextBox = Element.newPTextBox();
        final PButton selectButton = Element.newPButton("Select Tab");
        selectButton.setStyleProperty("margin", "10px");
        selectButton.addClickHandler(clickEvent -> {
            final String text = indexTextBox.getText();
            tabPanel.selectTab(Integer.valueOf(text));
        });

        final PHorizontalPanel horizontalPanel = Element.newPHorizontalPanel();
        horizontalPanel.setVerticalAlignment(PVerticalAlignment.ALIGN_MIDDLE);
        horizontalPanel.add(button);
        horizontalPanel.add(addCustomTabButton);
        horizontalPanel.add(indexTextBox);
        horizontalPanel.add(selectButton);

        dockLayoutPanel.addNorth(horizontalPanel, 50);
        dockLayoutPanel.add(tabPanel);

        addTabContent(tabPanel);

        examplePanel.setWidget(dockLayoutPanel);
    }

    protected void addTabContent(final PTabLayoutPanel tabPanel) {
        final PSimpleLayoutPanel tabContent = Element.newPSimpleLayoutPanel();
        tabContent.setStyleProperty("backgroundColor", colors[tabCount % colors.length]);

        final int tabIndex = tabCount;
        final PLabel label = Element.newPLabel("content-" + tabIndex);
        label.setStyleProperty("color", "white");
        tabContent.setWidget(label);
        tabPanel.add(tabContent, "Tab-" + tabIndex);
        tabCount++;
    }

    protected void addCustomTabContent(final PTabLayoutPanel tabPanel) {
        final PSimplePanel tabContent = Element.newPSimplePanel();

        final int tabIndex = tabCount;
        final PLabel tabLabel = Element.newPLabel("CustomTab-" + tabIndex);
        tabLabel.setStyleProperty("color", "blue");
        tabLabel.setStyleProperty("whiteSpace", "nowrap");
        final PLabel label = Element.newPLabel("content-" + tabIndex);
        tabContent.setWidget(label);
        tabPanel.add(tabContent, tabLabel);
        tabCount++;
    }
}
