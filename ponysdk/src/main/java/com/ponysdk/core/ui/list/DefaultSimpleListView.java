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

package com.ponysdk.core.ui.list;

import com.ponysdk.core.ui.basic.IsPWidget;
import com.ponysdk.core.ui.basic.PFlexTable;

public class DefaultSimpleListView extends PFlexTable implements SimpleListView {

    @Override
    public void addWidget(final IsPWidget widget, final int column, final int row, final int colspan) {
        setWidget(row, column, widget.asWidget());
        if (colspan > 1) getCellFormatter().setColSpan(row, column, colspan);
    }

    @Override
    public void clearList() {
        clear(0);
    }

    @Override
    public void clear(final int from) {
        final int rowCount = getRowCount();
        for (int i = rowCount; i >= from; i--) {
            removeRow(i);
        }
    }

    @Override
    public void selectRow(final int row) {
        getRowFormatter().addStyleName(row + 1, "pony-SimpleList-Row-Selected");
    }

    @Override
    public void unSelectRow(final int row) {
        getRowFormatter().removeStyleName(row + 1, "pony-SimpleList-Row-Selected");
    }

    @Override
    public void setColumns(final int size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRowStyle(final int row, final String styleName) {
        getRowFormatter().addStyleName(row, styleName);
    }

    @Override
    public void removeRowStyle(final int row, final String styleName) {
        getRowFormatter().removeStyleName(row, styleName);
    }

    @Override
    public void addHeaderStyle(final String styleName) {
        getRowFormatter().addStyleName(0, styleName);
    }

    @Override
    public void addCellStyle(final int row, final int col, final String styleName) {
        getCellFormatter().addStyleName(row, col, styleName);

    }

    @Override
    public void removeCellStyle(final int row, final int column, final String styleName) {
        getCellFormatter().removeStyleName(row, column, styleName);
    }

    @Override
    public void addColumnStyle(final int column, final String styleName) {
        getColumnFormatter().addStyleName(column, styleName);
    }

    @Override
    public void removeColumnStyle(final int column, final String styleName) {
        getColumnFormatter().removeStyleName(column, styleName);
    }

    @Override
    public void setColumnWidth(final int column, final String width) {
        getColumnFormatter().setWidth(column, width);
    }

    @Override
    public void moveRow(final int index, final int beforeIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveColumn(final int index, final int beforeIndex) {
        throw new UnsupportedOperationException();
    }

}
