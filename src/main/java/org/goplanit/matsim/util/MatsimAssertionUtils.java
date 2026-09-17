package org.goplanit.matsim.util;

import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterSettings;
import org.goplanit.matsim.converter.network.MatsimNetworkWriter;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.test.PlanItTestHelper;
import org.goplanit.utils.misc.FileUtils;
import org.xmlunit.matchers.CompareMatcher;

import java.io.*;
import java.nio.file.Path;

/**
 * Utilities for asserting MATSim outputs for any testing related code
 */
public class MatsimAssertionUtils {

  /** dummy constructor */
  private MatsimAssertionUtils(){}

  private static Path pathOfNetworkFile(String theDir){
    return Path.of(
        theDir, MatsimNetworkWriterSettings.DEFAULT_NETWORK_FILE_NAME + ".xml").toAbsolutePath();
  }

  private static Path pathOfTransitScheduleFile(String theDir){
    return Path.of(
        theDir, MatsimNetworkWriterSettings.DEFAULT_TRANSIT_SCHEDULE_FILE_NAME + ".xml").toAbsolutePath();
  }

  private static Path pathOfNetworkGeometryFile(String theDir){
    return Path.of(theDir,
        MatsimNetworkWriter.DEFAULT_NETWORK_GEOMETRY_FILE_NAME +
            MatsimNetworkWriter.DEFAULT_NETWORK_GEOMETRY_FILE_NAME_EXTENSION).toAbsolutePath();
  }

  private static Path pathOfPtStopsFile(String theDir){
    return Path.of(theDir,
        MatsimNetworkWriter.DEFAULT_PT_STOPS_FILE_NAME +
            MatsimNetworkWriter.DEFAULT_PT_STOPS_FILE_NAME_EXTENSION).toAbsolutePath();
  }

  private static Path pathOfPlansFile(String theDir){
    return Path.of(
        theDir, MatsimDiscreteDemandsWriterSettings.DEFAULT_PLANS_FILE_NAME + ".xml").toAbsolutePath();
  }

  /**
   * check xml file content is similar
   * @param file1 to use
   * @param file2 to use
   * @throws IOException throw if error
   */
  private static void assertXmlFileContentSimilar(String file1, String file2) throws IOException {
    org.hamcrest.MatcherAssert.assertThat(
        /* xml unit functionality comparing the two files */
        FileUtils.parseUtf8FileContentAsString(file1),
        CompareMatcher.isSimilarTo(FileUtils.parseUtf8FileContentAsString(file2)));
  }

  /**
   * check network geometry files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @return check result
   * @throws IOException throw if error
   */
  public static boolean isNetworkGeometryFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfNetworkGeometryFile(resultDir).toString();
    String referenceFile = pathOfNetworkGeometryFile(referenceDir).toString();

    return PlanItTestHelper.compareFilesExact(resultFile, referenceFile, true);
  }

  /**
   * check pt stops files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @return check result
   * @throws IOException throw if error
   */
  public static boolean isPtStopsFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfPtStopsFile(resultDir).toString();
    String referenceFile = pathOfPtStopsFile(referenceDir).toString();

    return PlanItTestHelper.compareFilesExact(resultFile, referenceFile, true);
  }

  /**
   * check network geometry files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @return check result
   * @throws IOException throw if error
   */
  public static boolean isNetworkGeometryFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    return isNetworkGeometryFilesSimilar(
        resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }

  /**
   * check pt stops files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @return check result
   * @throws IOException throw if error
   */
  public static boolean isPtStopsFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    return isPtStopsFilesSimilar(
        resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }

  /**
   * check network files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @throws IOException throw if error
   */
  public static void assertNetworkFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfNetworkFile(resultDir).toString();
    String referenceFile = pathOfNetworkFile(referenceDir).toString();

    assertXmlFileContentSimilar(resultFile, referenceFile);
  }

  /**
   * check network files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @throws IOException throw if error
   */
  public static void assertNetworkFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    assertNetworkFilesSimilar(resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }

  /**
   * check transit schedule files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @throws IOException throw if error
   */
  public static void assertTransitScheduleFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfTransitScheduleFile(resultDir).toString();
    String referenceFile = pathOfTransitScheduleFile(referenceDir).toString();

    assertXmlFileContentSimilar(resultFile, referenceFile);
  }

  /**
   * check plans files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @throws IOException throw if error
   */
  public static void assertPlansFilesSimilar(String resultDir, String referenceDir) throws IOException {
    String resultFile = pathOfPlansFile(resultDir).toString();
    String referenceFile = pathOfPlansFile(referenceDir).toString();

    assertXmlFileContentSimilar(resultFile, referenceFile);
  }

  /**
   * check transit schedule files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @throws IOException throw if error
   */
  public static void assertTransitScheduleFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    assertTransitScheduleFilesSimilar(resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }

  /**
   * check plans files are similar
   * @param resultDir to use
   * @param referenceDir to use
   * @throws IOException throw if error
   */
  public static void assertPlansFilesSimilar(Path resultDir, Path referenceDir) throws IOException {
    assertPlansFilesSimilar(resultDir.toAbsolutePath().toString(), referenceDir.toAbsolutePath().toString());
  }
}
