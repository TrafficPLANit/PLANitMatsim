package org.goplanit.matsim.util;

import org.goplanit.matsim.converter.MatsimNetworkWriter;
import org.goplanit.matsim.converter.MatsimNetworkWriterSettings;
import org.goplanit.test.PlanItTestHelper;
import org.goplanit.utils.misc.FileUtils;
import org.xmlunit.matchers.CompareMatcher;

import java.io.*;
import java.nio.file.Path;

/**
 * Utilities for asserting MATSim outputs for any testinf related code
 */
public class MatsimAssertionUtils {

  private static Path pathOfNetworkFile(String theDir){
    return Path.of(theDir.toString(), MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME + ".xml").toAbsolutePath();
  }

  private static Path pathOfTransitScheduleFile(String theDir){
    return Path.of(theDir.toString(), MatsimNetworkWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME + ".xml").toAbsolutePath();
  }

  private static Path pathOfNetworkGeometryFile(String theDir){
    return Path.of(theDir, MatsimNetworkWriter.DEFAULT_NETWORK_GEOMETRY_FILE_NAME + MatsimNetworkWriter.DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION).toAbsolutePath();
  }

  private static void assertXmlFileContentSimilar(String file1, String file2) throws IOException {
    org.hamcrest.MatcherAssert.assertThat(
        /* xml unit functionality comparing the two files */
        FileUtils.parseUtf8FileContentAsString(file1),
        CompareMatcher.isSimilarTo(FileUtils.parseUtf8FileContentAsString(file2)));
  }

  public static boolean isNetworkGeometryFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfNetworkGeometryFile(resultDir).toString();
    String referenceFile = pathOfNetworkGeometryFile(referenceDir).toString();

    return PlanItTestHelper.compareFilesExact(resultFile, referenceFile, true);
  }

  public static boolean isNetworkGeometryFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    return isNetworkGeometryFilesSimilar(resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }

  public static void assertNetworkFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfNetworkFile(resultDir).toString();
    String referenceFile = pathOfNetworkFile(referenceDir).toString();

    assertXmlFileContentSimilar(resultFile, referenceFile);
  }

  public static void assertNetworkFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    assertNetworkFilesSimilar(resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }


  public static void assertTransitScheduleFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfTransitScheduleFile(resultDir).toString();
    String referenceFile = pathOfTransitScheduleFile(referenceDir).toString();

    assertXmlFileContentSimilar(resultFile, referenceFile);
  }

  public static void assertTransitScheduleFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    assertTransitScheduleFilesSimilar(resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }
}
