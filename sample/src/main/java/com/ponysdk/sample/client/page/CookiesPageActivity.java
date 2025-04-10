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

import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.ui.basic.Element;
import com.ponysdk.core.ui.basic.PButton;
import com.ponysdk.core.ui.basic.PHorizontalPanel;
import com.ponysdk.core.ui.basic.PTextBox;
import com.ponysdk.core.ui.basic.PVerticalPanel;

public class CookiesPageActivity extends SamplePageActivity {

    public CookiesPageActivity() {
        super("Cookies", "Extra");
    }

    @Override
    protected void onFirstShowPage() {
        super.onFirstShowPage();

        final PTextBox name = Element.newPTextBox();
        name.setPlaceholder("Cookie name");
        final PTextBox value = Element.newPTextBox();
        name.setPlaceholder("Cookie value");
        final PButton add = Element.newPButton("Add");
        add.addClickHandler(event -> UIContext.get().getCookies().setCookie(name.getValue(), value.getValue()));

        final PTextBox name2 = Element.newPTextBox();
        name2.setPlaceholder("Cookie name");
        final PButton remove = Element.newPButton("Remove");
        remove.addClickHandler(event -> UIContext.get().getCookies().removeCookie(name2.getValue()));

        final PHorizontalPanel addPanel = Element.newPHorizontalPanel();
        addPanel.add(name);
        addPanel.add(value);
        addPanel.add(add);

        final PHorizontalPanel removePanel = Element.newPHorizontalPanel();
        removePanel.add(name2);
        removePanel.add(remove);

        final PVerticalPanel panel = Element.newPVerticalPanel();
        panel.setSpacing(10);

        panel.add(Element.newPLabel("Add a cookie:"));
        panel.add(addPanel);
        panel.add(Element.newPLabel("Remove a cookie:"));
        panel.add(removePanel);

        examplePanel.setWidget(panel);
    }
}
