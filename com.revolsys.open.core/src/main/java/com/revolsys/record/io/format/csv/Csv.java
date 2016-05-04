package com.revolsys.record.io.format.csv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.revolsys.datatype.DataTypes;
import com.revolsys.io.FileUtil;
import com.revolsys.io.map.IteratorMapReader;
import com.revolsys.io.map.MapReader;
import com.revolsys.io.map.MapWriter;
import com.revolsys.io.map.MapWriterFactory;
import com.revolsys.record.Record;
import com.revolsys.record.RecordFactory;
import com.revolsys.record.io.AbstractRecordIoFactory;
import com.revolsys.record.io.RecordReader;
import com.revolsys.record.io.RecordWriter;
import com.revolsys.record.io.RecordWriterFactory;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.spring.resource.Resource;
import com.revolsys.util.Exceptions;
import com.revolsys.util.Property;
import com.revolsys.util.WrappedException;

public class Csv extends AbstractRecordIoFactory implements RecordWriterFactory, MapWriterFactory {
  public static final char FIELD_SEPARATOR = ',';

  public static MapReader mapReader(final File file) {
    try {
      final FileInputStream in = new FileInputStream(file);
      return mapReader(in);
    } catch (final FileNotFoundException e) {
      throw new WrappedException(e);
    }
  }

  public static MapReader mapReader(final InputStream in) {
    final InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
    return mapReader(reader);
  }

  public static MapReader mapReader(final java.io.Reader reader) {
    try {
      final CsvMapIterator iterator = new CsvMapIterator(reader);
      return new IteratorMapReader(iterator);
    } catch (final IOException e) {
      throw new WrappedException(e);
    }
  }

  public static MapReader mapReader(final String string) {
    final StringReader reader = new StringReader(string);
    return mapReader(reader);
  }

  public static List<String> parseLine(final String text) {
    final StringBuilder sb = new StringBuilder();
    sb.delete(0, sb.length());
    final List<String> fields = new ArrayList<>();

    if (Property.hasValue(text)) {
      boolean inQuotes = false;
      boolean hadQuotes = false;
      final int length = text.length();
      for (int i = 0; i < length; i++) {
        final char c = text.charAt(i);
        switch (c) {
          case '"':
            if (i < length - 1) {
              hadQuotes = true;

              final char nextChar = text.charAt(i + 1);
              if (inQuotes && nextChar == '"') {
                sb.append('"');
                i++;
              } else {
                inQuotes = !inQuotes;
                if (sb.length() > 0 && nextChar != ',' && nextChar != '\n' && nextChar != 0) {
                  sb.append(c);
                }
              }
            } else {
              if (inQuotes) {
                fields.add(sb.toString());
              }
              return fields;
            }

          break;
          case ',':
            if (inQuotes) {
              sb.append(c);
            } else {
              if (hadQuotes || sb.length() > 0) {
                fields.add(sb.toString());
                sb.delete(0, sb.length());
              } else {
                fields.add(null);
              }
              hadQuotes = false;
            }
          break;
          case '\r':
            if (i < length - 1) {
              if (text.charAt(i + 1) == '\n') {
              } else {
                if (inQuotes) {
                  sb.append('\n');
                } else {
                  if (hadQuotes || sb.length() > 0) {
                    fields.add(sb.toString());
                    sb.delete(0, sb.length());
                  } else {
                    fields.add(null);
                  }
                  return fields;
                }
              }
            }
          break;
          case '\n':
            if (i > length - 1) {
              if (text.charAt(i + 1) == '\r') {
                i++;
              }
              if (inQuotes) {
                sb.append(c);
              } else {
                if (hadQuotes || sb.length() > 0) {
                  fields.add(sb.toString());
                  sb.delete(0, sb.length());
                } else {
                  fields.add(null);
                }
                return fields;
              }
            }
          break;
          default:
            sb.append(c);
          break;
        }
      }
    }
    if (sb.length() > 0) {
      fields.add(sb.toString());
    }
    return fields;
  }

