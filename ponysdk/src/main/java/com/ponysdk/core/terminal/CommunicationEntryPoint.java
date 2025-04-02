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

package com.ponysdk.core.terminal;

import com.google.gwt.core.client.EntryPoint;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CommunicationEntryPoint implements EntryPoint {

    private static final Logger log = Logger.getLogger(CommunicationEntryPoint.class.getName());

    @Override
    public void onModuleLoad() {
        if (useExternalStart()) {
            if (log.isLoggable(Level.INFO))
                log.info("'onPonySDKModuleLoaded' is detected, PonySDK must be instantiated by an external javascript");
            onModuleLoaded();
        } else {
            if (log.isLoggable(Level.INFO)) log.info("Create PonySDK using the default entry point");
            final PonySDK ponysdk = PonySDK.get();
            ponysdk.start();
        }
    }

    private native boolean useExternalStart() /*-{
                                              if ($wnd.onPonySDKModuleLoaded && typeof $wnd.onPonySDKModuleLoaded == 'function') return true;
                                              return false;
                                              }-*/;

    private native void onModuleLoaded() /*-{
                                         if ($wnd.onPonySDKModuleLoaded && typeof $wnd.onPonySDKModuleLoaded == 'function') $wnd.onPonySDKModuleLoaded();
                                         }-*/;
}
