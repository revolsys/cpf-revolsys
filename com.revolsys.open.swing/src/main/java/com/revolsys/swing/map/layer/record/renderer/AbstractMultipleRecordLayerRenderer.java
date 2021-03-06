package com.revolsys.swing.map.layer.record.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jeometry.common.logging.Logs;

import com.revolsys.collection.list.Lists;
import com.revolsys.collection.map.MapEx;
import com.revolsys.swing.map.layer.Layer;
import com.revolsys.swing.map.layer.LayerRenderer;
import com.revolsys.swing.map.layer.MultipleLayerRenderer;
import com.revolsys.swing.map.layer.record.AbstractRecordLayer;
import com.revolsys.swing.map.layer.record.LayerRecord;
import com.revolsys.swing.map.view.ViewRenderer;
import com.revolsys.swing.menu.MenuFactory;
import com.revolsys.util.JavaBeanUtil;
import com.revolsys.util.Property;

public abstract class AbstractMultipleRecordLayerRenderer extends AbstractRecordLayerRenderer
  implements MultipleLayerRenderer<AbstractRecordLayer, AbstractRecordLayerRenderer> {
  static {
    MenuFactory.addMenuInitializer(AbstractMultipleRecordLayerRenderer.class, menu -> {

      addAddMenuItem(menu, "Geometry", GeometryStyleRecordLayerRenderer::new);
      addAddMenuItem(menu, "Text", TextStyleRenderer::new);
      addAddMenuItem(menu, "Marker", MarkerStyleRenderer::new);
      addAddMenuItem(menu, "Multiple", MultipleRecordRenderer::new);
      addAddMenuItem(menu, "Filter", FilterMultipleRenderer::new);
      addAddMenuItem(menu, "Scale", ScaleMultipleRenderer::new);

      addConvertMenuItem(menu, "Multiple", MultipleRecordRenderer.class,
        AbstractMultipleRecordLayerRenderer::convertToMultipleStyle);
      addConvertMenuItem(menu, "Filter", FilterMultipleRenderer.class,
        AbstractMultipleRecordLayerRenderer::convertToFilterStyle);
      addConvertMenuItem(menu, "Scale", ScaleMultipleRenderer.class,
        AbstractMultipleRecordLayerRenderer::convertToScaleStyle);
    });
  }

  protected static void addAddMenuItem(final MenuFactory menu, final String type,
    final BiFunction<AbstractRecordLayer, AbstractMultipleRecordLayerRenderer, AbstractRecordLayerRenderer> rendererFactory) {
    final String iconName = ("style_" + type + ":add").toLowerCase();
    final String name = "Add " + type + " Style";
    menu.addMenuItem("add", name, iconName,
      (final AbstractMultipleRecordLayerRenderer parentRenderer) -> {
        final AbstractRecordLayer layer = parentRenderer.getLayer();
        final AbstractRecordLayerRenderer newRenderer = rendererFactory.apply(layer,
          parentRenderer);
        parentRenderer.addRendererEdit(newRenderer);
      }, false);
  }

  protected static void addConvertMenuItem(final MenuFactory menu, final String type,
    final Class<?> rendererClass, final Consumer<AbstractMultipleRecordLayerRenderer> consumer) {
    final String iconName = ("style_" + type + "_go").toLowerCase();
    final Predicate<AbstractMultipleRecordLayerRenderer> enabledFilter = (
      final AbstractMultipleRecordLayerRenderer renderer) -> {
      return renderer.getClass() != rendererClass;
    };
    final String name = "Convert to " + type + " Style";
    menu.addMenuItem("convert", -1, name, iconName, enabledFilter, consumer, false);
  }

  protected List<AbstractRecordLayerRenderer> renderers = new ArrayList<>();

  public AbstractMultipleRecordLayerRenderer(final String type, final AbstractRecordLayer layer,
    final LayerRenderer<?> parent) {
    super(type, "Styles", layer, parent);

  }

  public AbstractMultipleRecordLayerRenderer(final String type, final String name) {
    super(type, name);
  }

  @Override
  public int addRenderer(final AbstractRecordLayerRenderer renderer) {
    return addRenderer(-1, renderer);
  }

  @Override
  public int addRenderer(int index, final AbstractRecordLayerRenderer renderer) {
    if (renderer == null) {
      return -1;
    } else {
      final String originalName = renderer.getName();
      String name = originalName;
      int i = 1;
      while (hasRendererWithSameName(renderer, name)) {
        name = originalName + i;
        i++;
      }
      renderer.setName(name);
      renderer.setParent(this);
      synchronized (this.renderers) {
        if (index < 0) {
          index = this.renderers.size();
        }
        this.renderers.add(index, renderer);
      }
      firePropertyChange("renderers", index, null, renderer);
      return index;
    }
  }

  @Override
  public boolean canAddChild(final Object object) {
    return object instanceof AbstractRecordLayerRenderer;
  }

  @Override
  public AbstractMultipleRecordLayerRenderer clone() {
    final AbstractMultipleRecordLayerRenderer clone = (AbstractMultipleRecordLayerRenderer)super.clone();
    clone.renderers = JavaBeanUtil.clone(this.renderers);
    for (final AbstractRecordLayerRenderer renderer : clone.renderers) {
      renderer.setParent(clone);
    }
    return clone;
  }

  public FilterMultipleRenderer convertToFilterStyle() {
    final AbstractRecordLayer layer = getLayer();
    final List<AbstractRecordLayerRenderer> renderers = getRenderers();
    final AbstractMultipleRecordLayerRenderer parent = (AbstractMultipleRecordLayerRenderer)getParent();
    final Map<String, Object> style = toMap();
    style.remove("styles");
    final FilterMultipleRenderer newRenderer = new FilterMultipleRenderer(layer, parent);
    newRenderer.setProperties(style);
    newRenderer.setRenderers(JavaBeanUtil.clone(renderers));
    final String name = getName();
    if (name.equals("Multiple Style")) {
      newRenderer.setName("Filter Style");
    } else if (name.equals("Scale Style")) {
      newRenderer.setName("Filter Style");
    }
    replace(layer, parent, newRenderer);
    return newRenderer;
  }

  public MultipleRecordRenderer convertToMultipleStyle() {
    final AbstractRecordLayer layer = getLayer();
    final List<AbstractRecordLayerRenderer> renderers = getRenderers();
    final AbstractMultipleRecordLayerRenderer parent = (AbstractMultipleRecordLayerRenderer)getParent();
    final Map<String, Object> style = toMap();
    style.remove("styles");
    final MultipleRecordRenderer newRenderer = new MultipleRecordRenderer(layer, parent);
    newRenderer.setProperties(style);

    newRenderer.setRenderers(JavaBeanUtil.clone(renderers));
    final String name = getName();
    if (name.equals("Filter Style")) {
      newRenderer.setName("Multiple Style");
    } else if (name.equals("Scale Style")) {
      newRenderer.setName("Multiple Style");
    }
    replace(layer, parent, newRenderer);
    return newRenderer;
  }

  public ScaleMultipleRenderer convertToScaleStyle() {
    final AbstractRecordLayer layer = getLayer();
    final List<AbstractRecordLayerRenderer> renderers = getRenderers();
    final AbstractMultipleRecordLayerRenderer parent = (AbstractMultipleRecordLayerRenderer)getParent();
    final Map<String, Object> style = toMap();
    style.remove("styles");
    final ScaleMultipleRenderer newRenderer = new ScaleMultipleRenderer(layer, parent);
    newRenderer.setProperties(style);
    newRenderer.setRenderers(JavaBeanUtil.clone(renderers));
    final String name = getName();
    if (name.equals("Filter Style")) {
      newRenderer.setName("Scale Style");
    } else if (name.equals("Multiple Style")) {
      newRenderer.setName("Scale Style");
    }
    replace(layer, parent, newRenderer);
    return newRenderer;
  }

  private List<LayerRecord> filterRecords(final ViewRenderer view, List<LayerRecord> records) {
    if (isHasFilter()) {
      records = Lists.filter(view.cancellable(records), this::isFilterAccept);
    }
    return records;
  }

  public int getRendererCount() {
    return this.renderers.size();
  }

  @Override
  public List<AbstractRecordLayerRenderer> getRenderers() {
    synchronized (this.renderers) {
      return new ArrayList<>(this.renderers);
    }
  }

  public boolean isEmpty() {
    return this.renderers.isEmpty();
  }

  @Override
  public boolean isSameLayer(final Layer layer) {
    return getLayer() == layer;
  }

  @Override
  public boolean isVisible(final LayerRecord record) {
    if (super.isVisible() && super.isVisible(record)) {
      for (int i = 0; i < this.renderers.size(); i++) {
        final AbstractRecordLayerRenderer renderer;
        try {
          renderer = this.renderers.get(i);
        } catch (final ArrayIndexOutOfBoundsException e) {
          return false;
        }
        if (renderer.isVisible(record)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void refreshIcon() {
    if (this.renderers != null) {
      for (final AbstractRecordLayerRenderer renderer : this.renderers) {
        renderer.refreshIcon();
      }
    }
  }

  @Override
  public int removeRenderer(final AbstractRecordLayerRenderer renderer) {
    boolean removed = false;
    synchronized (this.renderers) {
      final int index = this.renderers.indexOf(renderer);
      if (index != -1) {
        if (renderer.getParent() == this) {
          renderer.setParent(null);
        }
        removed = this.renderers.remove(renderer);
      }
      if (removed) {
        firePropertyChange("renderers", index, renderer, null);
      }
      return index;
    }
  }

  protected abstract void renderMultipleRecords(final ViewRenderer view,
    final AbstractRecordLayer layer, final List<LayerRecord> records);

  protected abstract void renderMultipleSelectedRecords(final ViewRenderer view,
    final AbstractRecordLayer layer, final List<LayerRecord> records);

  @Override
  protected final void renderRecords(final ViewRenderer view, final AbstractRecordLayer layer,
    List<LayerRecord> records) {
    if (isVisible(view)) {
      records = filterRecords(view, records);
      renderMultipleRecords(view, layer, records);
    }
  }

  @Override
  protected final void renderSelectedRecordsDo(final ViewRenderer view,
    final AbstractRecordLayer layer, List<LayerRecord> records) {
    if (isVisible(view)) {
      records = filterRecords(view, records);

      renderMultipleSelectedRecords(view, layer, records);
    }
  }

  @Override
  public void setLayer(final AbstractRecordLayer layer) {
    super.setLayer(layer);
    refreshIcon();
  }

  public void setRenderers(final List<? extends AbstractRecordLayerRenderer> renderers) {
    List<AbstractRecordLayerRenderer> oldValue;
    synchronized (this.renderers) {
      oldValue = Lists.toArray(this.renderers);
      for (final AbstractRecordLayerRenderer renderer : this.renderers) {
        renderer.setParent(null);
      }
      if (renderers == null) {
        this.renderers.clear();
      }
      this.renderers = new ArrayList<>(renderers);
      for (final AbstractRecordLayerRenderer renderer : this.renderers) {
        renderer.setParent(this);
      }
    }
    firePropertyChange("renderers", oldValue, this.renderers);
  }

  public void setStyles(final List<?> styles) {
    if (Property.hasValue(styles)) {
      final List<AbstractRecordLayerRenderer> renderers = new ArrayList<>();
      for (final Object childStyle : styles) {
        if (childStyle instanceof AbstractRecordLayerRenderer) {
          final AbstractRecordLayerRenderer renderer = (AbstractRecordLayerRenderer)childStyle;
          renderers.add(renderer);
        } else {
          Logs.error(this, "Cannot create renderer for: " + childStyle);
        }
      }
      setRenderers(renderers);
    }
  }

  @Override
  public MapEx toMap() {
    final MapEx map = super.toMap();
    final List<AbstractRecordLayerRenderer> renderers = getRenderers();
    if (!renderers.isEmpty()) {
      final List<Map<String, Object>> rendererMaps = new ArrayList<>();
      for (final AbstractRecordLayerRenderer renderer : renderers) {
        rendererMaps.add(renderer.toMap());
      }
      addToMap(map, "styles", rendererMaps);
    }
    return map;
  }
}
