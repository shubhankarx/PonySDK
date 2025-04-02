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

package com.ponysdk.core.ui.basic;

import java.time.Duration;

import com.ponysdk.core.model.PAlignment;
import com.ponysdk.core.model.PUnit;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.model.WidgetType;

/**
 * A panel that lays its children
 * <p>
 * This widget will <em>only</em> work in standards mode, which requires that
 * the HTML page in which it is run have an explicit &lt;!DOCTYPE&gt;
 * declaration.
 * </p>
 */
public class PLayoutPanel extends PComplexPanel implements PAnimatedLayout {

    protected PLayoutPanel() {
    }

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.LAYOUT_PANEL;
    }

    public void setWidgetHorizontalPosition(final PWidget child, final PAlignment position) {
        assertIsChild(child);

        saveUpdate(writer -> {
            writer.write(ServerToClientModel.WIDGET_HORIZONTAL_ALIGNMENT, position.getValue());
            writer.write(ServerToClientModel.WIDGET_ID, child.getID());
        });
    }

    public void setWidgetVerticalPosition(final PWidget child, final PAlignment position) {
        assertIsChild(child);

        saveUpdate(writer -> {
            writer.write(ServerToClientModel.WIDGET_VERTICAL_ALIGNMENT, position.getValue());
            writer.write(ServerToClientModel.WIDGET_ID, child.getID());
        });
    }

    public void setWidgetHidden(final PWidget widget, final boolean hidden) {
        assertIsChild(widget);

        saveUpdate(writer -> {
            writer.write(ServerToClientModel.WIDGET_HIDDEN, hidden);
            writer.write(ServerToClientModel.WIDGET_ID, widget.getID());
        });
    }

    public void setWidgetLeftRight(final PWidget child, final double left, final double right, final PUnit unit) {
        assertIsChild(child);
        sendUpdate(child, ServerToClientModel.LEFT, left, ServerToClientModel.RIGHT, right, unit);
    }

    public void setWidgetLeftWidth(final PWidget child, final double left, final double width, final PUnit unit) {
        assertIsChild(child);
        sendUpdate(child, ServerToClientModel.LEFT, left, ServerToClientModel.WIDTH, width, unit);
    }

    public void setWidgetRightWidth(final PWidget child, final double right, final double width, final PUnit unit) {
        assertIsChild(child);
        sendUpdate(child, ServerToClientModel.RIGHT, right, ServerToClientModel.WIDTH, width, unit);
    }

    public void setWidgetTopBottom(final PWidget child, final double top, final double bottom, final PUnit unit) {
        assertIsChild(child);
        sendUpdate(child, ServerToClientModel.TOP, top, ServerToClientModel.BOTTOM, bottom, unit);
    }

    public void setWidgetTopHeight(final PWidget child, final double top, final double height, final PUnit unit) {
        assertIsChild(child);
        sendUpdate(child, ServerToClientModel.TOP, top, ServerToClientModel.HEIGHT, height, unit);
    }

    public void setWidgetBottomHeight(final PWidget child, final double bottom, final double height, final PUnit unit) {
        assertIsChild(child);
        sendUpdate(child, ServerToClientModel.BOTTOM, bottom, ServerToClientModel.HEIGHT, height, unit);
    }

    private void sendUpdate(final PWidget child, final ServerToClientModel key1, final double v1, final ServerToClientModel key2,
                            final double v2, final PUnit unit) {
        saveUpdate(writer -> {
            writer.write(ServerToClientModel.UNIT, unit.getByteValue());
            writer.write(ServerToClientModel.WIDGET_ID, child.getID());
            writer.write(key1, v1);
            writer.write(key2, v2);
        });
    }

    @Override
    public void animate(final Duration duration) {
        saveUpdate(writer -> writer.write(ServerToClientModel.ANIMATE, duration.toMillis()));
    }
}
