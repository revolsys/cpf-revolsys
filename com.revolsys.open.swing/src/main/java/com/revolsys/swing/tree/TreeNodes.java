package com.revolsys.swing.tree;

import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.Icon;

import org.slf4j.LoggerFactory;

import com.revolsys.swing.Icons;
import com.revolsys.swing.action.RunnableAction;
import com.revolsys.swing.action.enablecheck.EnableCheck;
import com.revolsys.swing.menu.MenuFactory;

public interface TreeNodes {
  static <V extends BaseTreeNode> void addMenuItem(final MenuFactory menu, final String groupName,
    final CharSequence name, final String iconName, final Consumer<V> consumer) {
    addMenuItem(menu, groupName, name, iconName, null, consumer);
  }

  static <V extends BaseTreeNode> void addMenuItem(final MenuFactory menu, final String groupName,
    final CharSequence name, final String iconName, final Predicate<V> enabledFilter,
    final Consumer<V> consumer) {
    final Icon icon = Icons.getIcon(iconName);
    final EnableCheck enableCheck = enableCheck(enabledFilter);
    final RunnableAction action = new RunnableAction(name, name.toString(), icon, true, () -> {
      final V object = BaseTree.getMenuNode();
      consumer.accept(object);
    });
    action.setEnableCheck(enableCheck);
    menu.addMenuItem(groupName, action);
  }

  static <V> void addMenuItemNodeValue(final MenuFactory menu, final String groupName,
    final CharSequence name, final String iconName, final Consumer<V> consumer) {
    addMenuItemNodeValue(menu, groupName, name, iconName, null, consumer);
  }

  static <V> void addMenuItemNodeValue(final MenuFactory menu, final String groupName,
    final CharSequence name, final String iconName, final Predicate<V> enabledFilter,
    final Consumer<V> consumer) {
    final Icon icon = Icons.getIcon(iconName);
    final EnableCheck enableCheck = enableCheckNodeValue(enabledFilter);
    final RunnableAction action = new RunnableAction(name, name.toString(), icon, true, () -> {
      final BaseTreeNode node = BaseTree.getMenuNode();
      if (node != null) {
        @SuppressWarnings("unchecked")
        final V value = (V)node.getUserObject();
        if (value != null) {
          consumer.accept(value);
        }
      }
    });
    action.setEnableCheck(enableCheck);
    menu.addMenuItem(groupName, action);
  }

  static <V extends BaseTreeNode> EnableCheck enableCheck(final Predicate<V> filter) {
    if (filter == null) {
      return null;
    } else {
      return () -> {
        final V node = BaseTree.getMenuNode();
        if (node == null) {
          return false;
        } else {
          try {
            return filter.test(node);
          } catch (final Throwable e) {
            LoggerFactory.getLogger(TreeNodes.class).debug("Exception processing enable check", e);
            return false;
          }
        }
      };
    }
  }

  static <V> EnableCheck enableCheckNodeValue(final Predicate<V> filter) {
    if (filter == null) {
      return null;
    } else {
      return () -> {
        final BaseTreeNode node = BaseTree.getMenuNode();
        if (node == null) {
          return false;
        } else {
          @SuppressWarnings("unchecked")
          final V value = (V)node.getUserObject();
          if (value == null) {
            return false;
          } else {
            try {
              return filter.test(value);
            } catch (final Throwable e) {
              LoggerFactory.getLogger(TreeNodes.class).debug("Exception processing enable check",
                e);
              return false;
            }
          }
        }
      };
    }
  }
}
