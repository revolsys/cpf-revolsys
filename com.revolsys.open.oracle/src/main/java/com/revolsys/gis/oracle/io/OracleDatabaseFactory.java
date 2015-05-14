package com.revolsys.gis.oracle.io;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import oracle.net.jdbc.nl.NLParamParser;

import org.slf4j.LoggerFactory;

import com.revolsys.converter.string.StringConverterRegistry;
import com.revolsys.data.record.schema.RecordStore;
import com.revolsys.data.types.DataType;
import com.revolsys.jdbc.io.AbstractJdbcDatabaseFactory;
import com.revolsys.jdbc.io.JdbcRecordStore;

/**
jdbc:oracle:thin:@//<host>:<port>/<ServiceName>
jdbc:oracle:thin:@<host>:<port>:<SID>
jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=<host>)(PORT=<port>))(CONNECT_DATA=(SERVICE_NAME=<service>)))
jdbc:oracle:oci:@<tnsname>
jdbc:oracle:oci:@<host>:<port>:<sid>
jdbc:oracle:oci:@<host>:<port>/<service>
 */
public class OracleDatabaseFactory extends AbstractJdbcDatabaseFactory {
  public static final String URL_REGEX = "jdbc:oracle:thin:(.+)";

  public static final List<String> URL_PATTERNS = Arrays.asList(URL_REGEX);

  private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

  public static List<String> getTnsConnectionNames() {
    File tnsFile = new File(System.getProperty("oracle.net.tns_admin"),
      "tnsnames.ora");
    if (!tnsFile.exists()) {
      tnsFile = new File(System.getenv("TNS_ADMIN"), "tnsnames.ora");
      if (!tnsFile.exists()) {
        tnsFile = new File(System.getenv("ORACLE_HOME") + "/network/admin",
          "tnsnames.ora");
        if (!tnsFile.exists()) {
          tnsFile = new File(System.getenv("ORACLE_HOME") + "/NETWORK/ADMIN",
            "tnsnames.ora");

        }
      }
    }
    if (tnsFile.exists()) {
      try {
        FileReader reader = new FileReader(tnsFile);
        final NLParamParser parser = new NLParamParser(reader);
        return Arrays.asList(parser.getNLPAllNames());
      } catch (final Throwable e) {
        LoggerFactory.getLogger(OracleDatabaseFactory.class).error(
          "Error reading: " + tnsFile, e);
      }
    }
    return Collections.emptyList();
  }

  protected void addCacheProperty(final Map<String, Object> config,
    final String key, final Properties cacheProperties,
    final String propertyName, final Object defaultValue,
    final DataType dataType) {
    Object value = config.remove(key);
    if (value == null) {
      value = config.get(propertyName);
    }
    cacheProperties.put(propertyName, String.valueOf(defaultValue));
    if (value != null) {
      try {
        final Object propertyValue = StringConverterRegistry.toObject(dataType,
          value);
        final String stringValue = String.valueOf(propertyValue);
        cacheProperties.put(propertyName, stringValue);
      } catch (final Throwable e) {
      }
    }
  }

  @Override
  public boolean canHandleUrl(final String url) {
    final Matcher urlMatcher = URL_PATTERN.matcher(url);
    return urlMatcher.matches();
  }

  @Override
  public JdbcRecordStore createRecordStore(final DataSource dataSource) {
    return new OracleRecordStore(dataSource);
  }

  @Override
  public JdbcRecordStore createRecordStore(
    final Map<String, ? extends Object> connectionProperties) {
    return new OracleRecordStore(this, connectionProperties);
  }

  @Override
  public String getConnectionValidationQuery() {
    return "SELECT 1 FROM DUAL";
  }

  @Override
  public String getDriverClassName() {
    return "oracle.jdbc.OracleDriver";
  }

  @Override
  public String getName() {
    return "Oracle Database";
  }

  @Override
  public List<String> getProductNames() {
    return Collections.singletonList("Oracle");
  }

  @Override
  public List<String> getRecordStoreFileExtensions() {
    return Collections.emptyList();
  }

  @Override
  public Class<? extends RecordStore> getRecordStoreInterfaceClass(
    final Map<String, ? extends Object> connectionProperties) {
    return JdbcRecordStore.class;
  }

  @Override
  public List<String> getUrlPatterns() {
    return URL_PATTERNS;
  }
}