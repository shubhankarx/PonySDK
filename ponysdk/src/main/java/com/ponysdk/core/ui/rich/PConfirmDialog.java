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

package com.ponysdk.core.ui.rich;

import com.ponysdk.core.model.PHorizontalAlignment;
import com.ponysdk.core.ui.basic.Element;
import com.ponysdk.core.ui.basic.PButton;
import com.ponysdk.core.ui.basic.PConfirmDialogHandler;
import com.ponysdk.core.ui.basic.PDialogBox;
import com.ponysdk.core.ui.basic.PHorizontalPanel;
import com.ponysdk.core.ui.basic.PVerticalPanel;
import com.ponysdk.core.ui.basic.PWidget;
import com.ponysdk.core.ui.basic.PWindow;
import com.ponysdk.core.ui.i18n.PString;

public class PConfirmDialog extends PDialogBox {

    private PButton okButton;
    private PButton cancelButton;

    public PConfirmDialog() {
        super();
    }

    public static PConfirmDialog show(final PWindow window, final String windowCaption, final String message, final String okCaption,
                                      final String cancelCaption, final PConfirmDialogHandler confirmDialogHandler) {
        return show(window, windowCaption, Element.newPLabel(message), okCaption, cancelCaption, confirmDialogHandler);
    }

    // show a popup which have a ok button hiding the popup by default
    public static PConfirmDialog show(final PWindow window, final String windowCaption, final PWidget content) {
        return show(window, windowCaption, content, PString.get("dialog.ok"), null, null);
    }

    public static PConfirmDialog show(final PWindow window, final String windowCaption, final PWidget content,
                                      final PConfirmDialogHandler confirmDialogHandler) {
        return show(window, windowCaption, content, PString.get("dialog.ok"), null, confirmDialogHandler);
    }

    public static PConfirmDialog show(final PWindow window, final String windowCaption, final PWidget content, final String okCaption,
                                      final String cancelCaption, final PConfirmDialogHandler confirmDialogHandler) {
        final PConfirmDialog confirmDialog = buildPopup(windowCaption, content, okCaption, cancelCaption, confirmDialogHandler);
        window.add(confirmDialog);
        confirmDialog.center();
        return confirmDialog;
    }

    public static PConfirmDialog buildPopup(final String windowCaption, final PWidget content, final String okCaption,
                                            final String cancelCaption, final PConfirmDialogHandler confirmDialogHandler) {
        final PConfirmDialog confirmDialog = new PConfirmDialog();
        confirmDialog.setStyleName("pony-DialogBox");
        confirmDialog.setAnimationEnabled(false);
        confirmDialog.setGlassEnabled(true);

        // Build content
        final PVerticalPanel dialogContent = Element.newPVerticalPanel();
        dialogContent.add(content);
        final PHorizontalPanel controlsPanel = Element.newPHorizontalPanel();
        controlsPanel.setStyleName("dialogControls");

        if (cancelCaption != null) {
            final PButton cancelButton = Element.newPButton(cancelCaption);
            cancelButton.addClickHandler(event -> {
                if (confirmDialogHandler != null) {
                    confirmDialogHandler.onCancel();
                }
                confirmDialog.hide();
            });
            controlsPanel.add(cancelButton);
            controlsPanel.setCellHorizontalAlignment(cancelButton, PHorizontalAlignment.ALIGN_CENTER);
            confirmDialog.setCancelButton(cancelButton);
        }
        if (okCaption != null) {
            final PButton okButton = Element.newPButton(okCaption);
            okButton.addClickHandler(event -> {
                if (confirmDialogHandler != null) {
                    if (confirmDialogHandler.onOK(confirmDialog)) confirmDialog.hide();
                } else confirmDialog.hide();
            });

            controlsPanel.add(okButton);
            controlsPanel.setCellHorizontalAlignment(okButton, PHorizontalAlignment.ALIGN_CENTER);
            confirmDialog.setOkButton(okButton);
        }

        dialogContent.add(controlsPanel);
        dialogContent.setCellHorizontalAlignment(controlsPanel, PHorizontalAlignment.ALIGN_CENTER);
        dialogContent.setCellHorizontalAlignment(content, PHorizontalAlignment.ALIGN_CENTER);
        confirmDialog.setCaption(windowCaption);
        confirmDialog.setWidget(dialogContent);

        return confirmDialog;
    }

    @Override
    public void ensureDebugId(final String debugID) {
        super.ensureDebugId(debugID);
        enrichEnsureDebugID();
    }

    protected void setOkButton(final PButton okButton) {
        this.okButton = okButton;
        enrichEnsureDebugID();
    }

    protected void setCancelButton(final PButton cancelButton) {
        this.cancelButton = cancelButton;
        enrichEnsureDebugID();
    }

    protected void enrichEnsureDebugID() {
        if (okButton != null) okButton.ensureDebugId(getDebugID() + "[ok]");
        if (cancelButton != null) cancelButton.ensureDebugId(getDebugID() + "[cancel]");
    }

}
