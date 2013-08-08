package com.revolsys.collection;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;

import com.revolsys.beans.PropertyChangeSupportProxy;

public class PropertyChangeArrayList<T> extends ArrayList<T> implements
  PropertyChangeListener, PropertyChangeSupportProxy {
  private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(
    this);

  @Override
  public void add(final int index, final T object) {
    if (object != null && !contains(object)) {
      super.add(index, object);
      addListener(object);
      propertyChangeSupport.fireIndexedPropertyChange("objects", index, null,
        object);
    }
  }

  @Override
  public boolean add(final T object) {
    final int index = size();
    add(index, object);
    return true;
  }

  @Override
  public boolean addAll(final Collection<? extends T> objects) {
    boolean added = false;
    for (final T object : objects) {
      if (add(object)) {
        added = true;
      }
    }
    return added;
  }

  @Override
  public boolean addAll(int index, final Collection<? extends T> objects) {
    boolean added = false;
    for (final T object : objects) {
      if (!contains(object)) {
        add(index, object);
        added = true;
        index++;
      }
    }
    return added;
  }

  protected void addListener(final Object object) {
    if (object instanceof PropertyChangeSupportProxy) {
      final PropertyChangeSupportProxy proxy = (PropertyChangeSupportProxy)object;
      final PropertyChangeSupport propertyChangeSupport = proxy.getPropertyChangeSupport();
      if (propertyChangeSupport != null) {
        propertyChangeSupport.addPropertyChangeListener(this);
      }
    }
  }

  @Override
  public PropertyChangeSupport getPropertyChangeSupport() {
    return propertyChangeSupport;
  }

  @Override
  public void propertyChange(final PropertyChangeEvent event) {
    propertyChangeSupport.firePropertyChange(event);
  }

  @Override
  public T remove(final int index) {
    final T object = remove(index);
    removeListener(object);
    propertyChangeSupport.fireIndexedPropertyChange("objects", index, object,
      null);
    return object;
  }

  @Override
  public boolean remove(final Object o) {
    final int index = indexOf(o);
    if (index < 0) {
      return false;
    } else {
      remove(index);
      return true;
    }
  }

  @Override
  public boolean removeAll(final Collection<?> c) {
    final boolean removed = false;
    for (Object object : c) {
      if (remove(object)) {
        object = true;
      }
    }
    return removed;
  }

  protected void removeListener(final Object object) {
    if (object instanceof PropertyChangeSupportProxy) {
      final PropertyChangeSupportProxy proxy = (PropertyChangeSupportProxy)object;
      final PropertyChangeSupport propertyChangeSupport = proxy.getPropertyChangeSupport();
      if (propertyChangeSupport != null) {
        propertyChangeSupport.removePropertyChangeListener(this);
      }
    }
  }

  @Override
  public T set(final int index, final T value) {
    final T oldValue = super.set(index, value);
    if (value != oldValue) {
      removeListener(oldValue);
      propertyChangeSupport.fireIndexedPropertyChange("objects", index,
        oldValue, value);
      addListener(value);
    }
    return oldValue;
  }
}