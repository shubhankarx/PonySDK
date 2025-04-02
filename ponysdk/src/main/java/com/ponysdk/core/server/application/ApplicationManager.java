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

package com.ponysdk.core.server.application;

import com.ponysdk.core.ui.main.EntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ApplicationManager {

    private static final Logger log = LoggerFactory.getLogger(ApplicationManager.class);

    protected ApplicationConfiguration configuration;

    public void startApplication(final UIContext uiContext) {
        uiContext.execute(() -> {
            final EntryPoint entryPoint = initializeEntryPoint();
            final String historyToken = uiContext.getHistoryToken();

            if (historyToken != null && !historyToken.isEmpty()) {
                uiContext.getHistory().newItem(historyToken, false);
            }

            entryPoint.start(uiContext);
        });
    }

    protected abstract EntryPoint initializeEntryPoint();

    public ApplicationConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final ApplicationConfiguration configuration) {
        this.configuration = configuration;
    }

    public abstract void start();

}
