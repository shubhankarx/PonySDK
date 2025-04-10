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

package com.ponysdk.impl.webapplication.page;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DefaultPageProvider implements PageProvider {

    private final Map<String, PageActivity> allPageActivitiesDeclared = new LinkedHashMap<>();

    private Map<String, PageActivity> allActivePageActivities;

    @Override
    public PageActivity getPageActivity(final String pageName) {
        if (allActivePageActivities == null) {
            initPagePermissions();
        }
        return allActivePageActivities.get(pageName);
    }

    @Override
    public Collection<PageActivity> getPageActivities() {
        if (allActivePageActivities == null) {
            initPagePermissions();
        }
        return allActivePageActivities.values();
    }

    public void setPageActivities(final Collection<PageActivity> pageActivities) {
        pageActivities.forEach(this::addPageActivity);
    }

    private void initPagePermissions() {
        allActivePageActivities = new LinkedHashMap<>();
        for (final Entry<String, PageActivity> entry : allPageActivitiesDeclared.entrySet()) {
            allActivePageActivities.put(entry.getKey(), entry.getValue());
        }
    }

    public void addPageActivity(final PageActivity pageActivity) {
        if (pageActivity.getPageName() != null) {
            allPageActivitiesDeclared.put(pageActivity.getPageName(), pageActivity);
        } else if (pageActivity.getPageCategories() != null) {
            for (final String category : pageActivity.getPageCategories()) {
                allPageActivitiesDeclared.put(category, pageActivity);
            }
        }
    }

}