  public static CsvWriter plainWriter(final File file) {
    if (file == null) {
      throw new NullPointerException("File must not be null");
    } else {
      final java.io.Writer writer = FileUtil.newUtf8Writer(file);
      return plainWriter(writer);
    }
  }

  public static CsvWriter plainWriter(final java.io.Writer writer) {
    return new CsvWriter(writer);
  }

  /**
   * Convert a to a CSV string with a header row and a data row.
   *
   * @param map The to convert to CSV
   * @return The CSV string.
   */
  public static String toCsv(final Map<String, ? extends Object> map) {
    final StringWriter csvString = new StringWriter();
    try (
      final CsvMapWriter csvMapWriter = new CsvMapWriter(csvString)) {
      csvMapWriter.write(map);
      return csvString.toString();
    }
  }

  public static Map<String, String> toMap(final String businessApplicationParameters) {
    final HashMap<String, String> map = new LinkedHashMap<String, String>();
    final CsvIterator iterator = new CsvIterator(new StringReader(businessApplicationParameters));
    if (iterator.hasNext()) {
      final List<String> keys = iterator.next();
      if (iterator.hasNext()) {
        final List<String> values = iterator.next();
        for (int i = 0; i < keys.size() && i < values.size(); i++) {
          map.put(keys.get(i), values.get(i));
        }
      }
    }
    return map;
  }

  public static Map<String, Object> toObjectMap(final String businessApplicationParameters) {
    final HashMap<String, Object> map = new LinkedHashMap<>();
    final CsvIterator iterator = new CsvIterator(new StringReader(businessApplicationParameters));
    if (iterator.hasNext()) {
      final List<String> keys = iterator.next();
      if (iterator.hasNext()) {
        final List<String> values = iterator.next();
        for (int i = 0; i < keys.size() && i < values.size(); i++) {
          map.put(keys.get(i), values.get(i));
        }
      }
    }
    return map;
  }

  /*
   * Replaces whitespace with spaces
   */
  public static void writeColumns(final StringWriter out,
    final Collection<? extends Object> columns, final char fieldSeparator,
    final char recordSeparator) {
    boolean first = true;
    for (final Object value : columns) {
      if (first) {
        first = false;
      } else {
        out.write(fieldSeparator);
      }
      if (value != null) {
        String text = DataTypes.toString(value);
        text = text.replaceAll("\\s", " ");

        out.write(text);
      }
    }
    out.write(recordSeparator);
  }

  public Csv() {
    super("Comma-Separated Values");
    addMediaTypeAndFileExtension("text/csv", "csv");
  }

  @Override
  public MapReader newMapreader(final Resource resource) {
    try {
      final CsvMapIterator iterator = new CsvMapIterator(resource.newReader());
      return new IteratorMapReader(iterator);
    } catch (final IOException e) {
      return Exceptions.throwUncheckedException(e);
    }
  }

  @Override
  public MapWriter newMapWriter(final java.io.Writer out) {
    return new CsvMapWriter(out);
  }

  @Override
  public RecordReader newRecordReader(final Resource resource,
    final RecordFactory<? extends Record> recordFactory) {
    return new CsvRecordReader(resource, recordFactory);
  }

  @Override
  public RecordWriter newRecordWriter(final RecordDefinition recordDefinition, final Path path) {
    return new CsvRecordWriter(recordDefinition, path, Csv.FIELD_SEPARATOR, true, true);
  }

  @Override
  public RecordWriter newRecordWriter(final String baseName,
    final RecordDefinition recordDefinition, final OutputStream outputStream,
    final Charset charset) {
    final OutputStreamWriter writer = new OutputStreamWriter(outputStream, charset);

    return new CsvRecordWriter(recordDefinition, writer, Csv.FIELD_SEPARATOR, true, true);
  }
}
