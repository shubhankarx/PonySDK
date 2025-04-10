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

package com.ponysdk.impl.webapplication.page;

import com.ponysdk.core.ui.activity.Activity;
import com.ponysdk.core.ui.activity.ActivityMapper;
import com.ponysdk.core.ui.place.Place;
import com.ponysdk.impl.webapplication.application.ApplicationActivity;
import com.ponysdk.impl.webapplication.page.place.LoginPlace;

public class ApplicationActivityMapper implements ActivityMapper {

    private Activity loginActivity;
    private ApplicationActivity applicationActivity;

    @Override
    public Activity getActivity(final Place place) {
        if (place instanceof LoginPlace) return loginActivity;
        else return applicationActivity;
    }

    public void setLoginActivity(final Activity loginActivity) {
        this.loginActivity = loginActivity;
    }

    public void setApplicationActivity(final ApplicationActivity applicationActivity) {
        this.applicationActivity = applicationActivity;
    }

}
