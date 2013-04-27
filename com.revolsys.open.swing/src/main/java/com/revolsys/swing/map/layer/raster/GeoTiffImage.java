package com.revolsys.swing.map.layer.raster;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.media.jai.JAI;
import javax.media.jai.OperationRegistry;
import javax.media.jai.RenderedOp;

import org.libtiff.jai.codec.XTIFF;
import org.libtiff.jai.codec.XTIFFDirectory;
import org.libtiff.jai.codec.XTIFFField;
import org.libtiff.jai.codecimpl.XTIFFCodec;
import org.libtiff.jai.operator.XTIFFDescriptor;
import org.springframework.core.io.Resource;

import com.revolsys.gis.cs.BoundingBox;
import com.revolsys.gis.cs.GeometryFactory;
import com.revolsys.spring.SpringUtil;
import com.sun.media.jai.codec.ImageCodec;

public class GeoTiffImage extends GeoReferencedImage {
  static {
    try {
      final OperationRegistry reg = JAI.getDefaultInstance()
        .getOperationRegistry();
      ImageCodec.unregisterCodec("tiff");
      reg.unregisterOperationDescriptor("tiff");

      ImageCodec.registerCodec(new XTIFFCodec());
      final XTIFFDescriptor descriptor = new XTIFFDescriptor();

      reg.registerDescriptor(descriptor);

    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  private static final int PROJECTED_CS_TYPE_GEO_KEY = 3072;

  private static final int GEOGRAPHIC_TYPE_GEO_KEY = 2048;

  private static void addGeoKey(final Map<Integer, Object> geoKeys,
    final XTIFFDirectory dir, final int keyId, final int tiffTag,
    final int valueCount, final int valueOrOffset) {
    int type = XTIFFField.TIFF_SHORT;
    Object value = null;
    if (tiffTag > 0) {
      // Values are in another tag:
      final XTIFFField values = dir.getField(tiffTag);
      if (values != null) {
        type = values.getType();
        if (type == XTIFFField.TIFF_ASCII) {
          final String string = values.getAsString(0).substring(valueOrOffset,
            valueOrOffset + valueCount - 1);
          value = string;
        } else if (type == XTIFFField.TIFF_DOUBLE) {
          final double number = values.getAsDouble(valueOrOffset);
          value = number;
        }
      } else {
        throw new IllegalArgumentException("GeoTIFF tag not found");
      }
    } else {
      // value is SHORT, stored in valueOrOffset
      type = XTIFFField.TIFF_SHORT;
      value = (short)valueOrOffset;
    }

    geoKeys.put(keyId, value);
  }

  private static Map<Integer, Object> getGeoKeys(final XTIFFDirectory dir) {
    final Map<Integer, Object> geoKeys = new LinkedHashMap<Integer, Object>();

    final XTIFFField geoKeyTag = dir.getField(XTIFF.TIFFTAG_GEO_KEY_DIRECTORY);

    if (geoKeyTag != null) {
      final char[] keys = geoKeyTag.getAsChars();
      for (int i = 4; i < keys.length; i += 4) {
        final int keyId = keys[i];
        final int tiffTag = keys[i + 1];
        final int valueCount = keys[i + 2];
        final int valueOrOffset = keys[i + 3];
        addGeoKey(geoKeys, dir, keyId, tiffTag, valueCount, valueOrOffset);
      }

    }
    return geoKeys;
  }

  public GeoTiffImage(final Resource imageResource) {
    final File file = SpringUtil.getOrDownloadFile(imageResource);
    final RenderedOp image = JAI.create("fileload", file.getAbsolutePath());
    setImage(image.getAsBufferedImage());
    loadImageMetaData(imageResource, image);
  }

  private boolean loadGeoTiffMetaData(final XTIFFDirectory directory) {
    Map<Integer, Object> geoKeys = getGeoKeys(directory);
    Short coordinateSystemId = (Short)geoKeys.get(PROJECTED_CS_TYPE_GEO_KEY);
    if (coordinateSystemId == null) {
      coordinateSystemId = (Short)geoKeys.get(GEOGRAPHIC_TYPE_GEO_KEY);
    }
    if (coordinateSystemId != null) {
      GeometryFactory geometryFactory = GeometryFactory.getFactory(coordinateSystemId);
      setGeometryFactory(geometryFactory);
    }

    final XTIFFField tiePoints = directory.getField(XTIFF.TIFFTAG_GEO_TIEPOINTS);
    if (tiePoints == null) {
      final XTIFFField geoTransform = directory.getField(XTIFF.TIFFTAG_GEO_TRANS_MATRIX);
      if (geoTransform == null) {
        return false;
      } else {
        final double x1 = geoTransform.getAsDouble(3);
        final double y1 = geoTransform.getAsDouble(7);
        final double pixelWidth = geoTransform.getAsDouble(0);
        final double pixelHeight = geoTransform.getAsDouble(5);
        final double xRotation = geoTransform.getAsDouble(4);
        final double yRotation = geoTransform.getAsDouble(1);

        // TODO rotation
        setBoundingBox(x1, y1, pixelWidth, pixelHeight);
        return true;
      }
    } else {
      final XTIFFField pixelScale = directory.getField(XTIFF.TIFFTAG_GEO_PIXEL_SCALE);
      if (pixelScale == null) {
        return false;
      } else {
        final double rasterXOffset = tiePoints.getAsDouble(0);
        final double rasterYOffset = tiePoints.getAsDouble(1);
        if (rasterXOffset != 0 && rasterYOffset != 0) {
          // These should be 0, not sure what to do if they are not
          throw new IllegalArgumentException(
            "Exepectig 0 for the raster x,y tie points in a GeoTIFF");
        }
        // double rasterZOffset = fieldModelTiePoints.getAsDouble(2);
        // setTopLeftRasterPoint(new DoubleCoordinates(
        // rasterXOffset,
        // rasterYOffset));

        // Top left corner of image in model coordinates
        final double x1 = tiePoints.getAsDouble(3);
        final double y1 = tiePoints.getAsDouble(4);
        // double modelZOffset = fieldModelTiePoints.getAsDouble(5);
        // setTopLeftModelPoint(new DoubleCoordinates(
        // modelXOffset,
        // modelYOffset));
        final double pixelWidth = pixelScale.getAsDouble(0);
        final double pixelHeight = pixelScale.getAsDouble(1);

        setBoundingBox(x1, y1, pixelWidth, pixelHeight);
        return true;
      }
    }
  }

  public void setBoundingBox(final double x1, final double y1,
    final double pixelWidth, final double pixelHeight) {
    final GeometryFactory geometryFactory = getGeometryFactory();

    final int imageWidth = getImageWidth();
    final double x2 = x1 + pixelWidth * imageWidth;

    final int imageHeight = getImageHeight();
    final double y2 = y1 - pixelHeight * imageHeight;
    final BoundingBox boundingBox = new BoundingBox(geometryFactory, x1,
      y1, x2, y2);
    setBoundingBox(boundingBox);
  }

  protected void loadImageMetaData(final Resource resource,
    final RenderedOp image) {
    final Object tiffDirectory = image.getProperty("tiff.directory");
    if (tiffDirectory == null) {
      throw new IllegalArgumentException(
        "This is not a (geo)tiff file. Missing TIFF directory.");
    } else {
      if (!(tiffDirectory instanceof XTIFFDirectory)
        || !loadGeoTiffMetaData((XTIFFDirectory)tiffDirectory)) {
        loadProjectionFile(resource);
        loadWorldFile(resource, "tfw");
      }
    }
  }
}