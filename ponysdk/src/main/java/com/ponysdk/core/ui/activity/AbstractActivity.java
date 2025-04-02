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

package com.ponysdk.core.ui.activity;

import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.server.stm.Txn;
import com.ponysdk.core.ui.basic.IsPWidget;
import com.ponysdk.core.ui.basic.PAcceptsOneWidget;
import com.ponysdk.core.ui.eventbus.BroadcastEventHandler;
import com.ponysdk.core.ui.eventbus.Event;
import com.ponysdk.core.ui.eventbus.EventHandler;
import com.ponysdk.core.ui.eventbus.HandlerRegistration;
import com.ponysdk.core.ui.place.Place;
import com.ponysdk.core.ui.place.PlaceChangeRequest;

public abstract class AbstractActivity<T extends IsPWidget> implements Activity {

    protected boolean firstStart = true;
    protected boolean started = false;

    protected PAcceptsOneWidget world;
    protected T view;

    public AbstractActivity() {
    }

    @Override
    public void start(final PAcceptsOneWidget world, final Place place) {
        this.world = world;
        this.started = true;

        final T view2 = getView();
        view2.asWidget().addStyleName("pony-LoadingBox");
        this.world.setWidget(view2);

        // Force flush to show the Loading information
        Txn.get().flush();

        if (firstStart) {
            buildView();
            firstStart = false;
        }

        updateView(place);
        view2.asWidget().removeStyleName("pony-LoadingBox");
    }

    public T getView() {
        return view;
    }

    @Override
    public void stop() {
        this.started = false;
    }

    protected void buildView() {
        // Nothing to do
    }

    protected void updateView(final Place place) {
        // Nothing to do
    }

    public void goTo(final Place place) {
        PlaceChangeRequest.fire(this, place);
    }

    public HandlerRegistration addHandler(final Event.Type type, final EventHandler handler) {
        return UIContext.get().getRootEventBus().addHandler(type, handler);
    }

    public void removeHandler(final Event.Type type, final EventHandler handler) {
        UIContext.get().getRootEventBus().removeHandler(type, handler);
    }

    public void fireEvent(final Event<? extends EventHandler> event) {
        UIContext.get().getRootEventBus().fireEvent(event);
    }

    public void addHandler(final BroadcastEventHandler handler) {
        UIContext.get().getRootEventBus().addHandler(handler);
    }

    public void removeHandler(final BroadcastEventHandler handler) {
        UIContext.get().getRootEventBus().removeHandler(handler);
    }

}
