package com.revolsys.raster.io.format.tiff.directory.entry;

import java.util.Arrays;

import com.revolsys.io.channels.ChannelReader;
import com.revolsys.io.channels.ChannelWriter;
import com.revolsys.raster.io.format.tiff.code.TiffFieldType;

public class TiffDirectoryEntrySignedShortArray extends AbstractTiffDirectoryEntry<short[]> {

  @Override
  public Number getNumber() {
    if (getCount() == 1) {
      return this.value[0];
    } else {
      throw new IllegalStateException("Cannot get single value from array of size " + getCount());
    }
  }

  @Override
  public Number getNumber(final int index) {
    return this.value[index];
  }

  @Override
  public short getShort(final int index) {
    return this.value[index];
  }

  @Override
  public String getString() {
    return Arrays.toString(this.value);
  }

  @Override
  public TiffFieldType getType() {
    return TiffFieldType.SSHORT;
  }

  @Override
  protected short[] loadValueDo(final ChannelReader in, final int count) {
    final short[] value = new short[count];
    for (int i = 0; i < count; i++) {
      value[i] = in.getShort();
    }
    return value;
  }

  @Override
  public void writeValueDo(final ChannelWriter out) {
    for (int i = 0; i < this.value.length; i++) {
      final short number = this.value[i];
      out.putShort(number);
    }
  }
}
