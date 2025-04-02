/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *  Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
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

import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.model.WidgetType;

/**
 * A type of widget that can wrap another widget, hiding the wrapped widget's methods. When added to
 * a panel, a composite behaves exactly as if the widget it wraps had been added.
 * <p>
 * The composite is useful for creating a single widget out of an aggregate of multiple other
 * widgets contained in a single panel.
 * </p>
 */
public abstract class PComposite<T extends PWidget> extends PWidget {

    protected T widget;

    protected PComposite() {
    }

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.COMPOSITE;
    }

    protected void initWidget(final T child) {
        if (this.widget != null) throw new IllegalStateException("PComposite.initWidget() may only be " + "called once.");

        child.removeFromParent();
        this.widget = child;
        child.setParent(this);
        saveUpdate(writer -> writer.write(ServerToClientModel.WIDGET_ID, child.getID()));
    }

}
