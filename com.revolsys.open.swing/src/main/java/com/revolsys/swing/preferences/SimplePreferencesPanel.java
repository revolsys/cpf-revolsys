package com.revolsys.swing.preferences;

import java.util.LinkedHashSet;
import java.util.Set;

public class SimplePreferencesPanel extends AbstractPreferencesPanel {
  private static final long serialVersionUID = 1L;

  private final Set<Preference> preferences = new LinkedHashSet<Preference>();

  public SimplePreferencesPanel(final String title) {
    super(title, null);
  }

  public void addPreference(final String applicationName, final String path,
    final String propertyName, final Class<?> valueClass,
    final Object defaultValue) {
    final Preference preference = new Preference(applicationName, path,
      propertyName, valueClass, defaultValue);
    if (!preferences.contains(preference)) {
      preferences.add(preference);
      addField(preference.getField());
    }

  }

  @Override
  public void cancelChanges() {
    for (final Preference preference : preferences) {
      preference.cancelChanges();
    }
  }

  @Override
  protected void doSavePreferences() {
    for (final Preference preference : preferences) {
      preference.saveChanges();
    }
  }
}