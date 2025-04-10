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

package com.ponysdk.core.ui.rich;

import java.util.ArrayList;
import java.util.List;

import com.ponysdk.core.model.PHorizontalAlignment;
import com.ponysdk.core.ui.basic.Element;
import com.ponysdk.core.ui.basic.PButton;
import com.ponysdk.core.ui.basic.PFlexTable;
import com.ponysdk.core.ui.basic.PListBox;
import com.ponysdk.core.ui.basic.PListBox.ListItem;
import com.ponysdk.core.ui.basic.event.HasPChangeHandlers;
import com.ponysdk.core.ui.basic.event.PChangeEvent;
import com.ponysdk.core.ui.basic.event.PChangeHandler;

public class PTwinListBox<T> extends PFlexTable implements HasPChangeHandlers {

    private final String leftCaption;
    private final String rightCaption;
    private final List<PChangeHandler> handlers = new ArrayList<>();
    private boolean enabled = true;
    private PListBox leftListBox;
    private PListBox rightListBox;
    private PButton switchButton;

    public PTwinListBox() {
        this(null, null, false);
    }

    public PTwinListBox(final String leftCaption, final String rightCaption) {
        this(leftCaption, rightCaption, false);
    }

    public PTwinListBox(final String leftCaption, final String rightCaption, final boolean containsEmptyItem) {
        addStyleName("pony-TwinListBox");
        this.leftCaption = leftCaption;
        this.rightCaption = rightCaption;
        init(containsEmptyItem);
    }

    private void init(final boolean containsEmptyItem) {
        if (leftCaption != null) {
            setWidget(0, 0, Element.newPLabel(leftCaption));
            getCellFormatter().setHorizontalAlignment(0, 0, PHorizontalAlignment.ALIGN_CENTER);
        }

        if (rightCaption != null) {
            setWidget(0, 2, Element.newPLabel(rightCaption));
            getCellFormatter().setHorizontalAlignment(0, 2, PHorizontalAlignment.ALIGN_CENTER);
        }

        leftListBox = Element.newPListBox(containsEmptyItem);
        leftListBox.setMultipleSelect(true);
        leftListBox.addStyleName("pony-TwinListBox-Left-ListBox");

        rightListBox = Element.newPListBox(containsEmptyItem);
        rightListBox.setMultipleSelect(true);
        rightListBox.addStyleName("pony-TwinListBox-Right-ListBox");

        setWidget(1, 0, leftListBox);
        setWidget(1, 2, rightListBox);

        switchButton = Element.newPButton("<>");
        switchButton.addStyleName("pony-TwinListBox-Switch-ListBox");
        switchButton.addClickHandler(clickEvent -> {
            final List<ListItem> leftRemovedItems = new ArrayList<>();
            for (int i = leftListBox.getItemCount(); i > 0; i--) {
                if (leftListBox.isItemSelected(i - 1)) {
                    leftRemovedItems.add(leftListBox.removeItem(i - 1));
                }
            }

            final List<ListItem> rightRemovedItems = new ArrayList<>();
            for (int i = rightListBox.getItemCount(); i > 0; i--) {
                if (rightListBox.isItemSelected(i - 1)) {
                    rightRemovedItems.add(rightListBox.removeItem(i - 1));
                }
            }

            for (int i = leftRemovedItems.size() - 1; i >= 0; i--) {
                final ListItem listItem = leftRemovedItems.get(i);
                rightListBox.addItem(listItem.getLabel(), listItem.getValue());
            }
            for (int i = rightRemovedItems.size() - 1; i >= 0; i--) {
                final ListItem listItem = rightRemovedItems.get(i);
                leftListBox.addItem(listItem.getLabel(), listItem.getValue());
            }

            fireChangeHandler();

        });
        setWidget(1, 1, switchButton);
    }

    protected void fireChangeHandler() {
        final PChangeEvent event = new PChangeEvent(this);
        for (final PChangeHandler handler : handlers) {
            handler.onChange(event);
        }
    }

    @Override
    public void clear() {
        rightListBox.clear();
        leftListBox.clear();
    }

    public PListBox getLeftListBox() {
        return leftListBox;
    }

    public PListBox getRightListBox() {
        return rightListBox;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        rightListBox.setEnabled(enabled);
        leftListBox.setEnabled(enabled);
        switchButton.setEnabled(enabled);
    }

    @Override
    public void addChangeHandler(final PChangeHandler handler) {
        handlers.add(handler);
    }

    public boolean removeChangeHandler(final PChangeHandler handler) {
        return handlers.remove(handler);
    }

}
