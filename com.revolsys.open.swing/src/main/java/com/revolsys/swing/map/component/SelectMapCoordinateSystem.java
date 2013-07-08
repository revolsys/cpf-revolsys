package com.revolsys.swing.map.component;

import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;

import com.revolsys.converter.string.StringConverterRegistry;
import com.revolsys.gis.cs.CoordinateSystem;
import com.revolsys.gis.cs.GeometryFactory;
import com.revolsys.gis.cs.epsg.EpsgCoordinateSystems;
import com.revolsys.swing.field.ComboBox;
import com.revolsys.swing.field.InvokeMethodStringConverter;
import com.revolsys.swing.map.MapPanel;

@SuppressWarnings("serial")
public class SelectMapCoordinateSystem extends ComboBox implements
  ItemListener, PropertyChangeListener {

  private final MapPanel map;

  public SelectMapCoordinateSystem(final MapPanel map) {
    super(3857, 3005);

    this.map = map;
    setEditable(true);
    final InvokeMethodStringConverter renderer = new InvokeMethodStringConverter(
      this, "formatCoordinateSystem");
    setRenderer(renderer);
    AutoCompleteDecorator.decorate(this, renderer);
    addItemListener(this);
    map.addPropertyChangeListener("geometryFactory", this);
    final Dimension size = new Dimension(200, 30);
    setMaximumSize(size);
  }

  public String formatCoordinateSystem(final Object value) {
    final CoordinateSystem coordinateSystem = getCoordinateSystem(value);
    if (coordinateSystem == null) {
      return StringConverterRegistry.toString(value);
    } else {
      return coordinateSystem.getId() + " " + coordinateSystem.getName();
    }
  }

  public CoordinateSystem getCoordinateSystem(final Object value) {
    CoordinateSystem coordinateSystem = null;
    if (value instanceof CoordinateSystem) {
      coordinateSystem = (CoordinateSystem)value;
    } else if (value != null) {
      try {
        final int coordinateSystemId = Integer.parseInt(StringConverterRegistry.toString(value));
        coordinateSystem = EpsgCoordinateSystems.getCoordinateSystem(coordinateSystemId);
      } catch (final Throwable t) {
      }
    }
    return coordinateSystem;
  }

  @Override
  public void itemStateChanged(final ItemEvent e) {
    if (e.getStateChange() == ItemEvent.SELECTED) {
      final Object value = e.getItem();
      final CoordinateSystem coordinateSystem = getCoordinateSystem(value);
      if (coordinateSystem != null) {
        map.setGeometryFactory(GeometryFactory.getFactory(coordinateSystem));
      }
    }
  }

  @Override
  public void propertyChange(final PropertyChangeEvent event) {
    final String propertyName = event.getPropertyName();
    if ("geometryFactory".equals(propertyName)) {
      final GeometryFactory geometryFactory = map.getGeometryFactory();
      setSelectedItem(geometryFactory.getSRID());
    }
  }

}