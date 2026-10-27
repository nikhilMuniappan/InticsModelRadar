package com.intics.modelradar.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;

/**
 * Application shell. {@link Push} enables server push so the "Refresh Now" action
 * can run the sync on a background thread and then update the grid/notification
 * from that thread via {@code UI.access(...)}.
 */
@Push
public class AppShell implements AppShellConfigurator {
}
